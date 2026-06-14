package com.seetalk.tool;

import com.seetalk.model.constants.ChatConstants;
import com.seetalk.model.entity.ChatMessageEntity;
import com.seetalk.model.entity.ChatSessionEntity;
import com.seetalk.service.ChatPersistenceService;
import com.seetalk.session.ChatRequestContext;
import com.seetalk.session.ChatSession;
import com.seetalk.session.ChatSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
public class ChatHistoryTools {

    private final ChatPersistenceService persistenceService;
    private final ChatSessionManager sessionManager;

    public ChatHistoryTools(ChatPersistenceService persistenceService, ChatSessionManager sessionManager) {
        this.persistenceService = persistenceService;
        this.sessionManager = sessionManager;
    }

    @Tool(description = """
            Search the current user's chat history. Use when the user asks about past conversations,
            wants to recall something discussed before, or mentions a specific date or time period.
            For abstract recall questions (e.g. who/what did I mention, what do I like), returns recent
            messages from the current session and database for you to summarize — not keyword matching.""")
    public String searchChatHistory(
            @ToolParam(description = "Keyword or topic to search for; leave empty for abstract recall", required = false)
            String query,
            @ToolParam(description = "Natural language time range, e.g. today, yesterday, last week, last 30 days", required = false)
            String timeRange,
            @ToolParam(description = "Max number of messages to return, suggest 5 to 10", required = false)
            Integer limit,
            ToolContext toolContext) {
        long start = System.currentTimeMillis();
        Long currentSessionId = resolveSessionId(toolContext);
        TimeWindow window = resolveTimeWindow(blankToDefault(timeRange, query));
        int safeLimit = limit == null ? ChatConstants.DEFAULT_MESSAGE_LIMIT : limit;
        String rawQuery = blankToDefault(query, "");
        HistorySearchSupportTools.SearchMode mode = HistorySearchSupportTools.resolveMode(rawQuery);

        log.info("[Tool:searchChatHistory] invoked query=\"{}\" mode={} sessionId={} timeRange=\"{}\" limit={}",
                rawQuery, mode, currentSessionId, timeRange, safeLimit);

        List<HistoryLine> lines = switch (mode) {
            case ABSTRACT_RECALL -> searchAbstractRecall(currentSessionId, window, safeLimit);
            case KEYWORD -> searchByKeyword(rawQuery, currentSessionId, window, safeLimit);
        };

        log.info("[Tool:searchChatHistory] completed results={} elapsed={}ms",
                lines.size(), System.currentTimeMillis() - start);

        if (lines.isEmpty()) {
            return ChatConstants.HISTORY_NO_MATCH;
        }

        String header = mode == HistorySearchSupportTools.SearchMode.ABSTRACT_RECALL
                ? ChatConstants.HISTORY_RECALL_HEADER
                : ChatConstants.HISTORY_HEADER;
        return formatLines(header, lines);
    }

    @Tool(description = """
            List the current user's recent chat sessions. Use when the user asks about what they
            discussed before, wants to see conversation history, or needs a session overview.
            Returns session titles, timestamps, message counts, and previews.""")
    public String getRecentChatSessions(
            @ToolParam(description = "Max number of sessions to return, suggest 3 to 8", required = false)
            Integer limit) {
        long start = System.currentTimeMillis();
        int safeLimit = limit == null ? ChatConstants.DEFAULT_SESSION_LIMIT : Math.min(Math.max(limit, 1), 10);

        log.info("[Tool:getRecentChatSessions] invoked limit={}", safeLimit);

        Page<ChatSessionEntity> page = persistenceService.listSessions(PageRequest.of(0, safeLimit));
        List<ChatSessionEntity> sessions = page.getContent();

        log.info("[Tool:getRecentChatSessions] completed results={} elapsed={}ms",
                sessions.size(), System.currentTimeMillis() - start);

        if (sessions.isEmpty()) {
            return ChatConstants.HISTORY_NO_SESSIONS;
        }

        StringBuilder result = new StringBuilder(ChatConstants.HISTORY_RECENT_HEADER + "\n");
        int sessionIndex = 1;
        for (ChatSessionEntity session : sessions) {
            result.append("- ")
                    .append(ChatConstants.HISTORY_SESSION_LABEL_PREFIX)
                    .append(sessionIndex++)
                    .append("，")
                    .append(formatTime(session.getLastActiveTime()))
                    .append("，标题：")
                    .append(blankToDefault(session.getTitle(), ChatConstants.DEFAULT_SESSION_TITLE))
                    .append("，消息数：")
                    .append(session.getMessageCount())
                    .append("，摘要：")
                    .append(blankToDefault(session.getLastMessagePreview(), ChatConstants.HISTORY_PREVIEW_DEFAULT))
                    .append('\n');
        }
        return result.toString();
    }

