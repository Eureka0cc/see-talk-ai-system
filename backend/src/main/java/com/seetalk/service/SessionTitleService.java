package com.seetalk.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.seetalk.model.constants.ChatConstants;
import com.seetalk.model.constants.PromptConstants;
import com.seetalk.model.entity.ChatMessageEntity;
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
                .withTemperature(ChatConstants.TITLE_GEN_TEMPERATURE)
                .withMaxToken(ChatConstants.TITLE_GEN_MAX_TOKENS)
                .build();
    }

    @Async("chatTaskExecutor")
    public void backfillSessionAsync(Long sessionId) {
        List<ChatMessageEntity> messages = messageRepository.findBySessionIdOrderByAuditCreateTimeAsc(sessionId);
        if (messages.isEmpty()) {
            return;
        }

        String lastAssistant = messages.stream()
                .filter(message -> ChatConstants.ROLE_ASSISTANT.equals(message.getRole()))
                .reduce((first, second) -> second)
                .map(ChatMessageEntity::getContent)
                .orElse("");

        String firstUser = messages.stream()
                .filter(message -> ChatConstants.ROLE_USER.equals(message.getRole()))
                .findFirst()
                .map(ChatMessageEntity::getContent)
                .orElse("");

        String firstAssistant = messages.stream()
                .filter(message -> ChatConstants.ROLE_ASSISTANT.equals(message.getRole()))
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
                """.formatted(truncate(userText, ChatConstants.TITLE_GEN_USER_TRUNCATE), truncate(assistantText, ChatConstants.TITLE_GEN_ASSISTANT_TRUNCATE));

        String raw = chatClient.prompt()
                .system(PromptConstants.TITLE_SYSTEM_PROMPT)
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
        if (title.length() <= ChatConstants.GENERATED_TITLE_MAX_LENGTH) {
            return title;
        }
        return title.substring(0, ChatConstants.GENERATED_TITLE_MAX_LENGTH - 1) + "…";
    }

    private String truncatePreview(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= ChatConstants.SESSION_PREVIEW_MAX_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, ChatConstants.SESSION_PREVIEW_MAX_LENGTH - 1) + "…";
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
