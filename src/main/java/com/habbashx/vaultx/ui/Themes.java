package com.habbashx.vaultx.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.habbashx.vaultx.core.Fonts;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Font;
import java.awt.Window;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class Themes {

    public record ThemeInfo(@NotNull String name, @NotNull String className) {
    }

    public static final List<ThemeInfo> CORE = List.of(
            new ThemeInfo("Flat Dark", "com.formdev.flatlaf.FlatDarkLaf"),
            new ThemeInfo("Flat Light", "com.formdev.flatlaf.FlatLightLaf"),
            new ThemeInfo("IntelliJ", "com.formdev.flatlaf.FlatIntelliJLaf"),
            new ThemeInfo("Darcula", "com.formdev.flatlaf.FlatDarculaLaf")
    );

    private Themes() {
    }

    @Contract(" -> new")
    public static @NotNull List<ThemeInfo> all() {
        LinkedHashSet<ThemeInfo> result = new LinkedHashSet<>();
        result.addAll(CORE);
        try {
            Class<?> allClass = Class.forName("com.formdev.flatlaf.intellijthemes.FlatAllIJThemes");
            List<Object> entries = new ArrayList<>();
            try {
                Object infos = allClass.getField("INFOS").get(null);
                if (infos instanceof Object[] arr) {
                    entries.addAll(java.util.Arrays.asList(arr));
                }
            } catch (NoSuchFieldException ignored) {
                Object themesObj = allClass.getMethod("getInstalledThemes").invoke(null);
                if (themesObj instanceof Iterable<?> iterable) {
                    for (Object t : iterable) {
                        entries.add(t);
                    }
                } else if (themesObj != null && themesObj.getClass().isArray()) {
                    entries.addAll(java.util.Arrays.asList((Object[]) themesObj));
                }
            }
            for (Object t : entries) {
                addThemeInfo(result, t);
            }
        } catch (Throwable ignored) {
        }
        return new ArrayList<>(dedupeByName(result));
    }

    private static void addThemeInfo(LinkedHashSet<ThemeInfo> target, Object t) {
        try {
            String name = (String) t.getClass().getMethod("getName").invoke(t);
            String className = (String) t.getClass().getMethod("getClassName").invoke(t);
            if (name != null && className != null) {
                target.add(new ThemeInfo(name, className));
            }
        } catch (Throwable ignored) {
        }
    }

    private static @NotNull LinkedHashSet<ThemeInfo> dedupeByName(LinkedHashSet<ThemeInfo> themes) {
        LinkedHashSet<ThemeInfo> result = new LinkedHashSet<>();
        for (ThemeInfo t : themes) {
            boolean duplicate = false;
            for (ThemeInfo existing : result) {
                if (existing.name().equalsIgnoreCase(t.name())) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                result.add(t);
            }
        }
        return result;
    }

    public static @NotNull ThemeInfo byClass(String className) {
        for (ThemeInfo t : all()) {
            if (t.className().equals(className)) {
                return t;
            }
        }
        return CORE.get(0);
    }

    public static void apply(String lookAndFeelClass, String appFontFamily, int appFontSize) {
        try {
            Class<?> cls = Class.forName(lookAndFeelClass);
            LookAndFeel laf = (LookAndFeel) cls.getDeclaredConstructor().newInstance();
            UIManager.setLookAndFeel(laf);
        } catch (Throwable e) {
            try {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } catch (Exception ignored) {
            }
        }
        Font font = Fonts.resolveAppFont(appFontFamily, appFontSize);
        UIManager.put("defaultFont", font);
        for (Window window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
        }
    }
}