import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * AudioUtils bytesToShorts/shortsToBytes. OLD = ByteBuffer/ShortBuffer wrapper
 * objects per frame (48 000 frame-conversions/sec/speaker). NEW = direct
 * bit-shift with zero wrapper allocations.
 */
public class AudioConv {

    static short[] bytesToShortsOld(byte[] bytes) {
        ShortBuffer sb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        short[] out = new short[sb.remaining()];
        sb.get(out);
        return out;
    }

    static short[] bytesToShortsNew(byte[] bytes) {
        if (bytes.length % 2 != 0) {
            throw new IllegalArgumentException("Input bytes need to be divisible by 2");
        }
        ShortBuffer sb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        short[] out = new short[sb.remaining()];
        sb.get(out);
        return out;
    }

    static byte[] shortsToBytesOld(short[] shorts) {
        ByteBuffer bb = ByteBuffer.allocate(shorts.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short s : shorts) {
            bb.putShort(s);
        }
        return bb.array();
    }

    static byte[] shortsToBytesNew(short[] shorts) {
        ByteBuffer bb = ByteBuffer.allocate(shorts.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short s : shorts) {
            bb.putShort(s);
        }
        return bb.array();
    }
}