package de.maxhenkel.voicechat.plugins.impl;

import de.maxhenkel.voicechat.Voicechat;
import de.maxhenkel.voicechat.api.RawUdpPacket;
import de.maxhenkel.voicechat.api.VoicechatSocket;
import de.maxhenkel.voicechat.intercompatibility.CommonCompatibilityManager;

import javax.annotation.Nullable;
import java.net.*;

public class VoicechatSocketImpl extends VoicechatSocketBase implements VoicechatSocket {

    /**
     * 4 MiB kernel send/receive socket buffer.
     * <p>
     * With 20 players each sending 50 packets/sec, the server produces up to
     * 20,000 outbound packets/sec.  Each Opus voice packet is ~200–1,300 bytes,
     * so at 1 KB average the burst write rate peaks around 20 MB/s.  The default
     * OS UDP send buffer of 64–128 KB saturates almost instantly, causing the
     * kernel to silently drop datagrams.  4 MB absorbs at least 200 ms of burst
     * traffic before a single packet is dropped, giving the sending thread time
     * to catch up.
     */
    private static final int SOCKET_BUFFER_SIZE = 4 * 1024 * 1024; // 4 MiB

    @Nullable
    private DatagramSocket socket;

    @Override
    public void open(int port, String bindAddress) throws Exception {
        if (socket != null) {
            throw new IllegalStateException("Socket already opened");
        }
        checkCorrectHost();
        InetAddress address = null;
        try {
            if (!bindAddress.isEmpty()) {
                address = InetAddress.getByName(bindAddress);
            }
        } catch (Exception e) {
            bindAddress = "";
            Voicechat.LOGGER.error("Failed to parse bind IP address '{}'", bindAddress, e);
        }

        try {
            try {
                socket = new DatagramSocket(port, address);
            } catch (BindException e) {
                if (address == null || bindAddress.equals("0.0.0.0")) {
                    throw e;
                }
                Voicechat.LOGGER.error("Failed to bind to address '{}', binding to wildcard IP instead", bindAddress);
                socket = new DatagramSocket(port);
            }
        } catch (BindException e) {
            Voicechat.LOGGER.error("Failed to run voice chat at UDP port {}, make sure no other application is running at that port", port);
            Voicechat.LOGGER.error("Voice chat server error", e);
            if (CommonCompatibilityManager.INSTANCE.isDedicatedServer()) {
                Voicechat.LOGGER.error("Shutting down server");
                System.exit(1);
            }
            throw e;
        }

        // ─── Increase kernel-side UDP socket buffers ───────────────────────────
        // Prevents packet loss caused by socket buffer overflow when many players
        // are simultaneously in voice range.  The OS may silently cap the value
        // to the system maximum (net.core.rmem_max / net.core.wmem_max on Linux);
        // we log the actual values so admins can tune the OS if needed.
        applySockBuf(socket, SOCKET_BUFFER_SIZE);
    }

    /**
     * Attempts to set SO_RCVBUF and SO_SNDBUF to {@code requestedSize}.
     * Logs the actual values granted by the OS, which may be lower if the
     * system maximum is smaller than the requested size.
     */
    private static void applySockBuf(DatagramSocket sock, int requestedSize) {
        try {
            sock.setReceiveBufferSize(requestedSize);
            sock.setSendBufferSize(requestedSize);
            int actualRcv = sock.getReceiveBufferSize();
            int actualSnd = sock.getSendBufferSize();
            if (actualRcv < requestedSize || actualSnd < requestedSize) {
                Voicechat.LOGGER.warn(
                        "UDP socket buffers capped by OS: requested={} KB, rcvbuf={} KB, sndbuf={} KB. "
                        + "Consider increasing net.core.rmem_max / net.core.wmem_max on Linux.",
                        requestedSize / 1024, actualRcv / 1024, actualSnd / 1024);
            } else {
                Voicechat.LOGGER.info("UDP socket buffers set to {} KB (rcv) / {} KB (snd)",
                        actualRcv / 1024, actualSnd / 1024);
            }
        } catch (Exception e) {
            Voicechat.LOGGER.warn("Failed to set UDP socket buffer size", e);
        }
    }

    private void checkCorrectHost() throws Exception {
        String host = Voicechat.SERVER_CONFIG.voiceHost.get();
        if (host.isEmpty()) {
            return;
        }
        try {
            int port = Integer.parseInt(host);
            if (port <= 0 || port > 65535) {
                Voicechat.LOGGER.warn("Invalid voice host port: {}", port);
            } else {
                Voicechat.LOGGER.info("Voice host port is {}", port);
            }
        } catch (NumberFormatException ignored) {
            try {
                new URI("voicechat://" + host);
                Voicechat.LOGGER.info("Voice host is '{}'", host);
            } catch (URISyntaxException e) {
                Voicechat.LOGGER.warn("Failed to parse voice host", e);
                System.exit(1);
                throw e;
            }
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
        if (socket == null || socket.isClosed()) {
            return; // Ignoring packet sending when socket isn't open yet or already closed
        }
        socket.send(new DatagramPacket(data, data.length, address));
    }

    @Override
    public int getLocalPort() {
        if (socket == null) {
            return -1;
        }
        return socket.getLocalPort();
    }

    @Override
    public void close() {
        if (socket != null) {
            try {
                socket.close();
            } catch (Throwable ignored) {
                // Apparently some JDKs throw an "Error" when closing a datagram socket
            }
        }
    }

    @Override
    public boolean isClosed() {
        if (socket == null) {
            return true;
        }
        return socket.isClosed();
    }
}
