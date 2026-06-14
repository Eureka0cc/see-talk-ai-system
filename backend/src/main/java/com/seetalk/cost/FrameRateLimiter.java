package com.seetalk.cost;

public interface FrameRateLimiter {

    boolean allow(Long sessionId);

    void cleanup(Long sessionId);
}
