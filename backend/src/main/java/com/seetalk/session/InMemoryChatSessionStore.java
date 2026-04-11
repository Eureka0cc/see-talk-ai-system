package com.seetalk.session;

import com.seetalk.model.constants.SessionConstants;
import org.springframework.ai.chat.messages.Message;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("test")
public class InMemoryChatSessionStore implements ChatSessionStore {

    private final Map<Long, Map<String, String>> metaStore = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> messageStore = new ConcurrentHashMap<>();
    private final ChatMessageSerde messageSerde;

    public InMemoryChatSessionStore(ChatMessageSerde messageSerde) {
        this.messageSerde = messageSerde;
    }

    @Override
    public void save(ChatSession session) {
        Long id = session.getId();
        metaStore.put(id, Map.of(
                SessionConstants.META_LAST_IMAGE_HASH, session.getLastImageHash() != null
                        ? session.getLastImageHash() : "",
                SessionConstants.META_LAST_ACTIVE, String.valueOf(session.getLastActive().toEpochMilli()),
                SessionConstants.META_CREATED_AT, String.valueOf(session.getCreatedAt().toEpochMilli())
        ));
        List<String> msgs = new ArrayList<>();
        for (Message m : session.getMessages()) {
            String json = messageSerde.serialize(m);
            if (json != null) {
                msgs.add(json);
            }
        }
        messageStore.put(id, msgs);
    }

    @Override
    public ChatSession load(Long sessionId) {
        Map<String, String> meta = metaStore.get(sessionId);
        if (meta == null) {
            return null;
        }
        ChatSession session = new ChatSession(sessionId);
        String hash = meta.get(SessionConstants.META_LAST_IMAGE_HASH);
        if (hash != null && !hash.isBlank()) {
            session.setLastImageHash(hash);
        }
        List<String> rawMsgs = messageStore.get(sessionId);
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
        String lastActive = meta.get(SessionConstants.META_LAST_ACTIVE);
        if (lastActive != null && !lastActive.isBlank()) {
            session.setLastActive(Instant.ofEpochMilli(Long.parseLong(lastActive)));
        }
        return session;
    }

    @Override
    public void delete(Long sessionId) {
        metaStore.remove(sessionId);
        messageStore.remove(sessionId);
    }

    @Override
    public void refreshTtl(Long sessionId) {
        // TTL handled by ChatSessionManager idle timeout
    }
}
