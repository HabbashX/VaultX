package com.habbashx.vaultx.core;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class FileTypes {

    public enum Category {
        IMAGE, AUDIO, VIDEO, PDF, TEXT, OTHER
    }

    private static final Set<String> IMAGES = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "ico", "tif", "tiff");
    private static final Set<String> AUDIOS = Set.of("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma", "aiff", "m4b");
    private static final Set<String> VIDEOS = Set.of("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "mpeg", "mpg", "ts", "mts", "3gp");
    private static final Set<String> TEXTS = Set.of(
            "txt", "md", "markdown", "json", "xml", "html", "htm", "xhtml", "css", "scss", "sass",
            "js", "mjs", "cjs", "ts", "tsx", "jsx", "java", "kt", "kts", "py", "rb", "go", "rs",
            "cpp", "cc", "c", "h", "hh", "hpp", "cs", "sql", "sh", "bat", "cmd", "ps1",
            "properties", "cfg", "conf", "ini", "yml", "yaml", "toml", "log", "csv", "tsv",
            "tex", "php", "pl", "lua", "r", "groovy", "gradle", "vim", "gitignore", "gitattributes"
    );

    private FileTypes() {
    }

    public static @NotNull String ext(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase();
    }

    public static Category category(String name) {
        String e = ext(name);
        if (e.isEmpty()) {
            return Category.OTHER;
        }
        if (IMAGES.contains(e)) {
            return Category.IMAGE;
        }
        if (AUDIOS.contains(e)) {
            return Category.AUDIO;
        }
        if (VIDEOS.contains(e)) {
            return Category.VIDEO;
        }
        if ("pdf".equals(e)) {
            return Category.PDF;
        }
        if (TEXTS.contains(e)) {
            return Category.TEXT;
        }
        return Category.OTHER;
    }

    public static String label(Category category) {
        return switch (category) {
            case IMAGE -> "Image";
            case AUDIO -> "Audio";
            case VIDEO -> "Video";
            case PDF -> "PDF document";
            case TEXT -> "Text";
            default -> "Other";
        };
    }

    public static String mime(String name) {
        String e = ext(name);
        return switch (category(name)) {
            case IMAGE -> switch (e) {
                case "jpg", "jpeg" -> "image/jpeg";
                case "png" -> "image/png";
                case "gif" -> "image/gif";
                case "webp" -> "image/webp";
                case "bmp" -> "image/bmp";
                case "tif", "tiff" -> "image/tiff";
                default -> "application/octet-stream";
            };
            case AUDIO -> switch (e) {
                case "mp3" -> "audio/mpeg";
                case "m4a", "m4b" -> "audio/mp4";
                case "wav" -> "audio/wav";
                case "flac" -> "audio/flac";
                case "ogg", "opus" -> "audio/ogg";
                default -> "application/octet-stream";
            };
            case VIDEO -> switch (e) {
                case "mp4", "m4v" -> "video/mp4";
                case "mkv" -> "video/x-matroska";
                case "avi" -> "video/x-msvideo";
                case "mov" -> "video/quicktime";
                case "webm" -> "video/webm";
                default -> "application/octet-stream";
            };
            case PDF -> "application/pdf";
            case TEXT -> switch (e) {
                case "md", "markdown" -> "text/markdown";
                case "json" -> "application/json";
                case "xml" -> "application/xml";
                case "html", "htm", "xhtml" -> "text/html";
                case "css" -> "text/css";
                case "csv" -> "text/csv";
                default -> "text/plain";
            };
            default -> "application/octet-stream";
        };
    }
}