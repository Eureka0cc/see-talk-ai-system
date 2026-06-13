package com.seetalk.session.redis;

public final class RedisSessionKeys {

    private static final String PREFIX = "seetalk:";

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
