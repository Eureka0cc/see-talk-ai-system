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
public class MessageDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String role;
    private String content;
    private Boolean usedVision;
    private LocalDateTime createTime;
}
