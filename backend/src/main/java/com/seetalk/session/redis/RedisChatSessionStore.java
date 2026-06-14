package com.seetalk.session.redis;

import com.seetalk.config.SeeTalkProperties;
import com.seetalk.model.constants.SessionConstants;
import com.seetalk.session.ChatMessageSerde;
import com.seetalk.session.ChatSession;
import com.seetalk.session.ChatSessionStore;
import org.springframework.ai.chat.messages.Message;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Profile("!test")
public class RedisChatSessionStore implements ChatSessionStore {

    private final StringRedisTemplate redis;
    private final SeeTalkProperties properties;
    private final ChatMessageSerde messageSerde;

    public RedisChatSessionStore(
            StringRedisTemplate redis,
            SeeTalkProperties properties,
            ChatMessageSerde messageSerde) {
        this.redis = redis;
        this.properties = properties;
        this.messageSerde = messageSerde;
    }

    @Override
    public void save(ChatSession session) {
        Long id = session.getId();
        String sessionKey = RedisSessionKeys.session(id);
        String memoryKey = RedisSessionKeys.memory(id);

        redis.opsForHash().putAll(sessionKey, Map.of(
                SessionConstants.META_LAST_IMAGE_HASH, session.getLastImageHash() != null ? session.getLastImageHash() : "",
                SessionConstants.META_LAST_ACTIVE, String.valueOf(session.getLastActive().toEpochMilli()),
                SessionConstants.META_CREATED_AT, String.valueOf(session.getCreatedAt().toEpochMilli())
        ));

        redis.delete(memoryKey);
        for (Message message : session.getMessages()) {
            String json = messageSerde.serialize(message);
            if (json != null) {
                redis.opsForList().rightPush(memoryKey, json);
            }
        }

        refreshTtl(id);
    }

    @Override
    public ChatSession load(Long sessionId) {
        String sessionKey = RedisSessionKeys.session(sessionId);
        Map<Object, Object> meta = redis.opsForHash().entries(sessionKey);
        if (meta.isEmpty()) {
            return null;
        }

        ChatSession session = new ChatSession(sessionId);
        Object hash = meta.get(SessionConstants.META_LAST_IMAGE_HASH);
        if (hash instanceof String hashText && !hashText.isBlank()) {
            session.setLastImageHash(hashText);
        }

        List<String> rawMsgs = redis.opsForList().range(RedisSessionKeys.memory(sessionId), 0, -1);
        if (rawMsgs != null && !rawMsgs.isEmpty()) {
            List<Message> msgs = new ArrayList<>();
            for (String raw : rawMsgs) {
                Message message = messageSerde.deserialize(raw);
                if (message != null) {
                    msgs.add(message);
                }
            }
            session.replaceMessages(msgs);
        }

        Object lastActive = meta.get(SessionConstants.META_LAST_ACTIVE);
        if (lastActive instanceof String lastActiveText && !lastActiveText.isBlank()) {
            session.setLastActive(Instant.ofEpochMilli(Long.parseLong(lastActiveText)));
        }
        return session;
    }

    @Override
    public void delete(Long sessionId) {
        redis.delete(RedisSessionKeys.session(sessionId));
        redis.delete(RedisSessionKeys.memory(sessionId));
    }

    @Override
    public void refreshTtl(Long sessionId) {
        Duration ttl = Duration.ofSeconds(properties.getSessionTimeoutSeconds());
        redis.expire(RedisSessionKeys.session(sessionId), ttl);
        redis.expire(RedisSessionKeys.memory(sessionId), ttl);
    }
}
