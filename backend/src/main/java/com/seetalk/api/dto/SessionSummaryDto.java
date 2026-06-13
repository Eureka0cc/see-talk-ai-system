package com.seetalk.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

public record SessionSummaryDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String title,
        String preview,
        LocalDateTime lastActiveTime,
        Integer messageCount,
        LocalDateTime createTime) {}
