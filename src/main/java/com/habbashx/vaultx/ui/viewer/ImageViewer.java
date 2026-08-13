package com.habbashx.vaultx.ui.viewer;

import com.habbashx.vaultx.core.TempFiles;
import com.habbashx.vaultx.core.VaultItem;
import com.habbashx.vaultx.core.VaultManager;
import com.habbashx.vaultx.ui.Branding;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class ImageViewer extends JFrame {

    private final Path source;
    private final VaultManager manager;
    private final BufferedImage image;
    private final BufferedImage[] mip;
    private final ImageCanvas canvas = new ImageCanvas();
    private final JScrollPane scroll;
    private final JLabel info;
    private boolean autoFit = true;
    private Point dragStart;
    private boolean dragging;

    public ImageViewer(VaultItem item, VaultManager manager, Path source) {
        this(item, manager, source, null);
    }

    public ImageViewer(VaultItem item, VaultManager manager, byte @NotNull [] contentBytes) {
        this(item, manager, null, contentBytes);
    }

    private ImageViewer(VaultItem item, VaultManager manager, Path source, byte[] contentBytes) {
        super(item.name + " — Image");
        this.source = source;
        this.manager = manager;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        Branding.installWindowIcon(this);

        BufferedImage loaded = null;
        if (contentBytes != null) {
            try {
                loaded = ImageIO.read(new ByteArrayInputStream(contentBytes));
            } catch (Exception ignored) {
            }
        } else {
            try {
                loaded = ImageIO.read(source.toFile());
            } catch (Exception ignored) {
            }
        }
        if (loaded == null) {
            this.image = null;
            this.mip = null;
            this.scroll = new JScrollPane();
            this.info = new JLabel();
            JOptionPane.showMessageDialog(null, "Could not decode image.", "ImageViewer", JOptionPane.ERROR_MESSAGE);
            if (source != null) {
                TempFiles.delete(source);
            }
            dispose();
            return;
        }
        image = loaded;
        mip = buildMip(image);
        info = new JLabel();
        info.setForeground(Color.GRAY);
        info.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        canvas.setImage(image, mip);
        scroll = new JScrollPane(canvas);
        scroll.getViewport().setBackground(new Color(0x1B1B1B));
        scroll.setBorder(BorderFactory.createEmptyBorder());

        JButton fit = new JButton("Fit");
        fit.addActionListener(e -> fit());
        JButton actual = new JButton("100%");
        actual.addActionListener(e -> actualSize());
        JButton zoomIn = new JButton("+");
        zoomIn.addActionListener(e -> zoomFromCenter(1.25));
        JButton zoomOut = new JButton("\u2212");
        zoomOut.addActionListener(e -> zoomFromCenter(0.8));
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
        bar.add(new JLabel("Drag to pan, Ctrl+scroll to zoom"));
        bar.add(info);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                fit();
                updateInfo();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                if (ImageViewer.this.source != null) {
                    TempFiles.delete(ImageViewer.this.source);
                }
            }
        });

        scroll.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (autoFit) {
                    fit();
                }
            }
        });

        scroll.addMouseWheelListener(e -> {
            if (e.isControlDown() || e.isMetaDown()) {
                Point at = SwingUtilities.convertPoint(scroll, e.getPoint(), canvas);
                zoomTo(at, e.getWheelRotation() < 0 ? 1.2 : 1 / 1.2);
                e.consume();
            }
        });

        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
                dragging = true;
                canvas.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragging = false;
                dragStart = null;
                canvas.setCursor(Cursor.getDefaultCursor());
            }
        });

        canvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (!dragging || dragStart == null) {
                    return;
                }
                Point view = scroll.getViewport().getViewPosition();
                int x = Math.max(0, view.x - (e.getX() - dragStart.x));
                int y = Math.max(0, view.y - (e.getY() - dragStart.y));
                scroll.getViewport().setViewPosition(new Point(x, y));
            }
        });

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(bar, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        setContentPane(content);

        bindKey("zoomIn", KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, 0), e -> zoomFromCenter(1.25));
        bindKey("zoomInAdd", KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0), e -> zoomFromCenter(1.25));
        bindKey("zoomOut", KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0), e -> zoomFromCenter(0.8));
        bindKey("zoomOutSub", KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0), e -> zoomFromCenter(0.8));
        bindKey("actualSize", KeyStroke.getKeyStroke(KeyEvent.VK_0, 0), e -> actualSize());
        bindKey("fit", KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), e -> fit());

        setSize(Math.max(480, Math.min(1400, image.getWidth() + 80)),
                Math.max(400, Math.min(900, image.getHeight() + 140)));
        setLocationRelativeTo(null);
        updateInfo();
    }

    private void zoomTo(Point at, double factor) {
        if (image == null) {
            return;
        }
        double old = canvas.scale();
        double ns = Math.max(0.01, Math.min(32.0, old * factor));
        Point view = scroll.getViewport().getViewPosition();
        double ix = at.x / old;
        double iy = at.y / old;
        canvas.setScale(ns);
        Dimension dims = canvas.getPreferredSize();
        int maxX = Math.max(0, dims.width - scroll.getViewport().getExtentSize().width);
        int maxY = Math.max(0, dims.height - scroll.getViewport().getExtentSize().height);
        int nx = clamp((int) Math.round(ix * ns - (at.x - view.x)), 0, maxX);
        int ny = clamp((int) Math.round(iy * ns - (at.y - view.y)), 0, maxY);
        scroll.getViewport().setViewPosition(new Point(nx, ny));
        autoFit = false;
        updateInfo();
    }

    private void zoomFromCenter(double factor) {
        if (image == null) {
            return;
        }
        Point view = scroll.getViewport().getViewPosition();
        int cx = view.x + scroll.getViewport().getExtentSize().width / 2;
        int cy = view.y + scroll.getViewport().getExtentSize().height / 2;
        zoomTo(new Point(cx, cy), factor);
    }

    private void actualSize() {
        if (image == null) {
            return;
        }
        canvas.setScale(1.0);
        scroll.getViewport().setViewPosition(new Point(0, 0));
        autoFit = false;
        updateInfo();
    }

    private void fit() {
        if (image == null) {
            return;
        }
        Dimension viewport = scroll.getViewport().getExtentSize();
        if (viewport.width < 10 || viewport.height < 10) {
            return;
        }
        double sx = viewport.width / (double) image.getWidth();
        double sy = viewport.height / (double) image.getHeight();
        double s = Math.max(0.01, Math.min(sx, sy));
        canvas.setScale(s);
        scroll.getViewport().setViewPosition(new Point(0, 0));
        autoFit = true;
        updateInfo();
    }

    private void updateInfo() {
        if (image == null) {
            return;
        }
        info.setText("  " + image.getWidth() + " \u00D7 " + image.getHeight() + " px  \u2022  "
                + Math.round(canvas.scale() * 100) + "%");
    }

    private void bindKey(String name, KeyStroke key, java.util.function.Consumer<ActionEvent> action) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.accept(e);
            }
        });
    }

    private void exportCopy(@NotNull VaultItem item) {
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
            if (source != null) {
                Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            } else {
                manager.exportTo(item, dest, null);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Export failed: " + e.getMessage(), "Export",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static BufferedImage[] buildMip(BufferedImage img) {
        if (img == null || (img.getWidth() < 2048 && img.getHeight() < 2048)) {
            return null;
        }
        List<BufferedImage> levels = new ArrayList<>();
        int w = img.getWidth();
        int h = img.getHeight();
        while (w > 512 || h > 512) {
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
            BufferedImage prev = levels.isEmpty() ? img : levels.get(levels.size() - 1);
            BufferedImage level = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = level.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(prev, 0, 0, w, h, null);
            g.dispose();
            levels.add(level);
        }
        return levels.toArray(new BufferedImage[0]);
    }

    private static final class ImageCanvas extends JComponent {

        private BufferedImage image;
        private BufferedImage[] mip;
        private double scale = 1.0;

        void setImage(BufferedImage img, BufferedImage[] levels) {
            this.image = img;
            this.mip = levels;
            setAlignmentX(0f);
            setAlignmentY(0f);
            setOpaque(true);
            setBackground(new Color(0x1B1B1B));
            setFocusable(false);
            updateSize();
        }

        double scale() {
            return scale;
        }

        void setScale(double s) {
            scale = s;
            updateSize();
        }

        private void updateSize() {
            if (image == null) {
                setPreferredSize(new Dimension(1, 1));
                return;
            }
            setPreferredSize(new Dimension(Math.max(1, (int) Math.round(image.getWidth() * scale)),
                    Math.max(1, (int) Math.round(image.getHeight() * scale))));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(getBackground());
                g2.fillRect(0, 0, w, h);
                if (image == null) {
                    return;
                }
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        scale >= 1.0 ? RenderingHints.VALUE_INTERPOLATION_BICUBIC
                                : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                BufferedImage src = image;
                if (mip != null) {
                    for (BufferedImage level : mip) {
                        if (level.getWidth() >= w && level.getHeight() >= h) {
                            src = level;
                        } else {
                            break;
                        }
                    }
                }
                g2.drawImage(src, 0, 0, w, h, null);
            } finally {
                g2.dispose();
            }
        }
    }
}