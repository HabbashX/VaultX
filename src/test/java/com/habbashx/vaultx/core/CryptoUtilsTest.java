package com.habbashx.vaultx.core;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CryptoUtilsTest {

    private static byte[] key() {
        byte[] key = new byte[CryptoUtils.KEY_BYTES];
        new Random(7).nextBytes(key);
        return key;
    }

    @Test
    void deriveKeyIsDeterministicWithSameSalt() throws Exception {
        byte[] salt = CryptoUtils.randomBytes(CryptoUtils.SALT_BYTES);
        SecretKey a = CryptoUtils.deriveKey("password123".toCharArray(), salt);
        SecretKey b = CryptoUtils.deriveKey("password123".toCharArray(), salt);
        assertArrayEquals(a.getEncoded(), b.getEncoded());

        byte[] salt2 = CryptoUtils.randomBytes(CryptoUtils.SALT_BYTES);
        SecretKey c = CryptoUtils.deriveKey("password123".toCharArray(), salt2);
        assertFalse(Arrays.equals(a.getEncoded(), c.getEncoded()));
    }

    @Test
    void hkdfIsDeterministicAndContextSensitive() throws Exception {
        byte[] salt = CryptoUtils.randomBytes(CryptoUtils.SALT_BYTES);
        SecretKey master = CryptoUtils.deriveKey("password123".toCharArray(), salt);
        byte[] a = CryptoUtils.hkdf(master, "context-a");
        byte[] b = CryptoUtils.hkdf(master, "context-a");
        byte[] c = CryptoUtils.hkdf(master, "context-b");
        assertArrayEquals(a, b);
        assertFalse(Arrays.equals(a, c));
        assertEquals(CryptoUtils.KEY_BYTES, a.length);
    }

    @Test
    void aesGcmRoundTrip() throws Exception {
        byte[] nonce = CryptoUtils.randomBytes(CryptoUtils.NONCE_BYTES);
        byte[] plain = "hello vault - AES roundtrip".getBytes();
        byte[] ct = CryptoUtils.aesGcmEncrypt(key(), nonce, plain);
        assertFalse(Arrays.equals(plain, ct));
        assertArrayEquals(plain, CryptoUtils.aesGcmDecrypt(key(), nonce, ct));
    }

    @Test
    void aesGcmDetectsTampering() throws Exception {
        byte[] nonce = CryptoUtils.randomBytes(CryptoUtils.NONCE_BYTES);
        byte[] plain = "tamper me".getBytes();
        byte[] ct = CryptoUtils.aesGcmEncrypt(key(), nonce, plain);
        ct[6] ^= 0x01;
        assertThrows(GeneralSecurityException.class, () -> CryptoUtils.aesGcmDecrypt(key(), nonce, ct));
    }

    @Test
    void streamRoundTripLargeFile() throws Exception {
        byte[] data = new byte[2_500_003];
        new Random(11).nextBytes(data);

        ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
        CryptoUtils.streamEncrypt(key(), new ByteArrayInputStream(data), encrypted, null, data.length);

        ByteArrayOutputStream decrypted = new ByteArrayOutputStream();
        CryptoUtils.streamDecrypt(key(), new java.io.ByteArrayInputStream(encrypted.toByteArray()),
                decrypted, null, data.length);

        assertArrayEquals(data, decrypted.toByteArray());
    }

    @Test
    void streamRoundTripEmptyFile() throws Exception {
        byte[] data = new byte[0];
        ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
        CryptoUtils.streamEncrypt(key(), new ByteArrayInputStream(data), encrypted, null, 0);

        ByteArrayOutputStream decrypted = new ByteArrayOutputStream();
        CryptoUtils.streamDecrypt(key(), new java.io.ByteArrayInputStream(encrypted.toByteArray()),
                decrypted, null, 0);

        assertArrayEquals(data, decrypted.toByteArray());
    }
}