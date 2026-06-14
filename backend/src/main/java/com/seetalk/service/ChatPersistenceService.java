package com.seetalk.service;

import com.seetalk.entity.ChatMessageEntity;
import com.seetalk.entity.ChatSessionEntity;
import com.seetalk.id.SnowflakeIdWorker;
import com.seetalk.repository.ChatMessageRepository;
import com.seetalk.repository.ChatSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ChatPersistenceService {

    private static final int TITLE_MAX_LENGTH = 128;
    private static final int PREVIEW_MAX_LENGTH = 80;

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final SessionTitleService sessionTitleService;

    public ChatPersistenceService(
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            SessionTitleService sessionTitleService) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.sessionTitleService = sessionTitleService;
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
            session.setLastActiveTime(LocalDateTime.now());
            session.setMessageCount(0);
            session = sessionRepository.save(session);
            log.warn("Compensated missing session on persistTurn: id={}", dbSessionId);
        }

        boolean isFirstTurn = session.getMessageCount() == 0;

        saveMessage(dbSessionId, "user", userText, false);
        saveMessage(dbSessionId, "assistant", assistantText, usedVision);

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
        return sessionRepository.findAllByOrderByLastActiveTimeDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageEntity> listMessages(Long sessionId) {
        return messageRepository.findBySessionIdOrderByAuditCreateTimeAsc(sessionId);
    }

    @Transactional(readOnly = true)
    public boolean sessionExists(Long sessionId) {
        return sessionRepository.findById(sessionId).isPresent();
    }

    @Transactional
    public boolean softDeleteSession(Long sessionId) {
        ChatSessionEntity session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return false;
        }

        session.setDeleted(true);
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
            return "新对话";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= TITLE_MAX_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, TITLE_MAX_LENGTH - 3) + "...";
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
}
