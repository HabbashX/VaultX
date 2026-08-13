package com.habbashx.vaultx.ui;

import com.habbashx.vaultx.core.Fonts;
import com.habbashx.vaultx.core.VaultManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    private static final String[] BACKUP_INTERVALS = {
            "Off", "Daily", "Weekly", "Monthly", "Quarterly", "Yearly"
    };
    private static final int[] BACKUP_INTERVAL_DAYS = {0, 1, 7, 30, 90, 365};

    private final VaultManager manager;
    private final List<Themes.ThemeInfo> themes = Themes.all();
    private final String originalTheme;
    private final String originalAppFont;
    private final int originalAppFontSize;
    private final String originalEditorFont;
    private final int originalEditorFontSize;
    private final String originalEditorTheme;
    private final boolean originalProtectFolder;
    private final boolean originalSelfDestruct;
    private final int originalMaxAttempts;
    private final int originalTrashRetention;
    private final String originalBackupDest;
    private final int originalBackupInterval;

    private final JComboBox<String> themeCombo = new JComboBox<>();
    private final JComboBox<String> appFontCombo = new JComboBox<>();
    private final JSpinner appFontSize = new JSpinner(new SpinnerNumberModel(13, 9, 28, 1));
    private final JComboBox<String> editorFontCombo = new JComboBox<>();
    private final JSpinner editorFontSize = new JSpinner(new SpinnerNumberModel(13, 8, 36, 1));
    private final JComboBox<String> editorThemeCombo = new JComboBox<>();
    private final RSyntaxTextArea preview = new RSyntaxTextArea();

    private final JCheckBox protectFolder = new JCheckBox("Protect vault folder on disk (hidden + deny delete)");
    private final JButton removeProtection = new JButton("Remove protection from this vault now");
    private final JCheckBox selfDestruct = new JCheckBox("Permanently delete the vault after repeated wrong attempts");
    private final JSpinner maxAttempts = new JSpinner(new SpinnerNumberModel(10, 3, 30, 1));
    private final JSpinner trashRetention = new JSpinner(new SpinnerNumberModel(30, 0, 365, 1));
    private final JTextField backupDest = new JTextField(24);
    private final JComboBox<String> backupInterval = new JComboBox<>(BACKUP_INTERVALS);

    public SettingsDialog(Window owner, VaultManager manager) {
        super(owner, "VaultX Settings", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        this.manager = manager;

        originalTheme = AppSettings.theme();
        originalAppFont = AppSettings.appFontFamily();
        originalAppFontSize = AppSettings.appFontSize();
        originalEditorFont = AppSettings.editorFontFamily();
        originalEditorFontSize = AppSettings.editorFontSize();
        originalEditorTheme = AppSettings.editorTheme();
        originalProtectFolder = AppSettings.protectFolder();
        originalSelfDestruct = AppSettings.selfDestruct();
        originalMaxAttempts = AppSettings.maxAttempts();
        originalTrashRetention = AppSettings.trashRetentionDays();
        originalBackupDest = AppSettings.backupDestination();
        originalBackupInterval = AppSettings.backupIntervalDays();

        buildControls();
        buildSecurityControls();
        buildBackupControls();

        Fonts.registerBundledFonts();
        preview.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        preview.setCodeFoldingEnabled(false);
        preview.setAntiAliasingEnabled(true);
        preview.setText(SAMPLE);
        refreshPreview();

        JPanel appearance = new JPanel(new BorderLayout(10, 10));
        appearance.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        appearance.add(buildAppearanceForm(), BorderLayout.NORTH);
        appearance.add(buildPreview(), BorderLayout.CENTER);

        JPanel security = new JPanel(new BorderLayout());
        security.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        security.add(buildSecurityForm(), BorderLayout.NORTH);

        JPanel backup = new JPanel(new BorderLayout());
        backup.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        backup.add(buildBackupForm(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Appearance", appearance);
        tabs.addTab("Security", security);
        tabs.addTab("Backup", backup);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(tabs, BorderLayout.CENTER);
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

    private void buildSecurityControls() {
        protectFolder.setSelected(originalProtectFolder);
        protectFolder.addActionListener(e -> {
            boolean value = protectFolder.isSelected();
            AppSettings.protectFolder(value);
            VaultManager.setAutoProtect(value);
        });
        removeProtection.setEnabled(manager != null);
        removeProtection.addActionListener(e -> removeProtectionNow());

        selfDestruct.setSelected(originalSelfDestruct);
        selfDestruct.addActionListener(e -> AppSettings.selfDestruct(selfDestruct.isSelected()));
        maxAttempts.setValue(originalMaxAttempts);
        maxAttempts.addChangeListener(e -> AppSettings.maxAttempts((Integer) maxAttempts.getValue()));
        trashRetention.setValue(originalTrashRetention);
        trashRetention.addChangeListener(e -> AppSettings.trashRetentionDays((Integer) trashRetention.getValue()));
    }

    private void buildBackupControls() {
        backupDest.setText(originalBackupDest);
        backupInterval.setSelectedIndex(intervalIndex(originalBackupInterval));
        backupInterval.addActionListener(e -> AppSettings.backupIntervalDays(daysForInterval(backupInterval.getSelectedIndex())));
    }

    private JPanel buildAppearanceForm() {
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

    private JPanel buildSecurityForm() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        form.add(protectFolder, c);

        c.gridy = 1;
        form.add(removeProtection, c);

        c.gridy = 2;
        form.add(selfDestruct, c);

        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 1;
        form.add(new JLabel("Max failed attempts:"), c);
        c.gridx = 1;
        form.add(maxAttempts, c);

        c.gridx = 0;
        c.gridy = 4;
        form.add(new JLabel("Keep trashed items (days):"), c);
        c.gridx = 1;
        form.add(trashRetention, c);

        JLabel hint = new JLabel("Self-destruct permanently deletes the vault folder after the maximum number of wrong passwords.");
        hint.setForeground(new java.awt.Color(0x888888));
        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = 2;
        form.add(hint, c);

        return form;
    }

    private JPanel buildBackupForm() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        form.add(new JLabel("Destination folder:"), c);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.add(backupDest);
        JButton browse = new JButton("Browse…");
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("Choose backup destination");
            if (fc.showOpenDialog(SettingsDialog.this) == JFileChooser.APPROVE_OPTION && fc.getSelectedFile() != null) {
                backupDest.setText(fc.getSelectedFile().getAbsolutePath());
                AppSettings.backupDestination(backupDest.getText());
            }
        });
        row.add(browse);
        c.gridx = 1;
        c.weightx = 1;
        form.add(row, c);

        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        form.add(new JLabel("Automatic backup:"), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(backupInterval, c);

        JLabel hint = new JLabel("Backups copy the encrypted vault (metadata + blobs). Scheduled backups run when the vault is opened.");
        hint.setForeground(new java.awt.Color(0x888888));
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        form.add(hint, c);

        return form;
    }

    private JPanel buildPreview() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Editor preview"));
        preview.setPreferredSize(new Dimension(520, 220));
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
            revertAll();
            dispose();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(apply);
        buttons.add(ok);
        buttons.add(cancel);
        return buttons;
    }

    private void removeProtectionNow() {
        if (manager == null) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Remove folder protection from this vault?\nProtection is re-applied on open when enabled in settings.",
                "Folder protection", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            manager.unprotectFolder();
            JOptionPane.showMessageDialog(this, "Folder protection removed.", "VaultX",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Could not remove protection: " + e.getMessage(), "VaultX",
                    JOptionPane.ERROR_MESSAGE);
        }
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
        AppSettings.protectFolder(protectFolder.isSelected());
        VaultManager.setAutoProtect(protectFolder.isSelected());
        AppSettings.selfDestruct(selfDestruct.isSelected());
        AppSettings.maxAttempts((Integer) maxAttempts.getValue());
        AppSettings.trashRetentionDays((Integer) trashRetention.getValue());
        AppSettings.backupDestination(backupDest.getText());
        AppSettings.backupIntervalDays(daysForInterval(backupInterval.getSelectedIndex()));
    }

    private void revertAll() {
        Themes.apply(originalTheme, originalAppFont, originalAppFontSize);
        AppSettings.protectFolder(originalProtectFolder);
        VaultManager.setAutoProtect(originalProtectFolder);
        AppSettings.selfDestruct(originalSelfDestruct);
        AppSettings.maxAttempts(originalMaxAttempts);
        AppSettings.trashRetentionDays(originalTrashRetention);
        AppSettings.backupDestination(originalBackupDest);
        AppSettings.backupIntervalDays(originalBackupInterval);
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

    private static int intervalIndex(int days) {
        for (int i = 0; i < BACKUP_INTERVAL_DAYS.length; i++) {
            if (BACKUP_INTERVAL_DAYS[i] == days) {
                return i;
            }
        }
        return 0;
    }

    private static int daysForInterval(int index) {
        if (index < 0 || index >= BACKUP_INTERVAL_DAYS.length) {
            return 0;
        }
        return BACKUP_INTERVAL_DAYS[index];
    }
}
