package de.maxhenkel.voicechat.voice.common;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class PacketRoundtripTest {

    @Test
    void testMicPacketRoundtrip() {
        byte[] data = new byte[512];
        new Random(1234).nextBytes(data);

        MicPacket packet = new MicPacket(data, true, 42L);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.toBytes(buf);

        MicPacket decoded = new MicPacket().fromBytes(buf);
        assertArrayEquals(data, decoded.getData());
        assertEquals(42L, decoded.getSequenceNumber());
        assertTrue(decoded.isWhispering());
        assertEquals(500L, decoded.getTTL());
        assertFalse(buf.isReadable());
    }

    @Test
    void testMicPacketRoundtripMaxSize() {
        byte[] data = new byte[AudioUtils.MAX_OPUS_PAYLOAD_SIZE];
        new Random(7).nextBytes(data);

        MicPacket packet = new MicPacket(data, false, Long.MAX_VALUE);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.toBytes(buf);
        MicPacket decoded = new MicPacket().fromBytes(buf);
        assertArrayEquals(data, decoded.getData());
        assertFalse(decoded.isWhispering());
        assertEquals(Long.MAX_VALUE, decoded.getSequenceNumber());

        MicPacket reencoded = new MicPacket(decoded.getData(), decoded.isWhispering(), decoded.getSequenceNumber());
        FriendlyByteBuf buf2 = new FriendlyByteBuf(Unpooled.buffer());
        reencoded.toBytes(buf2);
        assertArrayEquals(decoded.getData(), new MicPacket().fromBytes(buf2).getData());
    }

    @Test
    void testMicPacketEmptyData() {
        MicPacket packet = new MicPacket(new byte[0], false, 0L);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.toBytes(buf);

        MicPacket decoded = new MicPacket().fromBytes(buf);
        assertEquals(0, decoded.getData().length);
        assertEquals(0L, decoded.getSequenceNumber());
        assertFalse(decoded.isWhispering());
    }

    @Test
    void testMicPacketFlagsRoundtrip() {
        MicPacket packet = new MicPacket(new byte[]{1, 2, 3}, false, true, true, 5L);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.toBytes(buf);

        MicPacket decoded = new MicPacket().fromBytes(buf);
        assertArrayEquals(new byte[]{1, 2, 3}, decoded.getData());
        assertEquals(5L, decoded.getSequenceNumber());
        assertFalse(decoded.isWhispering());
        assertTrue(decoded.isMegaphone());
        assertTrue(decoded.isAnnounce());

        MicPacket whispered = new MicPacket(new byte[]{4}, true, false, false, 6L);
        FriendlyByteBuf buf2 = new FriendlyByteBuf(Unpooled.buffer());
        whispered.toBytes(buf2);
        MicPacket decoded2 = new MicPacket().fromBytes(buf2);
        assertTrue(decoded2.isWhispering());
        assertFalse(decoded2.isMegaphone());
        assertFalse(decoded2.isAnnounce());
    }
}