package com.habbashx.vaultx.ui;

import com.habbashx.vaultx.core.VaultItem;

import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

final class VaultIcons {

    private static final int BASE = 16;

    private static final Color FOLDER_FILL = new Color(0xE3B064);
    private static final Color FOLDER_FILL_OPEN = new Color(0xF0C883);
    private static final Color FOLDER_EDGE = new Color(0xB8863B);
    private static final Color PAGE = new Color(0xF0F2F4);
    private static final Color PAGE_EDGE = new Color(0xAEB4BD);
    private static final Color PAGE_FOLD = new Color(0xD3D8DE);
    private static final Color INK = new Color(0x7A8494);

    private static final Color TEXT = new Color(0x4E9BD8);
    private static final Color IMAGE = new Color(0x69B56F);
    private static final Color AUDIO = new Color(0x9C7BD8);
    private static final Color VIDEO = new Color(0xE8923C);
    private static final Color PDF = new Color(0xE05A50);
    private static final Color OTHER = new Color(0x8B95A5);

    private static final Map<String, ImageIcon> CACHE = new HashMap<>();

    private VaultIcons() {
    }

    static ImageIcon folder(int px) {
        return cached("folder:" + px, () -> renderFolder(false, px));
    }

    static ImageIcon folderOpen(int px) {
        return cached("folder-open:" + px, () -> renderFolder(true, px));
    }

    static ImageIcon forItem(VaultItem item, int px) {
        return switch (item.category()) {
            case IMAGE -> image(px);
            case AUDIO -> audio(px);
            case VIDEO -> video(px);
            case PDF -> pdf(px);
            case TEXT -> text(px);
            default -> generic(px);
        };
    }

    static ImageIcon image(int px) {
        return cached("image:" + px, () -> {
            BufferedImage img = renderPage(IMAGE, px);
            Graphics2D g = graphics(img, px);
            g.setColor(new Color(0xFFC53D));
            g.fillOval(11, 6, 3, 3);
            g.setColor(IMAGE);
            Path2D m = new Path2D.Float();
            m.moveTo(5, 13);
            m.lineTo(8, 9);
            m.lineTo(11, 13);
            m.closePath();
            g.fill(m);
            g.dispose();
            return img;
        });
    }

    static ImageIcon audio(int px) {
        return cached("audio:" + px, () -> {
            BufferedImage img = renderPage(AUDIO, px);
            Graphics2D g = graphics(img, px);
            g.setColor(AUDIO);
            g.fillOval(6, 11, 3, 3);
            g.fillOval(11, 12, 2, 2);
            g.drawLine(9, 10, 9, 13);
            g.drawLine(9, 10, 12, 11);
            g.dispose();
            return img;
        });
    }

    static ImageIcon video(int px) {
        return cached("video:" + px, () -> {
            BufferedImage img = renderPage(VIDEO, px);
            Graphics2D g = graphics(img, px);
            g.setColor(VIDEO);
            g.fillPolygon(new int[]{8, 12, 8}, new int[]{7, 10, 13}, 3);
            g.dispose();
            return img;
        });
    }

    static ImageIcon pdf(int px) {
        return cached("pdf:" + px, () -> {
            BufferedImage img = renderPage(PDF, px);
            Graphics2D g = graphics(img, px);
            g.setColor(PDF);
            g.drawLine(7, 7, 13, 7);
            g.drawLine(7, 10, 13, 10);
            g.dispose();
            return img;
        });
    }

    static ImageIcon text(int px) {
        return cached("text:" + px, () -> {
            BufferedImage img = renderPage(TEXT, px);
            Graphics2D g = graphics(img, px);
            g.setColor(INK);
            g.drawLine(7, 7, 13, 7);
            g.drawLine(7, 10, 13, 10);
            g.drawLine(7, 13, 10, 13);
            g.dispose();
            return img;
        });
    }

    static ImageIcon generic(int px) {
        return cached("generic:" + px, () -> renderPage(OTHER, px));
    }

    private static ImageIcon cached(String key, Supplier<BufferedImage> renderer) {
        return CACHE.computeIfAbsent(key, k -> new ImageIcon(renderer.get()));
    }

    private static BufferedImage renderFolder(boolean open, int px) {
        BufferedImage img = newCanvas(px);
        Graphics2D g = graphics(img, px);
        Path2D body = new Path2D.Float();
        if (open) {
            body.moveTo(1, 6);
            body.lineTo(6, 6);
            body.lineTo(8, 8);
            body.lineTo(15, 8);
            body.lineTo(15, 13);
            body.lineTo(1, 13);
            body.closePath();
            g.setColor(FOLDER_FILL);
            g.fill(body);
            g.setColor(FOLDER_EDGE);
            g.draw(body);

            Path2D flap = new Path2D.Float();
            flap.moveTo(2, 4);
            flap.lineTo(7, 4);
            flap.lineTo(9, 6);
            flap.lineTo(14, 6);
            flap.lineTo(13, 8);
            flap.lineTo(2, 8);
            flap.closePath();
            g.setColor(FOLDER_FILL_OPEN);
            g.fill(flap);
            g.setColor(FOLDER_EDGE);
            g.draw(flap);
        } else {
            body.moveTo(1, 5);
            body.lineTo(6, 5);
            body.lineTo(8, 7);
            body.lineTo(15, 7);
            body.lineTo(15, 13);
            body.lineTo(1, 13);
            body.closePath();
            g.setColor(FOLDER_FILL);
            g.fill(body);
            g.setColor(FOLDER_EDGE);
            g.draw(body);
        }
        g.dispose();
        return img;
    }

    private static BufferedImage renderPage(Color accent, int px) {
        BufferedImage img = newCanvas(px);
        Graphics2D g = graphics(img, px);
        Path2D page = new Path2D.Float();
        page.moveTo(3, 1);
        page.lineTo(11, 1);
        page.lineTo(15, 5);
        page.lineTo(15, 15);
        page.lineTo(1, 15);
        page.lineTo(1, 1);
        page.closePath();
        g.setColor(PAGE);
        g.fill(page);
        g.setColor(PAGE_EDGE);
        g.draw(page);

        Path2D fold = new Path2D.Float();
        fold.moveTo(11, 1);
        fold.lineTo(15, 5);
        fold.lineTo(11, 5);
        fold.closePath();
        g.setColor(PAGE_FOLD);
        g.fill(fold);
        g.setColor(PAGE_EDGE);
        g.draw(fold);

        g.setColor(accent);
        g.fillRoundRect(2, 6, 2, 7, 1, 1);
        g.dispose();
        return img;
    }

    private static BufferedImage newCanvas(int px) {
        return new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
    }

    private static Graphics2D graphics(BufferedImage img, int px) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float k = px / (float) BASE;
        g.scale(k, k);
        return g;
    }
}
