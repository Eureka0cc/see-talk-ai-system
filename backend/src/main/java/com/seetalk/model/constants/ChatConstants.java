package com.seetalk.model.constants;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 聊天相关常量 — 角色、模型配置、会话/标题参数、视觉识别、时间与历史查询。
 */
public final class ChatConstants {

    private ChatConstants() {}

    // ── 角色 ────────────────────────────────────────────
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    // ── 角色展示名 ──────────────────────────────────────
    public static final String ROLE_DISPLAY_USER = "用户";
    public static final String ROLE_DISPLAY_ASSISTANT = "SeeTalk";

    // ── 模型默认值 ──────────────────────────────────────
    public static final String DEFAULT_MODEL = "qwen3-vl-flash";
    public static final double DEFAULT_TEMPERATURE = 0.7;
    public static final int DEFAULT_MAX_TOKENS = 300;

    // ── 会话 ────────────────────────────────────────────
    public static final String DEFAULT_SESSION_TITLE = "新对话";
    public static final int SESSION_PREVIEW_MAX_LENGTH = 80;

    // ── 标题生成 ────────────────────────────────────────
    public static final int FALLBACK_TITLE_MAX_LENGTH = 128;
    public static final int GENERATED_TITLE_MAX_LENGTH = 30;
    public static final double TITLE_GEN_TEMPERATURE = 0.3;
    public static final int TITLE_GEN_MAX_TOKENS = 40;
    public static final int TITLE_GEN_USER_TRUNCATE = 200;
    public static final int TITLE_GEN_ASSISTANT_TRUNCATE = 300;

    // ── 流式处理 ────────────────────────────────────────
    /** 流式输出安全缓冲区字符数，防止截断敏感信息 */
    public static final int STREAM_GUARD_BUFFER_CHARS = 64;

    // ── 视觉请求识别（用户文本匹配） ────────────────────
    /** 用于判断用户是否主动要求画面描述的常见中文短语 */
    public static final List<String> VISION_REQUEST_PATTERNS = List.of(
            "看到什么", "看到了吗", "你看到", "你看到了",
            "帮我看看", "帮我看一下", "看一下画面", "看一下镜头",
            "画面", "镜头", "摄像头",
            "我脸上", "我的脸上", "我穿", "我身后", "我旁边", "我手里",
            "这是什么", "那个是什么", "这个是什么", "那是什么",
            "这个呢", "那个呢", "这边", "那边", "前面", "后面",
            "什么颜色", "长什么样", "好不好看");

    // ── 时间 ────────────────────────────────────────────
    public static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    public static final DateTimeFormatter BEIJING_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ── 历史查询默认值 ──────────────────────────────────
    public static final int DEFAULT_MESSAGE_LIMIT = 8;
    public static final int DEFAULT_SESSION_LIMIT = 5;
    /** 未指定时间范围时的默认回溯天数 */
    public static final int DEFAULT_LOOKBACK_DAYS = 30;

    // ── 历史工具响应文案 ────────────────────────────────
    public static final String HISTORY_NO_MATCH = "没有查到匹配的历史聊天消息。";
    public static final String HISTORY_HEADER = "历史聊天消息（按时间从近到远）：";
    public static final String HISTORY_NO_SESSIONS = "当前用户还没有历史会话。";
    public static final String HISTORY_RECENT_HEADER = "最近历史会话：";
    public static final String HISTORY_SESSION_LABEL_PREFIX = "历史会话#";
    public static final String HISTORY_PREVIEW_DEFAULT = "无";
    public static final String HISTORY_UNKNOWN_TIME = "未知时间";
}
