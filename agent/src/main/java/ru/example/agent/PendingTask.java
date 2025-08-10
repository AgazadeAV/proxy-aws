package ru.example.agent;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PendingTask {
    private final String receiptHandle;
    private final String json;
}
