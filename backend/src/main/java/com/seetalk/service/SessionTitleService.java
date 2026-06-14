package com.seetalk.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.seetalk.entity.ChatMessageEntity;
import com.seetalk.repository.ChatMessageRepository;
import com.seetalk.repository.ChatSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class SessionTitleService {

    private static final int TITLE_MAX_LENGTH = 30;
    private static final int PREVIEW_MAX_LENGTH = 80;

    private static final String TITLE_SYSTEM = """
            你是会话标题生成器。根据用户与 AI 的对话片段，生成一句简短的中文标题。
            要求：不超过 15 个字；概括对话主题；不加引号或书名号；不要以标点结尾；只输出标题文本。""";

    private final ChatClient chatClient;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatOptions titleOptions;
    private final String apiKey;

    public SessionTitleService(
            ChatClient chatClient,
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            @Value("${spring.ai.dashscope.api-key:}") String apiKey,
            @Value("${spring.ai.dashscope.chat.options.model:qwen3-vl-flash}") String model) {
        this.chatClient = chatClient;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.apiKey = apiKey;
        this.titleOptions = DashScopeChatOptions.builder()
                .withModel(model)
                .withMultiModel(true)
                .withTemperature(0.3)
                .withMaxToken(40)
                .build();
    }

    @Async("chatTaskExecutor")
    public void backfillSessionAsync(Long sessionId) {
        List<ChatMessageEntity> messages = messageRepository.findBySessionIdOrderByAuditCreateTimeAsc(sessionId);
        if (messages.isEmpty()) {
            return;
        }

        String lastAssistant = messages.stream()
                .filter(message -> "assistant".equals(message.getRole()))
                .reduce((first, second) -> second)
                .map(ChatMessageEntity::getContent)
                .orElse("");

        String firstUser = messages.stream()
                .filter(message -> "user".equals(message.getRole()))
                .findFirst()
                .map(ChatMessageEntity::getContent)
                .orElse("");

        String firstAssistant = messages.stream()
                .filter(message -> "assistant".equals(message.getRole()))
                .findFirst()
                .map(ChatMessageEntity::getContent)
                .orElse("");

        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setLastMessagePreview(truncatePreview(lastAssistant));
            sessionRepository.save(session);
        });

        generateAndSaveTitleAsync(sessionId, firstUser, firstAssistant);
    }

    @Async("chatTaskExecutor")
    public void generateAndSaveTitleAsync(Long sessionId, String userText, String assistantText) {
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        long start = System.currentTimeMillis();
        try {
            String title = generateTitle(userText, assistantText);
            if (title == null || title.isBlank()) {
                return;
            }
            sessionRepository.findById(sessionId).ifPresent(session -> {
                session.setTitle(title);
                sessionRepository.save(session);
            });
            log.info("Session title generated: id={}, title={}, cost={}ms",
                    sessionId, title, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("Failed to generate session title: id={}, cost={}ms",
                    sessionId, System.currentTimeMillis() - start, e);
        }
    }

    private String generateTitle(String userText, String assistantText) {
        String userContent = """
                用户：%s
                AI：%s
                """.formatted(truncate(userText, 200), truncate(assistantText, 300));

        String raw = chatClient.prompt()
                .system(TITLE_SYSTEM)
                .user(userContent)
                .options(titleOptions)
                .call()
                .content();

        return sanitizeTitle(raw);
    }

    private String sanitizeTitle(String raw) {
        if (raw == null) {
            return null;
        }
        String title = raw.trim()
                .replaceAll("^[\"'「『《]+", "")
                .replaceAll("[\"'」』》]+$", "")
                .replaceAll("[。！？…]+$", "")
                .trim();
        if (title.isBlank()) {
            return null;
        }
        if (title.length() <= TITLE_MAX_LENGTH) {
            return title;
        }
        return title.substring(0, TITLE_MAX_LENGTH - 1) + "…";
    }

    private String truncatePreview(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= PREVIEW_MAX_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, PREVIEW_MAX_LENGTH - 1) + "…";
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen) + "…";
    }
}
