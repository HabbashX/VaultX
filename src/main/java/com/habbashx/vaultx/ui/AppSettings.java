package com.habbashx.vaultx.ui;

import java.util.prefs.Preferences;

public final class AppSettings {

    public static final String DEFAULT_THEME = "com.formdev.flatlaf.FlatDarkLaf";
    public static final String DEFAULT_EDITOR_THEME = "island-dark.xml";
    public static final String DEFAULT_APP_FONT = com.habbashx.vaultx.core.Fonts.JETBRAINS_MONO;
    public static final String DEFAULT_EDITOR_FONT = com.habbashx.vaultx.core.Fonts.JETBRAINS_MONO;

    private static final String KEY_THEME = "appTheme";
    private static final String KEY_APP_FONT = "appFont";
    private static final String KEY_APP_FONT_SIZE = "appFontSize";
    private static final String KEY_EDITOR_FONT = "editorFont";
    private static final String KEY_EDITOR_FONT_SIZE = "editorFontSize";
    private static final String KEY_EDITOR_THEME = "editorTheme";

    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(AppSettings.class);

    private AppSettings() {
    }

    public static String theme() {
        return PREFERENCES.get(KEY_THEME, DEFAULT_THEME);
    }

    public static void theme(String lookAndFeelClass) {
        if (lookAndFeelClass == null || lookAndFeelClass.isBlank()) {
            return;
        }
        PREFERENCES.put(KEY_THEME, lookAndFeelClass);
    }

    public static String appFontFamily() {
        return PREFERENCES.get(KEY_APP_FONT, DEFAULT_APP_FONT);
    }

    public static void appFontFamily(String family) {
        if (family == null || family.isBlank()) {
            return;
        }
        PREFERENCES.put(KEY_APP_FONT, family);
    }

    public static int appFontSize() {
        int size = PREFERENCES.getInt(KEY_APP_FONT_SIZE, 13);
        return Math.max(9, Math.min(28, size));
    }

    public static void appFontSize(int size) {
        PREFERENCES.putInt(KEY_APP_FONT_SIZE, Math.max(9, Math.min(28, size)));
    }

    public static String editorFontFamily() {
        return PREFERENCES.get(KEY_EDITOR_FONT, DEFAULT_EDITOR_FONT);
    }

    public static void editorFontFamily(String family) {
        if (family == null || family.isBlank()) {
            return;
        }
        PREFERENCES.put(KEY_EDITOR_FONT, family);
    }

    public static int editorFontSize() {
        int size = PREFERENCES.getInt(KEY_EDITOR_FONT_SIZE, 13);
        return Math.max(8, Math.min(36, size));
    }

    public static void editorFontSize(int size) {
        PREFERENCES.putInt(KEY_EDITOR_FONT_SIZE, Math.max(8, Math.min(36, size)));
    }

    public static String editorTheme() {
        return PREFERENCES.get(KEY_EDITOR_THEME, DEFAULT_EDITOR_THEME);
    }

    public static void editorTheme(String themeFileName) {
        if (themeFileName == null || themeFileName.isBlank()) {
            return;
        }
        PREFERENCES.put(KEY_EDITOR_THEME, themeFileName);
    }
}