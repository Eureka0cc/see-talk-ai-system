package com.seetalk.service;

import com.seetalk.config.SeeTalkProperties;
import com.seetalk.model.constants.ChatConstants;
import com.seetalk.model.entity.ChatMessageEntity;
import com.seetalk.model.entity.ChatSessionEntity;
import com.seetalk.model.id.SnowflakeIdWorker;
import com.seetalk.repository.ChatMessageRepository;
import com.seetalk.repository.ChatSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class ChatPersistenceService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final SessionTitleService sessionTitleService;
    private final Long defaultUserId;

    public ChatPersistenceService(
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            SessionTitleService sessionTitleService,
            SeeTalkProperties properties) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.sessionTitleService = sessionTitleService;
        this.defaultUserId = properties.getDefaultUserId();
    }

    public Long allocateSessionId() {
        return SnowflakeIdWorker.nextId();
    }

    @Transactional
    public Long createSession() {
        long start = System.currentTimeMillis();
        Long sessionId = allocateSessionId();
        saveNewSession(sessionId);
        log.debug("Session created synchronously: id={}, cost={}ms", sessionId, System.currentTimeMillis() - start);
        return sessionId;
    }

    @Async("chatTaskExecutor")
    @Transactional
    public void initSessionAsync(Long sessionId) {
        long start = System.currentTimeMillis();
        try {
            saveNewSession(sessionId);
            log.info("Session persisted async: id={}, cost={}ms", sessionId, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("Failed to persist session async: id={}, cost={}ms", sessionId, System.currentTimeMillis() - start, e);
        }
    }

    private void saveNewSession(Long sessionId) {
        if (sessionRepository.existsById(sessionId)) {
            return;
        }
        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setId(sessionId);
        entity.setUserId(defaultUserId);
        entity.setLastActiveTime(LocalDateTime.now());
        entity.setMessageCount(0);
        try {
            sessionRepository.save(entity);
        } catch (DataIntegrityViolationException e) {
            log.debug("Session already exists (concurrent insert): id={}", sessionId);
        }
    }

    @Transactional
    public void persistTurn(Long dbSessionId, String userText, String assistantText, boolean usedVision) {
        if (dbSessionId == null) {
            return;
        }

        ChatSessionEntity session = sessionRepository.findById(dbSessionId).orElse(null);
        if (session == null) {
            session = new ChatSessionEntity();
            session.setId(dbSessionId);
            session.setUserId(defaultUserId);
            session.setLastActiveTime(LocalDateTime.now());
            session.setMessageCount(0);
            session = sessionRepository.save(session);
            log.warn("Compensated missing session on persistTurn: id={}", dbSessionId);
        } else if (session.getUserId() == null || session.getUserId() == 0) {
            session.setUserId(defaultUserId);
        }

        boolean isFirstTurn = session.getMessageCount() == 0;

        saveMessage(dbSessionId, ChatConstants.ROLE_USER, userText, false);
        saveMessage(dbSessionId, ChatConstants.ROLE_ASSISTANT, assistantText, usedVision);

        if (isFirstTurn) {
            session.setTitle(truncateTitle(userText));
            sessionTitleService.generateAndSaveTitleAsync(dbSessionId, userText, assistantText);
        }
        session.setLastMessagePreview(truncatePreview(assistantText));
        session.setLastActiveTime(LocalDateTime.now());
        session.setMessageCount(session.getMessageCount() + 2);
        sessionRepository.save(session);
    }

    @Async("chatTaskExecutor")
    @Transactional
    public void persistTurnAsync(Long dbSessionId, String userText, String assistantText, boolean usedVision) {
        persistTurn(dbSessionId, userText, assistantText, usedVision);
    }

    @Transactional(readOnly = true)
    public Page<ChatSessionEntity> listSessions(Pageable pageable) {
        return sessionRepository.findVisibleByUserIdOrderByLastActiveTimeDesc(defaultUserId, pageable);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageEntity> listMessages(Long sessionId) {
        return messageRepository.findBySessionIdOrderByAuditCreateTimeAsc(sessionId);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageEntity> listRecentMessagesForContext(Long sessionId, int limit) {
        if (sessionId == null || !sessionExists(sessionId)) {
            return List.of();
        }
        int safeLimit = Math.min(Math.max(limit, 1), 1000);
        List<ChatMessageEntity> recent = messageRepository.findBySessionIdOrderByAuditCreateTimeDesc(
                sessionId,
                PageRequest.of(0, safeLimit));
        Collections.reverse(recent);
        return recent;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageEntity> searchCurrentUserMessages(
            String query,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        String normalizedQuery = query == null ? "" : query.trim();
        return messageRepository.searchVisibleMessagesByUserId(
                defaultUserId,
                normalizedQuery,
                startTime,
                endTime,
                PageRequest.of(0, safeLimit));
    }

    @Transactional(readOnly = true)
    public List<ChatMessageEntity> listRecentCurrentUserMessages(
            LocalDateTime startTime,
            LocalDateTime endTime,
            int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), ChatConstants.HISTORY_RECALL_MAX_MESSAGES);
        return messageRepository.findRecentVisibleMessagesByUserId(
                defaultUserId,
                startTime,
                endTime,
                PageRequest.of(0, safeLimit));
    }

    @Transactional(readOnly = true)
    public boolean sessionExists(Long sessionId) {
        return sessionRepository.existsVisibleByIdAndUserId(sessionId, defaultUserId);
    }

    @Transactional
    public void clearSessionMessages(Long sessionId) {
        int deleted = messageRepository.softDeleteBySessionId(sessionId);

        ChatSessionEntity session = sessionRepository.findById(sessionId).orElse(null);
        if (session != null) {
            session.setMessageCount(0);
            session.setLastMessagePreview("");
            sessionRepository.save(session);
        }

        log.info("Soft-deleted {} messages for session {}", deleted, sessionId);
    }

    @Transactional
    public boolean softDeleteSession(Long sessionId) {
        ChatSessionEntity session = sessionRepository.findVisibleByIdAndUserId(sessionId, defaultUserId).orElse(null);
        if (session == null) {
            return false;
        }

        session.setDeleted(true);
        session.setLastActiveTime(LocalDateTime.now());
        sessionRepository.save(session);

        List<ChatMessageEntity> messages = messageRepository.findBySessionIdOrderByAuditCreateTimeAsc(sessionId);
        for (ChatMessageEntity message : messages) {
            message.setDeleted(true);
            messageRepository.save(message);
        }
        return true;
    }

    private void saveMessage(Long sessionId, String role, String content, boolean usedVision) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setId(SnowflakeIdWorker.nextId());
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setUsedVision(usedVision);
        messageRepository.save(message);
    }

    private String truncateTitle(String text) {
        if (text == null || text.isBlank()) {
            return ChatConstants.DEFAULT_SESSION_TITLE;
        }
        String trimmed = text.trim();
        if (trimmed.length() <= ChatConstants.FALLBACK_TITLE_MAX_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, ChatConstants.FALLBACK_TITLE_MAX_LENGTH - 3) + "...";
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
}
