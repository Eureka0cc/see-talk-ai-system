package com.seetalk.controller;

import com.seetalk.common.BaseResponse;
import com.seetalk.common.ResultUtils;
import com.seetalk.model.constants.ApiConstants;
import com.seetalk.model.dto.ChatRequestDto;
import com.seetalk.model.dto.ChatResponseDto;
import com.seetalk.model.dto.SessionCreateDto;
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
@RequestMapping(ApiConstants.SESSIONS_PATH)
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
    public BaseResponse<SessionCreateDto> createSession() {
        Long sessionId = persistenceService.createSession();
        sessionManager.create(sessionId);
        return ResultUtils.success(new SessionCreateDto(sessionId));
    }

    @Operation(summary = "发送消息", responses = {
            @ApiResponse(responseCode = "200", description = "成功"),
            @ApiResponse(responseCode = "404", description = "会话不存在或已过期")
    })
    @PostMapping("/{id}/messages")
    public BaseResponse<ChatResponseDto> sendMessage(
            @Parameter(description = "会话 ID") @PathVariable Long id,
            @RequestBody ChatRequestDto request) {
        ChatSession session = requireActiveSession(id);
        validateMessage(request);

        VisionChatService.ChatResult result = visionChatService.chat(
                session,
                request.getText(),
                request.getImage());
        return ResultUtils.success(new ChatResponseDto(result.text(), result.usedVision(), id));
    }

    @Operation(summary = "清空热会话上下文", description = "仅清除 Redis 中的对话上下文，不软删 MySQL 历史记录")
    @PostMapping("/{id}/clear")
    public BaseResponse<Void> clearSession(@Parameter(description = "会话 ID") @PathVariable Long id) {
        ChatSession session = requireActiveSession(id);
        session.clearHistory();
        sessionManager.save(session);
        return ResultUtils.success();
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
        boolean hasText = request.getText() != null && !request.getText().isBlank();
        boolean hasImage = request.getImage() != null && !request.getImage().isBlank();
        ThrowUtils.throwIf(!hasText && !hasImage,
                ErrorCode.PARAMS_ERROR, "消息内容为空");
    }
}
