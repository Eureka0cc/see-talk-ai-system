package com.seetalk.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.seetalk.cost.FrameRateLimiter;
import com.seetalk.session.ChatSession;
import com.seetalk.session.ChatSessionManager;
import com.seetalk.service.ChatPersistenceService;
import com.seetalk.service.VisionChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final int SEND_TIME_LIMIT_MS = 5000;
    private static final int SEND_BUFFER_SIZE_LIMIT = 512 * 1024;

    private final ChatSessionManager sessionManager;
    private final VisionChatService visionChatService;
    private final ChatPersistenceService persistenceService;
    private final FrameRateLimiter frameRateLimiter;
    private final Map<String, Long> wsToChatSession = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> streamingSessions = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> decoratedSessions = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(
            ChatSessionManager sessionManager,
            VisionChatService visionChatService,
            ChatPersistenceService persistenceService,
            FrameRateLimiter frameRateLimiter) {
        this.sessionManager = sessionManager;
        this.visionChatService = visionChatService;
        this.persistenceService = persistenceService;
        this.frameRateLimiter = frameRateLimiter;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        long start = System.currentTimeMillis();
        Long sessionId = persistenceService.allocateSessionId();
        ChatSession chatSession = sessionManager.create(sessionId);
        wsToChatSession.put(session.getId(), sessionId);
        persistenceService.initSessionAsync(sessionId);
        sendSafe(session, WsMessage.session(chatSession.getId(), "连接成功，开始对话吧！"));
        log.info("WebSocket connected wsId={} sessionId={} remote={} cost={}ms",
                session.getId(), sessionId, session.getRemoteAddress(), System.currentTimeMillis() - start);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode data = WsMessage.parse(message.getPayload());
        String type = data.path("type").asText();
        log.debug("WebSocket message wsId={} type={} bytes={}",
                session.getId(), type, message.getPayloadLength());

        if ("ping".equals(type)) {
            sendSafe(session, WsMessage.pong());
            return;
        }

        Long chatSessionId = wsToChatSession.get(session.getId());
        ChatSession chatSession = chatSessionId != null ? sessionManager.get(chatSessionId) : null;
        if (chatSession == null) {
            session.sendMessage(new TextMessage(WsMessage.error("会话已过期，请刷新页面重连")));
            return;
        }

        switch (type) {
            case "user_message" -> handleUserMessage(session, chatSession, data);
            case "clear_history" -> {
                chatSession.clearHistory();
                sessionManager.save(chatSession);
                session.sendMessage(new TextMessage(WsMessage.historyCleared()));
            }
            default -> session.sendMessage(new TextMessage(WsMessage.error("未知消息类型: " + type)));
        }
    }

    private void handleUserMessage(WebSocketSession session, ChatSession chatSession, JsonNode data)
            throws Exception {
        long start = System.currentTimeMillis();
        if (Boolean.TRUE.equals(streamingSessions.get(chatSession.getId()))) {
            sendSafe(session, WsMessage.error("请等待当前回复完成"));
            return;
        }

        String text = data.path("text").asText("");
        String image = data.has("image") && !data.get("image").isNull()
                ? data.get("image").asText(null)
                : null;
        boolean hasImage = image != null && !image.isBlank();

        if (text.isBlank() && !hasImage) {
            sendSafe(session, WsMessage.error("消息内容为空"));
            return;
        }

        log.info("User message received session={} textLen={} hasImage={} imageBytes={}",
                chatSession.getId(), text.length(), hasImage, hasImage ? image.length() : 0);

        sendSafe(session, WsMessage.thinking());

        try {
            VisionChatService.UserTurnContext turn =
                    visionChatService.prepareUserTurn(chatSession, text, image);
            long prepareMs = System.currentTimeMillis() - start;

            String messageId = UUID.randomUUID().toString();
            WebSocketSession safeSession = decorateSession(session);

            sendSafe(safeSession, WsMessage.assistantStart(messageId, turn.usedVision()));
            log.info("Assistant stream started session={} messageId={} useVision={} prepare={}ms",
                    chatSession.getId(), messageId, turn.usedVision(), prepareMs);

            streamingSessions.put(chatSession.getId(), true);

            visionChatService.streamResponse(
                    chatSession,
                    chunk -> sendSafe(safeSession, WsMessage.assistantDelta(messageId, chunk)),
                    fullText -> {
                        streamingSessions.remove(chatSession.getId());
                        sendSafe(safeSession, WsMessage.assistantDone(messageId, fullText, turn.usedVision()));
                        visionChatService.finalizeTurnAsync(
                                chatSession, turn.userText(), fullText, turn.usedVision());
                        log.info("User message handled session={} messageId={} total={}ms responseChars={}",
                                chatSession.getId(), messageId, System.currentTimeMillis() - start, fullText.length());
                    },
                    error -> {
                        streamingSessions.remove(chatSession.getId());
                        log.error("AI stream failed session={} elapsed={}ms",
                                chatSession.getId(), System.currentTimeMillis() - start, error);
                        sendSafe(safeSession, WsMessage.error("AI 调用失败: " + error.getMessage()));
                    });
        } catch (Exception e) {
            streamingSessions.remove(chatSession.getId());
            log.error("AI call failed session={} elapsed={}ms",
                    chatSession.getId(), System.currentTimeMillis() - start, e);
            sendSafe(session, WsMessage.error("AI 调用失败: " + e.getMessage()));
        }
    }

    private WebSocketSession decorateSession(WebSocketSession session) {
        return decoratedSessions.computeIfAbsent(session.getId(), id ->
                new ConcurrentWebSocketSessionDecorator(
                        session, SEND_TIME_LIMIT_MS, SEND_BUFFER_SIZE_LIMIT));
    }

    private void sendSafe(WebSocketSession session, String payload) {
        if (session == null || !session.isOpen()) {
            log.debug("Skip send on closed WebSocket wsId={}", session != null ? session.getId() : "null");
            return;
        }
        try {
            session.sendMessage(new TextMessage(payload));
        } catch (Exception e) {
            log.warn("Failed to send WebSocket message wsId={} type={}",
                    session.getId(), extractMessageType(payload), e);
        }
    }

    private String extractMessageType(String payload) {
        try {
            return WsMessage.parse(payload).path("type").asText("unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        decoratedSessions.remove(session.getId());
        Long chatSessionId = wsToChatSession.remove(session.getId());
        log.info("WebSocket closed wsId={} sessionId={} status={}",
                session.getId(), chatSessionId, status);
        if (chatSessionId != null) {
            streamingSessions.remove(chatSessionId);
            frameRateLimiter.cleanup(chatSessionId);
            sessionManager.remove(chatSessionId);
        }
    }
}
