package org.example.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class SignalingFiles {
    private SignalingFiles() {}

    public static void writeText(Path file, String text) {
        try {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(text, "text");
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(file, text);
            System.out.println("[Signaling] wrote " + file.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("write " + file + " failed", e);
        }
    }

    public static String readText(Path file) {
        try {
            Objects.requireNonNull(file, "file");
            String s = Files.readString(file);
            System.out.println("[Signaling] read " + file.toAbsolutePath());
            return s;
        } catch (IOException e) {
            throw new RuntimeException("read " + file + " failed", e);
        }
    }
}
