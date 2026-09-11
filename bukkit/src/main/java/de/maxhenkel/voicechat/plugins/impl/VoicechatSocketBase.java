package de.maxhenkel.voicechat.plugins.impl;

import de.maxhenkel.voicechat.Voicechat;
import de.maxhenkel.voicechat.debug.CooldownTimer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class VoicechatSocketBase {

    /**
     * Capacity of the UDP receive buffer.
     * <p>
     * Opus frames are at most 1,275 bytes per the RFC.  We add ~200 bytes for
     * the voicechat envelope (MAGIC_BYTE + UUID + varint len + AES-GCM IV +
     * GCM tag + packet type byte + sequence number).  4,096 is a comfortable
     * power-of-two that avoids any real truncation risk while staying in a
     * single typical OS network page.
     */
    private static final int BUFFER_CAPACITY = 4096;

    /**
     * Per-thread read buffer — avoids allocating a new {@code byte[4096]} on
     * every received datagram.  The buffer is reused across calls as long as
     * they happen on the same thread (which is always the case: each socket has
     * a dedicated blocking-read thread).
     */
    private static final ThreadLocal<byte[]> THREAD_BUFFER =
            ThreadLocal.withInitial(() -> new byte[BUFFER_CAPACITY]);

    public RawUdpPacketImpl read(DatagramSocket socket) throws IOException {
        byte[] buf = THREAD_BUFFER.get();
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
        if (packet.getLength() >= BUFFER_CAPACITY) {
            CooldownTimer.run("udp_packet_too_large", () -> {
                Voicechat.LOGGER.warn("Packet from {} is too large", packet.getSocketAddress());
            });
            throw new IOException(String.format("Packet from %s is too large", packet.getSocketAddress()));
        }
        // Copy only the actually received bytes so callers own a correctly-sized array
        long timestamp = System.currentTimeMillis();
        byte[] data = new byte[packet.getLength()];
        System.arraycopy(buf, packet.getOffset(), data, 0, packet.getLength());
        return new RawUdpPacketImpl(data, packet.getSocketAddress(), timestamp);
    }

}