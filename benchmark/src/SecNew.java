import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

/**
 * Faithful copy of the OPTIMIZED (working tree) Secret.encrypt/decrypt.
 *  - IV from a per-thread seeded 96-bit counter (no SecureRandom lock hit).
 *  - Per-thread Cipher pool (no Cipher.getInstance() provider-lock synchronization).
 *  - decrypt() uses the offset/length GCMParameterSpec + doFinal overload, so no
 *    IV/data copies are allocated.
 */
public class SecNew {

    public static final int IV_SIZE_BYTES = 12;
    public static final int TAG_LEN_BITS = 128;
    public static final String CIPHER = "AES/GCM/NoPadding";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] secret;
    private final SecretKeySpec keySpec;

    public SecNew(byte[] secret) {
        this.secret = secret;
        this.keySpec = new SecretKeySpec(secret, "AES");
    }

    private static final ThreadLocal<long[]> IV_COUNTER = ThreadLocal.withInitial(() -> {
        long[] c = new long[2];
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

    private static final ThreadLocal<byte[]> IV_BUFFER = ThreadLocal.withInitial(() -> new byte[IV_SIZE_BYTES]);

    public static byte[] generateIV() {
        long[] c = IV_COUNTER.get();
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

    private static final ThreadLocal<Cipher> ENCRYPT_CIPHER = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance(CIPHER);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });

    private static final ThreadLocal<Cipher> DECRYPT_CIPHER = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance(CIPHER);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });

    public byte[] encrypt(byte[] data) throws Exception {
        byte[] iv = generateIV();
        Cipher cipher = ENCRYPT_CIPHER.get();
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LEN_BITS, iv));
        byte[] enc = cipher.doFinal(data);
        byte[] payload = new byte[IV_SIZE_BYTES + enc.length];
        System.arraycopy(iv, 0, payload, 0, IV_SIZE_BYTES);
        System.arraycopy(enc, 0, payload, IV_SIZE_BYTES, enc.length);
        return payload;
    }

    public byte[] decrypt(byte[] payload) throws Exception {
        Cipher cipher = DECRYPT_CIPHER.get();
        cipher.init(Cipher.DECRYPT_MODE, keySpec,
                new GCMParameterSpec(TAG_LEN_BITS, payload, 0, IV_SIZE_BYTES));
        return cipher.doFinal(payload, IV_SIZE_BYTES, payload.length - IV_SIZE_BYTES);
    }

}