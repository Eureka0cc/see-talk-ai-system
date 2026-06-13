package com.seetalk.api;

import com.seetalk.api.dto.MessageDto;
import com.seetalk.api.dto.PageResponse;
import com.seetalk.api.dto.SessionSummaryDto;
import com.seetalk.entity.ChatMessageEntity;
import com.seetalk.entity.ChatSessionEntity;
import com.seetalk.service.ChatPersistenceService;
import com.seetalk.service.SessionTitleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "会话历史", description = "对话会话列表、消息查询与删除")
@RestController
@RequestMapping("/api/sessions")
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
    public PageResponse<SessionSummaryDto> listSessions(
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

        return new PageResponse<>(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Operation(summary = "查询会话消息", responses = {
            @ApiResponse(responseCode = "200", description = "成功"),
            @ApiResponse(responseCode = "404", description = "会话不存在")
    })
    @GetMapping("/{id}/messages")
    public ResponseEntity<List<MessageDto>> listMessages(
            @Parameter(description = "会话 ID") @PathVariable Long id) {
        if (!persistenceService.sessionExists(id)) {
            return ResponseEntity.notFound().build();
        }
        List<MessageDto> messages = persistenceService.listMessages(id).stream()
                .map(this::toMessageDto)
                .toList();
        return ResponseEntity.ok(messages);
    }

    @Operation(summary = "删除会话（软删除）", responses = {
            @ApiResponse(responseCode = "204", description = "删除成功"),
            @ApiResponse(responseCode = "404", description = "会话不存在")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(
            @Parameter(description = "会话 ID") @PathVariable Long id) {
        if (!persistenceService.softDeleteSession(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private SessionSummaryDto toSessionSummary(ChatSessionEntity entity) {
        String title = entity.getTitle();
        if (title == null || title.isBlank()) {
            title = "新对话";
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
