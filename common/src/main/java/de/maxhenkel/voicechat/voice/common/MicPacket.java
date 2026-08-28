package de.maxhenkel.voicechat.voice.common;

import net.minecraft.network.FriendlyByteBuf;

public class MicPacket implements Packet<MicPacket> {

    public static final byte WHISPERING_MASK = 0b1;
    public static final byte MEGAPHONE_MASK = 0b10;
    public static final byte ANNOUNCE_MASK = 0b100;

    private byte[] data;
    private boolean whispering;
    private boolean megaphone;
    private boolean announce;
    private long sequenceNumber;

    public MicPacket(byte[] data, boolean whispering, long sequenceNumber) {
        this(data, whispering, false, false, sequenceNumber);
    }

    public MicPacket(byte[] data, boolean whispering, boolean megaphone, boolean announce, long sequenceNumber) {
        this.data = data;
        this.whispering = whispering;
        this.megaphone = megaphone;
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

    public boolean isMegaphone() {
        return megaphone;
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
        soundPacket.megaphone = (flags & MEGAPHONE_MASK) != 0b0;
        soundPacket.announce = (flags & ANNOUNCE_MASK) != 0b0;
        return soundPacket;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeByteArray(data);
        buf.writeLong(sequenceNumber);

        byte flags = 0b0;
        if (whispering) {
            flags = (byte) (flags | WHISPERING_MASK);
        }
        if (megaphone) {
            flags = (byte) (flags | MEGAPHONE_MASK);
        }
        if (announce) {
            flags = (byte) (flags | ANNOUNCE_MASK);
        }
        buf.writeByte(flags);
    }
}
