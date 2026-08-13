package com.habbashx.vaultx.ui.viewer;

import com.habbashx.vaultx.core.FileTypes;
import com.habbashx.vaultx.core.Fonts;
import com.habbashx.vaultx.core.TempFiles;
import com.habbashx.vaultx.core.VaultItem;
import com.habbashx.vaultx.core.VaultManager;
import com.habbashx.vaultx.ui.AppSettings;
import com.habbashx.vaultx.ui.Branding;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public final class TextViewer extends JFrame {

    private final VaultItem item;
    private final VaultManager manager;
    private final Path source;
    private final RSyntaxTextArea area = new RSyntaxTextArea();
    private boolean dirty = false;
    private boolean closed = false;

    public TextViewer(VaultItem item, VaultManager manager, Path source) {
        this(item, manager, source, null);
    }

    public TextViewer(VaultItem item, VaultManager manager, byte @NotNull [] contentBytes) {
        this(item, manager, null, contentBytes);
    }

    private TextViewer(VaultItem item, VaultManager manager, Path source, byte[] contentBytes) {
        super(item.name + " — Editor");
        this.item = item;
        this.manager = manager;
        this.source = source;

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        Branding.installWindowIcon(this);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeRequested();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                if (TextViewer.this.source != null) {
                    TempFiles.delete(TextViewer.this.source);
                }
            }
        });

        area.setSyntaxEditingStyle(syntaxStyle(item.name));
        area.setCodeFoldingEnabled(false);
        area.setTabSize(4);
        area.setAntiAliasingEnabled(true);
        Fonts.registerBundledFonts();
        area.setFont(Fonts.resolveEditorFont(AppSettings.editorFontFamily(), AppSettings.editorFontSize()));
        applyEditorTheme();

        try {
            String initial = contentBytes != null ? decode(contentBytes) : load(source);
            area.setText(initial);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not read file content: " + e.getMessage(),
                    "Editor", JOptionPane.ERROR_MESSAGE);
        }
        area.setCaretPosition(0);

        area.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                markDirty();
            }

            public void removeUpdate(DocumentEvent e) {
                markDirty();
            }

            public void changedUpdate(DocumentEvent e) {
                markDirty();
            }
        });

        JButton save = new JButton("Save");
        save.addActionListener(e -> save());
        JButton reload = new JButton("Revert");
        reload.addActionListener(e -> reload());
        JButton close = new JButton("Close");
        close.addActionListener(e -> closeRequested());

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(save);
        bar.add(reload);
        bar.addSeparator(new java.awt.Dimension(24, 0));
        bar.add(close);

        JPanel content = new JPanel(new BorderLayout());
        content.add(bar, BorderLayout.NORTH);
        content.add(new RTextScrollPane(area), BorderLayout.CENTER);
        setContentPane(content);

        setSize(820, 620);
        setLocationRelativeTo(null);
        updateTitle();
    }

    private void applyEditorTheme() {
        String file = AppSettings.editorTheme();
        int size = AppSettings.editorFontSize();
        String family = AppSettings.editorFontFamily();
        try (InputStream in = TextViewer.class.getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/" + file)) {
            if (in != null) {
                Theme theme = Theme.load(in);
                theme.baseFont = Fonts.resolveEditorFont(family, size);
                theme.apply(area);
                return;
            }
        } catch (Exception ignored) {
        }
        area.setFont(Fonts.resolveEditorFont(family, size));
        area.setBackground(java.awt.Color.WHITE);
        area.setForeground(java.awt.Color.BLACK);
    }

    private static String load(java.nio.file.Path path) throws IOException {
        return decode(Files.readAllBytes(path));
    }

    private static String decode(byte[] raw) {
        boolean valid = true;
        try {
            var decoder = StandardCharsets.UTF_8.newDecoder();
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(raw);
            var chars = decoder.decode(bb);
            if (chars != null) {
                return chars.toString();
            }
        } catch (Exception e) {
            valid = false;
        }
        if (!valid) {
            return new String(raw, StandardCharsets.ISO_8859_1);
        }
        return new String(raw, StandardCharsets.UTF_8);
    }

    private void markDirty() {
        dirty = true;
        updateTitle();
    }

    private void updateTitle() {
        setTitle(item.name + (dirty ? "  •  (unsaved changes)" : "") + " — Editor");
    }

    private void save() {
        byte[] bytes = area.getText().getBytes(StandardCharsets.UTF_8);
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                manager.updateItemContent(item, new ByteArrayInputStream(bytes), bytes.length, null);
                if (TextViewer.this.source != null) {
                    manager.exportTo(item, TextViewer.this.source, null);
                }
                return null;
            }

            @Override
            protected void done() {
                setCursor(java.awt.Cursor.getDefaultCursor());
                try {
                    get();
                    dirty = false;
                    updateTitle();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    JOptionPane.showMessageDialog(TextViewer.this,
                            "Save failed: " + cause.getMessage(), "Editor", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void reload() {
        if (dirty) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Discard unsaved changes?", "Revert",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }
        if (source != null) {
            try {
                area.setText(load(source));
                dirty = false;
                updateTitle();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Could not reload: " + e.getMessage(),
                        "Editor", JOptionPane.ERROR_MESSAGE);
            }
            return;
        }
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        new SwingWorker<byte[], Void>() {
            @Override
            protected byte[] doInBackground() throws Exception {
                return manager.decryptToBytes(item);
            }

            @Override
            protected void done() {
                setCursor(java.awt.Cursor.getDefaultCursor());
                try {
                    area.setText(decode(get()));
                    dirty = false;
                    updateTitle();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    JOptionPane.showMessageDialog(TextViewer.this,
                            "Could not reload: " + cause.getMessage(), "Editor", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void closeRequested() {
        if (dirty) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "You have unsaved changes. Close anyway?", "Close",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }
        closed = true;
        dispose();
    }

    private static String syntaxStyle(String name) {
        String ext = FileTypes.ext(name);
        return switch (ext) {
            case "java" -> SyntaxConstants.SYNTAX_STYLE_JAVA;
            case "py" -> SyntaxConstants.SYNTAX_STYLE_PYTHON;
            case "js", "mjs", "cjs", "jsx", "ts", "tsx" -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
            case "json", "json5" -> SyntaxConstants.SYNTAX_STYLE_JSON;
            case "xml", "xhtml" -> SyntaxConstants.SYNTAX_STYLE_XML;
            case "html", "htm" -> SyntaxConstants.SYNTAX_STYLE_HTML;
            case "css", "scss", "sass" -> SyntaxConstants.SYNTAX_STYLE_CSS;
            case "c", "h" -> SyntaxConstants.SYNTAX_STYLE_C;
            case "cpp", "cc", "hpp", "hh" -> SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS;
            case "cs" -> SyntaxConstants.SYNTAX_STYLE_CSHARP;
            case "sql" -> SyntaxConstants.SYNTAX_STYLE_SQL;
            case "sh", "bash" -> SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL;
            case "php" -> SyntaxConstants.SYNTAX_STYLE_PHP;
            case "rb" -> SyntaxConstants.SYNTAX_STYLE_RUBY;
            case "yml", "yaml" -> SyntaxConstants.SYNTAX_STYLE_YAML;
            case "md", "markdown" -> SyntaxConstants.SYNTAX_STYLE_MARKDOWN;
            case "properties", "ini", "cfg", "conf" -> SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE;
            case "groovy", "gradle" -> SyntaxConstants.SYNTAX_STYLE_GROOVY;
            case "bat", "cmd" -> SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH;
            case "tex" -> SyntaxConstants.SYNTAX_STYLE_LATEX;
            case "lua" -> SyntaxConstants.SYNTAX_STYLE_LUA;
            default -> SyntaxConstants.SYNTAX_STYLE_NONE;
        };
    }
}