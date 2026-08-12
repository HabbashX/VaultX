package com.habbashx.vaultx.ui.viewer;

import com.habbashx.vaultx.core.TempFiles;
import com.habbashx.vaultx.core.VaultItem;
import com.habbashx.vaultx.core.VaultManager;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ImageViewer extends JFrame {

    private final Path source;
    private final BufferedImage image;
    private final JLabel canvas = new JLabel();
    private final JScrollPane scroll;
    private double scale = 1.0;

    public ImageViewer(VaultItem item, VaultManager manager, Path source) {
        super(item.name + " — Image");
        this.source = source;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        BufferedImage loaded = null;
        try {
            loaded = ImageIO.read(source.toFile());
        } catch (Exception ignored) {
            // falls through
        }
        if (loaded == null) {
            JOptionPane.showMessageDialog(null, "Could not decode image.", "ImageViewer", JOptionPane.ERROR_MESSAGE);
            TempFiles.delete(source);
            dispose();
            image = null;
            scroll = null;
            return;
        }
        image = loaded;

        canvas.setHorizontalAlignment(SwingConstants.CENTER);
        canvas.setVerticalAlignment(SwingConstants.CENTER);
        scroll = new JScrollPane(canvas);
        scroll.getViewport().setBackground(new Color(0x1B1B1B));

        JButton fit = new JButton("Fit");
        fit.addActionListener(e -> fit());
        JButton actual = new JButton("100%");
        actual.addActionListener(e -> {
            scale = 1.0;
            render();
        });
        JButton zoomIn = new JButton("+");
        zoomIn.addActionListener(e -> zoom(1.25));
        JButton zoomOut = new JButton("−");
        zoomOut.addActionListener(e -> zoom(0.8));
        JButton export = new JButton("Export copy");
        export.addActionListener(e -> exportCopy(item));
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(fit);
        bar.add(actual);
        bar.add(zoomIn);
        bar.add(zoomOut);
        bar.addSeparator();
        bar.add(export);
        bar.addSeparator(new Dimension(24, 0));
        bar.add(close);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                TempFiles.delete(ImageViewer.this.source);
            }
        });

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(bar, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        setContentPane(content);

        setSize(Math.max(480, Math.min(1200, image.getWidth() + 60)),
                Math.max(320, Math.min(900, image.getHeight() + 110)));
        setLocationRelativeTo(null);
        fit();

        scroll.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.isControlDown() || e.isMetaDown()) {
                    zoom(e.getWheelRotation() < 0 ? 1.15 : 1 / 1.15);
                    e.consume();
                }
            }
        });
    }

    private void zoom(double factor) {
        scale = Math.max(0.05, Math.min(16.0, scale * factor));
        render();
    }

    private void fit() {
        Dimension viewport = scroll.getViewport().getExtentSize();
        if (viewport.width < 10 || viewport.height < 10) {
            return;
        }
        double sx = viewport.width / (double) image.getWidth();
        double sy = viewport.height / (double) image.getHeight();
        scale = Math.max(0.01, Math.min(sx, sy) * 0.95);
        render();
    }

    private void render() {
        if (image == null) {
            return;
        }
        int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        g.drawImage(image, 0, 0, w, h, null);
        g.dispose();
        canvas.setIcon(new ImageIcon(resized));
        canvas.setSize(w, h);
        canvas.revalidate();
    }

    private void exportCopy(VaultItem item) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File(item.name));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION || fc.getSelectedFile() == null) {
            return;
        }
        Path dest = fc.getSelectedFile().toPath();
        if (Files.exists(dest)) {
            int choice = JOptionPane.showConfirmDialog(this, "Overwrite existing file?", "Export",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }
        try {
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Export failed: " + e.getMessage(), "Export",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}