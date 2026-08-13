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
    private static final String KEY_PROTECT_FOLDER = "protectFolder";
    private static final String KEY_SELF_DESTRUCT = "selfDestruct";
    private static final String KEY_MAX_ATTEMPTS = "maxAttempts";
    private static final String KEY_TRASH_RETENTION_DAYS = "trashRetentionDays";
    private static final String KEY_BACKUP_DEST = "backupDestination";
    private static final String KEY_BACKUP_INTERVAL_DAYS = "backupIntervalDays";

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

    public static boolean protectFolder() {
        return PREFERENCES.getBoolean(KEY_PROTECT_FOLDER, true);
    }

    public static void protectFolder(boolean value) {
        PREFERENCES.putBoolean(KEY_PROTECT_FOLDER, value);
    }

    public static boolean selfDestruct() {
        return PREFERENCES.getBoolean(KEY_SELF_DESTRUCT, false);
    }

    public static void selfDestruct(boolean value) {
        PREFERENCES.putBoolean(KEY_SELF_DESTRUCT, value);
    }

    public static int maxAttempts() {
        return Math.max(3, Math.min(30, PREFERENCES.getInt(KEY_MAX_ATTEMPTS, 10)));
    }

    public static void maxAttempts(int value) {
        PREFERENCES.putInt(KEY_MAX_ATTEMPTS, Math.max(3, Math.min(30, value)));
    }

    public static int trashRetentionDays() {
        return Math.max(0, Math.min(365, PREFERENCES.getInt(KEY_TRASH_RETENTION_DAYS, 30)));
    }

    public static void trashRetentionDays(int value) {
        PREFERENCES.putInt(KEY_TRASH_RETENTION_DAYS, Math.max(0, Math.min(365, value)));
    }

    public static String backupDestination() {
        return PREFERENCES.get(KEY_BACKUP_DEST, "");
    }

    public static void backupDestination(String path) {
        if (path == null || path.isBlank()) {
            PREFERENCES.remove(KEY_BACKUP_DEST);
        } else {
            PREFERENCES.put(KEY_BACKUP_DEST, path.trim());
        }
    }

    public static int backupIntervalDays() {
        return Math.max(0, Math.min(3650, PREFERENCES.getInt(KEY_BACKUP_INTERVAL_DAYS, 0)));
    }

    public static void backupIntervalDays(int value) {
        PREFERENCES.putInt(KEY_BACKUP_INTERVAL_DAYS, Math.max(0, Math.min(3650, value)));
    }
}