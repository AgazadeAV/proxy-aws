package org.example.common.signaling;

public interface SignalingProvider {
    void putText(String key, String content) throws Exception;
    String getText(String key) throws Exception;
    boolean exists(String key) throws Exception;
    void delete(String key) throws Exception;
}
