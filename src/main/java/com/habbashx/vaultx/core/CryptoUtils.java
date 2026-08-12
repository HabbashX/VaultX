package com.habbashx.vaultx.core;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public final class CryptoUtils {

    public static final int SALT_BYTES = 16;
    public static final int NONCE_BYTES = 12;
    public static final int KEY_BYTES = 32;
    public static final int GCM_TAG_BITS = 128;
    public static final int PBKDF2_ITERATIONS = 600_000;

    private static final int BUFFER = 8192;
    private static final SecureRandom RNG = new SecureRandom();

    private CryptoUtils() {
    }

    public static byte @NotNull [] randomBytes(int n) {
        byte[] b = new byte[n];
        RNG.nextBytes(b);
        return b;
    }

    public static String b64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public static byte[] unb64(String data) {
        return Base64.getDecoder().decode(data);
    }

    @Contract("_, _ -> new")
    public static @NotNull SecretKey deriveKey(char[] password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BYTES * 8);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] derived = factory.generateSecret(spec).getEncoded();
        spec.clearPassword();
        return new SecretKeySpec(derived, "AES");
    }

    public static byte @NotNull [] hkdf(@NotNull SecretKey ikm, @NotNull String info) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(new byte[KEY_BYTES], "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm.getEncoded());
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] infoBytes = info.getBytes(StandardCharsets.UTF_8);
        mac.update(infoBytes);
        mac.update((byte) 1);
        return Arrays.copyOf(mac.doFinal(), KEY_BYTES);
    }

    private static @NotNull Cipher gcm(int mode, byte[] key, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
        return cipher;
    }

    public static byte[] aesGcmEncrypt(byte[] key, byte[] nonce, byte[] plain) throws GeneralSecurityException {
        return gcm(Cipher.ENCRYPT_MODE, key, nonce).doFinal(plain);
    }

    public static byte[] aesGcmDecrypt(byte[] key, byte[] nonce, byte[] ciphertext) throws GeneralSecurityException {
        return gcm(Cipher.DECRYPT_MODE, key, nonce).doFinal(ciphertext);
    }

    public static void streamEncrypt(byte[] key, InputStream in, OutputStream out, Progress progress, long total)
            throws IOException {
        byte[] nonce = randomBytes(NONCE_BYTES);
        Cipher cipher;
        try {
            cipher = gcm(Cipher.ENCRYPT_MODE, key, nonce);
        } catch (GeneralSecurityException e) {
            throw new IOException("Unable to initialise AES-GCM", e);
        }
        out.write(nonce);
        try (CipherOutputStream cos = new CipherOutputStream(out, cipher)) {
            pipe(in, cos, progress, total);
        }
    }

    public static void streamDecrypt(byte[] key, InputStream in, OutputStream out, Progress progress, long total)
            throws IOException {
        byte[] nonce = new byte[NONCE_BYTES];
        readFully(in, nonce);
        Cipher cipher;
        try {
            cipher = gcm(Cipher.DECRYPT_MODE, key, nonce);
        } catch (GeneralSecurityException e) {
            throw new IOException("Unable to initialise AES-GCM", e);
        }
        try (CipherInputStream cis = new CipherInputStream(in, cipher)) {
            pipe(cis, out, progress, total);
        } catch (IOException e) {
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof javax.crypto.AEADBadTagException) {
                    throw new IOException("Data integrity check failed (wrong key or corrupted vault file)", cause);
                }
                cause = cause.getCause();
            }
            throw e;
        }
    }

    private static void pipe(@NotNull InputStream in, OutputStream out, Progress progress, long total) throws IOException {
        byte[] buffer = new byte[BUFFER];
        long done = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            done += read;
            if (progress != null) {
                progress.report(done, total);
            }
        }
    }

    private static void readFully(InputStream in, byte @NotNull [] target) throws IOException {
        int offset = 0;
        while (offset < target.length) {
            int read = in.read(target, offset, target.length - offset);
            if (read == -1) {
                throw new IOException("Unexpected end of stream reading header");
            }
            offset += read;
        }
    }

    public static void wipe(char[] data) {
        if (data != null) {
            Arrays.fill(data, '\0');
        }
    }

    public static void wipe(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }
}