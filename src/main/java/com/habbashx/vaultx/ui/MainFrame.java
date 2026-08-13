package com.habbashx.vaultx.ui;

import com.habbashx.vaultx.core.CryptoUtils;
import com.habbashx.vaultx.core.FileTypes;
import com.habbashx.vaultx.core.Progress;
import com.habbashx.vaultx.core.TempFiles;
import com.habbashx.vaultx.core.VaultItem;
import com.habbashx.vaultx.core.VaultManager;
import com.habbashx.vaultx.ui.viewer.ImageViewer;
import com.habbashx.vaultx.ui.viewer.MediaPlayerFrame;
import com.habbashx.vaultx.ui.viewer.PdfViewer;
import com.habbashx.vaultx.ui.viewer.TextViewer;
import org.jetbrains.annotations.NotNull;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

public final class MainFrame extends JFrame {

    @FunctionalInterface
    private interface TaskRunner {
        void run(Progress progress, AtomicBoolean cancelled) throws Exception;
    }

    private static final long RAM_PREVIEW_LIMIT = 8 * 1024 * 1024;

    private final VaultManager manager;
    private final Preferences prefs = Preferences.userNodeForPackage(MainFrame.class);
    private final VaultBrowser browser = new VaultBrowser();
    private final JTextField search = new JTextField(20);
    private final JLabel status = new JLabel(" ");
    private final AbstractAction openAction;
    private final AbstractAction exportAction;
    private final AbstractAction renameAction;
    private final AbstractAction moveAction;
    private final AbstractAction deleteAction;

