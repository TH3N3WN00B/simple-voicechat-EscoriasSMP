import java.util.Arrays;
import java.util.Random;

/**
 * Micro-benchmark: OLD (git HEAD) vs NEW (working tree) hot paths.
 * Measurement: for each of `rounds` rounds, OLD and NEW are timed back-to-back,
 * alternating which one runs first, and the best round is kept per variant
 * (min = least noise, alternating = no ordering/thermal bias).
 */
public class Main {

    static volatile long SINK;

    interface Op {
        Object run() throws Exception;
    }

    static double measurePair(String name, Op oldOp, Op newOp, Object[] oldSink, Object[] newSink,
                              int warmup, int iters, int rounds) throws Exception {
        for (int i = 0; i < warmup; i++) {
            oldSink[i % oldSink.length] = oldOp.run();
            newSink[i % newSink.length] = newOp.run();
        }
        long oldBest = Long.MAX_VALUE, newBest = Long.MAX_VALUE;
        for (int r = 0; r < rounds; r++) {
            boolean oldFirst = (r % 2 == 0);
            long t0, t1, t2;
            if (oldFirst) {
                t0 = System.nanoTime();
                for (int i = 0; i < iters; i++) oldSink[i % oldSink.length] = oldOp.run();
                t1 = System.nanoTime();
                for (int i = 0; i < iters; i++) newSink[i % newSink.length] = newOp.run();
                t2 = System.nanoTime();
            } else {
                t0 = System.nanoTime();
                for (int i = 0; i < iters; i++) newSink[i % newSink.length] = newOp.run();
                t1 = System.nanoTime();
                for (int i = 0; i < iters; i++) oldSink[i % oldSink.length] = oldOp.run();
                t2 = System.nanoTime();
            }
            if (oldFirst) {
                oldBest = Math.min(oldBest, t1 - t0);
                newBest = Math.min(newBest, t2 - t1);
            } else {
                oldBest = Math.min(oldBest, t2 - t1);
                newBest = Math.min(newBest, t1 - t0);
            }
        }
        long drain = 0;
        for (int i = 0; i < oldSink.length; i++) {
            drain += oldSink[i].hashCode() + newSink[i].hashCode();
        }
        SINK += drain;
        double oldNs = oldBest / (double) iters;
        double newNs = newBest / (double) iters;
        System.out.printf("%-46s %9.1f %9.1f %8.2fx%n", name, oldNs, newNs, oldNs / newNs);
        return oldNs / newNs;
    }

