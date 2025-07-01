package ru.example.agent.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ConnectCommand.class, name = "CONNECT"),
        @JsonSubTypes.Type(value = SendCommand.class, name = "SEND"),
        @JsonSubTypes.Type(value = ReceiveCommand.class, name = "RECEIVE")
})
public abstract class CommandMessage {
    public String command;
}
