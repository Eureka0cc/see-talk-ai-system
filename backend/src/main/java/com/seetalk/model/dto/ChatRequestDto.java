package com.seetalk.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequestDto {

    @Schema(description = "用户文本消息")
    private String text;

    @Schema(description = "Base64 编码的 JPEG/PNG 图像（可选）")
    private String image;
}
