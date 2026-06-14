package com.seetalk.api;

import com.seetalk.api.dto.ChatRequestDto;
import com.seetalk.api.dto.ChatResponseDto;
import com.seetalk.api.dto.SessionCreateDto;
import com.seetalk.exception.ErrorCode;
import com.seetalk.exception.ThrowUtils;
import com.seetalk.service.ChatPersistenceService;
import com.seetalk.service.VisionChatService;
import com.seetalk.session.ChatSession;
import com.seetalk.session.ChatSessionManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "对话", description = "REST 对话接口（创建会话、发送消息、清空上下文）")
@RestController
@RequestMapping("/api/sessions")
public class ChatController {

    private final ChatPersistenceService persistenceService;
    private final ChatSessionManager sessionManager;
    private final VisionChatService visionChatService;

    public ChatController(
            ChatPersistenceService persistenceService,
            ChatSessionManager sessionManager,
            VisionChatService visionChatService) {
        this.persistenceService = persistenceService;
        this.sessionManager = sessionManager;
        this.visionChatService = visionChatService;
    }

    @Operation(summary = "创建会话")
    @PostMapping
    public SessionCreateDto createSession() {
        Long sessionId = persistenceService.createSession();
        sessionManager.create(sessionId);
        return new SessionCreateDto(sessionId);
    }

    @Operation(summary = "发送消息", responses = {
            @ApiResponse(responseCode = "200", description = "成功"),
            @ApiResponse(responseCode = "404", description = "会话不存在或已过期")
    })
    @PostMapping("/{id}/messages")
    public ChatResponseDto sendMessage(
            @Parameter(description = "会话 ID") @PathVariable Long id,
            @RequestBody ChatRequestDto request) {
        ChatSession session = requireActiveSession(id);
        validateMessage(request);

        VisionChatService.ChatResult result = visionChatService.chat(
                session,
                request.text(),
                request.image());
        return new ChatResponseDto(result.text(), result.usedVision(), id);
    }

    @Operation(summary = "清空热会话上下文", description = "仅清除 Redis 中的对话上下文，不软删 MySQL 历史记录")
    @PostMapping("/{id}/clear")
    public void clearSession(@Parameter(description = "会话 ID") @PathVariable Long id) {
        ChatSession session = requireActiveSession(id);
        session.clearHistory();
        sessionManager.save(session);
    }

    private ChatSession requireActiveSession(Long id) {
        ThrowUtils.throwIf(!persistenceService.sessionExists(id),
                ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        ChatSession session = sessionManager.get(id);
        ThrowUtils.throwIf(session == null,
                ErrorCode.NOT_FOUND_ERROR, "会话不存在或已过期，请重新创建");
        return session;
    }

    private void validateMessage(ChatRequestDto request) {
        boolean hasText = request.text() != null && !request.text().isBlank();
        boolean hasImage = request.image() != null && !request.image().isBlank();
        ThrowUtils.throwIf(!hasText && !hasImage,
                ErrorCode.PARAMS_ERROR, "消息内容为空");
    }
}
