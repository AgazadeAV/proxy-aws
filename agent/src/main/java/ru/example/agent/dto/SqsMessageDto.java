package ru.example.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqsMessageDto {
    private String sessionId;
    private String s3Key;
    private String timestamp;
}
