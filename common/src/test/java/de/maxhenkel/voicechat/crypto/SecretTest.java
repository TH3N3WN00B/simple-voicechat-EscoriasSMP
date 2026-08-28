package de.maxhenkel.voicechat.crypto;

import de.maxhenkel.voicechat.voice.common.Secret;
import org.junit.jupiter.api.Test;

import javax.crypto.BadPaddingException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class SecretTest {

    @Test
    void testEncryptDecryptRoundtrip() throws Exception {
        Secret secret = Secret.generateNewRandomSecret();
        byte[] data = new byte[1024];
        new Random(42).nextBytes(data);

        byte[] encrypted = secret.encrypt(data);
        assertTrue(encrypted.length > data.length);

        byte[] decrypted = secret.decrypt(encrypted);
        assertArrayEquals(data, decrypted);
    }

    @Test
    void testTamperedCiphertextIsRejected() throws Exception {
        Secret secret = Secret.generateNewRandomSecret();
        byte[] encrypted = secret.encrypt(new byte[64]);
        encrypted[Secret.IV_SIZE_BYTES] ^= 0x01;
        assertThrows(BadPaddingException.class, () -> secret.decrypt(encrypted));
    }

    @Test
    void testTamperedIVIsRejected() throws Exception {
        Secret secret = Secret.generateNewRandomSecret();
        byte[] encrypted = secret.encrypt("data".getBytes());
        encrypted[0] ^= 0x01;
        assertThrows(BadPaddingException.class, () -> secret.decrypt(encrypted));
    }

    @Test
    void testIVIsUniquePerEncryption() throws Exception {
        Secret secret = Secret.generateNewRandomSecret();
        Set<String> ivs = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            byte[] encrypted = secret.encrypt(new byte[]{1});
            byte[] iv = Arrays.copyOfRange(encrypted, 0, Secret.IV_SIZE_BYTES);
            ivs.add(Base64.getEncoder().encodeToString(iv));
        }
        assertEquals(100, ivs.size());
    }

    @Test
    void testRandomSecretsDiffer() {
        assertNotEquals(Secret.generateNewRandomSecret(), Secret.generateNewRandomSecret());
    }

    @Test
    void testRandomSecretHasExpectedSize() {
        assertEquals(Secret.SECRET_SIZE_BYTES, Secret.generateNewRandomSecret().getSecret().length);
    }

    @Test
    void testFromBytes() {
        byte[] secretBytes = new byte[Secret.SECRET_SIZE_BYTES];
        new Random(7).nextBytes(secretBytes);

        Secret secret = Secret.fromBytes(secretBytes);
        assertArrayEquals(secretBytes, secret.getSecret());
        assertEquals(secret, Secret.fromBytes(secretBytes));
    }
}