    private List<HistoryLine> searchAbstractRecall(Long currentSessionId, TimeWindow window, int limit) {
        int recallLimit = Math.min(Math.max(limit, ChatConstants.DEFAULT_MESSAGE_LIMIT),
                ChatConstants.HISTORY_RECALL_MAX_MESSAGES);
        Map<String, HistoryLine> merged = new LinkedHashMap<>();

        appendHotSessionMessages(merged, currentSessionId, null, recallLimit, true);
        if (currentSessionId != null) {
            appendPersistedSessionMessages(merged, currentSessionId, null, recallLimit, true);
        }
        appendRecentPersistedMessages(merged, window, recallLimit, currentSessionId != null);

        return trimLines(merged, recallLimit);
    }

    private List<HistoryLine> searchByKeyword(String rawQuery, Long currentSessionId, TimeWindow window, int limit) {
        List<String> tokens = HistorySearchSupportTools.extractSearchTokens(rawQuery);
        String primaryToken = tokens.isEmpty() ? rawQuery.trim() : tokens.get(0);
        Map<String, HistoryLine> merged = new LinkedHashMap<>();

        appendHotSessionMessages(merged, currentSessionId,
                content -> matchesKeyword(content, rawQuery, tokens), limit, true);
        if (currentSessionId != null) {
            appendPersistedSessionMessages(merged, currentSessionId,
                    content -> matchesKeyword(content, rawQuery, tokens), limit, true);
        }

        List<ChatMessageEntity> keywordHits = persistenceService.searchCurrentUserMessages(
                primaryToken, window.startTime(), window.endTime(), limit);
        for (ChatMessageEntity message : keywordHits) {
            if (matchesKeyword(message.getContent(), rawQuery, tokens)) {
                addEntityLine(merged, message, currentSessionId != null && currentSessionId.equals(message.getSessionId()));
            }
        }

        if (merged.size() < limit && tokens.size() > 1) {
            for (String token : tokens.subList(1, tokens.size())) {
                List<ChatMessageEntity> tokenHits = persistenceService.searchCurrentUserMessages(
                        token, window.startTime(), window.endTime(), limit);
                for (ChatMessageEntity message : tokenHits) {
                    addEntityLine(merged, message, currentSessionId != null && currentSessionId.equals(message.getSessionId()));
                }
                if (merged.size() >= limit) {
                    break;
                }
            }
        }

        if (merged.isEmpty()) {
            List<ChatMessageEntity> recent = persistenceService.listRecentCurrentUserMessages(
                    window.startTime(), window.endTime(), limit);
            for (ChatMessageEntity message : recent) {
                if (matchesKeyword(message.getContent(), rawQuery, tokens)) {
                    addEntityLine(merged, message, currentSessionId != null && currentSessionId.equals(message.getSessionId()));
                }
            }
        }

        return trimLines(merged, limit);
    }

    private void appendHotSessionMessages(
            Map<String, HistoryLine> merged,
            Long sessionId,
            java.util.function.Predicate<String> contentFilter,
            int limit,
            boolean markCurrentSession) {
        if (sessionId == null) {
            return;
        }
        ChatSession session = sessionManager.get(sessionId);
        if (session == null) {
            return;
        }
        List<Message> messages = session.getMessages();
        int start = Math.max(0, messages.size() - limit);
        for (int i = messages.size() - 1; i >= start; i--) {
            Message message = messages.get(i);
            String role;
            String content;
            if (message instanceof UserMessage userMessage) {
                role = ChatConstants.ROLE_USER;
                content = userMessage.getText();
            } else if (message instanceof AssistantMessage assistantMessage) {
                role = ChatConstants.ROLE_ASSISTANT;
                content = assistantMessage.getText();
            } else {
                continue;
            }
            if (contentFilter != null && !contentFilter.test(content)) {
                continue;
            }
            addLine(merged, new HistoryLine(
                    LocalDateTime.now(ChatConstants.BEIJING_ZONE),
                    role,
                    content,
                    markCurrentSession));
        }
    }

