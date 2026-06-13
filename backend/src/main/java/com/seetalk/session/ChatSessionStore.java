package com.seetalk.session;

public interface ChatSessionStore {

    void save(ChatSession session);

    ChatSession load(Long sessionId);

    void delete(Long sessionId);

    void refreshTtl(Long sessionId);
}
