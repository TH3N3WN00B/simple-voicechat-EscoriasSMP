package de.maxhenkel.voicechat.voice.client;

import de.maxhenkel.voicechat.voice.common.SoundPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class AudioPacketBuffer {

    /**
     * Packet interval of the audio stream in milliseconds (20 ms @ 50 Hz).
     */
    private static final long PACKET_INTERVAL_MS = 20L;
    /**
     * Inter-arrival jitter smoothing factor (RFC 3550 style EMA).
     */
    private static final double JITTER_ALPHA = 1D / 16D;
    /**
     * Lower/upper bounds for the adaptive gap tolerance window.
     */
    private static final long MIN_GAP_TOLERANCE_NANOS = TimeUnit.NANOSECONDS.convert(40, TimeUnit.MILLISECONDS);
    private static final long MAX_GAP_TOLERANCE_NANOS = TimeUnit.NANOSECONDS.convert(150, TimeUnit.MILLISECONDS);

    private final int packetThreshold;
    @Nullable
    private List<SoundPacket<?>> packetBuffer;
    private long lastSequenceNumber = -1;
    private boolean isFlushingBuffer;

    // Arrival-time jitter estimation
    private long lastArrivalTime = -1L;
    private double lastIntervalNanos = -1D;
    private double interArrivalJitterNanos;
    // Deadline before the next buffered packet is played despite a sequence gap
    private long gapDeadline = -1L;

    public AudioPacketBuffer(int packetThreshold) {
        this.packetThreshold = packetThreshold;
        if (packetThreshold > 0) {
            this.packetBuffer = new ArrayList<>();
        }
    }

    @Nullable
    public SoundPacket<?> poll(BlockingQueue<SoundPacket<?>> queue) throws InterruptedException {
        if (packetThreshold <= 0) {
            return queue.poll(10, TimeUnit.MILLISECONDS);
        }

        SoundPacket<?> packet = getNext();
        if (packet != null) {
            return packet;
        }
        packet = queue.poll(5, TimeUnit.MILLISECONDS);
        if (packet == null) {
            return null;
        }
        long sequenceNumber = packet.getSequenceNumber();
        if (lastSequenceNumber < 0L) {
            lastSequenceNumber = sequenceNumber;
            onArrival();
            return packet;
        }
        if (sequenceNumber == lastSequenceNumber + 1L) {
            // In-order (or the missing packet finally arrived) — play it immediately
            lastSequenceNumber = sequenceNumber;
            gapDeadline = -1L;
            onArrival();
            return packet;
        } else if (sequenceNumber > lastSequenceNumber + 1L) {
            // Slightly out of order / ahead of playback — buffer it for reordering
            addSorted(packet);
            return null;
        } else {
            // Stale or duplicate packet — discard it
            return null;
        }
    }

    private void addSorted(SoundPacket<?> packet) {
        if (packet.getData().length <= 0) {
            isFlushingBuffer = true;
        }
        long sequenceNumber = packet.getSequenceNumber();
        int index = packetBuffer.size();
        while (index > 0 && packetBuffer.get(index - 1).getSequenceNumber() > sequenceNumber) {
            index--;
        }
        packetBuffer.add(index, packet);
    }

    @Nullable
    private SoundPacket<?> getNext() {
        if (isFlushingBuffer) {
            if (packetBuffer.isEmpty()) {
                isFlushingBuffer = false;
                return null;
            }
            return getFirstPacket();
        } else if (packetBuffer.size() > packetThreshold) {
            // Buffer overflowing — force playback regardless of the gap
            return getFirstPacket();
        } else if (!packetBuffer.isEmpty()) {
            SoundPacket<?> packet = packetBuffer.get(0);
            long sequenceNumber = packet.getSequenceNumber();
            if (lastSequenceNumber < 0L || sequenceNumber == lastSequenceNumber + 1L) {
                return getFirstPacket();
            }
            if (sequenceNumber > lastSequenceNumber + 1L) {
                // A packet is missing (reordered, late or lost). Previously the buffer
                // would stall indefinitely waiting for the exact next sequence number,
                // only recovering once the configured threshold overflowed — which
                // produced audible gaps and stutter for minimal reordering offsets.
                // Instead, wait a short adaptive window (based on measured jitter) for
                // the missing packet to arrive, then resume playback past the gap.
                if (gapDeadline < 0L) {
                    gapDeadline = System.nanoTime() + getGapToleranceNanos();
                }
                if (System.nanoTime() >= gapDeadline) {
                    gapDeadline = -1L;
                    return getFirstPacket();
                }
            }
            return null;
        }
        return null;
    }

    private SoundPacket<?> getFirstPacket() {
        SoundPacket<?> packet = packetBuffer.remove(0);
        lastSequenceNumber = packet.getSequenceNumber();
        return packet;
    }

    /**
     * The maximum time to wait for a missing packet before advanced playback past
     * the gap. Scales with the measured inter-arrival jitter: on a stable link the
     * tolerance stays small (low added latency), while a jittery link gets a wider
     * window to absorb reordering without discarding audio.
     */
    private long getGapToleranceNanos() {
        double toleranceNanos = PACKET_INTERVAL_MS * 1_500_000D + interArrivalJitterNanos * 4D;
        if (toleranceNanos < MIN_GAP_TOLERANCE_NANOS) {
            return MIN_GAP_TOLERANCE_NANOS;
        }
        if (toleranceNanos > MAX_GAP_TOLERANCE_NANOS) {
            return MAX_GAP_TOLERANCE_NANOS;
        }
        return (long) toleranceNanos;
    }

    private void onArrival() {
        long now = System.nanoTime();
        if (lastArrivalTime >= 0L) {
            double intervalNanos = now - lastArrivalTime;
            if (lastIntervalNanos >= 0D) {
                double variation = Math.abs(intervalNanos - lastIntervalNanos);
                interArrivalJitterNanos += (variation - interArrivalJitterNanos) * JITTER_ALPHA;
            }
            lastIntervalNanos = intervalNanos;
        }
        lastArrivalTime = now;
    }

    public void clear() {
        if (packetBuffer != null) {
            packetBuffer.clear();
        }
        lastSequenceNumber = -1L;
        isFlushingBuffer = false;
        lastArrivalTime = -1L;
        lastIntervalNanos = -1D;
        interArrivalJitterNanos = 0D;
        gapDeadline = -1L;
    }

    public int getSize() {
        if (packetBuffer == null) {
            return 0;
        }
        return packetBuffer.size();
    }

}