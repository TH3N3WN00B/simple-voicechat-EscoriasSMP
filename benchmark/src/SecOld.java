import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Faithful copy of the ORIGINAL (git HEAD) Secret.encrypt/decrypt.
 * Problem profile:
 *  - static SecureRandom is a global-locked RNG -> generateIV() hits the lock on
 *    every packet and allocates a fresh byte[12].
 *  - Cipher.getInstance() synchronizes on the JCA provider registry on every call.
 *  - decrypt() allocates an IV copy + data copy before doFinal.
 */
public class SecOld {

    public static final int IV_SIZE_BYTES = 12;
    public static final int TAG_LEN_BITS = 128;
    public static final String CIPHER = "AES/GCM/NoPadding";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] secret;
    private final SecretKeySpec keySpec;

    public SecOld(byte[] secret) {
        this.secret = secret;
        this.keySpec = new SecretKeySpec(secret, "AES");
    }

    public static byte[] generateIV() {
        byte[] iv = new byte[IV_SIZE_BYTES];
        RANDOM.nextBytes(iv);
        return iv;
    }

    public byte[] encrypt(byte[] data) throws Exception {
        byte[] iv = generateIV();
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LEN_BITS, iv));
        byte[] enc = cipher.doFinal(data);
        byte[] payload = new byte[iv.length + enc.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(enc, 0, payload, iv.length, enc.length);
        return payload;
    }

    public byte[] decrypt(byte[] payload) throws Exception {
        byte[] iv = Arrays.copyOfRange(payload, 0, IV_SIZE_BYTES);
        byte[] data = Arrays.copyOfRange(payload, IV_SIZE_BYTES, payload.length);
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LEN_BITS, iv));
        return cipher.doFinal(data);
    }

}