    public MainFrame(@NotNull VaultManager manager) {
        super("VaultX — " + manager.vaultName());
        this.manager = manager;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Branding.installWindowIcon(this);
        setMinimumSize(new Dimension(820, 480));
        setSize(960, 560);
        setLocationRelativeTo(null);

        VaultManager.setAutoProtect(AppSettings.protectFolder());
        try {
            manager.autoPurgeExpiredTrash(AppSettings.trashRetentionDays());
        } catch (Exception ignored) {
        }

        openAction = action("Open", VaultIcons.open(16),
                KeyStroke.getKeyStroke("control O"), false, e -> openSelection());
        exportAction = action("Export…", VaultIcons.exportArrow(16),
                KeyStroke.getKeyStroke("control E"), false, e -> exportItems());
        renameAction = action("Rename…", VaultIcons.rename(16),
                KeyStroke.getKeyStroke("F2"), false, e -> renameItem());
        moveAction = action("Move to Folder…", VaultIcons.moveIcon(16),
                KeyStroke.getKeyStroke("control M"), false, e -> moveItemsToFolder());
        deleteAction = action("Move to Trash", VaultIcons.delete(16),
                KeyStroke.getKeyStroke("DELETE"), false, e -> deleteItems());

        AbstractAction newFolderAction = action("New Folder…", VaultIcons.newFolder(16),
                KeyStroke.getKeyStroke("control N"), true, e -> createFolder());
        AbstractAction importFilesAction = action("Import Files…", VaultIcons.importArrow(16),
                KeyStroke.getKeyStroke("control I"), true, e -> importFiles());
        AbstractAction importFolderAction = action("Import Folder…", VaultIcons.importArrow(16),
                KeyStroke.getKeyStroke("control shift I"), true, e -> importFolder());
        AbstractAction trashAction = action("Open Trash…", VaultIcons.trash(16),
                KeyStroke.getKeyStroke("control T"), true, e -> openTrash());
        AbstractAction backupAction = action("Backup Now…", VaultIcons.backup(16),
                null, true, e -> backupNow());
        AbstractAction changePasswordAction = action("Change Password…", null,
                null, true, e -> changePassword());
        AbstractAction renameVaultAction = action("Rename Vault…", null,
                null, true, e -> renameVault());
        AbstractAction lockAction = action("Lock Vault", VaultIcons.lock(16),
                KeyStroke.getKeyStroke("control L"), true, e -> lock());
        AbstractAction settingsAction = action("Settings…", VaultIcons.settings(16),
                KeyStroke.getKeyStroke("control COMMA"), true, e -> openSettings());
        AbstractAction refreshAction = action("Refresh", null,
                KeyStroke.getKeyStroke("F5"), true, e -> refresh());
        AbstractAction selectAllAction = action("Select All", null,
                KeyStroke.getKeyStroke("control A"), true, e -> browser.selectAll());

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic('F');
        fileMenu.add(newFolderAction);
        fileMenu.add(importFilesAction);
        fileMenu.add(importFolderAction);
        fileMenu.addSeparator();
        fileMenu.add(exportAction);
        fileMenu.addSeparator();
        fileMenu.add(lockAction);

        JMenu editMenu = new JMenu("Edit");
        editMenu.setMnemonic('E');
        editMenu.add(openAction);
        editMenu.add(renameAction);
        editMenu.add(moveAction);
        editMenu.add(deleteAction);
        editMenu.addSeparator();
        editMenu.add(selectAllAction);
        editMenu.add(refreshAction);

        JMenu vaultMenu = new JMenu("Vault");
        vaultMenu.setMnemonic('V');
        vaultMenu.add(trashAction);
        vaultMenu.add(backupAction);
        vaultMenu.addSeparator();
        vaultMenu.add(changePasswordAction);
        vaultMenu.add(renameVaultAction);
        vaultMenu.addSeparator();
        vaultMenu.add(settingsAction);

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(vaultMenu);
        setJMenuBar(menuBar);

        JToolBar bar = new JToolBar("Vault");
        bar.setFloatable(false);
        bar.setRollover(true);
        bar.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        bar.add(toolbarButton(importFilesAction));
        bar.add(toolbarButton(importFolderAction));
        bar.add(toolbarButton(newFolderAction));
        bar.addSeparator();
        bar.add(toolbarButton(openAction));
        bar.add(toolbarButton(exportAction));
        bar.add(toolbarButton(renameAction));
        bar.add(toolbarButton(moveAction));
        JButton deleteBtn = toolbarButton(deleteAction);
        deleteBtn.setText("Delete");
        bar.add(deleteBtn);
        bar.addSeparator();
        JButton trashBtn = toolbarButton(trashAction);
        trashBtn.setText("Trash");
        bar.add(trashBtn);
        JButton backupBtn = toolbarButton(backupAction);
        backupBtn.setText("Backup");
        bar.add(backupBtn);
        bar.add(Box.createHorizontalGlue());
        bar.add(new JLabel(VaultIcons.search(16)));
        bar.add(search);
        JButton clearSearchBtn = new JButton("Clear");
        clearSearchBtn.setFocusable(false);
        clearSearchBtn.addActionListener(e -> {
            search.setText("");
            search.requestFocusInWindow();
        });
        bar.add(clearSearchBtn);

        browser.setOpenAction(this::openItem);
        browser.setOnDelete(this::deleteItems);
        browser.addSelectionListener(e -> updateSelectionState());
        browser.setDropHandler(new VaultBrowser.DropHandler() {
            @Override
            public void importDropped(List<Path> paths, String targetFolder) {
                importPaths(paths, targetFolder);
            }

            @Override
            public void moveDropped(List<VaultItem> items, String targetFolder) {
                moveDroppedItems(items, targetFolder);
            }
        });
        browser.setOnClearFilters(() -> {
            search.setText("");
            applyFilter();
        });

        MouseAdapter popupListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }
            }
        };
        browser.entryList().addMouseListener(popupListener);
        browser.emptyHint().addMouseListener(popupListener);
        browser.addMouseListener(popupListener);

        JPanel content = new JPanel(new BorderLayout());
        content.add(bar, BorderLayout.NORTH);
        content.add(browser, BorderLayout.CENTER);
        content.add(buildStatusBar(), BorderLayout.SOUTH);
        setContentPane(content);

        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }
        });

        refresh();
        SwingUtilities.invokeLater(this::maybeRunScheduledBackup);
    }

    private @NotNull JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        bar.add(status, BorderLayout.WEST);
        JLabel credit = new JLabel("Developed by HabbashX");
        credit.setForeground(Color.GRAY);
        credit.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        bar.add(credit, BorderLayout.EAST);
        return bar;
    }

    private static JButton toolbarButton(Action a) {
        JButton b = new JButton(a);
        b.setFocusable(false);
        b.setFocusPainted(false);
        return b;
    }

    private static AbstractAction action(String name, Icon icon, KeyStroke key,
                                         boolean enabled, Consumer<ActionEvent> handler) {
        AbstractAction a = new AbstractAction(name, icon) {
            @Override
            public void actionPerformed(ActionEvent e) {
                handler.accept(e);
            }
        };
        a.setEnabled(enabled);
        if (key != null) {
            a.putValue(AbstractAction.ACCELERATOR_KEY, key);
        }
        return a;
    }

    private void refresh() {
        browser.ensureCurrentFolderExists(manager.folders());
        browser.setItems(manager.items(), manager.folders(), manager.vaultName());
        browser.filter(search.getText());
        updateSelectionState();
        status.setText(manager.size() + " items  •  "
                + ProgressDialog.formatBytes(manager.totalSize()) + " stored  •  "
                + "AES-256-GCM · PBKDF2-HMAC-SHA256 (600k iterations)  •  "
                + "media playback: VLC 3.x");
    }

    private void updateSelectionState() {
        boolean files = !browser.selectedFiles().isEmpty();
        boolean folders = !browser.selectedFolders().isEmpty();
        openAction.setEnabled(files);
        exportAction.setEnabled(files);
        renameAction.setEnabled(files);
        moveAction.setEnabled(files);
        deleteAction.setEnabled(files || folders);
    }

    private @NotNull List<VaultItem> selectedItems() {
        return browser.selectedFiles();
    }

    private void applyFilter() {
        browser.filter(search.getText());
    }

    private void runTask(String title, String message, TaskRunner task, Runnable onDone) {
        ProgressDialog dialog = new ProgressDialog(this, title, message);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                task.run(dialog.progress(), cancelled);
                return null;
            }

            @Override
            protected void done() {
                dialog.dispose();
                try {
                    get();
                    if (onDone != null) {
                        onDone.run();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (CancellationException e) {
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    showError("Operation failed", cause.getMessage());
                }
            }
        };
        dialog.onCancelRequest(() -> {
            cancelled.set(true);
            worker.cancel(true);
        });
        worker.execute();
        dialog.setVisible(true);
    }

    private void importFiles() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setMultiSelectionEnabled(true);
        fc.setDialogTitle("Import files into the vault");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION || fc.getSelectedFiles() == null) {
            return;
        }
        List<Path> files = Arrays.stream(fc.getSelectedFiles()).map(File::toPath).toList();
        importPaths(files, browser.currentPath());
    }

    private void importFolder() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Import a folder into the vault");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION || fc.getSelectedFile() == null) {
            return;
        }
        importPaths(List.of(fc.getSelectedFile().toPath()), browser.currentPath());
    }

    private void importPaths(List<Path> paths, String targetFolder) {
        if (paths == null || paths.isEmpty()) {
            return;
        }
        List<Path> fileList = new ArrayList<>();
        List<String> storedNames = new ArrayList<>();
        for (Path p : paths) {
            if (Files.isDirectory(p)) {
                Path folderName = p.getFileName() == null ? p : p.getFileName();
                try (var stream = Files.walk(p)) {
                    for (Path f : stream.filter(Files::isRegularFile).sorted().toList()) {
                        String rel = p.relativize(f).toString().replace('\\', '/');
                        fileList.add(f);
                        storedNames.add(folderName + "/" + rel);
                    }
                } catch (IOException e) {
                    showError("Import", e.getMessage());
                    return;
                }
            } else if (Files.isRegularFile(p)) {
                fileList.add(p);
                storedNames.add(p.getFileName().toString());
            }
        }
        if (fileList.isEmpty()) {
            return;
        }
        String subfolder = targetFolder == null ? "" : targetFolder;
        runTask("Importing files", "Encrypting and storing files…", (progress, cancelled) -> {
            long[] total = {0};
            for (Path f : fileList) {
                total[0] += Files.size(f);
            }
            long[] done = {0};
            for (int i = 0; i < fileList.size(); i++) {
                if (cancelled.get()) {
                    break;
                }
                Path f = fileList.get(i);
                long fileSize = Files.size(f);
                long offset = done[0];
                String full = subfolder.isEmpty() ? storedNames.get(i) : subfolder + "/" + storedNames.get(i);
                manager.importItemWithName(f, full, (d, t) -> progress.report(offset + d, total[0]));
                done[0] += fileSize;
            }
        }, this::refresh);
    }

    private void createFolder() {
        String name = (String) JOptionPane.showInputDialog(this,
                "Folder name:", "New Folder", JOptionPane.PLAIN_MESSAGE, null, null, "");
        if (name == null || name.isBlank()) {
            return;
        }
        String path = browser.currentPath().isEmpty() ? name.trim() : browser.currentPath() + "/" + name.trim();
        try {
            manager.createFolder(path);
        } catch (Exception e) {
            showError("New Folder", e.getMessage());
            return;
        }
        refresh();
    }

    private void moveItemsToFolder() {
        List<VaultItem> selected = selectedItems();
        if (selected.isEmpty()) {
            return;
        }
        List<String> folders = manager.folders();
        JComboBox<String> combo = new JComboBox<>();
        combo.addItem("(Root)");
        for (String f : folders) {
            combo.addItem(f);
        }
        JTextField newFolderInput = new JTextField(20);
        JPanel panel = new JPanel(new java.awt.GridLayout(3, 2, 6, 6));
        panel.add(new JLabel("Target folder:"));
        panel.add(combo);
        panel.add(new JLabel("New folder:"));
        panel.add(newFolderInput);
        panel.add(new JLabel(""));
        panel.add(new JLabel("(optional — type name to create it)"));
        int result = JOptionPane.showConfirmDialog(this, panel, "Move to Folder",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        String target;
        String newFolder = newFolderInput.getText() == null ? "" : newFolderInput.getText().trim();
        if (!newFolder.isEmpty()) {
            try {
                target = manager.createFolder(newFolder);
            } catch (Exception e) {
                showError("Move to Folder", e.getMessage());
                return;
            }
        } else {
            target = (String) combo.getSelectedItem();
            if (target == null || target.equals("(Root)")) {
                target = "";
            }
        }
        try {
            manager.moveItems(selected, target);
        } catch (Exception e) {
            showError("Move to Folder", e.getMessage());
            return;
        }
        refresh();
    }

    private void exportItems() {
        List<VaultItem> selected = selectedItems();
        if (selected.isEmpty()) {
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Choose export folder");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION || fc.getSelectedFile() == null) {
            return;
        }
        Path dir = fc.getSelectedFile().toPath();
        boolean overwrite = true;
        for (VaultItem item : selected) {
            if (Files.exists(dir.resolve(item.name))) {
                int choice = JOptionPane.showConfirmDialog(this,
                        "Some destination files already exist. Overwrite them?",
                        "Export", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                overwrite = choice == JOptionPane.YES_OPTION;
                break;
            }
        }
        final boolean allowOverwrite = overwrite;
        runTask("Exporting", "Decrypting files…", (progress, cancelled) -> {
            long[] total = {0};
            for (VaultItem item : selected) {
                total[0] += item.size;
            }
            long[] done = {0};
            for (VaultItem item : selected) {
                if (cancelled.get()) {
                    break;
                }
                Path dest = dir.resolve(item.name).normalize();
                if (Files.exists(dest) && !allowOverwrite) {
                    done[0] += item.size;
                    continue;
                }
                Path parent = dest.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                long offset = done[0];
                manager.exportTo(item, dest, (d, t) -> progress.report(offset + d, total[0]));
                done[0] += item.size;
            }
        }, this::refresh);
    }

    private void renameItem() {
        List<VaultItem> selected = selectedItems();
        if (selected.isEmpty()) {
            return;
        }
        VaultItem item = selected.getFirst();
        String newName = (String) JOptionPane.showInputDialog(this,
                "New name:", "Rename item", JOptionPane.PLAIN_MESSAGE, null, null, item.name);
        if (newName == null || newName.isBlank() || newName.equals(item.name)) {
            return;
        }
        runTask("Renaming", "Updating vault metadata…", (progress, cancelled) ->
                manager.renameItem(item, newName.trim()), this::refresh);
    }

    private void deleteItems() {
        List<VaultItem> selected = selectedItems();
        List<String> folders = browser.selectedFolders();
        if (selected.isEmpty() && folders.isEmpty()) {
            return;
        }
        String message;
        if (selected.isEmpty() && folders.size() == 1) {
            message = "Move folder \"" + folders.getFirst()
                    + "\" and everything inside it to the trash?";
        } else if (selected.isEmpty()) {
            message = "Move " + folders.size() + " folders and everything inside them to the trash?";
        } else if (folders.isEmpty() && selected.size() == 1) {
            message = "Move \"" + selected.getFirst().name + "\" to the trash?";
        } else {
            message = "Move " + (selected.size() + folders.size()) + " selected items to the trash?";
        }
        int choice = JOptionPane.showConfirmDialog(this, message, "Move to Trash",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        runTask("Moving to trash", "Updating vault metadata…", (progress, cancelled) -> {
            for (String folder : folders) {
                if (cancelled.get()) {
                    break;
                }
                manager.trashFolder(folder);
            }
            for (VaultItem item : selected) {
                if (cancelled.get()) {
                    break;
                }
                VaultItem current = manager.find(item.id);
                if (current != null) {
                    manager.trashItems(List.of(current));
                }
            }
        }, this::refresh);
    }

    private void openTrash() {
        new TrashDialog(this, manager, this::refresh).setVisible(true);
    }

    private void moveDroppedItems(List<VaultItem> items, String targetFolder) {
        if (items == null || items.isEmpty()) {
            return;
        }
        try {
            manager.moveItems(items, targetFolder == null ? "" : targetFolder);
            refresh();
        } catch (Exception e) {
            showError("Move", e.getMessage());
        }
    }

    private void backupNow() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Choose backup destination");
        String dest = AppSettings.backupDestination();
        if (!dest.isBlank()) {
            try {
                Path d = Paths.get(dest);
                if (Files.isDirectory(d)) {
                    fc.setSelectedFile(d.toFile());
                }
            } catch (Exception ignored) {
            }
        }
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION || fc.getSelectedFile() == null) {
            return;
        }
        Path dir = fc.getSelectedFile().toPath();
        AppSettings.backupDestination(dir.toString());
        runTask("Backing up vault", "Copying encrypted vault…", (progress, cancelled) ->
                manager.backupTo(dir, progress), () -> {
            rememberBackupTime();
            JOptionPane.showMessageDialog(this,
                    "Vault backed up to:\n" + dir, "Backup", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private void maybeRunScheduledBackup() {
        int days = AppSettings.backupIntervalDays();
        if (days <= 0) {
            return;
        }
        String dest = AppSettings.backupDestination();
        if (dest.isBlank()) {
            return;
        }
        Path dir;
        try {
            dir = Paths.get(dest);
            if (!Files.isDirectory(dir)) {
                return;
            }
        } catch (Exception e) {
            return;
        }
        long last = prefs.getLong(backupPrefKey(), 0);
        if (last > 0 && System.currentTimeMillis() - last < days * 86_400_000L) {
            return;
        }
        runTask("Scheduled backup", "Backing up vault to " + dir + "…", (progress, cancelled) ->
                manager.backupTo(dir, progress), this::rememberBackupTime);
    }

    private void rememberBackupTime() {
        prefs.putLong(backupPrefKey(), System.currentTimeMillis());
    }

    private String backupPrefKey() {
        return "lastBackup." + manager.vaultDir();
    }

    private void renameVault() {
        String newName = (String) JOptionPane.showInputDialog(this,
                "Vault name:", "Rename vault", JOptionPane.PLAIN_MESSAGE, null, null, manager.vaultName());
        if (newName == null || newName.isBlank() || newName.equals(manager.vaultName())) {
            return;
        }
        runTask("Renaming vault", "Updating vault metadata…", (progress, cancelled) ->
                manager.changeVaultName(newName.trim()), () -> {
            setTitle("VaultX — " + manager.vaultName());
            refresh();
        });
    }

    private void changePassword() {
        JPasswordField newPassword = new JPasswordField(20);
        JPasswordField confirm = new JPasswordField(20);
        JPanel panel = new JPanel(new java.awt.GridLayout(2, 2, 6, 6));
        panel.add(new JLabel("New master password:"));
        panel.add(newPassword);
        panel.add(new JLabel("Confirm new password:"));
        panel.add(confirm);
        int result = JOptionPane.showConfirmDialog(this, panel, "Change master password",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            CryptoUtils.wipe(newPassword.getPassword());
            CryptoUtils.wipe(confirm.getPassword());
            return;
        }
        char[] a = newPassword.getPassword();
        char[] b = confirm.getPassword();
        if (a.length < 8) {
            errorDialog("Master password must be at least 8 characters long.");
            CryptoUtils.wipe(a);
            CryptoUtils.wipe(b);
            return;
        }
        if (!java.util.Arrays.equals(a, b)) {
            errorDialog("Passwords do not match.");
            CryptoUtils.wipe(a);
            CryptoUtils.wipe(b);
            return;
        }
        final char[] newPwd = Arrays.copyOf(a, a.length);
        CryptoUtils.wipe(a);
        CryptoUtils.wipe(b);
        runTask("Changing password", "Re-encrypting vault metadata…", (progress, cancelled) ->
                manager.changePassword(newPwd), () -> {
            CryptoUtils.wipe(newPwd);
            JOptionPane.showMessageDialog(this,
                    "Master password changed. All stored files remain encrypted with the same data keys.",
                    "VaultX", JOptionPane.INFORMATION_MESSAGE);
            refresh();
        });
    }

    private void lock() {
        manager.lock();
        dispose();
        new com.habbashx.vaultx.ui.LoginDialog().setVisible(true);
    }

    private void openSettings() {
        new SettingsDialog(this, manager).setVisible(true);
    }

    private void openSelection() {
        List<VaultItem> selected = selectedItems();
        if (selected.isEmpty()) {
            return;
        }
        openItem(selected.getFirst());
    }

    private void openItem(VaultItem item) {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        SwingWorker<Object, Void> worker = new SwingWorker<>() {
            @Override
            protected Object doInBackground() throws Exception {
                FileTypes.Category category = item.category();
                if (item.size <= RAM_PREVIEW_LIMIT
                        && (category == FileTypes.Category.IMAGE || category == FileTypes.Category.TEXT)) {
                    return manager.decryptToBytes(item);
                }
                return manager.decryptToTemp(item);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                Object result = null;
                try {
                    result = get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    if (result instanceof Path path) {
                        TempFiles.delete(path);
                    }
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    showError("Could not open \"" + item.name + "\"", cause.getMessage());
                    return;
                }
                if (result instanceof byte[] bytes) {
                    launchViewer(item, bytes);
                } else if (result instanceof Path path) {
                    launchViewer(item, path);
                }
            }
        };
        worker.execute();
    }

    private void launchViewer(VaultItem item, byte @NotNull [] content) {
        switch (item.category()) {
            case IMAGE -> new ImageViewer(item, manager, content).setVisible(true);
            case TEXT -> new TextViewer(item, manager, content).setVisible(true);
            default -> {
            }
        }
    }

    private void launchViewer(VaultItem item, Path temp) {
        switch (item.category()) {
            case IMAGE -> new ImageViewer(item, manager, temp).setVisible(true);
            case PDF -> new PdfViewer(item, manager, temp).setVisible(true);
            case TEXT -> new TextViewer(item, manager, temp).setVisible(true);
            case AUDIO, VIDEO -> new MediaPlayerFrame(item, manager, temp).setVisible(true);
            default -> {
                JOptionPane.showMessageDialog(this,
                        "There is no built-in preview for this file type.\nUse Export to get a decrypted copy on disk.",
                        "VaultX", JOptionPane.INFORMATION_MESSAGE);
                TempFiles.delete(temp);
            }
        }
    }

    private void showPopup(MouseEvent e) {
        Component source = (Component) e.getSource();
        JList<?> list = browser.entryList();
        Point inList = SwingUtilities.convertPoint(source, e.getPoint(), list);
        int index = browser.indexAt(inList);
        if (index >= 0 && !browser.isSelected(index)) {
            browser.selectOnly(index);
        }
        boolean files = !browser.selectedFiles().isEmpty();
        boolean folders = !browser.selectedFolders().isEmpty();
        boolean selection = files || folders;

        JPopupMenu menu = new JPopupMenu();
        if (selection) {
            if (files) {
                menu.add(openAction);
                menu.add(exportAction);
                menu.add(renameAction);
                menu.add(moveAction);
                menu.add(deleteAction);
            } else {
                menu.add(new AbstractAction("Open Folder") {
                    @Override
                    public void actionPerformed(ActionEvent ev) {
                        List<String> selected = browser.selectedFolders();
                        if (!selected.isEmpty()) {
                            browser.openFolder(selected.getFirst());
                        }
                    }
                });
                menu.add(deleteAction);
            }
            menu.addSeparator();
        }
        menu.add(new AbstractAction("New Folder…") {
            @Override
            public void actionPerformed(ActionEvent ev) {
                createFolder();
            }
        });
        menu.add(new AbstractAction("Import Files…") {
            @Override
            public void actionPerformed(ActionEvent ev) {
                importFiles();
            }
        });
        menu.add(new AbstractAction("Import Folder…") {
            @Override
            public void actionPerformed(ActionEvent ev) {
                importFolder();
            }
        });
        menu.addSeparator();
        menu.add(new AbstractAction("Search…") {
            @Override
            public void actionPerformed(ActionEvent ev) {
                search.requestFocusInWindow();
                search.selectAll();
            }
        });
        menu.add(new AbstractAction("Filter by Size/Date…") {
            @Override
            public void actionPerformed(ActionEvent ev) {
                browser.openFilterDialog();
            }
        });
        menu.add(new AbstractAction("Refresh") {
            @Override
            public void actionPerformed(ActionEvent ev) {
                refresh();
            }
        });
        menu.addSeparator();
        menu.add(new AbstractAction("Select All") {
            @Override
            public void actionPerformed(ActionEvent ev) {
                browser.selectAll();
            }
        });
        menu.show(source, e.getX(), e.getY());
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private void errorDialog(String message) {
        JOptionPane.showMessageDialog(this, message, "VaultX", JOptionPane.ERROR_MESSAGE);
    }
}