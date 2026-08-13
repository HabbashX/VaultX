package com.habbashx.vaultx.ui;

import com.habbashx.vaultx.core.VaultItem;
import com.habbashx.vaultx.core.VaultManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class TrashDialog extends JDialog {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final VaultManager manager;
    private final Runnable onChanged;
    private final DefaultListModel<VaultItem> model = new DefaultListModel<>();
    private final JList<VaultItem> list = new JList<>(model);
    private final JLabel info = new JLabel(" ", SwingConstants.CENTER);

    public TrashDialog(Window owner, @NotNull VaultManager manager, Runnable onChanged) {
        super(owner, "VaultX — Trash", ModalityType.APPLICATION_MODAL);
        this.manager = manager;
        this.onChanged = onChanged == null ? () -> {
        } : onChanged;

        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setCellRenderer(new VaultItemCellRenderer());

        JButton restore = new JButton("Restore");
        restore.addActionListener(e -> restoreSelected());
        JButton purge = new JButton("Delete Forever");
        purge.addActionListener(e -> purgeSelected());
        JButton empty = new JButton("Empty Trash");
        empty.addActionListener(e -> emptyTrash());
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        buttons.add(restore);
        buttons.add(purge);
        buttons.add(empty);
        buttons.add(new javax.swing.JSeparator(SwingConstants.VERTICAL));
        buttons.add(close);

        info.setForeground(new Color(0x888888));
        info.setFont(info.getFont().deriveFont(Font.PLAIN, 12f));

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(new JLabel("Items in the trash are still encrypted on disk until deleted forever.", SwingConstants.CENTER),
                BorderLayout.NORTH);
        content.add(new JScrollPane(list), BorderLayout.CENTER);
        content.add(info, BorderLayout.SOUTH);
        content.add(buttons, BorderLayout.EAST);

        setContentPane(content);
        setMinimumSize(new Dimension(560, 380));
        setSize(640, 420);
        setLocationRelativeTo(owner);
        refresh();
    }

    private void refresh() {
        model.clear();
        List<VaultItem> trashed = manager.trashedItems();
        trashed.sort((a, b) -> Long.compare(b.trashedAt, a.trashedAt));
        model.addAll(trashed);
        info.setText(trashed.size() + " item(s) in trash  •  "
                + ProgressDialog.formatBytes(trashed.stream().mapToLong(i -> i.size).sum()));
    }

    private List<VaultItem> selected() {
        List<VaultItem> result = new ArrayList<>();
        for (int index : list.getSelectedIndices()) {
            result.add(model.get(index));
        }
        return result;
    }

    private void restoreSelected() {
        List<VaultItem> items = selected();
        if (items.isEmpty()) {
            return;
        }
        try {
            manager.restoreItems(items);
        } catch (Exception e) {
            error("Restore", e.getMessage());
            return;
        }
        onChanged.run();
        refresh();
    }

    private void purgeSelected() {
        List<VaultItem> items = selected();
        if (items.isEmpty()) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Permanently delete " + items.size() + " item(s)?\nTheir encrypted data will be overwritten and removed.",
                "Delete Forever", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            manager.purgeTrashed(items);
        } catch (Exception e) {
            error("Delete", e.getMessage());
            return;
        }
        onChanged.run();
        refresh();
    }

    private void emptyTrash() {
        if (manager.trashedItems().isEmpty()) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Empty the trash permanently?\nAll trashed items will be overwritten and removed.",
                "Empty Trash", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            manager.purgeTrashed();
        } catch (Exception e) {
            error("Empty Trash", e.getMessage());
            return;
        }
        onChanged.run();
        refresh();
    }

    private void error(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    public static String dateText(long millis) {
        return millis <= 0 ? "" : FMT.format(Instant.ofEpochMilli(millis));
    }
}
