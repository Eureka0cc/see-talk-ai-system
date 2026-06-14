package com.seetalk.session;

/**
 * 请求级会话上下文键，供 Spring AI {@code ToolContext} 使用。
 */
public final class ChatRequestContext {

    public static final String SESSION_ID_KEY = "sessionId";

    private ChatRequestContext() {}
}
