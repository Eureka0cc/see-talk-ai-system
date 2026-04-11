package com.seetalk.session.redis;

import com.seetalk.model.constants.SessionConstants;

public final class RedisSessionKeys {

    private static final String PREFIX = SessionConstants.REDIS_KEY_PREFIX;

    private RedisSessionKeys() {}

    public static String session(Long sessionId) {
        return PREFIX + "session:" + sessionId;
    }

    public static String memory(Long sessionId) {
        return PREFIX + "memory:" + sessionId;
    }

    public static String frames(Long sessionId) {
        return PREFIX + "frames:" + sessionId;
    }
}
