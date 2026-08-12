package com.habbashx.vaultx.core;

import java.util.ArrayList;
import java.util.List;

public class Manifest {

    public int version = 1;
    public String name;
    public long createdAt;
    public long updatedAt;
    public List<VaultItem> items = new ArrayList<>();
    public List<String> folders = new ArrayList<>();

    public Manifest() {
    }
}