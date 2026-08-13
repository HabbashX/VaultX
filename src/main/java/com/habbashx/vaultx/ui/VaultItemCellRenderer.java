package com.habbashx.vaultx.ui;

import com.habbashx.vaultx.core.VaultItem;
import org.jetbrains.annotations.Contract;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridLayout;

public final class VaultItemCellRenderer extends JPanel implements ListCellRenderer<VaultItem> {

    private final JLabel icon = new JLabel();
    private final JLabel name = new JLabel();
    private final JLabel meta = new JLabel();
    private final JPanel right = new JPanel(new GridLayout(2, 1, 0, 1));

    public VaultItemCellRenderer() {
        setLayout(new BorderLayout(10, 0));
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        name.setFont(name.getFont().deriveFont(Font.BOLD, 12f));
        meta.setFont(meta.getFont().deriveFont(Font.PLAIN, 11f));
        meta.setForeground(new java.awt.Color(0x888888));
        right.setOpaque(false);
        right.add(name);
        right.add(meta);
        add(icon, BorderLayout.WEST);
        add(right, BorderLayout.CENTER);
        setOpaque(true);
    }

    @Contract("_, _, _, _, _ -> this")
    @Override
    public Component getListCellRendererComponent(JList<? extends VaultItem> list, VaultItem value,
                                                  int index, boolean selected, boolean cellHasFocus) {
        icon.setIcon(VaultIcons.forItem(value, 28));
        name.setText(value.name);
        StringBuilder metaText = new StringBuilder(ProgressDialog.formatBytes(value.size));
        if (value.trashedAt > 0) {
            metaText.append("   •   deleted ").append(TrashDialog.dateText(value.trashedAt));
        }
        meta.setText(metaText.toString());
        if (selected) {
            setBackground(list.getSelectionBackground());
            name.setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            name.setForeground(list.getForeground());
        }
        return this;
    }
}
