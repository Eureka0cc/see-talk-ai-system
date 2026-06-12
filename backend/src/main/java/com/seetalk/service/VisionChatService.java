package com.seetalk.service;

import com.seetalk.config.SeeTalkProperties;
import com.seetalk.session.ChatSession;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class VisionChatService {

    private final ChatClient chatClient;
    private final SeeTalkProperties properties;
    private final String apiKey;

    public VisionChatService(
            ChatClient chatClient,
            SeeTalkProperties properties,
            @Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        this.chatClient = chatClient;
        this.properties = properties;
        this.apiKey = apiKey;
    }

    public record ChatResult(String text, boolean usedVision) {}

    public boolean isApiConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public ChatResult chat(ChatSession session, String text) {
        if (!isApiConfigured()) {
            throw new IllegalStateException("服务端未配置 DASHSCOPE_API_KEY，请检查 backend 环境变量");
        }

        String userText = (text == null || text.isBlank()) ? "你好" : text.trim();
        session.addUserMessage(userText);
        session.trimHistory(properties.getMaxContextMessages());

        List<Message> history = buildHistory(session);
        String response = chatClient.prompt()
                .messages(history)
                .call()
                .content();

        session.addAssistantMessage(response);
        return new ChatResult(response, false);
    }

    private List<Message> buildHistory(ChatSession session) {
        List<Message> history = new ArrayList<>();
        for (Message message : session.getMessages()) {
            if (message instanceof UserMessage || message instanceof AssistantMessage) {
                history.add(message);
            }
        }
        return history;
    }
}
