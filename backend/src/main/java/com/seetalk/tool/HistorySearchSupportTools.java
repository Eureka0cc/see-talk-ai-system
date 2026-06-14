package com.seetalk.tool;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 历史检索策略：区分「抽象回忆」与「关键词检索」，并抽取可检索词元。
 */
final class HistorySearchSupportTools {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern CJK_SEGMENT = Pattern.compile("[\\u4e00-\\u9fff]{2,}");
    private static final List<String> ABSTRACT_MARKERS = List.of(
            "说过什么", "聊过什么", "提过什么", "我说过什么", "还记得", "记得吗",
            "有没有说过", "刚才说的", "刚刚说的", "上面说的", "之前说的什么");
    private static final List<String> STOP_WORDS = List.of(
            "有没有", "是否", "找过你", "聊过", "聊天", "历史", "昨天", "今天", "前天",
            "上次", "之前", "过去", "提到的", "说的", "请问", "告诉", "一下", "能否",
            "可以", "帮我", "查一下", "搜索", "关于");

    private HistorySearchSupportTools() {}

    enum SearchMode {
        ABSTRACT_RECALL,
        KEYWORD
    }

    static SearchMode resolveMode(String query) {
        if (query == null || query.isBlank()) {
            return SearchMode.ABSTRACT_RECALL;
        }
        String trimmed = query.trim();
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        for (String marker : ABSTRACT_MARKERS) {
            if (normalized.contains(marker)) {
                return SearchMode.ABSTRACT_RECALL;
            }
        }
        if (endsWithRecallPronoun(trimmed)) {
            return SearchMode.ABSTRACT_RECALL;
        }
        if (extractSearchTokens(trimmed).isEmpty()) {
            return SearchMode.ABSTRACT_RECALL;
        }
        return SearchMode.KEYWORD;
    }

    static List<String> extractSearchTokens(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String cleaned = query.trim();
        for (String stopWord : STOP_WORDS) {
            cleaned = cleaned.replace(stopWord, " ");
        }
        cleaned = cleaned.replace("我", " ")
                .replace("你", " ")
                .replace("吗", " ")
                .replace("呢", " ")
                .replace("？", " ")
                .replace("?", " ")
                .trim();
        cleaned = WHITESPACE.matcher(cleaned).replaceAll(" ");

        Set<String> tokens = new LinkedHashSet<>();
        for (String part : cleaned.split(" ")) {
            collectTokens(part.trim(), tokens);
        }
        if (tokens.isEmpty()) {
            collectTokens(cleaned.replace(" ", ""), tokens);
        }
        return List.copyOf(tokens);
    }

    private static void collectTokens(String text, Set<String> tokens) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher matcher = CJK_SEGMENT.matcher(text);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            addToken(matcher.group(), tokens);
        }
        if (!found) {
            addToken(text, tokens);
        }
    }

    private static void addToken(String token, Set<String> tokens) {
        if (token.length() < 2 || isRecallPronoun(token)) {
            return;
        }
        tokens.add(token);
        if (token.length() > 3) {
            for (int len = 2; len <= Math.min(4, token.length() - 1); len++) {
                String suffix = token.substring(token.length() - len);
                if (suffix.length() >= 2 && !isRecallPronoun(suffix)) {
                    tokens.add(suffix);
                }
            }
        }
    }

    static boolean matchesAnyToken(String content, List<String> tokens) {
        if (content == null || content.isBlank() || tokens.isEmpty()) {
            return false;
        }
        String haystack = content.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (haystack.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean endsWithRecallPronoun(String text) {
        return text.endsWith("谁")
                || text.endsWith("什么")
                || text.endsWith("哪些")
                || text.endsWith("哪个")
                || text.endsWith("哪天")
                || text.endsWith("几时");
    }

    private static boolean isRecallPronoun(String token) {
        return token.equals("谁")
                || token.equals("什么")
                || token.equals("哪些")
                || token.equals("哪个")
                || token.equals("哪天")
                || token.equals("几时")
                || token.equals("喜欢谁");
    }
}
