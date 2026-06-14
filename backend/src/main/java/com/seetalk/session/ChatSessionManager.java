package com.seetalk.session;

import com.seetalk.config.SeeTalkProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatSessionManager {

    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();
    private final SeeTalkProperties properties;

    public ChatSessionManager(SeeTalkProperties properties) {
        this.properties = properties;
    }

    public ChatSession create() {
        ChatSession session = new ChatSession();
        sessions.put(session.getId(), session);
        return session;
    }

    public ChatSession get(String sessionId) {
        ChatSession session = sessions.get(sessionId);
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

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }
}
