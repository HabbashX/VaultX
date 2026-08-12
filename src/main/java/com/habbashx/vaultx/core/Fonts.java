package com.habbashx.vaultx.core;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Fonts {

    public static final String JETBRAINS_MONO = "JetBrains Mono";
    public static final String SYSTEM_DEFAULT = "System Default";

    private static final String[] BUNDLED = {
            "/fonts/JetBrainsMono-Regular.ttf",
            "/fonts/JetBrainsMono-Bold.ttf",
            "/fonts/JetBrainsMono-Italic.ttf",
            "/fonts/JetBrainsMono-BoldItalic.ttf"
    };

    private static boolean registered = false;

    @Contract(pure = true)
    private Fonts() {
    }

    public static synchronized void registerBundledFonts() {
        if (registered) {
            return;
        }
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (String path : BUNDLED) {
            try (InputStream in = Fonts.class.getResourceAsStream(path)) {
                if (in == null) {
                    continue;
                }
                Font base = Font.createFont(Font.TRUETYPE_FONT, in);
                ge.registerFont(base);
            } catch (Exception ignored) {
                // font already registered or not loadable; skip
            }
        }
        registered = true;
    }

    public static @NotNull List<String> appFontFamilies() {
        Set<String> names = new LinkedHashSet<>();
        names.add(SYSTEM_DEFAULT);
        names.add(JETBRAINS_MONO);
        names.add("Segoe UI");
        names.add("Tahoma");
        names.add("Arial");
        names.add("Dialog");
        return new ArrayList<>(names);
    }

    public static @NotNull List<String> editorFontFamilies() {
        registerBundledFonts();
        Set<String> names = new LinkedHashSet<>();
        names.add(JETBRAINS_MONO);
        names.add("Consolas");
        names.add("Courier New");
        names.add("Menlo");
        names.add("Monaco");
        names.add("DejaVu Sans Mono");
        names.add("Monospaced");
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (String family : ge.getAvailableFontFamilyNames()) {
            String lc = family.toLowerCase();
            if (lc.contains("mono") || lc.contains("console")) {
                names.add(family);
            }
        }
        return new ArrayList<>(names);
    }

    public static @NotNull Font resolveAppFont(String family, int size) {
        if (family == null || family.isBlank() || SYSTEM_DEFAULT.equals(family)) {
            return new Font(Font.SANS_SERIF, Font.PLAIN, size);
        }
        return new Font(family, Font.PLAIN, size);
    }

    public static @NotNull Font resolveEditorFont(String family, int size) {
        registerBundledFonts();
        if (family == null || family.isBlank()) {
            return new Font(Font.MONOSPACED, Font.PLAIN, size);
        }
        return new Font(family, Font.PLAIN, size);
    }
}