package com.habbashx.vaultx.ui;

import com.habbashx.vaultx.core.Progress;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProgressDialog extends JDialog {

    private final NyanCatProgressBar bar = new NyanCatProgressBar();
    private final JLabel label;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private Runnable cancelAction = () -> {
    };

    public ProgressDialog(Window owner, String title, String message) {
        super(owner, title, ModalityType.DOCUMENT_MODAL);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                cancel();
            }
        });

        label = new JLabel(message == null ? "Working…" : message);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 13f));

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> cancel());

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        panel.add(label, BorderLayout.NORTH);
        panel.add(bar, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(cancel);
        panel.add(bottom, BorderLayout.SOUTH);
        setContentPane(panel);
        setResizable(false);
        pack();
        setLocationRelativeTo(owner);
    }

    public void onCancelRequest(Runnable action) {
        this.cancelAction = action;
    }

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            label.setText("Cancelling…");
            cancelAction.run();
        }
    }

    public AtomicBoolean cancelled() {
        return cancelled;
    }

    public void update(double ratio, String text) {
        SwingUtilities.invokeLater(() -> {
            if (text != null) {
                label.setText(text);
            }
            if (ratio < 0) {
                bar.setIndeterminate(true);
            } else {
                bar.setProgress(ratio);
            }
        });
    }

    public Progress progress() {
        return (done, total) -> update(total > 0 ? (double) done / total : -1, formatBytes(done));
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.1f MB", mb);
        }
        return String.format("%.2f GB", mb / 1024.0);
    }

    /**
     * A progress bar that flies Nyan Cat across a rainbow trail instead of a plain
     * blue rectangle. Supports both determinate (0..1) and indeterminate modes.
     */
    private static final class NyanCatProgressBar extends JComponent {

        private static final int TRACK_HEIGHT = 32;
        private static final int CAT_SIZE = 128;
        private static final Color[] RAINBOW = {
                new Color(0xFF2E2E), new Color(0xFF9A2E), new Color(0xFFE22E),
                new Color(0x4CD964), new Color(0x2E9AFF), new Color(0xB24CFF)
        };

        private static final BufferedImage CAT_IMAGE = loadCatImage();

        private double progress = 0.0;
        private boolean indeterminate = false;
        private int tick = 0;
        private final List<Star> stars = new ArrayList<>();
        private final Timer timer;

        NyanCatProgressBar() {
            setPreferredSize(new Dimension(300, CAT_SIZE + 4));
            setOpaque(false);
            Random rnd = new Random(7);
            for (int i = 0; i < 26; i++) {
                stars.add(new Star(rnd.nextDouble(), rnd.nextDouble(), rnd.nextInt(360)));
            }
            timer = new Timer(40, e -> {
                tick++;
                repaint();
            });
            timer.start();
        }

        void setProgress(double ratio) {
            indeterminate = false;
            progress = Math.max(0.0, Math.min(1.0, ratio));
            repaint();
        }

        void setIndeterminate(boolean value) {
            indeterminate = value;
            repaint();
        }

        private double currentPosition() {
            if (!indeterminate) {
                return progress;
            }
            // Smooth back-and-forth sweep for indeterminate mode.
            return (Math.sin(tick * 0.035) + 1.0) / 2.0;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int w = getWidth();
            int h = getHeight();
            int trackY = (h - TRACK_HEIGHT) / 2;
            RoundRectangle2D track = new RoundRectangle2D.Double(0, trackY, w, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);

            Graphics2D clipped = (Graphics2D) g2.create();
            clipped.clip(track);

            clipped.setPaint(new GradientPaint(0, trackY, new Color(0x0B0B2E), 0, trackY + TRACK_HEIGHT, new Color(0x241046)));
            clipped.fill(track);

            for (Star star : stars) {
                float alpha = (float) (0.35 + 0.65 * (0.5 + 0.5 * Math.sin((tick + star.phase) * 0.06)));
                clipped.setColor(new Color(1f, 1f, 1f, alpha));
                int sx = (int) (star.x * w);
                int sy = trackY + (int) (star.y * TRACK_HEIGHT);
                clipped.fillOval(sx, sy, 2, 2);
            }

            double p = currentPosition();
            int catX = (int) Math.round(p * (w - CAT_SIZE));
            catX = Math.max(0, Math.min(w - CAT_SIZE, catX));
            int catY = (h - CAT_SIZE) / 2;

            int trailEnd = Math.max(0, catX + CAT_SIZE / 2);
            int stripeH = TRACK_HEIGHT / RAINBOW.length;
            for (int i = 0; i < RAINBOW.length; i++) {
                int sy = trackY + i * stripeH;
                int sh = (i == RAINBOW.length - 1) ? (trackY + TRACK_HEIGHT - sy) : stripeH;
                clipped.setColor(RAINBOW[i]);
                clipped.fillRect(0, sy, trailEnd, sh);
            }

            clipped.dispose();

            // Draw the cat on the unclipped graphics so it can overflow the (smaller) track,
            // instead of being cropped to the thin bar's rounded rect.
            if (CAT_IMAGE != null) {
                g2.drawImage(CAT_IMAGE, catX, catY, CAT_SIZE, CAT_SIZE, null);
            } else {
                g2.setColor(Color.WHITE);
                g2.drawString("nyan~", catX, catY + CAT_SIZE / 2);
            }

            g2.setColor(new Color(255, 255, 255, 60));
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(track);
            g2.dispose();
        }

        @Override
        public void removeNotify() {
            timer.stop();
            super.removeNotify();
        }

        private static final String CAT_RESOURCE_PATH = "/images/nyan_cat.png";

        private static @Nullable BufferedImage loadCatImage() {
            try (var in = NyanCatProgressBar.class.getResourceAsStream(CAT_RESOURCE_PATH)) {
                if (in == null) {
                    return null;
                }
                return ImageIO.read(in);
            } catch (IOException e) {
                return null;
            }
        }

        private static final class Star {
            final double x;
            final double y;
            final int phase;

            Star(double x, double y, int phase) {
                this.x = x;
                this.y = y;
                this.phase = phase;
            }
        }

    }
}