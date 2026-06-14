package com.seetalk.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    private static final String SYSTEM_PROMPT = """
            你是 SeeTalk，一位友好的 AI 视觉对话助手。
            用户通过摄像头与你实时交流，你可以看到当前画面并听到用户说的话。
            请用简洁、自然的中文回答，语气亲切。
            如果画面中没有明显可描述的内容，请诚实说明。
            回答控制在 2-4 句话，适合语音播报。""";

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }
}
