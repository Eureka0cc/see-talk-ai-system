package com.seetalk.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.chat.MessageFormat;
import com.alibaba.cloud.ai.dashscope.common.DashScopeApiConstants;
import com.seetalk.config.SeeTalkProperties;
import com.seetalk.guard.PromptSafetyGuard;
import com.seetalk.model.constants.ChatConstants;
import com.seetalk.model.constants.PromptConstants;
import com.seetalk.rate.FrameRateLimiter;
import com.seetalk.rate.ImageDeduplicator;
import com.seetalk.session.ChatRequestContext;
import com.seetalk.session.ChatSession;
import com.seetalk.session.ChatSessionManager;
import com.seetalk.tool.ChatHistoryTools;
import com.seetalk.tool.WebSearchTools;
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
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
@Service
public class VisionChatService {

    // 视觉请求识别与流式缓冲区参数见 ChatConstants
    // 北京时间与时区格式化见 ChatConstants

    private final ChatClient chatClient;
    private final ImageProcessService imageProcessService;
    private final ImageDeduplicator imageDeduplicator;
    private final FrameRateLimiter frameRateLimiter;
    private final SeeTalkProperties properties;
    private final ChatPersistenceService persistenceService;
    private final ChatSessionManager sessionManager;
    private final ChatHistoryTools chatHistoryTools;
    private final WebSearchTools webSearchTools;
    private final PromptSafetyGuard promptSafetyGuard;
    private final String apiKey;
    private final ChatOptions dashScopeOptions;
    private final ConcurrentHashMap<Long, ActiveStream> activeStreams = new ConcurrentHashMap<>();

    private static final class ActiveStream {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final StringBuilder rawText = new StringBuilder();
        private volatile Disposable subscription;
    }

    public VisionChatService(
            ChatClient chatClient,
            ImageProcessService imageProcessService,
            ImageDeduplicator imageDeduplicator,
            FrameRateLimiter frameRateLimiter,
            SeeTalkProperties properties,
            ChatPersistenceService persistenceService,
            ChatSessionManager sessionManager,
            ChatHistoryTools chatHistoryTools,
            WebSearchTools webSearchTools,
            PromptSafetyGuard promptSafetyGuard,
            @Value("${spring.ai.dashscope.api-key:}") String apiKey,
            @Value("${spring.ai.dashscope.chat.options.model:qwen3-vl-flash}") String model) {
        this.chatClient = chatClient;
        this.imageProcessService = imageProcessService;
        this.imageDeduplicator = imageDeduplicator;
        this.frameRateLimiter = frameRateLimiter;
        this.properties = properties;
        this.persistenceService = persistenceService;
        this.sessionManager = sessionManager;
        this.chatHistoryTools = chatHistoryTools;
        this.webSearchTools = webSearchTools;
        this.promptSafetyGuard = promptSafetyGuard;
        this.apiKey = apiKey;
        this.dashScopeOptions = DashScopeChatOptions.builder()
                .withModel(model)
                .withMultiModel(true)
                .withStream(true)
                .withTemperature(ChatConstants.DEFAULT_TEMPERATURE)
                .withMaxToken(ChatConstants.DEFAULT_MAX_TOKENS)
                .build();
    }

    public record ChatResult(String text, boolean usedVision) {}

    public record UserTurnContext(String rawUserText, boolean usedVision) {}

