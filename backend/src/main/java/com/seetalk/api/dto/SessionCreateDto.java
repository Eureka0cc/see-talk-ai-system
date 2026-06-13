package com.seetalk.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record SessionCreateDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id) {}
