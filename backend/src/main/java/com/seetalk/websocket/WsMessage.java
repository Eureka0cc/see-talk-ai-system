package com.seetalk.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class WsMessage {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WsMessage() {}

    public static String session(String sessionId, String message) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "session");
        node.put("session_id", sessionId);
        node.put("message", message);
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
