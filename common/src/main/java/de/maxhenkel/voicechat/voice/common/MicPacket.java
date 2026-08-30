package de.maxhenkel.voicechat.voice.common;

import net.minecraft.network.FriendlyByteBuf;

public class MicPacket implements Packet<MicPacket> {

    public static final byte WHISPERING_MASK = 0b001;
    public static final byte ANNOUNCE_MASK = 0b100;

    private byte[] data;
    private boolean whispering;
    private boolean announce;
    private long sequenceNumber;

    public MicPacket(byte[] data, boolean whispering, long sequenceNumber) {
        this.data = data;
        this.whispering = whispering;
        this.sequenceNumber = sequenceNumber;
    }

    public MicPacket(byte[] data, boolean whispering, boolean announce, long sequenceNumber) {
        this.data = data;
        this.whispering = whispering;
        this.announce = announce;
        this.sequenceNumber = sequenceNumber;
    }

    public MicPacket() {

    }

    @Override
    public long getTTL() {
        return 500L;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public boolean isWhispering() {
        return whispering;
    }

    public boolean isAnnounce() {
        return announce;
    }

    @Override
    public MicPacket fromBytes(FriendlyByteBuf buf) {
        MicPacket soundPacket = new MicPacket();
        soundPacket.data = buf.readByteArray(AudioUtils.MAX_OPUS_PAYLOAD_SIZE);
        soundPacket.sequenceNumber = buf.readLong();
        byte flags = buf.readByte();
        soundPacket.whispering = (flags & WHISPERING_MASK) != 0b0;
        soundPacket.announce = (flags & ANNOUNCE_MASK) != 0b0;
        return soundPacket;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        byte flags = 0b0;
        if (whispering) {
            flags = (byte) (flags | WHISPERING_MASK);
        }
        if (announce) {
            flags = (byte) (flags | ANNOUNCE_MASK);
        }
        buf.writeByteArray(data);
        buf.writeLong(sequenceNumber);
        buf.writeByte(flags);
    }
}