    private void appendPersistedSessionMessages(
            Map<String, HistoryLine> merged,
            Long sessionId,
            java.util.function.Predicate<String> contentFilter,
            int limit,
            boolean markCurrentSession) {
        List<ChatMessageEntity> messages = persistenceService.listRecentMessagesForContext(sessionId, limit);
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessageEntity message = messages.get(i);
            if (contentFilter != null && !contentFilter.test(message.getContent())) {
                continue;
            }
            addEntityLine(merged, message, markCurrentSession);
        }
    }

    private void appendRecentPersistedMessages(
            Map<String, HistoryLine> merged,
            TimeWindow window,
            int limit,
            boolean markCurrentSession) {
        List<ChatMessageEntity> messages = persistenceService.listRecentCurrentUserMessages(
                window.startTime(), window.endTime(), limit);
        for (ChatMessageEntity message : messages) {
            addEntityLine(merged, message, markCurrentSession);
        }
    }

    private void addEntityLine(Map<String, HistoryLine> merged, ChatMessageEntity message, boolean currentSession) {
        addLine(merged, new HistoryLine(message.getCreateTime(), message.getRole(), message.getContent(), currentSession));
    }

    private void addLine(Map<String, HistoryLine> merged, HistoryLine line) {
        merged.putIfAbsent(line.dedupeKey(), line);
    }

    private List<HistoryLine> trimLines(Map<String, HistoryLine> merged, int limit) {
        return new ArrayList<>(merged.values()).stream().limit(limit).toList();
    }

    private boolean matchesKeyword(String content, String rawQuery, List<String> tokens) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String haystack = content.toLowerCase(Locale.ROOT);
        if (!rawQuery.isBlank() && haystack.contains(rawQuery.trim().toLowerCase(Locale.ROOT))) {
            return true;
        }
        return HistorySearchSupportTools.matchesAnyToken(content, tokens);
    }

    private Long resolveSessionId(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(ChatRequestContext.SESSION_ID_KEY);
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String formatLines(String header, List<HistoryLine> lines) {
        StringBuilder result = new StringBuilder(header).append('\n');
        for (HistoryLine line : lines) {
            result.append("- ")
                    .append(formatTime(line.time()))
                    .append(line.currentSession() ? ChatConstants.HISTORY_CURRENT_SESSION_TAG : "")
                    .append("，")
                    .append(formatRole(line.role()))
                    .append("：")
                    .append(truncate(line.content(), 180))
                    .append('\n');
        }
        return result.toString();
    }

    private TimeWindow resolveTimeWindow(String timeRange) {
        if (timeRange == null || timeRange.isBlank()) {
            return lastDays(ChatConstants.DEFAULT_LOOKBACK_DAYS);
        }

        String normalized = timeRange.trim().toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now(ChatConstants.BEIJING_ZONE);
        if (normalized.contains("今天") || normalized.contains("today")) {
            return day(today);
        }
        if (normalized.contains("昨晚") || normalized.contains("last night")) {
            return day(today.minusDays(1));
        }
        if (normalized.contains("昨天") || normalized.contains("yesterday")) {
            return day(today.minusDays(1));
        }
        if (normalized.contains("前天")) {
            return day(today.minusDays(2));
        }
        if (normalized.contains("一周") || normalized.contains("7") || normalized.contains("week")) {
            return lastDays(7);
        }
        if (normalized.contains("30") || normalized.contains("一个月") || normalized.contains("month")) {
            return lastDays(30);
        }
        if (normalized.contains("全部") || normalized.contains("所有") || normalized.contains("all")) {
            return new TimeWindow(null, null);
        }
        return lastDays(ChatConstants.DEFAULT_LOOKBACK_DAYS);
    }

    private TimeWindow day(LocalDate date) {
        return new TimeWindow(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }

    private TimeWindow lastDays(int days) {
        return new TimeWindow(LocalDateTime.now(ChatConstants.BEIJING_ZONE).minusDays(days), null);
    }

    private String formatRole(String role) {
        if (ChatConstants.ROLE_USER.equals(role)) {
            return ChatConstants.ROLE_DISPLAY_USER;
        }
        if (ChatConstants.ROLE_ASSISTANT.equals(role)) {
            return ChatConstants.ROLE_DISPLAY_ASSISTANT;
        }
        return role;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? ChatConstants.HISTORY_UNKNOWN_TIME
                : time.format(ChatConstants.BEIJING_TIME_FORMATTER);
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 1) + "…";
    }

    private record HistoryLine(LocalDateTime time, String role, String content, boolean currentSession) {
        String dedupeKey() {
            return role + "|" + content;
        }
    }

    private record TimeWindow(LocalDateTime startTime, LocalDateTime endTime) {}
}
