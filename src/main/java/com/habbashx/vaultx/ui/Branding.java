package com.habbashx.vaultx.ui;

import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Image;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class Branding {

    public static final String LOGO_PATH = "/images/vaultx_logo.png";
    public static final String ICON_32_PATH = "/images/vaultx_icon_32.png";
    public static final String ICON_64_PATH = "/images/vaultx_icon_64_1.png";
    public static final String ICON_128_PATH = "/images/vaultx_icon_128.png";

    private static final Map<String, BufferedImage> CACHE = new HashMap<>();

    private Branding() {
    }

    public static Image appIcon() {
        BufferedImage icon = load(ICON_128_PATH);
        if (icon == null) {
            icon = load(ICON_64_PATH);
        }
        return icon;
    }

    public static void installWindowIcon(Window window) {
        Image icon = appIcon();
        if (icon != null) {
            window.setIconImage(icon);
        }
    }

    public static @Nullable ImageIcon logo(int px) {
        BufferedImage logo = load(LOGO_PATH);
        if (logo == null) {
            return null;
        }
        int height = Math.max(1, Math.round(px * (float) logo.getHeight() / logo.getWidth()));
        return new ImageIcon(logo.getScaledInstance(px, height, Image.SCALE_SMOOTH));
    }

    private static BufferedImage load(String path) {
        BufferedImage cached = CACHE.get(path);
        if (cached != null) {
            return cached;
        }
        BufferedImage image = null;
        try (InputStream in = Branding.class.getResourceAsStream(path)) {
            if (in != null) {
                image = ImageIO.read(in);
            }
        } catch (IOException ignored) {
        }
        if (image != null) {
            CACHE.put(path, image);
        }
        return image;
    }
}