    public static void main(String[] args) throws Exception {
        Random rng = new Random(0x1234);

        byte[] key = new byte[16];
        rng.nextBytes(key);
        SecOld secOld = new SecOld(key);
        SecNew secNew = new SecNew(key);

        byte[] payload = new byte[1200];
        rng.nextBytes(payload);
        byte[] encrypted = secNew.encrypt(payload);
        if (!Arrays.equals(payload, secOld.decrypt(encrypted)) || !Arrays.equals(payload, secNew.decrypt(encrypted))) {
            throw new IllegalStateException("Secret correctness check failed");
        }

        short[] pcm = new short[960];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) rng.nextInt();
        }
        byte[] pcmBytes = AudioConv.shortsToBytesNew(pcm);
        if (!Arrays.equals(pcm, AudioConv.bytesToShortsOld(pcmBytes)) || !Arrays.equals(pcm, AudioConv.bytesToShortsNew(pcmBytes))) {
            throw new IllegalStateException("bytesToShorts correctness check failed");
        }
        if (!Arrays.equals(pcmBytes, AudioConv.shortsToBytesOld(pcm)) || !Arrays.equals(pcmBytes, AudioConv.shortsToBytesNew(pcm))) {
            throw new IllegalStateException("shortsToBytes correctness check failed");
        }

        Vec3 camPos = new Vec3(10, 20, 30);
        Vec3 soundPos = new Vec3(8, 22, 35);
        float yRot = 45F;
        float maxDistance = 48F;

        float[] so = Stereo.getStereoVolumeOld(camPos, yRot, soundPos);
        float[] sn = Stereo.getStereoVolumeNew(camPos, yRot, soundPos);
        if (Math.abs(so[0] - sn[0]) > 1e-3 || Math.abs(so[1] - sn[1]) > 1e-3) {
            throw new IllegalStateException("getStereoVolume mismatch: " + Arrays.toString(so) + " vs " + Arrays.toString(sn));
        }
        if (Math.abs(Stereo.getDistanceVolumeOld(maxDistance, camPos, soundPos) - Stereo.getDistanceVolumeNew(maxDistance, camPos, soundPos)) > 1e-5) {
            throw new IllegalStateException("getDistanceVolume mismatch");
        }
        if (Dist.isInRangeOld(camPos, soundPos, 10D) != Dist.isInRangeNew(camPos, soundPos, 10D)) {
            throw new IllegalStateException("isInRange mismatch");
        }

        System.out.println("Sanity checks: PASS");
        System.out.println();
        System.out.printf("%-46s %9s %9s %8s%n", "benchmark", "old ns/op", "new ns/op", "speedup");
        System.out.println("-".repeat(80));

        // 1-2. Secret.encrypt/decrypt (1200 B voice payload, server write / client read)
        Object[] encSink = new Object[256];
        measurePair("Secret.encrypt(1200B)",
                () -> secOld.encrypt(payload), () -> secNew.encrypt(payload),
                encSink, new Object[256], 20_000, 20_000, 9);
        Object[] decSink = new Object[256];
        measurePair("Secret.decrypt(1200B)",
                () -> secOld.decrypt(encrypted), () -> secNew.decrypt(encrypted),
                decSink, new Object[256], 20_000, 20_000, 9);

        // 3-4. AudioUtils conversions (client decode / server encode)
        Object[] shortsSink = new Object[512];
        measurePair("AudioUtils.bytesToShorts(1920B)",
                () -> AudioConv.bytesToShortsOld(pcmBytes), () -> AudioConv.bytesToShortsNew(pcmBytes),
                shortsSink, new Object[512], 50_000, 50_000, 9);
        Object[] bytesSink = new Object[512];
        measurePair("AudioUtils.shortsToBytes(960)",
                () -> AudioConv.shortsToBytesOld(pcm), () -> AudioConv.shortsToBytesNew(pcm),
                bytesSink, new Object[512], 50_000, 50_000, 9);

        // 5-7. NetworkMessage factory (receive dispatch / outbound type lookup)
        Object[] packetSink = new Object[512];
        measurePair("PacketFactory.create(0x1 MicPacket)",
                () -> Factory.createOld((byte) 0x1), () -> Factory.createNew((byte) 0x1),
                packetSink, new Object[512], 50_000, 100_000, 9);
        byte[] ids = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
        measurePair("PacketFactory.create(mixed 0x1..0xB)",
                () -> Factory.createOld(ids[(int) (SINK & 0xF) % 11]), () -> Factory.createNew(ids[(int) (SINK & 0xF) % 11]),
                packetSink, new Object[512], 50_000, 100_000, 9);
        measurePair("PacketFactory.getPacketType",
                () -> (byte) Factory.getPacketTypeOld(new MicPacket()), () -> (byte) Factory.getPacketTypeNew(new MicPacket()),
                new Object[512], new Object[512], 50_000, 100_000, 9);

        // 8-9. Positional audio (positional playback per frame per speaker)
        Object[] volSink = new Object[512];
        measurePair("getStereoVolume (per frame)",
                () -> Stereo.getStereoVolumeOld(camPos, yRot, soundPos), () -> Stereo.getStereoVolumeNew(camPos, yRot, soundPos),
                volSink, new Object[512], 50_000, 100_000, 9);
        Object[] distSink = new Object[512];
        measurePair("getDistanceVolume (per frame)",
                () -> (double) Stereo.getDistanceVolumeOld(maxDistance, camPos, soundPos),
                () -> (double) Stereo.getDistanceVolumeNew(maxDistance, camPos, soundPos),
                distSink, new Object[512], 50_000, 100_000, 9);

        // 10. ServerPlayerManager.isInRange (per candidate per broadcast)
        measurePair("ServerPlayerManager.isInRange",
                () -> Dist.isInRangeOld(camPos, soundPos, 10D), () -> Dist.isInRangeNew(camPos, soundPos, 10D),
                new Object[512], new Object[512], 50_000, 200_000, 9);

        System.out.println();
        System.out.println("sink=" + SINK);
    }
}