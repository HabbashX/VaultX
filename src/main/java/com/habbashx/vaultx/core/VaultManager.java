package com.habbashx.vaultx.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.SecretKey;
import javax.crypto.AEADBadTagException;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class VaultManager implements AutoCloseable {

    public static final String CONFIG_DIR = ".vaultx";
    private static final String BLOBS_DIR = "blobs";
    private static final String SALT_FILE = "salt.bin";
    private static final String MANIFEST_FILE = "manifest.enc";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path vaultDir;
    private final Path projectDir;
    private final Path blobsDir;
    private final Path saltFile;
    private final Path manifestFile;

    private Manifest manifest;
    private Session session;

    private static final class Session {
        final SecretKey master;
        final byte[] keyManifest;
        final byte[] keyFile;

        Session(SecretKey master) throws GeneralSecurityException {
            this.master = master;
            this.keyManifest = CryptoUtils.hkdf(master, "vaultx-manifest");
            this.keyFile = CryptoUtils.hkdf(master, "vaultx-files");
        }
    }

    private VaultManager(@NotNull Path vaultDir) {
        this.vaultDir = vaultDir.toAbsolutePath().normalize();
        this.projectDir = this.vaultDir.resolve(CONFIG_DIR);
        this.blobsDir = this.vaultDir.resolve(BLOBS_DIR);
        this.saltFile = projectDir.resolve(SALT_FILE);
        this.manifestFile = projectDir.resolve(MANIFEST_FILE);
    }

    public static boolean isVault(@NotNull Path dir) {
        return Files.isDirectory(dir.resolve(CONFIG_DIR));
    }

    public static @NotNull VaultManager create(Path vaultDir, String name, char[] password)
            throws IOException, GeneralSecurityException {
        VaultManager vm = new VaultManager(vaultDir);
        if (Files.exists(vm.vaultDir) && Files.exists(vm.projectDir)) {
            throw new IOException("A vault already exists at: " + vm.vaultDir);
        }
        if (Files.exists(vm.vaultDir)) {
            try (var stream = Files.list(vm.vaultDir)) {
                if (stream.findAny().isPresent()) {
                    throw new IOException("The selected folder is not empty. Choose an empty folder.");
                }
            }
        }
        Files.createDirectories(vm.projectDir);
        Files.createDirectories(vm.blobsDir);

        vm.protectFolder();

        byte[] salt = CryptoUtils.randomBytes(CryptoUtils.SALT_BYTES);
        Files.write(vm.saltFile, salt);
        SecretKey master = CryptoUtils.deriveKey(password, salt);
        vm.session = new Session(master);

        Manifest m = new Manifest();
        m.name = (name == null || name.isBlank()) ? "My Vault" : name.trim();
        m.createdAt = System.currentTimeMillis();
        m.updatedAt = m.createdAt;
        vm.manifest = m;
        vm.saveManifest();
        return vm;
    }

    public static VaultManager open(Path vaultDir, char[] password) throws IOException {
        VaultManager vm = new VaultManager(vaultDir);
        if (!Files.isRegularFile(vm.saltFile) || !Files.isRegularFile(vm.manifestFile)) {
            throw new WrongPasswordException("No VaultX vault found at this location.");
        }
        byte[] salt;
        try {
            salt = Files.readAllBytes(vm.saltFile);
        } catch (IOException e) {
            throw new IOException("Failed to read vault key material.", e);
        }
        SecretKey master;
        try {
            master = CryptoUtils.deriveKey(password, salt);
        } catch (GeneralSecurityException e) {
            throw new IOException("Key derivation failed.", e);
        }
        try {
            vm.session = new Session(master);
            vm.manifest = vm.readManifest();
        } catch (GeneralSecurityException e) {
            vm.session = null;
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof AEADBadTagException) {
                    throw new WrongPasswordException("Incorrect master password.");
                }
                cause = cause.getCause();
            }
            throw new IOException("Vault manifest is corrupt or unreadable.", e);
        }
        return vm;
    }

    public Path vaultDir() {
        return vaultDir;
    }

    public String vaultName() {
        return manifest == null ? "" : manifest.name;
    }

    public boolean locked() {
        return session == null || manifest == null;
    }

    public void lock() {
        manifest = null;
        session = null;
    }

    @Override
    public void close() {
        lock();
    }

    public synchronized List<VaultItem> items() {
        if (manifest == null) {
            return List.of();
        }
        return new ArrayList<>(manifest.items);
    }

    public int size() {
        return manifest == null ? 0 : manifest.items.size();
    }

    public long totalSize() {
        long total = 0;
        if (manifest != null) {
            for (VaultItem item : manifest.items) {
                total += item.size;
            }
        }
        return total;
    }

    public @NotNull List<String> folders() {
        if (manifest == null || manifest.folders == null) {
            return List.of();
        }
        List<String> copy = new ArrayList<>(manifest.folders);
        copy.sort(String.CASE_INSENSITIVE_ORDER);
        return copy;
    }

    public synchronized @NotNull String createFolder(String path) throws IOException {
        ensureUnlocked();
        String folder = normalizeFolder(path);
        if (folder.isEmpty()) {
            throw new IOException("Folder name cannot be empty.");
        }
        StringBuilder current = new StringBuilder();
        for (String part : folder.split("/")) {
            if (current.length() > 0) {
                current.append('/');
            }
            current.append(part);
            if (indexOfFolder(current.toString()) < 0) {
                manifest.folders.add(current.toString());
            }
        }
        saveManifest();
        return folder;
    }

    public synchronized void deleteFolder(String path) throws IOException {
        ensureUnlocked();
        String folder = normalizeFolder(path);
        if (folder.isEmpty()) {
            throw new IOException("Cannot delete the vault root.");
        }
        if (indexOfFolder(folder) < 0) {
            throw new IOException("Folder \"" + folder + "\" no longer exists.");
        }
        List<String> doomedFolders = new ArrayList<>();
        for (String f : manifest.folders) {
            if (f.equalsIgnoreCase(folder) || isUnder(f, folder)) {
                doomedFolders.add(f);
            }
        }
        List<VaultItem> doomed = new ArrayList<>();
        for (VaultItem item : manifest.items) {
            for (String f : doomedFolders) {
                if (isUnder(item.name, f)) {
                    doomed.add(item);
                    break;
                }
            }
        }
        manifest.items.removeAll(doomed);
        manifest.folders.removeAll(doomedFolders);
        saveManifest();
        for (VaultItem item : doomed) {
            Files.deleteIfExists(blobFile(item.blobName));
        }
    }

    public synchronized void moveItems(@NotNull List<VaultItem> items, String targetFolder) throws IOException {
        ensureUnlocked();
        if (items == null || items.isEmpty()) {
            return;
        }
        String target = normalizeFolder(targetFolder);
        if (!target.isEmpty() && indexOfFolder(target) < 0) {
            throw new IOException("Target folder no longer exists.");
        }
        boolean changed = false;
        for (VaultItem item : items) {
            VaultItem current = find(item.id);
            if (current == null) {
                continue;
            }
            String oldParent = parentOf(current.name);
            if (oldParent.equalsIgnoreCase(target)) {
                continue;
            }
            String base = baseOf(current.name);
            String unique = uniqueNameInFolder(base, target);
            current.name = target.isEmpty() ? unique : target + "/" + unique;
            changed = true;
        }
        if (changed) {
            saveManifest();
        }
    }

    @Contract(pure = true)
    public @Nullable VaultItem find(String id) {
        if (manifest == null || id == null) {
            return null;
        }
        for (VaultItem item : manifest.items) {
            if (id.equals(item.id)) {
                return item;
            }
        }
        return null;
    }

    public synchronized @NotNull VaultItem importFile(@NotNull Path source, Progress progress) throws IOException {
        ensureUnlocked();
        String name = source.getFileName() == null ? "file" : source.getFileName().toString();
        long size = Files.size(source);
        VaultItem item = newItem(name);
        item.size = size;
        try (InputStream in = Files.newInputStream(source);
             OutputStream out = Files.newOutputStream(blobFile(item.blobName))) {
            byte[] dataKey = deriveDataKey(item);
            CryptoUtils.streamEncrypt(dataKey, in, out, progress == null ? Progress.noop() : progress, size);
        } catch (IOException e) {
            Files.deleteIfExists(blobFile(item.blobName));
            throw e;
        }
        manifest.items.add(item);
        saveManifest();
        return item;
    }

    public synchronized @NotNull VaultItem importItemWithName(Path source, String storedName, Progress progress) throws IOException {
        ensureUnlocked();
        long size = Files.size(source);
        VaultItem item = newItem(storedName);
        item.size = size;
        try (InputStream in = Files.newInputStream(source);
             OutputStream out = Files.newOutputStream(blobFile(item.blobName))) {
            byte[] dataKey = deriveDataKey(item);
            CryptoUtils.streamEncrypt(dataKey, in, out, progress == null ? Progress.noop() : progress, size);
        } catch (IOException e) {
            Files.deleteIfExists(blobFile(item.blobName));
            throw e;
        }
        manifest.items.add(item);
        saveManifest();
        return item;
    }

    public synchronized @NotNull VaultItem importBytes(byte @NotNull [] content, String name) throws IOException {
        ensureUnlocked();
        VaultItem item = newItem(name);
        item.size = content.length;
        try (InputStream in = new java.io.ByteArrayInputStream(content);
             OutputStream out = Files.newOutputStream(blobFile(item.blobName))) {
            byte[] dataKey = deriveDataKey(item);
            CryptoUtils.streamEncrypt(dataKey, in, out, Progress.noop(), content.length);
        } catch (IOException e) {
            Files.deleteIfExists(blobFile(item.blobName));
            throw e;
        }
        manifest.items.add(item);
        saveManifest();
        return item;
    }

    public synchronized void updateItemContent(@NotNull VaultItem item, InputStream newContent, long newSize, Progress progress)
            throws IOException {
        ensureUnlocked();
        VaultItem current = find(item.id);
        if (current == null) {
            throw new IOException("Item no longer exists in the vault.");
        }
        Path oldBlob = blobFile(current.blobName);
        Path newBlob = oldBlob.resolveSibling(oldBlob.getFileName() + ".tmp");
        Files.deleteIfExists(newBlob);
        byte[] dataKey = CryptoUtils.randomBytes(CryptoUtils.KEY_BYTES);
        byte[] keyNonce = CryptoUtils.randomBytes(CryptoUtils.NONCE_BYTES);
        try (OutputStream out = Files.newOutputStream(newBlob)) {
            CryptoUtils.streamEncrypt(dataKey, newContent, out,
                    progress == null ? Progress.noop() : progress, newSize);
        } catch (IOException e) {
            Files.deleteIfExists(newBlob);
            throw e;
        }
        current.keyNonce = CryptoUtils.b64(keyNonce);
        current.keyCipher = CryptoUtils.b64(encryptDataKey(dataKey, keyNonce));
        current.size = newSize;
        try {
            Files.move(newBlob, oldBlob, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(newBlob);
            throw e;
        }
        saveManifest();
    }

    public @NotNull Path decryptToTemp(@NotNull VaultItem item) throws IOException {
        ensureUnlocked();
        VaultItem current = find(item.id);
        if (current == null) {
            throw new IOException("Item no longer exists in the vault.");
        }
        String ext = FileTypes.ext(current.name);
        Path temp = TempFiles.newFile(ext.isEmpty() ? "" : "." + ext);
        try {
            exportTo(current, temp, Progress.noop());
        } catch (IOException e) {
            TempFiles.delete(temp);
            throw e;
        }
        return temp;
    }

    public void exportTo(@NotNull VaultItem item, Path destination, Progress progress) throws IOException {
        ensureUnlocked();
        VaultItem current = find(item.id);
        if (current == null) {
            throw new IOException("Item no longer exists in the vault.");
        }
        try (InputStream in = Files.newInputStream(blobFile(current.blobName));
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(destination))) {
            byte[] dataKey = decryptDataKey(current);
            CryptoUtils.streamDecrypt(dataKey, in, out, progress == null ? Progress.noop() : progress, current.size);
        }
    }

    public synchronized void deleteItem(@NotNull VaultItem item) throws IOException {
        ensureUnlocked();
        VaultItem current = find(item.id);
        if (current == null) {
            return;
        }
        manifest.items.remove(current);
        saveManifest();
        Files.deleteIfExists(blobFile(current.blobName));
    }

    public synchronized void renameItem(VaultItem item, String newName) throws IOException {
        ensureUnlocked();
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Name cannot be empty.");
        }
        VaultItem current = find(item.id);
        if (current == null) {
            throw new IOException("Item no longer exists in the vault.");
        }
        String parent = parentOf(current.name);
        String candidate = newName.trim();
        if (candidate.indexOf('/') < 0 && !parent.isEmpty()) {
            candidate = parent + "/" + candidate;
        }
        candidate = normalizeFolder(candidate);
        if (candidate.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty.");
        }
        String newParent = parentOf(candidate);
        String base = baseOf(candidate);
        String unique = uniqueNameInFolder(base, newParent);
        current.name = newParent.isEmpty() ? unique : newParent + "/" + unique;
        saveManifest();
    }

    public synchronized void changeVaultName(String newName) throws IOException {
        ensureUnlocked();
        if (newName == null || newName.isBlank()) {
            return;
        }
        manifest.name = newName.trim();
        saveManifest();
    }

    public synchronized void changePassword(char[] newPassword) throws IOException {
        ensureUnlocked();
        byte[] newSalt = CryptoUtils.randomBytes(CryptoUtils.SALT_BYTES);
        try {
            SecretKey newMaster = CryptoUtils.deriveKey(newPassword, newSalt);
            Session newSession = new Session(newMaster);
            for (VaultItem item : manifest.items) {
                byte[] dataKey = decryptDataKey(item);
                byte[] keyNonce = CryptoUtils.randomBytes(CryptoUtils.NONCE_BYTES);
                item.keyNonce = CryptoUtils.b64(keyNonce);
                item.keyCipher = CryptoUtils.b64(CryptoUtils.aesGcmEncrypt(newSession.keyFile, keyNonce, dataKey));
                CryptoUtils.wipe(dataKey);
            }
            Files.write(saltFile, newSalt);
            this.session = newSession;
            saveManifest();
        } catch (GeneralSecurityException e) {
            throw new IOException("Key derivation failed.", e);
        }
    }

    private @NotNull VaultItem newItem(String name) throws IOException {
        byte[] dataKey = CryptoUtils.randomBytes(CryptoUtils.KEY_BYTES);
        byte[] keyNonce = CryptoUtils.randomBytes(CryptoUtils.NONCE_BYTES);
        VaultItem item = new VaultItem();
        item.id = UUID.randomUUID().toString();
        item.name = uniqueName(name);
        item.mime = FileTypes.mime(name);
        item.createdAt = System.currentTimeMillis();
        item.keyNonce = CryptoUtils.b64(keyNonce);
        item.keyCipher = CryptoUtils.b64(encryptDataKey(dataKey, keyNonce));
        item.blobName = item.id + ".bin";
        return item;
    }

    private byte[] deriveDataKey(@NotNull VaultItem item) throws IOException {
        try {
            byte[] keyNonce = CryptoUtils.unb64(item.keyNonce);
            return CryptoUtils.aesGcmDecrypt(session.keyFile, keyNonce, CryptoUtils.unb64(item.keyCipher));
        } catch (GeneralSecurityException e) {
            throw new IOException("Could not unwrap file key.", e);
        }
    }

    private byte[] decryptDataKey(VaultItem item) throws IOException {
        return deriveDataKey(item);
    }

    private byte[] encryptDataKey(byte[] dataKey, byte[] keyNonce) throws IOException {
        try {
            return CryptoUtils.aesGcmEncrypt(session.keyFile, keyNonce, dataKey);
        } catch (GeneralSecurityException e) {
            throw new IOException("Could not wrap file key.", e);
        }
    }

    @Contract(pure = true)
    private @NotNull Path blobFile(String blobName) {
        return blobsDir.resolve(blobName);
    }

    private void saveManifest() throws IOException {
        try {
            manifest.updatedAt = System.currentTimeMillis();
            byte[] json = GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8);
            byte[] nonce = CryptoUtils.randomBytes(CryptoUtils.NONCE_BYTES);
            byte[] ct = CryptoUtils.aesGcmEncrypt(session.keyManifest, nonce, json);
            byte[] out = new byte[CryptoUtils.NONCE_BYTES + ct.length];
            System.arraycopy(nonce, 0, out, 0, CryptoUtils.NONCE_BYTES);
            System.arraycopy(ct, 0, out, CryptoUtils.NONCE_BYTES, ct.length);
            Files.write(manifestFile, out);
        } catch (GeneralSecurityException e) {
            throw new IOException("Could not seal the vault manifest.", e);
        }
    }

    private @NotNull Manifest readManifest() throws IOException, GeneralSecurityException {
        byte[] all = Files.readAllBytes(manifestFile);
        if (all.length < CryptoUtils.NONCE_BYTES + 16) {
            throw new IOException("Vault manifest is too short.");
        }
        byte[] nonce = Arrays.copyOfRange(all, 0, CryptoUtils.NONCE_BYTES);
        byte[] ct = Arrays.copyOfRange(all, CryptoUtils.NONCE_BYTES, all.length);
        byte[] json = CryptoUtils.aesGcmDecrypt(session.keyManifest, nonce, ct);
        Manifest read = GSON.fromJson(new String(json, StandardCharsets.UTF_8), Manifest.class);
        if (read == null || read.items == null) {
            throw new IOException("Vault manifest is invalid.");
        }
        if (read.folders == null) {
            read.folders = new ArrayList<>();
        }
        return read;
    }

    @Contract("null -> null")
    private String uniqueName(String name) {
        if (name == null) {
            return name;
        }
        int slash = name.lastIndexOf('/');
        if (slash < 0) {
            return uniqueNameInFolder(name, "");
        }
        String folder = name.substring(0, slash);
        String base = name.substring(slash + 1);
        return folder + "/" + uniqueNameInFolder(base, folder);
    }

    private String uniqueNameInFolder(String baseName, String folder) {
        String candidate = baseName;
        int counter = 1;
        while (existsByNameInFolder(candidate, folder)) {
            String stem = baseName;
            String ext = "";
            int dot = baseName.lastIndexOf('.');
            if (dot > 0 && dot < baseName.length() - 1) {
                stem = baseName.substring(0, dot);
                ext = baseName.substring(dot);
            }
            candidate = stem + " (" + counter + ")" + ext;
            counter++;
        }
        return candidate;
    }

    private boolean existsByNameInFolder(String name, String folder) {
        if (manifest == null) {
            return false;
        }
        String full = folder.isEmpty() ? name : folder + "/" + name;
        for (VaultItem item : manifest.items) {
            if (full.equalsIgnoreCase(item.name)) {
                return true;
            }
        }
        return false;
    }

    private int indexOfFolder(String path) {
        if (manifest == null || manifest.folders == null) {
            return -1;
        }
        for (int i = 0; i < manifest.folders.size(); i++) {
            if (manifest.folders.get(i).equalsIgnoreCase(path)) {
                return i;
            }
        }
        return -1;
    }

    @Contract(pure = true)
    private static boolean isUnder(String name, @NotNull String folder) {
        if (folder.isEmpty()) {
            return true;
        }
        return name.regionMatches(true, 0, folder + "/", 0, folder.length() + 1);
    }

    @Contract(pure = true)
    private static @NotNull String parentOf(@NotNull String name) {
        int slash = name.lastIndexOf('/');
        return slash < 0 ? "" : name.substring(0, slash);
    }

    @Contract(pure = true)
    private static @NotNull String baseOf(String name) {
        int slash = name.lastIndexOf('/');
        return slash < 0 ? name : name.substring(slash + 1);
    }

    private static @NotNull String normalizeFolder(String path) throws IOException {
        if (path == null) {
            return "";
        }
        String p = path.trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        if (p.isEmpty()) {
            return "";
        }
        String[] parts = p.split("/");
        StringBuilder builder = new StringBuilder();
        for (String raw : parts) {
            String segment = raw.trim();
            if (segment.isEmpty()) {
                throw new IOException("Folder name contains an empty segment.");
            }
            if (segment.equals(".") || segment.equals("..")) {
                throw new IOException("Folder name \"" + segment + "\" is not allowed.");
            }
            if (hasIllegalChars(segment)) {
                throw new IOException("Folder name \"" + segment + "\" contains unsupported characters.");
            }
            if (!builder.isEmpty()) {
                builder.append('/');
            }
            builder.append(segment);
        }
        return builder.toString();
    }

    private static boolean hasIllegalChars(@NotNull String segment) {
        String illegal = "<>:\"\\|?*";
        for (char c : segment.toCharArray()) {
            if (c < 0x20 || illegal.indexOf(c) >= 0) {
                return true;
            }
        }
        return false;
    }

    private void ensureUnlocked() throws IOException {
        if (locked()) {
            throw new IOException("Vault is locked.");
        }
    }
    public void protectFolder() throws IOException {
        Path dir = vaultDir;
        if (!Files.isDirectory(dir)) {
            throw new IOException("Vault directory does not exist: " + dir);
        }

        if (tryUseRustDLLProtectFolder(dir)) {
            return;
        }
        
        try {
            Process p = Runtime.getRuntime().exec(
                    "cmd /c attrib +h +s +r \"" + dir.toAbsolutePath() + "\"");
            p.waitFor();
        } catch (Exception _) {
        }
        try {
            String user = System.getProperty("user.name");
            Process proc = Runtime.getRuntime().exec(
                    "cmd /c icacls \"" + dir.toAbsolutePath() + "\" /deny:" + user + ":D /T /C /Q");
            proc.waitFor();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private native boolean tryUseRustDLLProtectFolder(Path dir) throws IOException;

}