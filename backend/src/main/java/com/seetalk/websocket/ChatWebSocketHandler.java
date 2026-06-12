package com.seetalk.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.seetalk.cost.FrameRateLimiter;
import com.seetalk.session.ChatSession;
import com.seetalk.session.ChatSessionManager;
import com.seetalk.service.VisionChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final ChatSessionManager sessionManager;
    private final VisionChatService visionChatService;
    private final FrameRateLimiter frameRateLimiter;
    private final Map<String, String> wsToChatSession = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(
            ChatSessionManager sessionManager,
            VisionChatService visionChatService,
            FrameRateLimiter frameRateLimiter) {
        this.sessionManager = sessionManager;
        this.visionChatService = visionChatService;
        this.frameRateLimiter = frameRateLimiter;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        ChatSession chatSession = sessionManager.create();
        wsToChatSession.put(session.getId(), chatSession.getId());
        session.sendMessage(new TextMessage(
                WsMessage.session(chatSession.getId(), "连接成功，开始对话吧！")));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode data = WsMessage.parse(message.getPayload());
        String type = data.path("type").asText();

        if ("ping".equals(type)) {
            session.sendMessage(new TextMessage(WsMessage.pong()));
            return;
        }

        String chatSessionId = wsToChatSession.get(session.getId());
        ChatSession chatSession = chatSessionId != null ? sessionManager.get(chatSessionId) : null;
        if (chatSession == null) {
            session.sendMessage(new TextMessage(WsMessage.error("会话已过期，请刷新页面重连")));
            return;
        }

        switch (type) {
            case "user_message" -> handleUserMessage(session, chatSession, data);
            case "clear_history" -> {
                chatSession.clearHistory();
                session.sendMessage(new TextMessage(WsMessage.historyCleared()));
            }
            default -> session.sendMessage(new TextMessage(WsMessage.error("未知消息类型: " + type)));
        }
    }

    private void handleUserMessage(WebSocketSession session, ChatSession chatSession, JsonNode data)
            throws Exception {
        String text = data.path("text").asText("");
        if (text.isBlank()) {
            session.sendMessage(new TextMessage(WsMessage.error("消息内容为空")));
            return;
        }

        session.sendMessage(new TextMessage(WsMessage.thinking()));
        try {
            VisionChatService.ChatResult result = visionChatService.chat(chatSession, text);
            session.sendMessage(new TextMessage(
                    WsMessage.assistantMessage(result.text(), result.usedVision())));
        } catch (Exception e) {
            log.error("AI call failed for session {}", chatSession.getId(), e);
            session.sendMessage(new TextMessage(WsMessage.error("AI 调用失败: " + e.getMessage())));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String chatSessionId = wsToChatSession.remove(session.getId());
        if (chatSessionId != null) {
            frameRateLimiter.cleanup(chatSessionId);
            sessionManager.remove(chatSessionId);
        }
    }
}
