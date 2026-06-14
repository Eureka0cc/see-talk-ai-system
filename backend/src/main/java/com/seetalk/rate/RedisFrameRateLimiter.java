package com.seetalk.rate;

import com.seetalk.config.SeeTalkProperties;
import com.seetalk.model.constants.RateLimitConstants;
import com.seetalk.session.redis.RedisSessionKeys;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@Profile("!test")
public class RedisFrameRateLimiter implements FrameRateLimiter {

    private static final Duration FRAMES_TTL = Duration.ofSeconds(RateLimitConstants.FRAMES_TTL_SECONDS);

    private final StringRedisTemplate redis;
    private final int maxPerMinute;

    public RedisFrameRateLimiter(StringRedisTemplate redis, SeeTalkProperties properties) {
        this.redis = redis;
        this.maxPerMinute = properties.getMaxFramesPerMinute();
    }

    @Override
    public boolean allow(Long sessionId) {
        String key = RedisSessionKeys.frames(sessionId);
        long now = System.currentTimeMillis();
        long windowStart = now - RateLimitConstants.WINDOW_MS;

        redis.opsForZSet().removeRangeByScore(key, 0, windowStart);
        Long count = redis.opsForZSet().zCard(key);
        if (count != null && count >= maxPerMinute) {
            return false;
        }
        redis.opsForZSet().add(key, UUID.randomUUID().toString(), now);
        redis.expire(key, FRAMES_TTL);
        return true;
    }

    @Override
    public void cleanup(Long sessionId) {
        redis.delete(RedisSessionKeys.frames(sessionId));
    }
}
