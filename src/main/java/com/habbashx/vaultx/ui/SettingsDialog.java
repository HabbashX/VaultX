package com.habbashx.vaultx.ui;

import com.habbashx.vaultx.core.Fonts;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.InputStream;
import java.util.List;

public final class SettingsDialog extends JDialog {

    private record EditorTheme(String name, String file) {
    }

    private static final String SAMPLE = """
            public class Demo {
                // VaultX — editor preview
                private final String name = "vaultx";
                private int count = 0;

                public static void main(String[] args) {
                    System.out.println("Hello, VaultX!");
                    for (int i = 0; i < 10; i++) {
                        System.err.println(i * 2);
                    }
                    final String url = "https://example.com/path?q=vaultx";
                }
            }
            """;

    private static final List<EditorTheme> EDITOR_THEMES = List.of(
            new EditorTheme("One Dark", "one-dark.xml"),
            new EditorTheme("Island Dark", "island-dark.xml"),
            new EditorTheme("Dark", "dark.xml"),
            new EditorTheme("Monokai", "monokai.xml"),
            new EditorTheme("Idea", "idea.xml"),
            new EditorTheme("Visual Studio", "vs.xml"),
            new EditorTheme("Default", "default.xml"),
            new EditorTheme("Druid", "druid.xml"),
            new EditorTheme("Eclipse", "eclipse.xml")
    );

    private final List<Themes.ThemeInfo> themes = Themes.all();
    private final String originalTheme;
    private final String originalAppFont;
    private final int originalAppFontSize;
    private final String originalEditorFont;
    private final int originalEditorFontSize;
    private final String originalEditorTheme;

    private final JComboBox<String> themeCombo = new JComboBox<>();
    private final JComboBox<String> appFontCombo = new JComboBox<>();
    private final JSpinner appFontSize = new JSpinner(new SpinnerNumberModel(13, 9, 28, 1));
    private final JComboBox<String> editorFontCombo = new JComboBox<>();
    private final JSpinner editorFontSize = new JSpinner(new SpinnerNumberModel(13, 8, 36, 1));
    private final JComboBox<String> editorThemeCombo = new JComboBox<>();
    private final RSyntaxTextArea preview = new RSyntaxTextArea();

