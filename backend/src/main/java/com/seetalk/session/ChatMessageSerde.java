package com.seetalk.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ChatMessageSerde {

    private final ObjectMapper objectMapper;

    public ChatMessageSerde(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(Message message) {
        try {
            if (message instanceof UserMessage um) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("role", "user");
                payload.put("text", um.getText());
                payload.put("has_media", um.getMedia() != null && !um.getMedia().isEmpty());
                return objectMapper.writeValueAsString(payload);
            }
            if (message instanceof AssistantMessage am) {
                return objectMapper.writeValueAsString(
                        Map.of("role", "assistant", "text", am.getText()));
            }
            return null;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize message", e);
        }
    }

    public Message deserialize(String json) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            String role = String.valueOf(map.get("role"));
            Object textObj = map.get("text");
            if (textObj == null) {
                return null;
            }
            String text = String.valueOf(textObj);
            return switch (role) {
                case "user" -> new UserMessage(text);
                case "assistant" -> new AssistantMessage(text);
                default -> null;
            };
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize message", e);
        }
    }
}
