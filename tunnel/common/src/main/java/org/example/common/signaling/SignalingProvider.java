package org.example.common.signaling;

public interface SignalingProvider {
    void putText(String key, String content) throws Exception;
    String getText(String key) throws Exception;
    boolean exists(String key) throws Exception;
    void delete(String key) throws Exception;

    default void putOffer(String sessionId, String sdp) throws Exception {
        putText("sessions/" + sessionId + "/offer.sdp", sdp);
    }
    default String getOffer(String sessionId) throws Exception {
        return getText("sessions/" + sessionId + "/offer.sdp");
    }
    default boolean offerExists(String sessionId) throws Exception {
        return exists("sessions/" + sessionId + "/offer.sdp");
    }
    default void putAnswer(String sessionId, String sdp) throws Exception {
        putText("sessions/" + sessionId + "/answer.sdp", sdp);
    }
    default String getAnswer(String sessionId) throws Exception {
        return getText("sessions/" + sessionId + "/answer.sdp");
    }
    default boolean answerExists(String sessionId) throws Exception {
        return exists("sessions/" + sessionId + "/answer.sdp");
    }
}