    public boolean isApiConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public UserTurnContext prepareUserTurn(ChatSession session, String text, String imageBase64) {
        long start = System.currentTimeMillis();
        if (!isApiConfigured()) {
            throw new IllegalStateException("服务端未配置 DASHSCOPE_API_KEY，请检查 backend 环境变量");
        }

        String rawUserText = (text == null || text.isBlank()) ? "请描述你看到的画面。" : text.trim();
        boolean promptInjectionDetected = promptSafetyGuard.looksLikePromptInjection(rawUserText);
        String modelUserText = promptSafetyGuard.hardenUserInput(rawUserText);
        boolean userAsksForVision = containsVisionRequest(rawUserText);
        boolean useVision = false;
        byte[] compressedImage = null;
        boolean hasImage = imageBase64 != null && !imageBase64.isBlank();

        if (hasImage) {
            if (frameRateLimiter.allow(session.getId()) || userAsksForVision) {
                long compressStart = System.currentTimeMillis();
                compressedImage = imageProcessService.compressBase64Image(imageBase64);
                long compressMs = System.currentTimeMillis() - compressStart;
                String hash = imageDeduplicator.computeHash(compressedImage);
                String lastHash = session.getLastImageHash();
                int hammingDistance = imageDeduplicator.hammingDistance(lastHash, hash);
                boolean sceneChanged = imageDeduplicator.isNewScene(lastHash, hash);

                if (sceneChanged || userAsksForVision) {
                    session.setLastImageHash(hash);
                    useVision = true;
                } else {
                    log.debug("Skipping image: scene unchanged and user not requesting vision session={}",
                            session.getId());
                }
                log.info("Image processed session={} compress={}ms size={}KB vision={} sceneChanged={} asksVision={} hammingDistance={}",
                        session.getId(), compressMs, compressedImage.length / 1024, useVision,
                        sceneChanged, userAsksForVision, hammingDistance);
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
                    .text(modelUserText)
                    .media(media)
                    .metadata(metadata)
                    .build();
        } else {
            userMessage = new UserMessage(modelUserText);
        }

        session.getMessages().add(userMessage);
        session.trimHistory(properties.getMaxContextMessages());
        session.touch();

        log.info("User turn prepared session={} hasImage={} useVision={} historySize={} cost={}ms",
                session.getId(), hasImage, useVision, session.getMessages().size(),
                System.currentTimeMillis() - start);
        if (promptInjectionDetected) {
            log.warn("Potential prompt injection detected and hardened session={}", session.getId());
        }
        return new UserTurnContext(rawUserText, useVision);
    }

    public void discardActiveStream(Long sessionId) {
        ActiveStream active = activeStreams.remove(sessionId);
        if (active == null) {
            return;
        }
        active.cancelled.set(true);
        Disposable subscription = active.subscription;
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }

    /**
     * 取消进行中的流式回复，撤回未完成的 user 轮次，返回已生成的部分文本。
     */
    public String cancelActiveStream(ChatSession session) {
        ActiveStream active = activeStreams.remove(session.getId());
        if (active == null) {
            return "";
        }
        active.cancelled.set(true);
        Disposable subscription = active.subscription;
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
        session.rollbackPendingUserTurn();
        return promptSafetyGuard.sanitizeAssistantOutput(active.rawText.toString());
    }

    public boolean hasActiveStream(Long sessionId) {
        return activeStreams.containsKey(sessionId);
    }

    public void streamResponse(
            ChatSession session,
            Consumer<String> onChunk,
            Consumer<String> onComplete,
            Consumer<Throwable> onError) {
        sessionManager.save(session);
        List<Message> history = buildHistory(session);
        StringBuilder pendingBuffer = new StringBuilder();
        long start = System.currentTimeMillis();
        AtomicBoolean firstChunk = new AtomicBoolean(true);
        ActiveStream active = new ActiveStream();
        activeStreams.put(session.getId(), active);

        log.info("AI stream starting session={} historyMessages={}", session.getId(), history.size());

        String dynamicSystemPrompt = buildDynamicSystemPrompt();
        Map<String, Object> toolContext = Map.of(ChatRequestContext.SESSION_ID_KEY, session.getId());
        Flux<String> flux = chatClient.prompt()
                .system(dynamicSystemPrompt)
                .messages(history)
                .options(dashScopeOptions)
                .toolContext(toolContext)
                .tools(chatHistoryTools, webSearchTools)
                .stream()
                .content();

        active.subscription = flux.doOnNext(chunk -> {
                    if (active.cancelled.get()) {
                        return;
                    }
                    if (firstChunk.compareAndSet(true, false)) {
                        log.info("AI first token session={} ttft={}ms", session.getId(), System.currentTimeMillis() - start);
                    }
                    active.rawText.append(chunk);
                    pendingBuffer.append(chunk);
                    if (pendingBuffer.length() > ChatConstants.STREAM_GUARD_BUFFER_CHARS) {
                        int flushLength = pendingBuffer.length() - ChatConstants.STREAM_GUARD_BUFFER_CHARS;
                        String safeChunk = promptSafetyGuard.sanitizeAssistantOutput(pendingBuffer.substring(0, flushLength));
                        pendingBuffer.delete(0, flushLength);
                        if (safeChunk != null && !safeChunk.isBlank()) {
                            onChunk.accept(safeChunk);
                        }
                    }
                })
                .doOnComplete(() -> {
                    activeStreams.remove(session.getId());
                    if (active.cancelled.get()) {
                        log.info("AI stream cancelled before complete session={}", session.getId());
                        return;
                    }
                    String safeTail = promptSafetyGuard.sanitizeAssistantOutput(pendingBuffer.toString());
                    if (safeTail != null && !safeTail.isBlank()) {
                        onChunk.accept(safeTail);
                    }
                    String response = promptSafetyGuard.sanitizeAssistantOutput(active.rawText.toString());
                    session.addAssistantMessage(response);
                    log.info("AI stream complete session={} total={}ms chars={}",
                            session.getId(), System.currentTimeMillis() - start, response.length());
                    onComplete.accept(response);
                })
                .doOnError(error -> {
                    activeStreams.remove(session.getId());
                    if (active.cancelled.get()) {
                        log.info("AI stream cancelled with error session={}", session.getId());
                        return;
                    }
                    log.error("AI stream error session={} elapsed={}ms", session.getId(),
                            System.currentTimeMillis() - start, error);
                    onError.accept(error);
                })
                .subscribe(null, error -> { /* handled in doOnError */ });
    }

    public ChatResult chat(ChatSession session, String text, String imageBase64) {
        UserTurnContext turn = prepareUserTurn(session, text, imageBase64);
        sessionManager.save(session);

        List<Message> history = buildHistory(session);
        String dynamicSystemPrompt = buildDynamicSystemPrompt();
        Map<String, Object> toolContext = Map.of(ChatRequestContext.SESSION_ID_KEY, session.getId());
        String response = chatClient.prompt()
                .system(dynamicSystemPrompt)
                .messages(history)
                .options(dashScopeOptions)
                .toolContext(toolContext)
                .tools(chatHistoryTools, webSearchTools)
                .call()
                .content();
        String safeResponse = promptSafetyGuard.sanitizeAssistantOutput(response);

        session.addAssistantMessage(safeResponse);
        persistenceService.persistTurn(session.getId(), turn.rawUserText(), safeResponse, turn.usedVision());
        sessionManager.save(session);
        return new ChatResult(safeResponse, turn.usedVision());
    }

    public void finalizeTurn(ChatSession session, String userText, String assistantText, boolean usedVision) {
        persistenceService.persistTurn(session.getId(), userText, assistantText, usedVision);
        sessionManager.save(session);
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

    private String buildDynamicSystemPrompt() {
        String currentBeijingTime = ZonedDateTime.now(ChatConstants.BEIJING_ZONE)
                .format(ChatConstants.BEIJING_TIME_FORMATTER);
        return PromptConstants.SYSTEM_PROMPT + "\nCurrent Beijing Time: " + currentBeijingTime
                + " CST (UTC+8, Asia/Shanghai)。请基于该时间理解“今天”“昨天”“昨晚”等相对时间。";
    }

    private boolean containsVisionRequest(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String normalized = text.replaceAll("\\s+", "");
        for (String pattern : ChatConstants.VISION_REQUEST_PATTERNS) {
            if (normalized.contains(pattern)) {
                return true;
            }
        }
        boolean hasDeicticReference = normalized.contains("这个")
                || normalized.contains("那个")
                || normalized.contains("这张")
                || normalized.contains("那张")
                || normalized.contains("这边")
                || normalized.contains("那边");
        boolean hasQuestionForObject = normalized.contains("是什么")
                || normalized.contains("啥")
                || normalized.contains("怎么样")
                || normalized.contains("好不好看")
                || normalized.contains("干嘛")
                || normalized.contains("做什么")
                || normalized.contains("干什么");
        return hasDeicticReference && hasQuestionForObject;
    }
}
