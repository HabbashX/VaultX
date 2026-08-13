package com.habbashx.vaultx.ui;

import com.habbashx.vaultx.core.CryptoUtils;
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
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SwingWorker;
import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainFrame extends JFrame {

    @FunctionalInterface
    private interface TaskRunner {
        void run(Progress progress, AtomicBoolean cancelled) throws Exception;
    }

    private final VaultManager manager;
    private final VaultBrowser browser = new VaultBrowser();
    private final JTextField search = new JTextField(16);
    private final JLabel status = new JLabel(" ");
    private final JButton newFolderBtn;
    private final JButton moveBtn;
    private final JButton openBtn;
    private final JButton exportBtn;
    private final JButton renameBtn;
    private final JButton deleteBtn;

    public MainFrame(@NotNull VaultManager manager) {
        super("VaultX — " + manager.vaultName());
        this.manager = manager;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Branding.installWindowIcon(this);
        setMinimumSize(new Dimension(820, 480));
        setSize(960, 560);
        setLocationRelativeTo(null);

        openBtn = toolbarButton("Open", false);
        openBtn.addActionListener(e -> openSelection());
        newFolderBtn = toolbarButton("New Folder", true);
        newFolderBtn.addActionListener(e -> createFolder());
        moveBtn = toolbarButton("Move to Folder", false);
        moveBtn.addActionListener(e -> moveItemsToFolder());
        JButton importBtn = toolbarButton("Import Files", true);
        importBtn.addActionListener(e -> importFiles());
        JButton importFolderBtn = toolbarButton("Import Folder", true);
        importFolderBtn.addActionListener(e -> importFolder());
        exportBtn = toolbarButton("Export", false);
        exportBtn.addActionListener(e -> exportItems());
        renameBtn = toolbarButton("Rename", false);
        renameBtn.addActionListener(e -> renameItem());
        deleteBtn = toolbarButton("Delete", false);
        deleteBtn.addActionListener(e -> deleteItems());
        JButton passwordBtn = toolbarButton("Change Password", true);
        passwordBtn.addActionListener(e -> changePassword());
        JButton nameBtn = toolbarButton("Rename Vault", true);
        nameBtn.addActionListener(e -> renameVault());
        JButton lockBtn = toolbarButton("Lock", true);
        lockBtn.addActionListener(e -> lock());
        JButton settingsBtn = toolbarButton("Settings", true);
        settingsBtn.addActionListener(e -> openSettings());

        JToolBar bar = new JToolBar("Vault");
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        addToBar(bar, importBtn, importFolderBtn);
        bar.addSeparator();
        addToBar(bar, newFolderBtn, moveBtn, openBtn, exportBtn, renameBtn, deleteBtn);
        bar.addSeparator();
        bar.add(nameBtn);
        bar.add(passwordBtn);
        bar.add(lockBtn);
        bar.addSeparator();
        bar.add(settingsBtn);
        bar.addSeparator(new java.awt.Dimension(24, 0));
        bar.add(new JLabel("Search:"));
        bar.add(search);

        browser.setOpenAction(this::openItem);
        browser.setOnDelete(this::deleteItems);
        browser.addSelectionListener(e -> updateSelectionState());

        browser.entryList().addMouseListener(new MouseAdapter() {
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
        });

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
    }

    private @NotNull JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        bar.add(status);
        return bar;
    }

    private void addToBar(JToolBar bar, JButton @NotNull ... buttons) {
        for (JButton b : buttons) {
            bar.add(b);
        }
    }

    private @NotNull JButton toolbarButton(String text, boolean initiallyEnabled) {
        JButton b = new JButton(text);
        b.setFocusable(false);
        b.setEnabled(initiallyEnabled);
        return b;
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
        openBtn.setEnabled(files);
        exportBtn.setEnabled(files);
        renameBtn.setEnabled(files);
        moveBtn.setEnabled(files);
        deleteBtn.setEnabled(files || folders);
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
                    // cancelled
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
        String subfolder = browser.currentPath();
        runTask("Importing files", "Encrypting and storing files…", (progress, cancelled) -> {
            long[] total = {0};
            for (Path f : files) {
                total[0] += Files.size(f);
            }
            long[] done = {0};
            for (Path f : files) {
                if (cancelled.get()) {
                    break;
                }
                long fileSize = Files.size(f);
                long offset = done[0];
                String name = f.getFileName().toString();
                if (subfolder.isEmpty()) {
                    manager.importFile(f, (d, t) -> progress.report(offset + d, total[0]));
                } else {
                    manager.importItemWithName(f, subfolder + "/" + name, (d, t) -> progress.report(offset + d, total[0]));
                }
                done[0] += fileSize;
            }
        }, this::refresh);
    }

    private void importFolder() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Import a folder into the vault");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION || fc.getSelectedFile() == null) {
            return;
        }
        Path root = fc.getSelectedFile().toPath();
        String subfolder = browser.currentPath();
        runTask("Importing folder", "Encrypting and storing folder…", (progress, cancelled) -> {
            List<Path> files;
            try (var stream = Files.walk(root)) {
                files = stream.filter(Files::isRegularFile).sorted().toList();
            }
            long[] total = {0};
            for (Path f : files) {
                total[0] += Files.size(f);
            }
            long[] done = {0};
            for (Path f : files) {
                if (cancelled.get()) {
                    break;
                }
                long fileSize = Files.size(f);
                long offset = done[0];
                Path relative = root.relativize(f);
                String names = root.getFileName().toString() + "/" + relative.toString().replace('\\', '/');
                String named = subfolder.isEmpty() ? names : subfolder + "/" + names;
                manager.importItemWithName(f, named, (d, t) -> progress.report(offset + d, total[0]));
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
            message = "Delete folder \"" + folders.getFirst()
                    + "\" and permanently delete everything inside it?";
        } else if (selected.isEmpty()) {
            message = "Delete " + folders.size() + " folders and everything inside them?";
        } else if (folders.isEmpty() && selected.size() == 1) {
            message = "Permanently delete \"" + selected.getFirst().name + "\" from the vault?";
        } else {
            message = "Permanently delete " + (selected.size() + folders.size()) + " selected items from the vault?";
        }
        int choice = JOptionPane.showConfirmDialog(this, message, "Delete",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        runTask("Deleting", "Removing encrypted items…", (progress, cancelled) -> {
            for (String folder : folders) {
                if (cancelled.get()) {
                    break;
                }
                manager.deleteFolder(folder);
            }
            for (VaultItem item : selected) {
                if (cancelled.get()) {
                    break;
                }
                if (manager.find(item.id) != null) {
                    manager.deleteItem(item);
                }
            }
        }, this::refresh);
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
        new SettingsDialog(this).setVisible(true);
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
        SwingWorker<Path, Void> worker = new SwingWorker<>() {
            @Override
            protected Path doInBackground() throws Exception {
                return manager.decryptToTemp(item);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                Path temp = null;
                try {
                    temp = get();
                    launchViewer(item, temp);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    TempFiles.delete(temp);
                } catch (ExecutionException e) {
                    TempFiles.delete(temp);
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    showError("Could not open \"" + item.name + "\"", cause.getMessage());
                }
            }
        };
        worker.execute();
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
        int index = browser.indexAt(e.getPoint());
        if (!browser.isSelected(index)) {
            browser.selectOnly(index);
        }
        boolean files = !browser.selectedFiles().isEmpty();
        boolean folders = !browser.selectedFolders().isEmpty();
        JPopupMenu menu = new JPopupMenu();
        menu.add(new AbstractAction("New Folder…") {
            public void actionPerformed(ActionEvent ev) {
                createFolder();
            }
        });
        JMenuItem newFolderItem = null;
        if (files) {
            menu.addSeparator();
            menu.add(new AbstractAction("Open") {
                public void actionPerformed(ActionEvent ev) {
                    openSelection();
                }
            });
            menu.add(new AbstractAction("Export") {
                public void actionPerformed(ActionEvent ev) {
                    exportItems();
                }
            });
            menu.add(new AbstractAction("Move to Folder…") {
                public void actionPerformed(ActionEvent ev) {
                    moveItemsToFolder();
                }
            });
        }
        if (folders) {
            menu.addSeparator();
            menu.add(new AbstractAction("Delete Folder") {
                public void actionPerformed(ActionEvent ev) {
                    deleteItems();
                }
            });
        }
        if (files) {
            menu.addSeparator();
            menu.add(new AbstractAction("Delete") {
                public void actionPerformed(ActionEvent ev) {
                    deleteItems();
                }
            });
        }
        menu.show(browser.entryList(), e.getX(), e.getY());
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private void errorDialog(String message) {
        JOptionPane.showMessageDialog(this, message, "VaultX", JOptionPane.ERROR_MESSAGE);
    }
}