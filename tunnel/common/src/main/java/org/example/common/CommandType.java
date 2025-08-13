package org.example.common;

public enum CommandType {
    CONNECT,        // controller -> agent: запрос на TCP подключение
    CONNECT_ACK,    // agent -> controller: результат подключения (ok/fail)
    DATA,           // передача бинарных данных
    CLOSE,          // закрыть поток
    PING,           // keepalive
    PONG            // ответ на keepalive
}
