package com.seetalk.model.constants;

/**
 * WebSocket 相关常量 — 连接参数、消息类型、用户提示文案。
 */
public final class WebSocketConstants {

    private WebSocketConstants() {}

    // ── 传输参数 ────────────────────────────────────────
    public static final int BUFFER_SIZE = 512 * 1024;
    public static final int SEND_TIME_LIMIT_MS = 5000;

    // ── 路径 ────────────────────────────────────────────
    public static final String CHAT_PATH = "/ws/chat";

    // ── 消息类型 ────────────────────────────────────────
    public static final String MSG_TYPE_PING = "ping";
    public static final String MSG_TYPE_USER_MESSAGE = "user_message";
    public static final String MSG_TYPE_CLEAR_HISTORY = "clear_history";

    // ── 消息字段名 ──────────────────────────────────────
    public static final String FIELD_TEXT = "text";
    public static final String FIELD_IMAGE = "image";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_SESSION_ID = "sessionId";
    public static final String FIELD_SESSION_ID_ALT = "session_id";

    // ── 用户提示 ────────────────────────────────────────
    public static final String CONNECTED_MSG = "连接成功，开始对话吧！";
    public static final String SESSION_EXPIRED_MSG = "会话已过期，请刷新页面重连";
    public static final String WAIT_RESPONSE_MSG = "请等待当前回复完成";
    public static final String EMPTY_MESSAGE_MSG = "消息内容为空";
    public static final String AI_ERROR_PREFIX = "AI 调用失败: ";
    public static final String UNKNOWN_TYPE_PREFIX = "未知消息类型: ";
}
