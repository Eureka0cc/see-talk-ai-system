package com.seetalk.cost;

import com.seetalk.config.SeeTalkProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
@Profile("test")
public class InMemoryFrameRateLimiter implements FrameRateLimiter {

    private static final long WINDOW_MS = 60_000L;

    private final Map<Long, Deque<Long>> sessions = new ConcurrentHashMap<>();
    private final int maxPerMinute;

    public InMemoryFrameRateLimiter(SeeTalkProperties properties) {
        this.maxPerMinute = properties.getMaxFramesPerMinute();
    }

    @Override
    public boolean allow(Long sessionId) {
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_MS;

        Deque<Long> frames = sessions.computeIfAbsent(sessionId,
                k -> new ConcurrentLinkedDeque<>());

        synchronized (frames) {
            while (!frames.isEmpty() && frames.peekFirst() != null
                    && frames.peekFirst() < windowStart) {
                frames.pollFirst();
            }
            if (frames.size() >= maxPerMinute) {
                return false;
            }
            frames.addLast(now);
            return true;
        }
    }

    @Override
    public void cleanup(Long sessionId) {
        sessions.remove(sessionId);
    }
}
