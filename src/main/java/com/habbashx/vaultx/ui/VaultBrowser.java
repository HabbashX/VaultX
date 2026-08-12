package com.habbashx.vaultx.ui;

import com.habbashx.vaultx.core.VaultItem;
import org.jetbrains.annotations.NotNull;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public final class VaultBrowser extends JPanel {

    public interface OpenAction {
        void openFile(VaultItem item);
    }

    public static final class Entry {
        final VaultItem item;
        final String name;
        final boolean folder;
        final String folderPath;

        Entry(String folderName, String folderPath) {
            this.item = null;
            this.name = folderName;
            this.folder = true;
            this.folderPath = folderPath;
        }

        Entry(@NotNull VaultItem file) {
            this.item = file;
            this.name = file.name.substring(file.name.lastIndexOf('/') + 1);
            this.folder = false;
            this.folderPath = null;
        }
    }

    private static final int ICON = 40;
    private static final int CELL_W = 120;
    private static final int CELL_H = 92;

    private final DefaultListModel<Entry> model = new DefaultListModel<>();
    private final JList<Entry> list = new JList<>(model);
    private final JButton backBtn = new JButton("← Back");
    private final JButton upBtn = new JButton("↑ Up");
    private final JLabel pathLabel = new JLabel(" ");
    private final JLabel emptyHint = new JLabel("This folder is empty.", SwingConstants.CENTER);
    private final JScrollPane scroll = new JScrollPane(list);

    private final Deque<String> history = new ArrayDeque<>();
    private List<VaultItem> all = List.of();
    private List<String> folders = List.of();
    private String rootName = "Vault";
    private String currentPath = "";
    private String filter = "";
    private OpenAction openAction = item -> {
    };
    private Runnable deleteAction = () -> {
    };

    public VaultBrowser() {
        super(new BorderLayout(0, 0));

        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(0);
        list.setFixedCellWidth(CELL_W);
        list.setFixedCellHeight(CELL_H);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setCellRenderer(new BrowserRenderer());

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    int index = list.locationToIndex(e.getPoint());
                    if (index >= 0 && index < model.size()) {
                        activate(model.get(index));
                    }
                }
            }
        });

        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "open");
        list.getActionMap().put("open", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int index = list.getSelectedIndex();
                if (index >= 0) {
                    activate(model.get(index));
                }
            }
        });
        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("BACK_SPACE"), "go-up");
        list.getActionMap().put("go-up", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                goUp();
            }
        });
        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DELETE"), "delete");
        list.getActionMap().put("delete", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteAction.run();
            }
        });

        backBtn.addActionListener(e -> goBack());
        upBtn.addActionListener(e -> goUp());

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        nav.add(backBtn);
        nav.add(upBtn);
        nav.add(pathLabel);

        emptyHint.setForeground(new Color(0x8A8F98));
        emptyHint.setFont(emptyHint.getFont().deriveFont(Font.ITALIC, 13f));

        scroll.setBorder(BorderFactory.createEmptyBorder());
        JPanel center = new JPanel(new BorderLayout());
        center.add(scroll, BorderLayout.CENTER);

        add(nav, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);

        rewrap();
    }

    public void setOpenAction(OpenAction action) {
        this.openAction = action == null ? item -> {
        } : action;
    }

    public void setOnDelete(Runnable action) {
        this.deleteAction = action == null ? () -> {
        } : action;
    }

    public void addSelectionListener(ListSelectionListener listener) {
        list.getSelectionModel().addListSelectionListener(listener);
    }

    public JList<Entry> entryList() {
        return list;
    }

    public void setItems(@NotNull List<VaultItem> items, @NotNull List<String> folders, String vaultName) {
        this.all = items;
        this.folders = folders;
        this.rootName = vaultName == null || vaultName.isBlank() ? "Vault" : vaultName.trim();
        rebuild();
    }

    public void filter(String text) {
        this.filter = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        rebuild();
    }

    public @NotNull List<VaultItem> selectedFiles() {
        List<VaultItem> result = new ArrayList<>();
        for (int index : list.getSelectedIndices()) {
            Entry entry = model.get(index);
            if (!entry.folder && entry.item != null) {
                result.add(entry.item);
            }
        }
        return result;
    }

    public @NotNull List<String> selectedFolders() {
        List<String> result = new ArrayList<>();
        for (int index : list.getSelectedIndices()) {
            Entry entry = model.get(index);
            if (entry.folder && entry.folderPath != null) {
                result.add(entry.folderPath);
            }
        }
        return result;
    }

    public void ensureCurrentFolderExists(@NotNull List<String> folders) {
        java.util.Set<String> set = new java.util.HashSet<>();
        for (String f : folders) {
            set.add(f.toLowerCase(Locale.ROOT));
        }
        while (!currentPath.isEmpty() && !set.contains(currentPath.toLowerCase(Locale.ROOT))) {
            currentPath = parentOf(currentPath);
        }
    }

    public String currentPath() {
        return currentPath;
    }

    public boolean hasSelection() {
        return list.getSelectedIndices().length > 0;
    }

    public int indexAt(Point p) {
        return list.locationToIndex(p);
    }

    public boolean isSelected(int index) {
        return index >= 0 && list.isSelectedIndex(index);
    }

    public void selectOnly(int index) {
        if (index >= 0 && index < model.size()) {
            list.setSelectedIndex(index);
        }
    }

    private void activate(Entry entry) {
        if (entry.folder) {
            history.push(currentPath);
            currentPath = currentPath.isEmpty() ? entry.name : currentPath + "/" + entry.name;
            rebuild();
            list.requestFocusInWindow();
        } else if (entry.item != null) {
            openAction.openFile(entry.item);
        }
    }

    private void goUp() {
        if (currentPath.isEmpty()) {
            return;
        }
        history.push(currentPath);
        currentPath = parentOf(currentPath);
        rebuild();
    }

    private void goBack() {
        if (history.isEmpty()) {
            return;
        }
        currentPath = history.pop();
        rebuild();
    }

    private String parentOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private void rebuild() {
        model.clear();
        String prefix = currentPath.isEmpty() ? "" : currentPath + "/";
        TreeSet<String> folders = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        List<Entry> files = new ArrayList<>();
        for (String folder : this.folders) {
            if (folder == null || folder.isBlank()) {
                continue;
            }
            String name = folder;
            if (!prefix.isEmpty()) {
                if (!name.startsWith(prefix)) {
                    continue;
                }
                name = name.substring(prefix.length());
            }
            int slash = name.indexOf('/');
            if (slash >= 0) {
                name = name.substring(0, slash);
            }
            if (name.isEmpty()) {
                continue;
            }
            if (filter.isEmpty() || name.toLowerCase(Locale.ROOT).contains(filter)) {
                folders.add(name);
            }
        }
        for (VaultItem item : all) {
            String name = item.name;
            if (!prefix.isEmpty() && !name.startsWith(prefix)) {
                continue;
            }
            String rest = name.substring(prefix.length());
            int slash = rest.indexOf('/');
            if (slash >= 0) {
                String folder = rest.substring(0, slash);
                if (!folder.isEmpty() && (filter.isEmpty() || folder.toLowerCase(Locale.ROOT).contains(filter))) {
                    folders.add(folder);
                }
            } else {
                if (rest.isEmpty() || (!filter.isEmpty() && !rest.toLowerCase(Locale.ROOT).contains(filter))) {
                    continue;
                }
                files.add(new Entry(item));
            }
        }
        for (String folder : folders) {
            String full = currentPath.isEmpty() ? folder : currentPath + "/" + folder;
            model.addElement(new Entry(folder, full));
        }
        model.addAll(files);
        pathLabel.setText(displayPath());
        rewrap();
    }

    private String displayPath() {
        if (currentPath.isEmpty()) {
            return rootName;
        }
        return rootName + "   ▸   " + currentPath.replace('/', ' ');
    }

    private void rewrap() {
        JViewport viewport = scroll.getViewport();
        viewport.setView(model.isEmpty() ? emptyHint : list);
        backBtn.setEnabled(!history.isEmpty());
        upBtn.setEnabled(!currentPath.isEmpty());
    }

    private static final class BrowserRenderer extends JPanel implements ListCellRenderer<Entry> {

        private final JLabel iconLabel = new JLabel("", SwingConstants.CENTER);
        private final JLabel nameLabel = new JLabel("", SwingConstants.CENTER);

        BrowserRenderer() {
            setLayout(new BorderLayout());
            setOpaque(true);
            iconLabel.setVerticalAlignment(SwingConstants.CENTER);
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 11f));
            add(iconLabel, BorderLayout.CENTER);
            add(nameLabel, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Entry> list, Entry value,
                                                      int index, boolean selected, boolean cellHasFocus) {
            iconLabel.setIcon(value.folder ? VaultIcons.folder(ICON) : VaultIcons.forItem(value.item, ICON));
            nameLabel.setText(truncate(value.name));
            nameLabel.setToolTipText(value.name);
            if (selected) {
                setBackground(list.getSelectionBackground());
                nameLabel.setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                nameLabel.setForeground(list.getForeground());
            }
            setPreferredSize(new Dimension(CELL_W, CELL_H));
            return this;
        }

        private static String truncate(String name) {
            if (name.length() <= 16) {
                return name;
            }
            return name.substring(0, 13) + "…";
        }
    }
}