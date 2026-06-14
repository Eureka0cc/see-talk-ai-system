package com.seetalk.rate;

public interface FrameRateLimiter {

    boolean allow(Long sessionId);

    void cleanup(Long sessionId);
}
