package com.seetalk.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatMessageSerdeTest {

    private ChatMessageSerde serde;

    @BeforeEach
    void setUp() {
        serde = new ChatMessageSerde(new ObjectMapper());
    }

    @Test
    void textOnlyUserMessageRoundTrip() {
        UserMessage original = new UserMessage("hello");
        String json = serde.serialize(original);
        assertNotNull(json);
        assertTrue(json.contains("\"has_media\":false"));

        Message restored = serde.deserialize(json);
        assertInstanceOf(UserMessage.class, restored);
        assertEquals("hello", ((UserMessage) restored).getText());
    }

    @Test
    void assistantMessageRoundTrip() {
        AssistantMessage original = new AssistantMessage("reply");
        String json = serde.serialize(original);
        assertNotNull(json);

        Message restored = serde.deserialize(json);
        assertInstanceOf(AssistantMessage.class, restored);
        assertEquals("reply", ((AssistantMessage) restored).getText());
    }

    @Test
    void userMessageWithMediaSerializesTextAndMediaFlag() {
        Media media = Media.builder()
                .mimeType(MimeTypeUtils.IMAGE_JPEG)
                .data(new ByteArrayResource(new byte[] {1, 2, 3}))
                .build();
        UserMessage original = UserMessage.builder()
                .text("describe this")
                .media(media)
                .build();

        String json = serde.serialize(original);
        assertNotNull(json);
        assertTrue(json.contains("\"has_media\":true"));
        assertTrue(json.contains("describe this"));

        Message restored = serde.deserialize(json);
        assertInstanceOf(UserMessage.class, restored);
        UserMessage user = (UserMessage) restored;
        assertEquals("describe this", user.getText());
        assertTrue(user.getMedia() == null || user.getMedia().isEmpty());
    }
}
