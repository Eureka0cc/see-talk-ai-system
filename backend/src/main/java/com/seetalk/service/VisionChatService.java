package com.seetalk.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.chat.MessageFormat;
import com.alibaba.cloud.ai.dashscope.common.DashScopeApiConstants;
import com.seetalk.config.SeeTalkProperties;
import com.seetalk.cost.FrameRateLimiter;
import com.seetalk.cost.ImageDeduplicator;
import com.seetalk.session.ChatSession;
import com.seetalk.session.ChatSessionManager;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
@Service
public class VisionChatService {

    private final ChatClient chatClient;
    private final ImageProcessService imageProcessService;
    private final ImageDeduplicator imageDeduplicator;
    private final FrameRateLimiter frameRateLimiter;
    private final SeeTalkProperties properties;
    private final ChatPersistenceService persistenceService;
    private final ChatSessionManager sessionManager;
    private final String apiKey;
    private final ChatOptions dashScopeOptions;

    public VisionChatService(
            ChatClient chatClient,
            ImageProcessService imageProcessService,
            ImageDeduplicator imageDeduplicator,
            FrameRateLimiter frameRateLimiter,
            SeeTalkProperties properties,
            ChatPersistenceService persistenceService,
            ChatSessionManager sessionManager,
            @Value("${spring.ai.dashscope.api-key:}") String apiKey,
            @Value("${spring.ai.dashscope.chat.options.model:qwen3-vl-flash}") String model) {
        this.chatClient = chatClient;
        this.imageProcessService = imageProcessService;
        this.imageDeduplicator = imageDeduplicator;
        this.frameRateLimiter = frameRateLimiter;
        this.properties = properties;
        this.persistenceService = persistenceService;
        this.sessionManager = sessionManager;
        this.apiKey = apiKey;
        this.dashScopeOptions = DashScopeChatOptions.builder()
                .withModel(model)
                .withMultiModel(true)
                .build();
    }

    public record ChatResult(String text, boolean usedVision) {}

    public record UserTurnContext(String userText, boolean usedVision) {}

    public boolean isApiConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public UserTurnContext prepareUserTurn(ChatSession session, String text, String imageBase64) {
        long start = System.currentTimeMillis();
        if (!isApiConfigured()) {
            throw new IllegalStateException("服务端未配置 DASHSCOPE_API_KEY，请检查 backend 环境变量");
        }

        String userText = (text == null || text.isBlank()) ? "请描述你看到的画面。" : text.trim();
        boolean useVision = false;
        byte[] compressedImage = null;
        boolean hasImage = imageBase64 != null && !imageBase64.isBlank();

        if (hasImage) {
            if (frameRateLimiter.allow(session.getId())) {
                long compressStart = System.currentTimeMillis();
                compressedImage = imageProcessService.compressBase64Image(imageBase64);
                long compressMs = System.currentTimeMillis() - compressStart;
                String hash = imageDeduplicator.computeHash(compressedImage);
                boolean duplicate = imageDeduplicator.isDuplicate(session.getLastImageHash(), hash);
                if (!duplicate) {
                    session.setLastImageHash(hash);
                    useVision = true;
                }
                log.info("Image processed session={} compress={}ms size={}KB vision={} duplicate={}",
                        session.getId(), compressMs, compressedImage.length / 1024, useVision, duplicate);
            } else {
                log.info("Image skipped by rate limiter session={}", session.getId());
            }
        }

        UserMessage userMessage;
        if (useVision && compressedImage != null) {
            Media media = Media.builder()
                    .mimeType(MimeTypeUtils.IMAGE_JPEG)
                    .data(new ByteArrayResource(compressedImage))
                    .build();
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(DashScopeApiConstants.MESSAGE_FORMAT, MessageFormat.IMAGE);
            userMessage = UserMessage.builder()
                    .text(userText)
                    .media(media)
                    .metadata(metadata)
                    .build();
        } else {
            userMessage = new UserMessage(userText);
        }

        session.getMessages().add(userMessage);
        session.trimHistory(properties.getMaxContextMessages());
        session.touch();

        log.info("User turn prepared session={} hasImage={} useVision={} historySize={} cost={}ms",
                session.getId(), hasImage, useVision, session.getMessages().size(),
                System.currentTimeMillis() - start);
        return new UserTurnContext(userText, useVision);
    }

    public void streamResponse(
            ChatSession session,
            Consumer<String> onChunk,
            Consumer<String> onComplete,
            Consumer<Throwable> onError) {
        List<Message> history = buildHistory(session);
        StringBuilder fullText = new StringBuilder();
        long start = System.currentTimeMillis();
        AtomicBoolean firstChunk = new AtomicBoolean(true);

        log.info("AI stream starting session={} historyMessages={}", session.getId(), history.size());

        Flux<String> flux = chatClient.prompt()
                .messages(history)
                .options(dashScopeOptions)
                .stream()
                .content();

        flux.doOnNext(chunk -> {
                    if (firstChunk.compareAndSet(true, false)) {
                        log.info("AI first token session={} ttft={}ms", session.getId(), System.currentTimeMillis() - start);
                    }
                    fullText.append(chunk);
                    onChunk.accept(chunk);
                })
                .doOnComplete(() -> {
                    String response = fullText.toString();
                    session.addAssistantMessage(response);
                    log.info("AI stream complete session={} total={}ms chars={}",
                            session.getId(), System.currentTimeMillis() - start, response.length());
                    onComplete.accept(response);
                })
                .doOnError(error -> {
                    log.error("AI stream error session={} elapsed={}ms", session.getId(),
                            System.currentTimeMillis() - start, error);
                    onError.accept(error);
                })
                .subscribe(null, error -> { /* handled in doOnError */ });
    }

    public ChatResult chat(ChatSession session, String text, String imageBase64) {
        UserTurnContext turn = prepareUserTurn(session, text, imageBase64);

        List<Message> history = buildHistory(session);
        String response = chatClient.prompt()
                .messages(history)
                .options(dashScopeOptions)
                .call()
                .content();

        session.addAssistantMessage(response);
        persistenceService.persistTurn(session.getId(), turn.userText(), response, turn.usedVision());
        sessionManager.save(session);
        return new ChatResult(response, turn.usedVision());
    }

    public void finalizeTurnAsync(ChatSession session, String userText, String assistantText, boolean usedVision) {
        persistenceService.persistTurnAsync(session.getId(), userText, assistantText, usedVision);
        sessionManager.save(session);
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
