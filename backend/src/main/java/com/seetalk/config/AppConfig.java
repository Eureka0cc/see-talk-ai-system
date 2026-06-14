package com.seetalk.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AppConfig {

    private static final String MULTIMODAL_COMPLETIONS_PATH =
            "/api/v1/services/aigc/multimodal-generation/generation";

    private static final String SYSTEM_PROMPT = """
            你是 SeeTalk，一位友好的 AI 视觉对话助手。
            用户通过摄像头与你实时交流，你可以看到当前画面并听到用户说的话。
            请用简洁、自然的中文回答，语气亲切。
            如果画面中没有明显可描述的内容，请诚实说明。
            回答控制在 2-4 句话，适合语音播报。""";

    @Bean
    @Primary
    public ChatModel customDashScopeChatModel(
            @Value("${spring.ai.dashscope.api-key:}") String apiKey,
            @Value("${spring.ai.dashscope.chat.options.model:qwen3-vl-flash}") String model,
            @Value("${spring.ai.dashscope.chat.options.temperature:0.7}") Double temperature,
            @Value("${spring.ai.dashscope.chat.options.max-tokens:300}") Integer maxTokens) {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(apiKey)
                .completionsPath(MULTIMODAL_COMPLETIONS_PATH)
                .build();

        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(model)
                .withMultiModel(true)
                .withTemperature(temperature)
                .withMaxToken(maxTokens)
                .build();

        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(options)
                .build();
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }
}
