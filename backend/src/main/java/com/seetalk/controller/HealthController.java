package com.seetalk.controller;

import com.seetalk.service.VisionChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "健康检查", description = "服务状态与模型配置")
@RestController
public class HealthController {

    private final VisionChatService visionChatService;

    @Value("${spring.ai.dashscope.chat.options.model:qwen3-vl-flash}")
    private String visionModel;

    public HealthController(VisionChatService visionChatService) {
        this.visionChatService = visionChatService;
    }

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "api_configured", visionChatService.isApiConfigured(),
                "vision_model", visionModel
        );
    }
}
