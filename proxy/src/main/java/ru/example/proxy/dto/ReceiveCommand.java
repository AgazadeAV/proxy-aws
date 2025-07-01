package ru.example.proxy.dto;

import lombok.Getter;

@Getter
public class ReceiveCommand extends CommandMessage {

    public ReceiveCommand() {
        this.command = "RECEIVE";
    }
}
