package com.seetalk.session;

import java.time.Instant;
import java.util.UUID;

public class ChatSession {

    private final String id;
    private final Instant createdAt = Instant.now();
    private Instant lastActive = Instant.now();

    public ChatSession() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastActive() {
        return lastActive;
    }

    public void touch() {
        this.lastActive = Instant.now();
    }

    public void clearHistory() {
        touch();
    }
}
