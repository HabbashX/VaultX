package com.habbashx.vaultx.core;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface Progress {

    void report(long done, long total);

    @Contract(pure = true)
    static @NotNull Progress noop() {
        return (done, total) -> {
        };
    }
}