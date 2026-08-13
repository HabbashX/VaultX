package com.habbashx.vaultx.core;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Lockout {

    private static final String FILE = "lockout.json";
    private static final Gson GSON = new Gson();

    public static final class State {
        int attempts;
        long lastAttempt;
    }

    private Lockout() {
    }

    private static Path file(Path vaultDir) {
        return vaultDir.resolve(VaultManager.CONFIG_DIR).resolve(FILE);
    }

    public static synchronized int recordFailure(Path vaultDir) {
        State state = read(vaultDir);
        state.attempts++;
        state.lastAttempt = System.currentTimeMillis();
        write(vaultDir, state);
        return state.attempts;
    }

    public static synchronized long currentDelayMillis(Path vaultDir) {
        State state = read(vaultDir);
        if (state.attempts <= 1) {
            return 0;
        }
        long delay = delayFor(state.attempts);
        long elapsed = System.currentTimeMillis() - state.lastAttempt;
        return Math.max(0, delay - elapsed);
    }

    public static synchronized long delayFor(int attempts) {
        if (attempts <= 1) {
            return 0;
        }
        long seconds = Math.min(300L, (long) attempts * attempts);
        return seconds * 1000L;
    }

    public static synchronized void recordSuccess(Path vaultDir) {
        try {
            Files.deleteIfExists(file(vaultDir));
        } catch (IOException ignored) {
        }
    }

    public static synchronized void clear(Path vaultDir) {
        recordSuccess(vaultDir);
    }

    public static synchronized int attempts(Path vaultDir) {
        return read(vaultDir).attempts;
    }

    private static State read(Path vaultDir) {
        Path f = file(vaultDir);
        byte[] blob = null;
        try {
            if (Files.isRegularFile(f)) {
                blob = Files.readAllBytes(f);
            }
        } catch (IOException ignored) {
        }
        if (blob == null || blob.length == 0) {
            return new State();
        }
        try {
            State state = GSON.fromJson(new String(blob, StandardCharsets.UTF_8), State.class);
            return state == null ? new State() : state;
        } catch (Exception e) {
            return new State();
        }
    }

    private static void write(Path vaultDir, State state) {
        try {
            Path f = file(vaultDir);
            Files.createDirectories(f.getParent());
            Files.write(f, GSON.toJson(state).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }
}
