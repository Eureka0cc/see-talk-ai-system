package com.seetalk.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.seetalk.config.SeeTalkProperties;
import com.seetalk.model.constants.ChatConstants;
import com.seetalk.model.constants.WebSocketConstants;
import com.seetalk.model.entity.ChatMessageEntity;
import com.seetalk.rate.FrameRateLimiter;
import com.seetalk.session.ChatSession;
import com.seetalk.session.ChatSessionManager;
import com.seetalk.service.ChatPersistenceService;
import com.seetalk.service.VisionChatService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final ChatSessionManager sessionManager;
    private final VisionChatService visionChatService;
    private final ChatPersistenceService persistenceService;
    private final FrameRateLimiter frameRateLimiter;
    private final SeeTalkProperties properties;
    private final Map<String, Long> wsToChatSession = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> streamingSessions = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> decoratedSessions = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(
            ChatSessionManager sessionManager,
            VisionChatService visionChatService,
            ChatPersistenceService persistenceService,
            FrameRateLimiter frameRateLimiter,
            SeeTalkProperties properties) {
        this.sessionManager = sessionManager;
        this.visionChatService = visionChatService;
        this.persistenceService = persistenceService;
        this.frameRateLimiter = frameRateLimiter;
        this.properties = properties;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        long start = System.currentTimeMillis();
        ChatSession chatSession = bindChatSession(session);
        sendSafe(session, WsMessage.session(chatSession.getId(), WebSocketConstants.CONNECTED_MSG));
        log.info("WebSocket connected wsId={} sessionId={} remote={} cost={}ms",
                session.getId(), chatSession.getId(), session.getRemoteAddress(), System.currentTimeMillis() - start);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode data = WsMessage.parse(message.getPayload());
        String type = data.path("type").asText();
        log.debug("WebSocket message wsId={} type={} bytes={}",
                session.getId(), type, message.getPayloadLength());

        if (WebSocketConstants.MSG_TYPE_PING.equals(type)) {
            sendSafe(session, WsMessage.pong());
            return;
        }

        Long chatSessionId = wsToChatSession.get(session.getId());
        ChatSession chatSession = chatSessionId != null ? sessionManager.get(chatSessionId) : null;
        if (chatSession == null) {
            session.sendMessage(new TextMessage(
                    WsMessage.error(WebSocketConstants.SESSION_EXPIRED_MSG)));
            return;
        }

        switch (type) {
            case WebSocketConstants.MSG_TYPE_USER_MESSAGE ->
                    handleUserMessage(session, chatSession, data);
            case WebSocketConstants.MSG_TYPE_CLEAR_HISTORY -> {
                chatSession.clearHistory();
                try {
                    persistenceService.clearSessionMessages(chatSession.getId());
                } catch (Exception e) {
                    log.error("Failed to clear persisted messages for session {}",
                            chatSession.getId(), e);
                }
                sessionManager.save(chatSession);
                session.sendMessage(new TextMessage(WsMessage.historyCleared()));
            }
            default -> session.sendMessage(new TextMessage(
                    WsMessage.error(WebSocketConstants.UNKNOWN_TYPE_PREFIX + type)));
        }
    }

    private void handleUserMessage(WebSocketSession session, ChatSession chatSession, JsonNode data)
            throws Exception {
        long start = System.currentTimeMillis();
        if (Boolean.TRUE.equals(streamingSessions.get(chatSession.getId()))) {
            sendSafe(session, WsMessage.error(WebSocketConstants.WAIT_RESPONSE_MSG));
            return;
        }

        String text = data.path(WebSocketConstants.FIELD_TEXT).asText("");
        String image = data.has(WebSocketConstants.FIELD_IMAGE)
                && !data.get(WebSocketConstants.FIELD_IMAGE).isNull()
                ? data.get(WebSocketConstants.FIELD_IMAGE).asText(null)
                : null;
        boolean hasImage = image != null && !image.isBlank();

        if (text.isBlank() && !hasImage) {
            sendSafe(session, WsMessage.error(WebSocketConstants.EMPTY_MESSAGE_MSG));
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
                        visionChatService.finalizeTurnAsync(
                                chatSession, turn.rawUserText(), fullText, turn.usedVision());
                        streamingSessions.remove(chatSession.getId());
                        sendSafe(safeSession, WsMessage.assistantDone(messageId, fullText, turn.usedVision()));
                        log.info("User message handled session={} messageId={} total={}ms responseChars={}",
                                chatSession.getId(), messageId, System.currentTimeMillis() - start, fullText.length());
                    },
                    error -> {
                        log.error("AI stream failed session={} elapsed={}ms",
                                chatSession.getId(), System.currentTimeMillis() - start, error);
                        sendSafe(safeSession, WsMessage.error(WebSocketConstants.AI_ERROR_PREFIX + error.getMessage()));
                        streamingSessions.remove(chatSession.getId());
                    });
        } catch (Exception e) {
            log.error("AI call failed session={} elapsed={}ms",
                    chatSession.getId(), System.currentTimeMillis() - start, e);
            sendSafe(session, WsMessage.error(WebSocketConstants.AI_ERROR_PREFIX + e.getMessage()));
            streamingSessions.remove(chatSession.getId());
        }
    }

    private WebSocketSession decorateSession(WebSocketSession session) {
        return decoratedSessions.computeIfAbsent(session.getId(), id ->
                new ConcurrentWebSocketSessionDecorator(
                        session, WebSocketConstants.SEND_TIME_LIMIT_MS, WebSocketConstants.BUFFER_SIZE));
    }

    private ChatSession bindChatSession(WebSocketSession session) {
        Long requestedSessionId = resolveRequestedSessionId(session);
        if (requestedSessionId != null && persistenceService.sessionExists(requestedSessionId)) {
            ChatSession resumed = sessionManager.getOrCreate(requestedSessionId, () -> restoreRecentContext(requestedSessionId));
            wsToChatSession.put(session.getId(), resumed.getId());
            log.info("WebSocket resumed wsId={} requestedSessionId={} restoredMessages={}",
                    session.getId(), requestedSessionId, resumed.getMessages().size());
            return resumed;
        }

        Long sessionId = persistenceService.allocateSessionId();
        ChatSession chatSession = sessionManager.create(sessionId);
        wsToChatSession.put(session.getId(), sessionId);
        persistenceService.initSessionAsync(sessionId);
        return chatSession;
    }

    private Long resolveRequestedSessionId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        String idText = UriComponentsBuilder.fromUri(uri).build().getQueryParams()
                .getFirst(WebSocketConstants.FIELD_SESSION_ID);
        if (idText == null || idText.isBlank()) {
            idText = UriComponentsBuilder.fromUri(uri).build().getQueryParams()
                    .getFirst(WebSocketConstants.FIELD_SESSION_ID_ALT);
        }
        if (idText == null || idText.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(idText.trim());
        } catch (NumberFormatException ignored) {
            log.warn("Invalid resume session id wsId={} value={}", session.getId(), idText);
            return null;
        }
    }

    private List<Message> restoreRecentContext(Long sessionId) {
        List<ChatMessageEntity> entities = persistenceService.listRecentMessagesForContext(
                sessionId,
                properties.getMaxContextMessages());
        if (entities.isEmpty()) {
            return List.of();
        }
        List<Message> restored = new ArrayList<>();
        for (ChatMessageEntity entity : entities) {
            if (ChatConstants.ROLE_ASSISTANT.equals(entity.getRole())) {
                restored.add(new AssistantMessage(entity.getContent()));
            } else if (ChatConstants.ROLE_USER.equals(entity.getRole())) {
                restored.add(new UserMessage(entity.getContent()));
            }
        }
        return restored;
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
