package com.seetalk.controller;

import com.seetalk.service.VisionChatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final VisionChatService visionChatService;

    @Value("${spring.ai.dashscope.chat.options.model:qwen-vl-flash}")
    private String visionModel;

    public HealthController(VisionChatService visionChatService) {
        this.visionChatService = visionChatService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "api_configured", visionChatService.isApiConfigured(),
                "vision_model", visionModel
        );
    }
}
