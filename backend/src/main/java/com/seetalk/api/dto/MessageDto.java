package com.seetalk.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

public record MessageDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String role,
        String content,
        Boolean usedVision,
        LocalDateTime createTime) {}
