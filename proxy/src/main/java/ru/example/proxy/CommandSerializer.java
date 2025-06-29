package ru.example.proxy;

import java.util.Base64;

public class CommandSerializer {

    public static String toJsonConnect(String host, int port) {
        return String.format("{\"command\":\"CONNECT\",\"address\":\"%s\",\"port\":%d}", host, port);
    }

    public static String toJsonSend(byte[] data) {
        String encoded = Base64.getEncoder().encodeToString(data);
        return String.format("{\"command\":\"SEND\",\"payload\":\"%s\"}", encoded);
    }

    public static String toJsonReceive() {
        return "{\"command\":\"RECEIVE\"}";
    }
}
