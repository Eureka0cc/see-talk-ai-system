package com.seetalk.session;

import com.seetalk.config.SeeTalkProperties;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

@Component
public class ChatSessionManager {

    private final ChatSessionStore store;
    private final SeeTalkProperties properties;

    public ChatSessionManager(ChatSessionStore store, SeeTalkProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    public ChatSession create(Long sessionId) {
        ChatSession session = new ChatSession(sessionId);
        store.save(session);
        return session;
    }

    public ChatSession get(Long sessionId) {
        ChatSession session = store.load(sessionId);
        if (session == null) {
            return null;
        }
        Duration idle = Duration.between(session.getLastActive(), Instant.now());
        if (idle.getSeconds() > properties.getSessionTimeoutSeconds()) {
            remove(sessionId);
            return null;
        }
        return session;
    }

    public ChatSession getOrCreate(Long sessionId, Supplier<List<Message>> restorer) {
        ChatSession existing = get(sessionId);
        if (existing != null) {
            return existing;
        }
        ChatSession session = new ChatSession(sessionId);
        if (restorer != null) {
            List<Message> restoredMessages = restorer.get();
            if (restoredMessages != null && !restoredMessages.isEmpty()) {
                session.replaceMessages(restoredMessages);
            }
        }
        save(session);
        return session;
    }

    public void save(ChatSession session) {
        store.save(session);
        store.refreshTtl(session.getId());
    }

    public void remove(Long sessionId) {
        store.delete(sessionId);
    }
}
