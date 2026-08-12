package com.habbashx.vaultx.core;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

public final class TempFiles {

    private static volatile Path root;

    private TempFiles() {
    }

    private static Path root() throws IOException {
        Path r = root;
        if (r == null) {
            synchronized (TempFiles.class) {
                r = root;
                if (r == null) {
                    r = Files.createTempDirectory("vaultx");
                    root = r;
                    final Path createdRoot = r;
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteTree(createdRoot)));
                }
            }
        }
        return root;
    }

    public static @NotNull Path newFile(String extension) throws IOException {
        String ext = extension == null ? "" : extension;
        Path path = root().resolve("vaultx-" + UUID.randomUUID() + ext);
        Files.deleteIfExists(path);
        return path;
    }

    public static void delete(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static void deleteTree(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }
}