    public SettingsDialog(Window owner) {
        super(owner, "VaultX Settings", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        originalTheme = AppSettings.theme();
        originalAppFont = AppSettings.appFontFamily();
        originalAppFontSize = AppSettings.appFontSize();
        originalEditorFont = AppSettings.editorFontFamily();
        originalEditorFontSize = AppSettings.editorFontSize();
        originalEditorTheme = AppSettings.editorTheme();

        buildControls();

        Fonts.registerBundledFonts();
        preview.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        preview.setCodeFoldingEnabled(false);
        preview.setAntiAliasingEnabled(true);
        preview.setText(SAMPLE);
        refreshPreview();

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        content.add(buildForm(), BorderLayout.NORTH);
        content.add(buildPreview(), BorderLayout.CENTER);
        content.add(buildButtons(), BorderLayout.SOUTH);
        setContentPane(content);

        pack();
        setMinimumSize(new Dimension(getWidth(), getHeight()));
        setLocationRelativeTo(owner);
    }

    private void buildControls() {
        for (Themes.ThemeInfo theme : themes) {
            themeCombo.addItem(theme.name());
        }
        select(themeCombo, Themes.byClass(originalTheme).name());
        themeCombo.addActionListener(e -> applyUiChanges());

        for (String family : Fonts.appFontFamilies()) {
            appFontCombo.addItem(family);
        }
        select(appFontCombo, originalAppFont);
        appFontSize.setValue(originalAppFontSize);
        appFontCombo.addActionListener(e -> applyUiChanges());
        appFontSize.addChangeListener(e -> applyUiChanges());

        for (String family : Fonts.editorFontFamilies()) {
            editorFontCombo.addItem(family);
        }
        select(editorFontCombo, originalEditorFont);
        editorFontSize.setValue(originalEditorFontSize);
        editorFontCombo.addActionListener(e -> refreshPreview());
        editorFontSize.addChangeListener(e -> refreshPreview());

        for (EditorTheme t : EDITOR_THEMES) {
            editorThemeCombo.addItem(t.name());
        }
        select(editorThemeCombo, nameForFile(originalEditorTheme));
        editorThemeCombo.addActionListener(e -> refreshPreview());
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        form.add(new JLabel("Application theme:"), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(themeCombo, c);

        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        form.add(new JLabel("Application font:"), c);
        JPanel appFontRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        appFontRow.add(appFontCombo);
        appFontRow.add(new JLabel("Size:"));
        appFontRow.add(appFontSize);
        c.gridx = 1;
        c.weightx = 1;
        form.add(appFontRow, c);

        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 0;
        form.add(new JLabel("Editor font:"), c);
        JPanel editorFontRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        editorFontRow.add(editorFontCombo);
        editorFontRow.add(new JLabel("Size:"));
        editorFontRow.add(editorFontSize);
        c.gridx = 1;
        c.weightx = 1;
        form.add(editorFontRow, c);

        c.gridx = 0;
        c.gridy = 3;
        c.weightx = 0;
        form.add(new JLabel("Editor theme:"), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(editorThemeCombo, c);

        JLabel hint = new JLabel("Changes apply immediately to open windows and are saved automatically.");
        hint.setForeground(new java.awt.Color(0x888888));
        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 2;
        form.add(hint, c);

        return form;
    }

    private JPanel buildPreview() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Editor preview"));
        preview.setPreferredSize(new Dimension(520, 240));
        panel.add(new RTextScrollPane(preview), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildButtons() {
        JButton apply = new JButton("Apply");
        apply.addActionListener(e -> applySavedSettings());
        JButton ok = new JButton("OK");
        ok.addActionListener(e -> {
            applySavedSettings();
            dispose();
        });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> {
            Themes.apply(originalTheme, originalAppFont, originalAppFontSize);
            dispose();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(apply);
        buttons.add(ok);
        buttons.add(cancel);
        return buttons;
    }

    private void applyUiChanges() {
        AppSettings.theme(selectedThemeClass());
        AppSettings.appFontFamily((String) appFontCombo.getSelectedItem());
        AppSettings.appFontSize((Integer) appFontSize.getValue());
        Themes.apply(AppSettings.theme(), AppSettings.appFontFamily(), AppSettings.appFontSize());
    }

    private void applySavedSettings() {
        applyUiChanges();
        AppSettings.editorFontFamily(selectedEditorFont());
        AppSettings.editorFontSize((Integer) editorFontSize.getValue());
        AppSettings.editorTheme(fileForSelectedEditorTheme());
    }

    private void refreshPreview() {
        String file = fileForSelectedEditorTheme();
        try (InputStream in = SettingsDialog.class.getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/" + file)) {
            if (in != null) {
                Theme theme = Theme.load(in);
                theme.baseFont = Fonts.resolveEditorFont(selectedEditorFont(), (Integer) editorFontSize.getValue());
                theme.apply(preview);
            }
        } catch (Exception ignored) {
            preview.setFont(Fonts.resolveEditorFont(selectedEditorFont(), (Integer) editorFontSize.getValue()));
        }
        preview.repaint();
    }

    private String selectedThemeClass() {
        Object selected = themeCombo.getSelectedItem();
        for (Themes.ThemeInfo t : themes) {
            if (t.name().equals(selected)) {
                return t.className();
            }
        }
        return originalTheme;
    }

    private String selectedEditorFont() {
        Object selected = editorFontCombo.getSelectedItem();
        return selected == null ? AppSettings.editorFontFamily() : selected.toString();
    }

    private String fileForSelectedEditorTheme() {
        Object selected = editorThemeCombo.getSelectedItem();
        for (EditorTheme t : EDITOR_THEMES) {
            if (t.name().equals(selected)) {
                return t.file();
            }
        }
        return "dark.xml";
    }

    private static String nameForFile(String file) {
        for (EditorTheme t : EDITOR_THEMES) {
            if (t.file().equals(file)) {
                return t.name();
            }
        }
        return "Dark";
    }

    private static void select(JComboBox<String> combo, String value) {
        if (value == null) {
            return;
        }
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).equals(value)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }
}