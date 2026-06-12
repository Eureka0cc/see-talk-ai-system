package com.seetalk.service;

import com.seetalk.config.SeeTalkProperties;
import com.seetalk.cost.FrameRateLimiter;
import com.seetalk.cost.ImageDeduplicator;
import com.seetalk.session.ChatSession;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class VisionChatService {

    private final ChatClient chatClient;
    private final ImageProcessService imageProcessService;
    private final ImageDeduplicator imageDeduplicator;
    private final FrameRateLimiter frameRateLimiter;
    private final SeeTalkProperties properties;
    private final String apiKey;

    public VisionChatService(
            ChatClient chatClient,
            ImageProcessService imageProcessService,
            ImageDeduplicator imageDeduplicator,
            FrameRateLimiter frameRateLimiter,
            SeeTalkProperties properties,
            @Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        this.chatClient = chatClient;
        this.imageProcessService = imageProcessService;
        this.imageDeduplicator = imageDeduplicator;
        this.frameRateLimiter = frameRateLimiter;
        this.properties = properties;
        this.apiKey = apiKey;
    }

    public record ChatResult(String text, boolean usedVision) {}

    public boolean isApiConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public ChatResult chat(ChatSession session, String text, String imageBase64) {
        if (!isApiConfigured()) {
            throw new IllegalStateException("服务端未配置 DASHSCOPE_API_KEY，请检查 backend 环境变量");
        }

        String userText = (text == null || text.isBlank()) ? "请描述你看到的画面。" : text.trim();
        boolean useVision = false;
        byte[] compressedImage = null;

        if (imageBase64 != null && !imageBase64.isBlank()) {
            if (frameRateLimiter.allow(session.getId())) {
                compressedImage = imageProcessService.compressBase64Image(imageBase64);
                String hash = imageDeduplicator.computeHash(compressedImage);
                if (!imageDeduplicator.isDuplicate(session.getLastImageHash(), hash)) {
                    session.setLastImageHash(hash);
                    useVision = true;
                }
            }
        }

        UserMessage userMessage;
        if (useVision && compressedImage != null) {
            Media media = Media.builder()
                    .mimeType(MimeTypeUtils.IMAGE_JPEG)
                    .data(new ByteArrayResource(compressedImage))
                    .build();
            userMessage = UserMessage.builder()
                    .text(userText)
                    .media(media)
                    .build();
        } else {
            userMessage = new UserMessage(userText);
        }

        session.getMessages().add(userMessage);
        session.trimHistory(properties.getMaxContextMessages());
        session.touch();

        List<Message> history = buildHistory(session);
        String response = chatClient.prompt()
                .messages(history)
                .call()
                .content();

        session.addAssistantMessage(response);
        return new ChatResult(response, useVision);
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
