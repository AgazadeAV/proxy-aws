package org.example.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;

public class JsonCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    public static String encode(Frame frame) {
        try {
            return MAPPER.writeValueAsString(frame);
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode Frame to JSON", e);
        }
    }

    public static Frame decode(String json) {
        try {
            return MAPPER.readValue(json, Frame.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to decode Frame from JSON", e);
        }
    }

    public static byte[] encodeBytes(Frame frame) {
        try {
            return MAPPER.writeValueAsBytes(frame);
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode Frame to bytes", e);
        }
    }

    public static Frame decodeBytes(byte[] bytes) {
        try {
            return MAPPER.readValue(bytes, Frame.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to decode Frame from bytes", e);
        }
    }
}
