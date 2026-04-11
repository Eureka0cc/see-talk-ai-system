package com.seetalk.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.seetalk.model.constants.ApiConstants;
import com.seetalk.model.constants.ChatConstants;
import com.seetalk.model.constants.PromptConstants;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AppConfig {

    // System prompt is now in PromptConstants.SYSTEM_PROMPT

    @Bean
    @Primary
    public ChatModel customDashScopeChatModel(
            @Value("${spring.ai.dashscope.api-key:}") String apiKey,
            @Value("${spring.ai.dashscope.chat.options.model:" + ChatConstants.DEFAULT_MODEL + "}") String model,
            @Value("${spring.ai.dashscope.chat.options.temperature:" + ChatConstants.DEFAULT_TEMPERATURE + "}") Double temperature,
            @Value("${spring.ai.dashscope.chat.options.max-tokens:" + ChatConstants.DEFAULT_MAX_TOKENS + "}") Integer maxTokens) {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(apiKey)
                .completionsPath(ApiConstants.DASHSCOPE_MULTIMODAL_PATH)
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
                .defaultSystem(PromptConstants.SYSTEM_PROMPT)
                .build();
    }
}
