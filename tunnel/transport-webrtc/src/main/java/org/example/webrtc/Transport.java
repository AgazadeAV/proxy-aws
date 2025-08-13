package org.example.webrtc;

/**
 * Абстракция транспорта (в будущем: WebRTC DataChannel поверх TURN).
 * Единый интерфейс для controller и agent.
 */
public interface Transport {

    interface Listener {
        /** Результат CONNECT с удалённой стороны (ACK). */
        void onConnectAck(int streamId, boolean ok, String message);

        /** Входящие данные по открытому потоку. */
        void onData(int streamId, byte[] data);

        /** Закрытие потока с причиной (может быть null). */
        void onClose(int streamId, String reason);

        /** Необязательный лог/диагностика. */
        default void onLog(String msg) {}
    }

    /** Назначить listener (до start()). */
    void setListener(Listener listener);

    /** Установить логический идентификатор сессии (для логов, тегов). */
    void setSessionId(String sessionId);

    /** Запуск транспорта (установить канал). */
    void start();

    /** Остановка транспорта (закрыть канал и ресурсы). */
    void stop();

    /** Открыть новый логический поток к host:port (со стороны controller). */
    void open(int streamId, String host, int port);

    /** Отправить данные по потоку. */
    void send(int streamId, byte[] data);

    /** Закрыть поток. */
    void close(int streamId, String reason);
}
