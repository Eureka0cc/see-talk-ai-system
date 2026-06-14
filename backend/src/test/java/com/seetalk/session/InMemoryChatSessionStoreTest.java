package com.seetalk.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class InMemoryChatSessionStoreTest {

    private final InMemoryChatSessionStore store =
            new InMemoryChatSessionStore(new ChatMessageSerde(new ObjectMapper()));

    @Test
    void saveLoadAndDeleteRoundTrip() {
        Long sessionId = 1749123456789012345L;
        ChatSession session = new ChatSession(sessionId);
        session.addUserMessage("hello");
        session.setLastImageHash("abc");

        store.save(session);
        ChatSession loaded = store.load(sessionId);
        assertNotNull(loaded);
        assertEquals(1, loaded.getMessages().size());
        assertEquals("abc", loaded.getLastImageHash());

        store.delete(sessionId);
        assertNull(store.load(sessionId));
    }
}
