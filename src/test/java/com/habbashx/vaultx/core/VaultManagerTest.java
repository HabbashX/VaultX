package com.habbashx.vaultx.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VaultManagerTest {

    @TempDir
    Path tempDir;

    private static final char[] PASSWORD = "hunter2-vault".toCharArray();

    private Path writeFile(String name, int bytes) throws Exception {
        byte[] data = new byte[bytes];
        new Random(bytes).nextBytes(data);
        Path path = tempDir.resolve(name);
        Files.write(path, data);
        return path;
    }

    private Path writeText(String name, String content) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    @Test
    void createOpenImportExportRoundTrip() throws Exception {
        Path vault = tempDir.resolve("vault");
        VaultManager manager = VaultManager.create(vault, "Test", PASSWORD);

        Path source = writeFile("photo.jpg", 200_000);
        VaultItem item = manager.importFile(source, null);

        assertNotNull(item.id);
        assertEquals("photo.jpg", item.name);
        assertEquals(200_000, item.size);
        manager.close();

        VaultManager reopened = VaultManager.open(vault, PASSWORD);
        assertEquals(1, reopened.size());
        VaultItem stored = reopened.items().get(0);
        assertEquals(item.id, stored.id);

        Path dest = tempDir.resolve("photo-out.jpg");
        reopened.exportTo(stored, dest, null);
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(dest));
        reopened.close();
    }

    @Test
    void wrongPasswordFails() throws Exception {
        Path vault = tempDir.resolve("vault");
        VaultManager.create(vault, "Test", PASSWORD).close();
        assertThrows(WrongPasswordException.class,
                () -> VaultManager.open(vault, "wrong-password".toCharArray()));
    }

    @Test
    void notAVaultFails() {
        assertThrows(WrongPasswordException.class,
                () -> VaultManager.open(tempDir.resolve("missing"), PASSWORD));
    }

    @Test
    void deleteAndRenamePersist() throws Exception {
        Path vault = tempDir.resolve("vault");
        VaultManager manager = VaultManager.create(vault, "Test", PASSWORD);
        VaultItem a = manager.importFile(writeFile("a.txt", 100), null);
        VaultItem b = manager.importFile(writeFile("b.txt", 200), null);

        manager.renameItem(a, "renamed.txt");
        manager.deleteItem(b);
        manager.close();

        VaultManager reopened = VaultManager.open(vault, PASSWORD);
        assertEquals(1, reopened.size());
        assertEquals("renamed.txt", reopened.items().get(0).name);
        reopened.close();
    }

    @Test
    void changePasswordInvalidatesOldAndKeepsData() throws Exception {
        Path vault = tempDir.resolve("vault");
        VaultManager manager = VaultManager.create(vault, "Test", PASSWORD);
        VaultItem item = manager.importFile(writeText("secret.txt", "secret content"), null);
        manager.changePassword("new-hunter-pw".toCharArray());
        manager.close();

        assertThrows(WrongPasswordException.class, () -> VaultManager.open(vault, PASSWORD));

        VaultManager reopened = VaultManager.open(vault, "new-hunter-pw".toCharArray());
        Path dest = tempDir.resolve("secret-out.txt");
        reopened.exportTo(item, dest, null);
        assertEquals("secret content", Files.readString(dest));
        reopened.close();
    }

    @Test
    void updateItemContentReEncrypts() throws Exception {
        Path vault = tempDir.resolve("vault");
        VaultManager manager = VaultManager.create(vault, "Test", PASSWORD);
        VaultItem item = manager.importFile(writeText("note.txt", "version one"), null);

        byte[] v2 = "version two".getBytes(StandardCharsets.UTF_8);
        manager.updateItemContent(item, new ByteArrayInputStream(v2), v2.length, null);
        manager.close();

        VaultManager reopened = VaultManager.open(vault, PASSWORD);
        assertEquals(v2.length, reopened.items().get(0).size);
        Path dest = tempDir.resolve("note-out.txt");
        reopened.exportTo(reopened.items().get(0), dest, null);
        assertEquals("version two", Files.readString(dest));
        reopened.close();
    }

    @Test
    void duplicateNamesAreUniquified() throws Exception {
        Path vault = tempDir.resolve("vault");
        VaultManager manager = VaultManager.create(vault, "Test", PASSWORD);
        Path source = writeText("same.txt", "hello");
        manager.importFile(source, null);
        manager.importItemWithName(source, "same.txt", null);

        assertEquals(2, manager.size());
        assertEquals("same.txt", manager.items().get(0).name);
        assertEquals("same (1).txt", manager.items().get(1).name);
        manager.close();
    }

    @Test
    void previewTempIsDecryptedCopy() throws Exception {
        Path vault = tempDir.resolve("vault");
        VaultManager manager = VaultManager.create(vault, "Test", PASSWORD);
        VaultItem item = manager.importFile(writeText("log.txt", "line1\nline2\n"), null);

        Path temp = manager.decryptToTemp(item);
        assertEquals("line1\nline2\n", Files.readString(temp));
        TempFiles.delete(temp);
        manager.close();
    }
}