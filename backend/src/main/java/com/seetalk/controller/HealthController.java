package com.seetalk.controller;

import com.seetalk.common.BaseResponse;
import com.seetalk.common.ResultUtils;
import com.seetalk.model.constants.ApiConstants;
import com.seetalk.model.constants.ChatConstants;
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

    @Value("${spring.ai.dashscope.chat.options.model:" + ChatConstants.DEFAULT_MODEL + "}")
    private String visionModel;

    public HealthController(VisionChatService visionChatService) {
        this.visionChatService = visionChatService;
    }

    @Operation(summary = "健康检查")
    @GetMapping(ApiConstants.HEALTH_PATH)
    public BaseResponse<Map<String, Object>> health() {
        return ResultUtils.success(Map.of(
                "status", "ok",
                "api_configured", visionChatService.isApiConfigured(),
                "vision_model", visionModel
        ));
    }
}
