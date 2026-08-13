package com.habbashx.vaultx.ui;

import com.habbashx.vaultx.core.FileTypes;
import com.habbashx.vaultx.core.VaultItem;
import org.jetbrains.annotations.NotNull;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.TransferHandler;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
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

    public interface DropHandler {
        void importDropped(List<Path> paths, String targetFolder);

        void moveDropped(List<VaultItem> items, String targetFolder);
    }

    public static final class Entry {
        final VaultItem item;
        final String name;
        final String fullPath;
        final boolean folder;
        final String folderPath;

        Entry(String folderName, String folderPath) {
            this.item = null;
            this.name = folderName;
            this.fullPath = folderPath;
            this.folder = true;
            this.folderPath = folderPath;
        }

        Entry(@NotNull VaultItem file, String displayName) {
            this.item = file;
            this.name = displayName;
            this.fullPath = file.name;
            this.folder = false;
            this.folderPath = null;
        }
    }

    private static final int ICON = 40;
    private static final int CELL_W = 120;
    private static final int CELL_H = 92;

    private static final DataFlavor ITEM_FLAVOR = new DataFlavor(List.class, "Vault items");
    private static final String[] TYPE_OPTIONS = {
            "All types", "Images", "Audio", "Video", "PDF", "Text", "Other"
    };
    private static final String[] AGE_OPTIONS = {
            "Any time", "Today", "7 days", "30 days", "90 days", "1 year"
    };
    private static final long[] AGE_DAYS = {0, 1, 7, 30, 90, 365};

    private final DefaultListModel<Entry> model = new DefaultListModel<>();
    private final JList<Entry> list = new JList<>(model);
    private final JButton backBtn = new JButton("← Back");
    private final JButton upBtn = new JButton("↑ Up");
    private final JLabel pathLabel = new JLabel(" ");
    private final JLabel emptyHint = new JLabel("This folder is empty.", SwingConstants.CENTER);
    private final JScrollPane scroll = new JScrollPane(list);
    private final JComboBox<String> typeCombo = new JComboBox<>(TYPE_OPTIONS);
    private final JButton filterBtn = new JButton("Size/Date…");
    private final JButton clearBtn = new JButton("Clear");

    private final Deque<String> history = new ArrayDeque<>();
    private List<VaultItem> all = List.of();
    private List<String> folders = List.of();
    private String rootName = "Vault";
    private String currentPath = "";
    private String filter = "";
    private FileTypes.Category categoryFilter = null;
    private long sizeMin = -1;
    private long sizeMax = -1;
    private long newerCutoff = 0;
    private OpenAction openAction = item -> {
    };
    private Runnable deleteAction = () -> {
    };
    private DropHandler dropHandler = new DropHandler() {
        @Override
        public void importDropped(List<Path> paths, String targetFolder) {
        }

        @Override
        public void moveDropped(List<VaultItem> items, String targetFolder) {
        }
    };
    private Runnable onClearFilters = () -> {
    };

    public VaultBrowser() {
        super(new BorderLayout(0, 0));

        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(0);
        list.setFixedCellWidth(CELL_W);
        list.setFixedCellHeight(CELL_H);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setCellRenderer(new BrowserRenderer());
        list.setDragEnabled(true);
        list.setDropMode(javax.swing.DropMode.ON);
        list.setTransferHandler(new VaultTransferHandler());
        emptyHint.setTransferHandler(new EmptyDropHandler());

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

        typeCombo.setFocusable(false);
        typeCombo.addActionListener(e -> {
            categoryFilter = categoryForIndex(typeCombo.getSelectedIndex());
            rebuild();
        });
        filterBtn.setFocusable(false);
        filterBtn.addActionListener(e -> showFilterDialog());
        clearBtn.setFocusable(false);
        clearBtn.setEnabled(false);
        clearBtn.addActionListener(e -> clearFilters());

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        nav.add(backBtn);
        nav.add(upBtn);
        nav.add(pathLabel);
        nav.add(new javax.swing.JSeparator(SwingConstants.VERTICAL));
        nav.add(new JLabel("Type:"));
        nav.add(typeCombo);
        nav.add(filterBtn);
        nav.add(clearBtn);

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

    public void setDropHandler(DropHandler handler) {
        this.dropHandler = handler == null ? new DropHandler() {
            @Override
            public void importDropped(List<Path> paths, String targetFolder) {
            }

            @Override
            public void moveDropped(List<VaultItem> items, String targetFolder) {
            }
        } : handler;
    }

    public void setOnClearFilters(Runnable action) {
        this.onClearFilters = action == null ? () -> {
        } : action;
    }

    public void addSelectionListener(ListSelectionListener listener) {
        list.getSelectionModel().addListSelectionListener(listener);
    }

    public JList<Entry> entryList() {
        return list;
    }

    public JLabel emptyHint() {
        return emptyHint;
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

    public void openFolder(String folderPath) {
        if (folderPath == null || folderPath.isBlank()) {
            return;
        }
        history.push(currentPath);
        currentPath = folderPath;
        rebuild();
        list.requestFocusInWindow();
    }

    public void selectAll() {
        if (model.size() > 0) {
            list.getSelectionModel().setSelectionInterval(0, model.size() - 1);
            list.requestFocusInWindow();
        }
    }

    public void openFilterDialog() {
        showFilterDialog();
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

    private boolean advanced() {
        return !filter.isEmpty() || categoryFilter != null
                || sizeMin >= 0 || sizeMax >= 0 || newerCutoff > 0;
    }

    private void rebuild() {
        if (advanced()) {
            buildSearchResults();
        } else {
            buildFolderView();
        }
        clearBtn.setEnabled(advanced());
    }

    private void buildFolderView() {
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
                files.add(new Entry(item, rest));
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

    private void buildSearchResults() {
        model.clear();
        List<Entry> files = new ArrayList<>();
        for (VaultItem item : all) {
            if (item.trashed) {
                continue;
            }
            if (categoryFilter != null && item.category() != categoryFilter) {
                continue;
            }
            if (sizeMin >= 0 && item.size < sizeMin) {
                continue;
            }
            if (sizeMax >= 0 && item.size > sizeMax) {
                continue;
            }
            if (newerCutoff > 0 && item.createdAt < newerCutoff) {
                continue;
            }
            if (!filter.isEmpty() && !item.name.toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            String dir = parentOf(item.name);
            String base = baseOf(item.name);
            String display = dir.isEmpty() ? base : dir + "/" + base;
            files.add(new Entry(item, display));
        }
        files.sort(Comparator.comparing(e -> e.name.toLowerCase(Locale.ROOT)));
        model.addAll(files);
        pathLabel.setText(displayPath() + "   •   " + model.size() + " match(es)");
        rewrap();
    }

    private String baseOf(String name) {
        int slash = name.lastIndexOf('/');
        return slash < 0 ? name : name.substring(slash + 1);
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
        emptyHint.setText(advanced()
                ? "No matching items."
                : "This folder is empty - drag and drop files here to add them.");
        backBtn.setEnabled(!history.isEmpty());
        upBtn.setEnabled(!currentPath.isEmpty());
    }

    private void showFilterDialog() {
        JTextField minField = new JTextField(sizeMin >= 0 ? String.valueOf(sizeMin / 1048576.0) : "", 8);
        JTextField maxField = new JTextField(sizeMax >= 0 ? String.valueOf(sizeMax / 1048576.0) : "", 8);
        JComboBox<String> age = new JComboBox<>(AGE_OPTIONS);
        int ageIndex = 0;
        if (newerCutoff > 0) {
            for (int i = 1; i < AGE_DAYS.length; i++) {
                if (System.currentTimeMillis() - newerCutoff >= AGE_DAYS[i] * 86_400_000L
                        && System.currentTimeMillis() - newerCutoff < AGE_DAYS[i - 1] * 86_400_000L + 1) {
                    ageIndex = i;
                    break;
                }
            }
        }
        age.setSelectedIndex(ageIndex);
        JPanel panel = new JPanel(new GridLayout(3, 2, 6, 6));
        panel.add(new JLabel("Minimum size (MB):"));
        panel.add(minField);
        panel.add(new JLabel("Maximum size (MB):"));
        panel.add(maxField);
        panel.add(new JLabel("Created within:"));
        panel.add(age);
        int result = JOptionPane.showConfirmDialog(this, panel, "Size and date filter",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        sizeMin = parseMb(minField.getText());
        sizeMax = parseMb(maxField.getText());
        long days = AGE_DAYS[Math.max(0, Math.min(AGE_DAYS.length - 1, age.getSelectedIndex()))];
        newerCutoff = days <= 0 ? 0 : System.currentTimeMillis() - days * 86_400_000L;
        rebuild();
    }

    private static long parseMb(String text) {
        if (text == null || text.isBlank()) {
            return -1;
        }
        try {
            double mb = Double.parseDouble(text.trim());
            return mb < 0 ? -1 : (long) (mb * 1048576L);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void clearFilters() {
        categoryFilter = null;
        sizeMin = -1;
        sizeMax = -1;
        newerCutoff = 0;
        typeCombo.setSelectedIndex(0);
        onClearFilters.run();
        rebuild();
    }

    private static FileTypes.Category categoryForIndex(int index) {
        return switch (index) {
            case 1 -> FileTypes.Category.IMAGE;
            case 2 -> FileTypes.Category.AUDIO;
            case 3 -> FileTypes.Category.VIDEO;
            case 4 -> FileTypes.Category.PDF;
            case 5 -> FileTypes.Category.TEXT;
            case 6 -> FileTypes.Category.OTHER;
            default -> null;
        };
    }

    private String targetFolderAt(TransferHandler.DropLocation dropLocation) {
        if (dropLocation instanceof JList.DropLocation loc) {
            int index = loc.getIndex();
            if (index >= 0 && index < model.size()) {
                Entry entry = model.get(index);
                if (entry.folder && entry.folderPath != null) {
                    return entry.folderPath;
                }
            }
        }
        return currentPath;
    }

    private static final class ItemTransferable implements Transferable {
        private final List<VaultItem> items;

        ItemTransferable(List<VaultItem> items) {
            this.items = items;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{ITEM_FLAVOR};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return ITEM_FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (ITEM_FLAVOR.equals(flavor)) {
                return items;
            }
            throw new UnsupportedFlavorException(flavor);
        }
    }

    private final class VaultTransferHandler extends TransferHandler {
        @Override
        public int getSourceActions(JComponent c) {
            return TransferHandler.COPY_OR_MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            List<VaultItem> items = new ArrayList<>();
            for (int index : list.getSelectedIndices()) {
                Entry entry = model.get(index);
                if (!entry.folder && entry.item != null) {
                    items.add(entry.item);
                }
            }
            return new ItemTransferable(items);
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                    || support.isDataFlavorSupported(ITEM_FLAVOR);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            String target = targetFolderAt(support.getDropLocation());
            try {
                if (support.isDataFlavorSupported(ITEM_FLAVOR)) {
                    Object data = support.getTransferable().getTransferData(ITEM_FLAVOR);
                    if (data instanceof List<?> raw) {
                        List<VaultItem> items = new ArrayList<>();
                        for (Object o : raw) {
                            if (o instanceof VaultItem vi) {
                                items.add(vi);
                            }
                        }
                        dropHandler.moveDropped(items, target);
                        return true;
                    }
                }
                if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    List<Path> paths = pathsFrom(support);
                    if (paths.isEmpty()) {
                        return false;
                    }
                    dropHandler.importDropped(paths, target);
                    return true;
                }
            } catch (Exception ignored) {
                return false;
            }
            return false;
        }
    }

    private static List<Path> pathsFrom(TransferHandler.TransferSupport support) {
        List<Path> paths = new ArrayList<>();
        try {
            Object data = support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
            if (data instanceof List<?> raw) {
                for (Object o : raw) {
                    if (o instanceof File f) {
                        paths.add(f.toPath());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return paths;
    }

    private final class EmptyDropHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDrop() && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            List<Path> paths = pathsFrom(support);
            if (paths.isEmpty()) {
                return false;
            }
            dropHandler.importDropped(paths, currentPath);
            return true;
        }
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
            nameLabel.setToolTipText(value.folder ? value.name : value.fullPath);
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
