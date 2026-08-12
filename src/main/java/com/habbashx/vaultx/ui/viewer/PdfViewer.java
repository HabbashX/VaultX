package com.habbashx.vaultx.ui.viewer;

import com.habbashx.vaultx.core.TempFiles;
import com.habbashx.vaultx.core.VaultItem;
import com.habbashx.vaultx.core.VaultManager;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SwingWorker;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

public final class PdfViewer extends JFrame {

    private static final String[] ZOOM_OPTIONS = {"50%", "75%", "100%", "125%", "150%"};

    private final Path source;
    private PDDocument document;
    private PDFRenderer renderer;
    private int page = 0;
    private int pages = 0;
    private float dpi = 72f;

    private final JLabel canvas = new JLabel();
    private final JScrollPane scroll;
    private final JTextField pageField = new JTextField(4);
    private final JLabel pageLabel = new JLabel();
    private final JComboBox<String> zoom = new JComboBox<>(ZOOM_OPTIONS);
    private final JButton prev;
    private final JButton next;
    private SwingWorker<BufferedImage, Void> current;

    public PdfViewer(@NotNull VaultItem item, VaultManager manager, Path source) {
        super(item.name + " — PDF");
        this.source = source;
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        canvas.setHorizontalAlignment(SwingConstants.CENTER);
        canvas.setVerticalAlignment(SwingConstants.CENTER);
        scroll = new JScrollPane(canvas);
        scroll.getViewport().setBackground(new Color(0x2A2A2A));
        scroll.getVerticalScrollBar().setUnitIncrement(20);

        try {
            document = PDDocument.load(source.toFile());
            renderer = new PDFRenderer(document);
            pages = document.getNumberOfPages();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Could not open PDF: " + e.getMessage(),
                    "PDF viewer", JOptionPane.ERROR_MESSAGE);
            TempFiles.delete(source);
            dispose();
            document = null;
            renderer = null;
            prev = null;
            next = null;
            return;
        }

        prev = new JButton("◀");
        prev.addActionListener(e -> goTo(page - 1));
        next = new JButton("▶");
        next.addActionListener(e -> goTo(page + 1));

        zoom.setSelectedItem("100%");
        zoom.addActionListener(e -> {
            String sel = (String) zoom.getSelectedItem();
            if (sel != null) {
                int percent = Integer.parseInt(sel.replace("%", ""));
                dpi = 72f * percent / 100f;
                renderPage();
            }
        });

        pageField.addActionListener(e -> {
            try {
                int requested = Integer.parseInt(pageField.getText()) - 1;
                goTo(requested);
            } catch (NumberFormatException ignored) {
                updateNav();
            }
        });

        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(prev);
        bar.add(next);
        bar.addSeparator(new Dimension(8, 0));
        bar.add(pageField);
        bar.add(pageLabel);
        bar.addSeparator(new Dimension(8, 0));
        bar.add(new JLabel("Zoom:"));
        bar.add(zoom);
        bar.addSeparator(new Dimension(24, 0));
        bar.add(close);

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(bar, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        setContentPane(content);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                shutdown();
            }
        });

        setSize(980, 760);
        setLocationRelativeTo(null);
        goTo(0);
    }

    private void goTo(int requested) {
        if (document == null) {
            return;
        }
        page = Math.max(0, Math.min(pages - 1, requested));
        updateNav();
        renderPage();
    }

    private void updateNav() {
        pageLabel.setText(" / " + pages);
        pageField.setText(String.valueOf(page + 1));
        prev.setEnabled(page > 0);
        next.setEnabled(page < pages - 1);
    }

    private void renderPage() {
        if (document == null || renderer == null) {
            return;
        }
        if (current != null) {
            current.cancel(true);
        }
        canvas.setIcon(null);
        final int index = page;
        final float targetDpi = dpi;
        current = new SwingWorker<>() {
            @Override
            protected BufferedImage doInBackground() throws Exception {
                return renderer.renderImageWithDPI(index, targetDpi);
            }

            @Override
            protected void done() {
                try {
                    BufferedImage img = get();
                    canvas.setIcon(new ImageIcon(img));
                    Dimension d = new Dimension(img.getWidth(), img.getHeight());
                    canvas.setPreferredSize(d);
                    canvas.setSize(d);
                    canvas.revalidate();
                    scroll.getVerticalScrollBar().setValue(0);
                    scroll.getHorizontalScrollBar().setValue(0);
                } catch (Exception ignored) {
                }
            }
        };
        current.execute();
    }

    private void shutdown() {
        if (current != null) {
            current.cancel(true);
        }
        TempFiles.delete(source);
        try {
            if (document != null) {
                document.close();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void dispose() {
        shutdown();
        super.dispose();
    }
}