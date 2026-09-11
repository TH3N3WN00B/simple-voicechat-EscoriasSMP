package de.maxhenkel.voicechat.plugins.impl;

import de.maxhenkel.voicechat.Voicechat;
import de.maxhenkel.voicechat.api.ClientVoicechatSocket;
import de.maxhenkel.voicechat.api.RawUdpPacket;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;

public class ClientVoicechatSocketImpl extends VoicechatSocketBase implements ClientVoicechatSocket {

    /**
     * 4 MiB client socket buffer.
     * <p>
     * With 20 players in range each speaking, a client receives up to
     * 20 × 50 = 1 000 packets/sec (~1 KB each ≈ 1 MB/s).  The default OS
     * buffer of 64–128 KB can fill in ~65 ms of audio traffic, causing silent
     * packet drops that manifest as audio crackling and cut-outs.
     * 4 MiB provides ~4 s of headroom against burst arrivals and matches the
     * server-side socket buffers.
     */
    private static final int SOCKET_BUFFER_SIZE = 4 * 1024 * 1024; // 4 MiB

    private DatagramSocket socket;

    @Override
    public void open() throws Exception {
        this.socket = new DatagramSocket();
        applySockBuf(socket);
    }

    /**
     * Attempts to set SO_RCVBUF and SO_SNDBUF to {@link #SOCKET_BUFFER_SIZE}.
     * Logs the actual values granted by the OS.
     */
    private static void applySockBuf(DatagramSocket sock) {
        try {
            sock.setReceiveBufferSize(SOCKET_BUFFER_SIZE);
            sock.setSendBufferSize(SOCKET_BUFFER_SIZE);
            int actualRcv = sock.getReceiveBufferSize();
            int actualSnd = sock.getSendBufferSize();
            if (actualRcv < SOCKET_BUFFER_SIZE || actualSnd < SOCKET_BUFFER_SIZE) {
                Voicechat.LOGGER.debug(
                        "Client UDP socket buffers capped by OS: requested={} KB, rcvbuf={} KB, sndbuf={} KB",
                        SOCKET_BUFFER_SIZE / 1024, actualRcv / 1024, actualSnd / 1024);
            }
        } catch (Exception e) {
            Voicechat.LOGGER.debug("Failed to set client UDP socket buffer size", e);
        }
    }

    @Override
    public RawUdpPacket read() throws Exception {
        if (socket == null) {
            throw new IllegalStateException("Socket not opened yet");
        }
        return read(socket);
    }

    @Override
    public void send(byte[] data, SocketAddress address) throws Exception {
        if (socket == null) {
            return; // Ignoring packet sending when socket isn't open yet
        }
        socket.send(new DatagramPacket(data, data.length, address));
    }

    @Override
    public void close() {
        if (socket != null) {
            socket.close();
        }
    }

    @Override
    public boolean isClosed() {
        return socket == null || socket.isClosed();
    }
}
