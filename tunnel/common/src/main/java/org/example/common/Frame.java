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
        Frame frame = new Frame();
        frame.sessionId = sessionId;
        frame.streamId = streamId;
        frame.cmd = CommandType.CONNECT;
        frame.host = host;
        frame.port = port;
        return frame;
    }

    public static Frame connectAck(String sessionId, int streamId, boolean ok, String reason) {
        Frame frame = new Frame();
        frame.sessionId = sessionId;
        frame.streamId = streamId;
        frame.cmd = CommandType.CONNECT_ACK;
        frame.ok = ok;
        frame.reason = reason;
        return frame;
    }

    public static Frame data(String sessionId, int streamId, byte[] payload) {
        Frame frame = new Frame();
        frame.sessionId = sessionId;
        frame.streamId = streamId;
        frame.cmd = CommandType.DATA;
        frame.payload = payload;
        return frame;
    }

    public static Frame close(String sessionId, int streamId, String reason) {
        Frame frame = new Frame();
        frame.sessionId = sessionId;
        frame.streamId = streamId;
        frame.cmd = CommandType.CLOSE;
        frame.reason = reason;
        return frame;
    }

    public static Frame ping(String sessionId) {
        Frame frame = new Frame();
        frame.sessionId = sessionId;
        frame.cmd = CommandType.PING;
        return frame;
    }

    public static Frame pong(String sessionId) {
        Frame frame = new Frame();
        frame.sessionId = sessionId;
        frame.cmd = CommandType.PONG;
        return frame;
    }
}
