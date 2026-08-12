package com.habbashx.vaultx.core;

public class VaultItem {

    public String id;
    public String name;
    public long size;
    public String mime;
    public long createdAt;
    public String keyNonce;
    public String keyCipher;
    public String blobName;

    public VaultItem() {
    }

    public FileTypes.Category category() {
        return FileTypes.category(name);
    }
}