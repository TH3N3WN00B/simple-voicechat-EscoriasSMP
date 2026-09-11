package de.maxhenkel.voicechat.voice.common;

import io.netty.buffer.ByteBuf;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

public class Secret {

    public static final int SECRET_SIZE_BYTES = 16;
    public static final int IV_SIZE_BYTES = 12;
    public static final int TAG_LEN_BITS = 128;
    public static final String CIPHER = "AES/GCM/NoPadding";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] secret;
    private final SecretKeySpec keySpec;

    protected Secret(byte[] secret) {
        this.secret = secret;
        this.keySpec = new SecretKeySpec(secret, "AES");
    }

    public static Secret generateNewRandomSecret() {
        byte[] secret = new byte[SECRET_SIZE_BYTES];
        RANDOM.nextBytes(secret);
        return new Secret(secret);
    }

    public static Secret fromBytes(byte[] secret) {
        return new Secret(secret);
    }

    public static Secret fromBytes(ByteBuf buf) {
        byte[] secretBytes = new byte[SECRET_SIZE_BYTES];
        buf.readBytes(secretBytes);
        return Secret.fromBytes(secretBytes);
    }

    public void toBytes(ByteBuf buf) {
        buf.writeBytes(secret);
    }

    public byte[] getSecret() {
        return secret;
    }

    public SecretKeySpec getKeySpec() {
        return keySpec;
    }

    // ─── Fast IV generation ───────────────────────────────────────────────────
    // Each thread keeps a 96-bit counter seeded from SecureRandom.
    // This eliminates the global lock on SecureRandom.nextBytes() on every packet.
    // With 20 players each sending 50 packets/sec, the old code hit SecureRandom
    // contention ~1000x/sec on the server alone.
    private static final ThreadLocal<long[]> IV_COUNTER = ThreadLocal.withInitial(() -> {
        long[] c = new long[2]; // [hi 32-bits | lo 64-bits] — encodes 96 bits total
        byte[] seed = new byte[12];
        RANDOM.nextBytes(seed);
        c[0] = ((seed[0] & 0xFFL) << 24) | ((seed[1] & 0xFFL) << 16)
             | ((seed[2] & 0xFFL) << 8)  |  (seed[3] & 0xFFL);
        c[1] = ((seed[4]  & 0xFFL) << 56) | ((seed[5]  & 0xFFL) << 48)
             | ((seed[6]  & 0xFFL) << 40) | ((seed[7]  & 0xFFL) << 32)
             | ((seed[8]  & 0xFFL) << 24) | ((seed[9]  & 0xFFL) << 16)
             | ((seed[10] & 0xFFL) << 8)  |  (seed[11] & 0xFFL);
        return c;
    });

    // Per-thread reusable IV buffer — avoids allocating 12 bytes on every packet
    private static final ThreadLocal<byte[]> IV_BUFFER = ThreadLocal.withInitial(() -> new byte[IV_SIZE_BYTES]);

    /**
     * Returns a fresh 96-bit IV from an atomic per-thread counter.
     * Cryptographically safe: each thread starts from a SecureRandom seed,
     * and the counter guarantees uniqueness within the thread's lifetime
     * (wraps after 2^96 packets per thread, which is effectively never).
     * <p>
     * The returned array is a ThreadLocal buffer — callers must use it
     * immediately or copy it before the next call on the same thread.
     */
    public static byte[] generateIV() {
        long[] c = IV_COUNTER.get();
        // Increment 96-bit counter (hi 32 bits : lo 64 bits)
        c[1]++;
        if (c[1] == 0L) {
            c[0] = (c[0] + 1L) & 0xFFFFFFFFL;
        }
        byte[] iv = IV_BUFFER.get();
        iv[0]  = (byte) (c[0] >> 24);
        iv[1]  = (byte) (c[0] >> 16);
        iv[2]  = (byte) (c[0] >> 8);
        iv[3]  = (byte)  c[0];
        iv[4]  = (byte) (c[1] >> 56);
        iv[5]  = (byte) (c[1] >> 48);
        iv[6]  = (byte) (c[1] >> 40);
        iv[7]  = (byte) (c[1] >> 32);
        iv[8]  = (byte) (c[1] >> 24);
        iv[9]  = (byte) (c[1] >> 16);
        iv[10] = (byte) (c[1] >> 8);
        iv[11] = (byte)  c[1];
        return iv;
    }

    // ─── ThreadLocal Cipher pool ──────────────────────────────────────────────
    // Cipher.getInstance() synchronises on the JCA provider registry each call.
    // Under 20 players × 50 packets/sec this caused significant lock contention.
    // ThreadLocal gives each thread its own Cipher instance — safe because
    // AES-GCM Cipher.init() resets state before each use.
    private static final ThreadLocal<Cipher> ENCRYPT_CIPHER = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance(CIPHER);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new RuntimeException("Failed to create AES-GCM encrypt cipher", e);
        }
    });

    private static final ThreadLocal<Cipher> DECRYPT_CIPHER = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance(CIPHER);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new RuntimeException("Failed to create AES-GCM decrypt cipher", e);
        }
    });

    public byte[] encrypt(byte[] data) throws InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        byte[] iv = generateIV();
        Cipher cipher = ENCRYPT_CIPHER.get();
        cipher.init(Cipher.ENCRYPT_MODE, getKeySpec(), new GCMParameterSpec(TAG_LEN_BITS, iv));
        byte[] enc = cipher.doFinal(data);
        // Write IV + ciphertext in a single allocation — avoids second arraycopy
        byte[] payload = new byte[IV_SIZE_BYTES + enc.length];
        System.arraycopy(iv, 0, payload, 0, IV_SIZE_BYTES);
        System.arraycopy(enc, 0, payload, IV_SIZE_BYTES, enc.length);
        return payload;
    }

    public byte[] decrypt(byte[] payload) throws InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        // Use offset/length overload to avoid allocating a separate IV copy
        Cipher cipher = DECRYPT_CIPHER.get();
        cipher.init(Cipher.DECRYPT_MODE, getKeySpec(),
                new GCMParameterSpec(TAG_LEN_BITS, payload, 0, IV_SIZE_BYTES));
        return cipher.doFinal(payload, IV_SIZE_BYTES, payload.length - IV_SIZE_BYTES);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Secret)) {
            return false;
        }
        return Arrays.equals(secret, ((Secret) o).secret);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(secret);
    }
}
