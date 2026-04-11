package com.seetalk.tool;

import com.seetalk.model.constants.ChatConstants;
import com.seetalk.model.entity.ChatMessageEntity;
import com.seetalk.model.entity.ChatSessionEntity;
import com.seetalk.service.ChatPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class ChatHistoryTools {

    // 时间、历史查询默认值与响应文案见 ChatConstants

    private final ChatPersistenceService persistenceService;

    public ChatHistoryTools(ChatPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Tool(description = """
            Search the current user's chat history. Use when the user asks about past conversations,
            wants to recall something discussed before, or mentions a specific date or time period.
            Returns summarized matching messages.""")
    public String searchChatHistory(
            @ToolParam(description = "Keyword or topic to search for; leave empty if user only asks about a date", required = false)
            String query,
            @ToolParam(description = "Natural language time range, e.g. today, yesterday, last week, last 30 days", required = false)
            String timeRange,
            @ToolParam(description = "Max number of messages to return, suggest 5 to 10", required = false)
            Integer limit) {
        TimeWindow window = resolveTimeWindow(blankToDefault(timeRange, query));
        int safeLimit = limit == null ? ChatConstants.DEFAULT_MESSAGE_LIMIT : limit;
        String normalizedQuery = normalizeQuery(query);
        List<ChatMessageEntity> messages = persistenceService.searchCurrentUserMessages(
                normalizedQuery, window.startTime(), window.endTime(), safeLimit);

        log.info("Chat history tool search query={} normalizedQuery={} timeRange={} resultCount={}",
                query, normalizedQuery, timeRange, messages.size());

        if (messages.isEmpty()) {
            return ChatConstants.HISTORY_NO_MATCH;
        }

        StringBuilder result = new StringBuilder(ChatConstants.HISTORY_HEADER + "\n");
        for (ChatMessageEntity message : messages) {
            result.append("- ")
                    .append(formatTime(message.getCreateTime()))
                    .append("，")
                    .append(formatRole(message.getRole()))
                    .append("：")
                    .append(truncate(message.getContent(), 180))
                    .append('\n');
        }
        return result.toString();
    }

    @Tool(description = """
            List the current user's recent chat sessions. Use when the user asks about what they
            discussed before, wants to see conversation history, or needs a session overview.
            Returns session titles, timestamps, message counts, and previews.""")
    public String getRecentChatSessions(
            @ToolParam(description = "Max number of sessions to return, suggest 3 to 8", required = false)
            Integer limit) {
        int safeLimit = limit == null ? ChatConstants.DEFAULT_SESSION_LIMIT : Math.min(Math.max(limit, 1), 10);
        Page<ChatSessionEntity> page = persistenceService.listSessions(PageRequest.of(0, safeLimit));
        List<ChatSessionEntity> sessions = page.getContent();

        log.info("Chat history tool recent sessions resultCount={}", sessions.size());

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

    private TimeWindow resolveTimeWindow(String timeRange) {
        if (timeRange == null || timeRange.isBlank()) {
            return lastDays(30);
        }

        String normalized = timeRange.trim().toLowerCase();
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
        return lastDays(30);
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        String normalized = query.trim()
                .replace("有没有", "")
                .replace("是否", "")
                .replace("找过你", "")
                .replace("聊过", "")
                .replace("聊天", "")
                .replace("历史", "")
                .replace("昨天", "")
                .replace("今天", "")
                .replace("前天", "")
                .replace("上次", "")
                .replace("之前", "")
                .replace("过去", "")
                .replace("提到的", "")
                .replace("说的", "")
                .replace("我", "")
                .replace("你", "")
                .trim();
        return normalized.length() < 2 ? "" : normalized;
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

    private record TimeWindow(LocalDateTime startTime, LocalDateTime endTime) {}
}
