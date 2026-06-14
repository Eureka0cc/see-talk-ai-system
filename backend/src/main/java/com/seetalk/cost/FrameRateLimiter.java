package com.seetalk.cost;

import com.seetalk.config.SeeTalkProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FrameRateLimiter {

    private final Map<String, List<Long>> timestamps = new ConcurrentHashMap<>();
    private final int maxPerMinute;

    public FrameRateLimiter(SeeTalkProperties properties) {
        this.maxPerMinute = properties.getMaxFramesPerMinute();
    }

    public boolean allow(String sessionId) {
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L;

        List<Long> ts = timestamps.computeIfAbsent(sessionId, k -> new ArrayList<>());
        synchronized (ts) {
            ts.removeIf(t -> t < windowStart);
            if (ts.size() >= maxPerMinute) {
                return false;
            }
            ts.add(now);
            return true;
        }
    }

    public void cleanup(String sessionId) {
        timestamps.remove(sessionId);
    }
}
