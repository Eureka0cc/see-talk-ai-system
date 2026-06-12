package com.seetalk.session;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatSession {

    private final String id;
    private final List<Message> messages = new ArrayList<>();
    private String lastImageHash;
    private final Instant createdAt = Instant.now();
    private Instant lastActive = Instant.now();

    public ChatSession() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public String getLastImageHash() {
        return lastImageHash;
    }

    public void setLastImageHash(String lastImageHash) {
        this.lastImageHash = lastImageHash;
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

    public void addUserMessage(String text) {
        messages.add(new UserMessage(text));
        touch();
    }

    public void addAssistantMessage(String text) {
        messages.add(new AssistantMessage(text));
        touch();
    }

    public void clearHistory() {
        messages.clear();
        lastImageHash = null;
        touch();
    }

    public void trimHistory(int maxMessages) {
        if (messages.size() > maxMessages) {
            messages.subList(0, messages.size() - maxMessages).clear();
        }
    }
}
