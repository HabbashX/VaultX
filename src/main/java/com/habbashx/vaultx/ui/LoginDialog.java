package com.habbashx.vaultx.ui;

import com.habbashx.vaultx.core.CryptoUtils;
import com.habbashx.vaultx.core.VaultManager;
import com.habbashx.vaultx.core.WrongPasswordException;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

public final class LoginDialog extends JFrame {

    private static final String KEY_RECENT_DIR = "recentDir";
    private static final String KEY_LAST_VAULT = "lastVault";
    private static final String KEY_RECENT_VAULTS = "recentVaults";

    private final Preferences prefs = Preferences.userNodeForPackage(LoginDialog.class);

    private final JTextField createName = new JTextField("My Vault");
    private final JTextField createLocation = new JTextField(28);
    private final JPasswordField createPassword = new JPasswordField(28);
    private final JPasswordField createConfirm = new JPasswordField(28);
    private final JLabel strength = new JLabel("Choose a strong master password.");

    private final JTextField openLocation = new JTextField(28);
    private final JPasswordField openPassword = new JPasswordField(28);
    private final JComboBox<String> recentVaults = new JComboBox<>();

    public LoginDialog() {
        super("VaultX — Secure Encrypted Vault");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        Branding.installWindowIcon(this);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Create New Vault", buildCreatePanel());
        tabs.addTab("Open Vault", buildOpenPanel());

        JLabel logo = new JLabel(Branding.logo(260));
        logo.setHorizontalAlignment(SwingConstants.CENTER);
        logo.setBorder(BorderFactory.createEmptyBorder(18, 18, 6, 18));
        JPanel root = new JPanel(new BorderLayout());
        root.add(logo, BorderLayout.NORTH);
        root.add(tabs, BorderLayout.CENTER);
        setContentPane(root);

        pack();
        setLocationRelativeTo(null);

        createPassword.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                strength();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                strength();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                strength();
            }
        });
        strength.updateUI();
    }

    private JPanel buildCreatePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("Vault name:"), c);
        c.gridx = 1;
        panel.add(createName, c);

        c.gridx = 0;
        c.gridy = 1;
        panel.add(new JLabel("Location:"), c);
        c.gridx = 1;
        createLocation.setText(prefs.get(KEY_RECENT_DIR, System.getProperty("user.dir")));
        JPanel locRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        locRow.add(createLocation);
        JButton browse = new JButton("Browse…");
        browse.addActionListener(e -> browse(parentDirFor(createLocation)));
        locRow.add(browse);
        panel.add(locRow, c);

        c.gridx = 0;
        c.gridy = 2;
        panel.add(new JLabel("Master password:"), c);
        c.gridx = 1;
        panel.add(createPassword, c);

        c.gridx = 0;
        c.gridy = 3;
        panel.add(new JLabel("Confirm password:"), c);
        c.gridx = 1;
        panel.add(createConfirm, c);

        JCheckBox show = new JCheckBox("Show password");
        show.addActionListener(e -> {
            Character echo = show.isSelected() ? (char) 0 : '\u2022';
            for (JPasswordField f : new JPasswordField[]{createPassword, createConfirm}) {
                f.setEchoChar(echo);
            }
        });
        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 1;
        panel.add(show, c);

        c.gridx = 0;
        c.gridy = 5;
        panel.add(strength, c);

        JButton create = new JButton("Create Vault");
        create.addActionListener(e -> doCreate());
        c.gridx = 1;
        c.gridy = 6;
        c.anchor = GridBagConstraints.EAST;
        panel.add(create, c);

        JLabel hint = new JLabel("All files are encrypted with AES-256-GCM using a key derived from your master password (PBKDF2).");
        hint.setForeground(Color.GRAY);
        c.gridx = 0;
        c.gridy = 7;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        panel.add(hint, c);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        wrap.add(panel, BorderLayout.NORTH);
        return wrap;
    }

    private JPanel buildOpenPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("Vault folder:"), c);
        c.gridx = 1;
        String last = prefs.get(KEY_LAST_VAULT, "");
        if (!last.isEmpty() && Files.isDirectory(Paths.get(last))) {
            openLocation.setText(last);
        } else {
            openLocation.setText(prefs.get(KEY_RECENT_DIR, System.getProperty("user.dir")));
        }
        JPanel locRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        locRow.add(openLocation);
        JButton browse = new JButton("Browse…");
        browse.addActionListener(e -> browse(pathFor(openLocation)));
        locRow.add(browse);
        panel.add(locRow, c);

        c.gridx = 0;
        c.gridy = 1;
        panel.add(new JLabel("Master password:"), c);
        c.gridx = 1;
        panel.add(openPassword, c);

        populateRecentVaults();
        recentVaults.addActionListener(e -> {
            Object selected = recentVaults.getSelectedItem();
            if (selected != null && !selected.toString().isEmpty()) {
                openLocation.setText(selected.toString());
                openPassword.requestFocusInWindow();
            }
        });
        c.gridx = 0;
        c.gridy = 2;
        panel.add(new JLabel("Recent vaults:"), c);
        c.gridx = 1;
        panel.add(recentVaults, c);

        JButton open = new JButton("Unlock Vault");
        open.addActionListener(e -> doOpen());
        c.gridx = 1;
        c.gridy = 3;
        c.anchor = GridBagConstraints.EAST;
        panel.add(open, c);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        wrap.add(panel, BorderLayout.NORTH);
        return wrap;
    }

    private Path parentDirFor(JTextField field) {
        try {
            Path p = Paths.get(field.getText().trim());
            if (Files.isDirectory(p)) {
                return p;
            }
            return p.toAbsolutePath().getParent();
        } catch (Exception e) {
            return Paths.get(System.getProperty("user.home"));
        }
    }

    private Path pathFor(JTextField field) {
        return Paths.get(field.getText().trim());
    }

    private void browse(Path initial) {
        JFileChooser fc = new JFileChooser(initial == null ? null : initial.toFile());
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Choose folder");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION && fc.getSelectedFile() != null) {
            createLocation.setText(fc.getSelectedFile().getAbsolutePath());
            openLocation.setText(fc.getSelectedFile().getAbsolutePath());
            prefs.put(KEY_RECENT_DIR, fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void strength() {
        char[] pwd = createPassword.getPassword();
        int len = pwd.length;
        boolean upper = false, lower = false, digit = false, symbol = false;
        for (char ch : pwd) {
            if (Character.isUpperCase(ch)) upper = true;
            else if (Character.isLowerCase(ch)) lower = true;
            else if (Character.isDigit(ch)) digit = true;
            else symbol = true;
        }
        int score = 0;
        if (len >= 8) score++;
        if (len >= 12) score++;
        if ((upper && lower && digit)) score++;
        if (symbol && len >= 10) score++;
        if (len >= 16) score++;
        if (upper && lower && digit && symbol && len >= 12) score += 2;
        strength.setForeground(new Color(0x666666));
        if (score <= 1) {
            strength.setText("Weak password. Use at least 8 characters with mixed cases, numbers and symbols.");
            strength.setForeground(new Color(0xC62828));
        } else if (score == 2 || score == 3) {
            strength.setText("Fair password.");
            strength.setForeground(new Color(0xF9A825));
        } else {
            strength.setText("Strong password.");
            strength.setForeground(new Color(0x2E7D32));
        }
    }

    private void doCreate() {
        String name = createName.getText().trim();
        if (name.isEmpty()) {
            error("Please give your vault a name.");
            return;
        }
        String loc = createLocation.getText().trim();
        if (loc.isEmpty()) {
            error("Please choose a location for the vault.");
            return;
        }
        Path target = Paths.get(loc);
        char[] pwd = createPassword.getPassword();
        char[] confirm = createConfirm.getPassword();
        if (pwd.length < 8) {
            error("Master password must be at least 8 characters long.");
            wipe(pwd, confirm);
            return;
        }
        if (!java.util.Arrays.equals(pwd, confirm)) {
            error("Passwords do not match.");
            wipe(pwd, confirm);
            return;
        }
        if (Files.isDirectory(target.resolve(VaultManager.CONFIG_DIR))) {
            error("A vault already exists at this location.");
            wipe(pwd, confirm);
            return;
        }
        final char[] pwdForWorker = pwd;
        setGlassBusy(true);
        new SwingWorker<VaultManager, Void>() {
            @Override
            protected VaultManager doInBackground() throws Exception {
                return VaultManager.create(target, name, pwdForWorker);
            }

            @Override
            protected void done() {
                setGlassBusy(false);
                try {
                    VaultManager manager = get();
                    prefs.put(KEY_RECENT_DIR, target.getParent() == null ? loc : target.getParent().toString());
                    rememberVault(target);
                    wipe(pwd);
                    wipe(confirm);
                    openVault(manager);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    wipe(pwd, confirm);
                } catch (ExecutionException e) {
                    wipe(pwd, confirm);
                    error("Could not create the vault: " + cause(e).getMessage());
                }
            }
        }.execute();
    }

    private void doOpen() {
        String loc = openLocation.getText().trim();
        if (loc.isEmpty()) {
            error("Please select the vault folder.");
            return;
        }
        Path target = Paths.get(loc);
        char[] pwd = openPassword.getPassword();
        if (pwd.length == 0) {
            error("Enter your master password.");
            return;
        }
        final char[] pwdForWorker = pwd;
        setGlassBusy(true);
        new SwingWorker<VaultManager, Void>() {
            @Override
            protected VaultManager doInBackground() throws Exception {
                return VaultManager.open(target, pwdForWorker);
            }

            @Override
            protected void done() {
                setGlassBusy(false);
                try {
                    VaultManager manager = get();
                    prefs.put(KEY_RECENT_DIR, target.toString());
                    rememberVault(target);
                    wipe(pwd);
                    openVault(manager);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    wipe(pwd);
                } catch (ExecutionException e) {
                    wipe(pwd);
                    openPassword.selectAll();
                    openPassword.requestFocusInWindow();
                    Throwable cause = cause(e);
                    if (cause instanceof WrongPasswordException) {
                        error(cause.getMessage());
                    } else {
                        error("Could not open the vault: " + cause.getMessage());
                    }
                }
            }
        }.execute();
    }

    private void openVault(VaultManager manager) {
        dispose();
        MainFrame frame = new MainFrame(manager);
        frame.setVisible(true);
    }

    private List<String> recentVaults() {
        String saved = prefs.get(KEY_RECENT_VAULTS, "");
        if (saved.isBlank()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String path : saved.split("\n")) {
            if (!path.isBlank()) {
                result.add(path.trim());
            }
        }
        return result;
    }

    private void populateRecentVaults() {
        recentVaults.removeAllItems();
        List<String> vaults = recentVaults();
        recentVaults.addItem("");
        for (String path : vaults) {
            if (Files.isDirectory(Paths.get(path))) {
                recentVaults.addItem(path);
            }
        }
    }

    private void rememberVault(Path vaultDir) {
        if (vaultDir == null) {
            return;
        }
        List<String> list = recentVaults();
        String entry = vaultDir.toString();
        list.remove(entry);
        list.add(0, entry);
        while (list.size() > 8) {
            list.remove(list.size() - 1);
        }
        prefs.put(KEY_RECENT_VAULTS, String.join("\n", list));
        prefs.put(KEY_LAST_VAULT, entry);
    }

    private void setGlassBusy(boolean busy) {
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : null);
    }

    private Throwable cause(ExecutionException e) {
        return e.getCause() != null ? e.getCause() : e;
    }

    private void wipe(char[]... passwords) {
        for (char[] p : passwords) {
            CryptoUtils.wipe(p);
        }
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(LoginDialog.this, message, "VaultX", JOptionPane.ERROR_MESSAGE);
    }
}