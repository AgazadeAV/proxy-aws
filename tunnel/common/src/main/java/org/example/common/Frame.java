package org.example.common;

public class Frame {
    public String sessionId;   // идентификатор сессии controller<->agent
    public int streamId;       // id отдельного TCP-потока
    public CommandType cmd;    // тип команды
    public String host;        // для CONNECT
    public int port;           // для CONNECT
    public boolean ok;         // для CONNECT_ACK
    public String reason;      // для CLOSE или ошибки
    public byte[] payload;     // данные (для DATA)

    public Frame() {
    }

    @Override
    public String toString() {
        return "Frame{" +
                "sessionId='" + sessionId + '\'' +
                ", streamId=" + streamId +
                ", cmd=" + cmd +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", ok=" + ok +
                ", reason='" + reason + '\'' +
                ", payload=" + (payload != null ? payload.length + " bytes" : "null") +
                '}';
    }

    public static Frame connect(String sessionId, int streamId, String host, int port) {
        Frame f = new Frame();
        f.sessionId = sessionId;
        f.streamId = streamId;
        f.cmd = CommandType.CONNECT;
        f.host = host;
        f.port = port;
        return f;
    }

    public static Frame connectAck(String sessionId, int streamId, boolean ok, String reason) {
        Frame f = new Frame();
        f.sessionId = sessionId;
        f.streamId = streamId;
        f.cmd = CommandType.CONNECT_ACK;
        f.ok = ok;
        f.reason = reason;
        return f;
    }

    public static Frame data(String sessionId, int streamId, byte[] payload) {
        Frame f = new Frame();
        f.sessionId = sessionId;
        f.streamId = streamId;
        f.cmd = CommandType.DATA;
        f.payload = payload;
        return f;
    }

    public static Frame close(String sessionId, int streamId, String reason) {
        Frame f = new Frame();
        f.sessionId = sessionId;
        f.streamId = streamId;
        f.cmd = CommandType.CLOSE;
        f.reason = reason;
        return f;
    }

    public static Frame ping(String sessionId) {
        Frame f = new Frame();
        f.sessionId = sessionId;
        f.cmd = CommandType.PING;
        return f;
    }

    public static Frame pong(String sessionId) {
        Frame f = new Frame();
        f.sessionId = sessionId;
        f.cmd = CommandType.PONG;
        return f;
    }
}
