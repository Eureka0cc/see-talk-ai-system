package com.seetalk.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionSummaryDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String title;
    private String preview;
    private LocalDateTime lastActiveTime;
    private Integer messageCount;
    private LocalDateTime createTime;
}
