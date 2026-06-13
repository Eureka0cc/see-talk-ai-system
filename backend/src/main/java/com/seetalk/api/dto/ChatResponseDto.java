package com.seetalk.api.dto;

public record ChatResponseDto(
        String text,
        boolean usedVision,
        Long sessionId) {}
