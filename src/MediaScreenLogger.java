package com.mediascreen.client;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MediaScreenLogger {
    private static final Path LOG_PATH = net.fabricmc.loader.api.FabricLoader.getInstance()
        .getGameDir().resolve("mediascreen").resolve("mediascreen.log");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void log(String message) {
        String line = "[" + LocalDateTime.now().format(FMT) + "] " + message;
        try {
            Files.writeString(LOG_PATH, line + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }

    public static void clearLog() {
        try { Files.deleteIfExists(LOG_PATH); } catch (IOException ignored) {}
    }
}
