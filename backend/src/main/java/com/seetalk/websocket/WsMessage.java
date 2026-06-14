package com.seetalk.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class WsMessage {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WsMessage() {}

    public static String session(Long sessionId, String message) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "session");
        node.put("session_id", String.valueOf(sessionId));
        node.put("message", message);
        return node.toString();
    }

    public static String thinking() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "thinking");
        return node.toString();
    }

    public static String assistantMessage(String text, boolean usedVision) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "assistant_message");
        node.put("text", text);
        node.put("used_vision", usedVision);
        return node.toString();
    }

    public static String assistantStart(String messageId, boolean usedVision) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "assistant_start");
        node.put("message_id", messageId);
        node.put("used_vision", usedVision);
        return node.toString();
    }

    public static String assistantDelta(String messageId, String delta) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "assistant_delta");
        node.put("message_id", messageId);
        node.put("delta", delta);
        return node.toString();
    }

    public static String assistantDone(String messageId, String text, boolean usedVision) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "assistant_done");
        node.put("message_id", messageId);
        node.put("text", text);
        node.put("used_vision", usedVision);
        return node.toString();
    }

    public static String error(String message) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "error");
        node.put("message", message);
        return node.toString();
    }

    public static String historyCleared() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "history_cleared");
        return node.toString();
    }

    public static String pong() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "pong");
        return node.toString();
    }

    public static JsonNode parse(String payload) throws Exception {
        return MAPPER.readTree(payload);
    }
}
