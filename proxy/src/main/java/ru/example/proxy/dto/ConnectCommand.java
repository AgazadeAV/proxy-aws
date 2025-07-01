package ru.example.proxy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectCommand extends CommandMessage {
    public String address;
    public int port;

    {
        this.command = "CONNECT";
    }
}
