package com.seetalk.tool;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网页搜索工具 — 通过 DuckDuckGo 免费搜索接口获取实时网页信息。
 * <p>AI 模型可通过 {@code @Tool} 注解自动调用此工具，无需用户手动触发。</p>
 */
@Slf4j
@Component
public class WebSearchTools {

    /** 搜索结果数量上限 */
    private static final int MAX_RESULTS = 8;
    /** 每条结果摘要最大字符数 */
    private static final int MAX_SNIPPET_LENGTH = 280;
    /** 搜索请求超时毫秒 */
    private static final int SEARCH_TIMEOUT_MS = 8_000;
    /** DuckDuckGo HTML 搜索（非 JS 版本，轻量 HTML，便于解析） */
    private static final String SEARCH_URL = "https://html.duckduckgo.com/html/";
    /** 请求 User-Agent */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    // 预编译正则（DuckDuckGo HTML 搜索结果格式）
    private static final Pattern RESULT_BLOCK = Pattern.compile(
            "<a[^>]*rel=\"nofollow\"[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>([^<]+)</a>");
    private static final Pattern SNIPPET_BLOCK = Pattern.compile(
            "<a[^>]*class=\"result__snippet\"[^>]*>((?:(?!</a>).)*)</a>", Pattern.DOTALL);

    /**
     * 搜索网页并返回结果摘要。
     * <p>当用户询问实时信息、新闻、事实性知识等超出 AI 训练数据的问题时使用。</p>
     *
     * @param query 搜索关键词或问题
     * @param limit 返回结果数量上限，建议 3 到 8
     * @return 格式化后的搜索结果文本
     */
    @Tool(description = """
            Search the web for real-time information. Use when the user asks about current events,
            news, facts, weather, or any topic that may require up-to-date data beyond your
            training cutoff. Returns a formatted list of search result titles, URLs, and snippets.""")
    public String searchWeb(
            @ToolParam(description = "Search query — keywords or a natural language question", required = true)
            String query,
            @ToolParam(description = "Max number of results to return, suggest 3 to 8", required = false)
            Integer limit) {

        Instant start = Instant.now();
        int safeLimit = Math.min(limit == null ? 5 : Math.max(limit, 1), MAX_RESULTS);
        String trimmedQuery = query == null || query.isBlank() ? "" : query.trim();

        log.info("[Tool:webSearch] invoked query=\"{}\" limit={}", trimmedQuery, safeLimit);

        if (trimmedQuery.isEmpty()) {
            log.warn("[Tool:webSearch] empty query, returning fallback");
            return "搜索关键词为空，请输入你想搜索的内容。";
        }

        try {
            String html = fetchSearchResults(trimmedQuery);
            List<SearchResult> results = parseResults(html, safeLimit);

            long elapsed = Duration.between(start, Instant.now()).toMillis();
            log.info("[Tool:webSearch] completed query=\"{}\" results={} elapsed={}ms",
                    trimmedQuery, results.size(), elapsed);

            if (results.isEmpty()) {
                return "未找到与「" + trimmedQuery + "」相关的搜索结果。";
            }

            return formatResults(results, trimmedQuery);
        } catch (Exception e) {
            long elapsed = Duration.between(start, Instant.now()).toMillis();
            log.error("[Tool:webSearch] failed query=\"{}\" elapsed={}ms", trimmedQuery, elapsed, e);
            return "网页搜索暂时不可用：请求超时或网络异常。建议稍后重试，或换个关键词再搜。";
        }
    }

