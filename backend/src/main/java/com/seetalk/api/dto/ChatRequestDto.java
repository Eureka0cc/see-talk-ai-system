package com.seetalk.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ChatRequestDto(
        @Schema(description = "用户文本消息")
        String text,
        @Schema(description = "Base64 编码的 JPEG/PNG 图像（可选）")
        String image) {}
