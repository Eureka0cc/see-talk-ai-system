package com.seetalk.controller;

import com.seetalk.common.BaseResponse;
import com.seetalk.common.ResultUtils;
import com.seetalk.exception.ErrorCode;
import com.seetalk.exception.ThrowUtils;
import com.seetalk.model.constants.ApiConstants;
import com.seetalk.model.constants.ChatConstants;
import com.seetalk.model.dto.MessageDto;
import com.seetalk.model.dto.PageResponse;
import com.seetalk.model.dto.SessionSummaryDto;
import com.seetalk.model.entity.ChatMessageEntity;
import com.seetalk.model.entity.ChatSessionEntity;
import com.seetalk.service.ChatPersistenceService;
import com.seetalk.service.SessionTitleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "会话历史", description = "对话会话列表、消息查询与删除")
@RestController
@RequestMapping(ApiConstants.SESSIONS_PATH)
public class SessionHistoryController {

    private final ChatPersistenceService persistenceService;
    private final SessionTitleService sessionTitleService;

    public SessionHistoryController(
            ChatPersistenceService persistenceService,
            SessionTitleService sessionTitleService) {
        this.persistenceService = persistenceService;
        this.sessionTitleService = sessionTitleService;
    }

    @Operation(summary = "查询全部会话列表")
    @GetMapping
    public BaseResponse<PageResponse<SessionSummaryDto>> listSessions(
            @Parameter(description = "页码，从 0 开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数，最大 10000") @RequestParam(defaultValue = "10000") int size) {
        int safeSize = Math.min(Math.max(size, 1), 10000);
        Page<ChatSessionEntity> result = persistenceService.listSessions(PageRequest.of(page, safeSize));

        List<ChatSessionEntity> sessions = result.getContent();
        sessions.stream()
                .filter(session -> session.getMessageCount() > 0
                        && (session.getLastMessagePreview() == null || session.getLastMessagePreview().isBlank()))
                .forEach(session -> sessionTitleService.backfillSessionAsync(session.getId()));

        List<SessionSummaryDto> content = sessions.stream()
                .map(this::toSessionSummary)
                .toList();

        return ResultUtils.success(new PageResponse<>(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()));
    }

    @Operation(summary = "查询会话消息", responses = {
            @ApiResponse(responseCode = "200", description = "成功"),
            @ApiResponse(responseCode = "404", description = "会话不存在")
    })
    @GetMapping("/{id}/messages")
    public BaseResponse<List<MessageDto>> listMessages(
            @Parameter(description = "会话 ID") @PathVariable Long id) {
        ThrowUtils.throwIf(!persistenceService.sessionExists(id),
                ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        List<MessageDto> messages = persistenceService.listMessages(id).stream()
                .map(this::toMessageDto)
                .toList();
        return ResultUtils.success(messages);
    }

    @Operation(summary = "删除会话（软删除）", responses = {
            @ApiResponse(responseCode = "204", description = "删除成功"),
            @ApiResponse(responseCode = "404", description = "会话不存在")
    })
    @DeleteMapping("/{id}")
    public BaseResponse<Void> deleteSession(
            @Parameter(description = "会话 ID") @PathVariable Long id) {
        boolean deleted = persistenceService.softDeleteSession(id);
        ThrowUtils.throwIf(!deleted, ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        return ResultUtils.success();
    }

    private SessionSummaryDto toSessionSummary(ChatSessionEntity entity) {
        String title = entity.getTitle();
        if (title == null || title.isBlank()) {
            title = ChatConstants.DEFAULT_SESSION_TITLE;
        }
        String preview = entity.getLastMessagePreview();
        if (preview == null || preview.isBlank()) {
            preview = "";
        }
        return new SessionSummaryDto(
                entity.getId(),
                title,
                preview,
                entity.getLastActiveTime(),
                entity.getMessageCount(),
                entity.getCreateTime());
    }

    private MessageDto toMessageDto(ChatMessageEntity entity) {
        return new MessageDto(
                entity.getId(),
                entity.getRole(),
                entity.getContent(),
                entity.getUsedVision(),
                entity.getCreateTime());
    }
}