    /**
     * 搜索并返回简短回答（用于快速事实查询）。
     * <p>与 searchWeb 互补：searchWeb 返回多条结果列表，quickSearch 返回一句话摘要。</p>
     */
    @Tool(description = """
            Quick fact lookup — for simple questions expecting a short answer.
            Use when the user asks a straightforward factual question (e.g., "What time is it in Tokyo?",
            "Who won the 2024 World Series?"). Returns a concise answer, not a full result list.""")
    public String quickSearch(
            @ToolParam(description = "A short, specific question or fact lookup query", required = true)
            String query) {

        Instant start = Instant.now();
        String trimmedQuery = query == null || query.isBlank() ? "" : query.trim();

        log.info("[Tool:quickSearch] invoked query=\"{}\"", trimmedQuery);

        if (trimmedQuery.isEmpty()) {
            return "搜索关键词为空。";
        }

        try {
            // quickSearch 取前 2 条结果，合并为简短摘要
            String html = fetchSearchResults(trimmedQuery);
            List<SearchResult> results = parseResults(html, 2);

            long elapsed = Duration.between(start, Instant.now()).toMillis();
            log.info("[Tool:quickSearch] completed query=\"{}\" results={} elapsed={}ms",
                    trimmedQuery, results.size(), elapsed);

            if (results.isEmpty()) {
                return "未找到与「" + trimmedQuery + "」相关的信息。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("快速搜索结果：\n");
            for (int i = 0; i < results.size(); i++) {
                SearchResult r = results.get(i);
                sb.append((i + 1)).append(". ").append(r.title).append("\n");
                sb.append("   ").append(r.snippet).append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            long elapsed = Duration.between(start, Instant.now()).toMillis();
            log.error("[Tool:quickSearch] failed query=\"{}\" elapsed={}ms", trimmedQuery, elapsed, e);
            return "快速搜索暂时不可用。";
        }
    }

    // ── 内部实现 ────────────────────────────────────────────

    private String fetchSearchResults(String query) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = SEARCH_URL + "?q=" + encoded;

        log.debug("[WebSearch] requesting url={}", url);

        try (HttpResponse response = HttpRequest.get(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .timeout(SEARCH_TIMEOUT_MS)
                .execute()) {

            if (response.getStatus() >= 400) {
                log.warn("[WebSearch] non-2xx status={} url={}", response.getStatus(), url);
                throw new RuntimeException("Search returned HTTP " + response.getStatus());
            }
            return response.body();
        }
    }

    private List<SearchResult> parseResults(String html, int limit) {
        List<SearchResult> results = new ArrayList<>();

        // 提取所有结果块：标题 + 链接
        Matcher linkMatcher = RESULT_BLOCK.matcher(html);
        List<String[]> links = new ArrayList<>();
        while (linkMatcher.find()) {
            String href = linkMatcher.group(1);
            String title = linkMatcher.group(2).trim();
            // 清理标题中的 HTML 实体和标签
            title = title.replaceAll("<[^>]+>", "").replaceAll("&[a-z]+;", " ").trim();
            if (!title.isEmpty() && !href.isBlank()) {
                links.add(new String[]{title, href});
            }
        }

        // 提取摘要
        Matcher snippetMatcher = SNIPPET_BLOCK.matcher(html);
        List<String> snippets = new ArrayList<>();
        while (snippetMatcher.find()) {
            String snippet = snippetMatcher.group(1).trim();
            snippet = snippet.replaceAll("<[^>]+>", "").replaceAll("&[a-z]+;", " ").trim();
            if (!snippet.isEmpty()) {
                snippets.add(snippet);
            }
        }

        // 配对标题与摘要
        int count = Math.min(limit, links.size());
        for (int i = 0; i < count; i++) {
            String[] link = links.get(i);
            String snippet = i < snippets.size() ? snippets.get(i) : "";
            if (snippet.length() > MAX_SNIPPET_LENGTH) {
                snippet = snippet.substring(0, MAX_SNIPPET_LENGTH - 1) + "…";
            }
            results.add(new SearchResult(link[0], link[1], snippet));
        }

        return results;
    }

    private String formatResults(List<SearchResult> results, String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("关于「").append(query).append("」的搜索结果：\n\n");
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append(i + 1).append(". **").append(r.title).append("**\n");
            sb.append("   ").append(r.url).append("\n");
            if (!r.snippet.isBlank()) {
                sb.append("   ").append(r.snippet).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private record SearchResult(String title, String url, String snippet) {}
}
