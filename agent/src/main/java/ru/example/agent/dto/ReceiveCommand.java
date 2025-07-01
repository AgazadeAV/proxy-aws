package ru.example.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiveCommand extends CommandMessage {
    {
        this.command = "RECEIVE";
    }
}
