package de.maxhenkel.voicechat.net;

import de.maxhenkel.voicechat.Voicechat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import javax.annotation.Nullable;
import java.util.UUID;

public class ManageGroupPacket implements Packet<ManageGroupPacket> {

    public static final CustomPacketPayload.Type<ManageGroupPacket> MANAGE_GROUP = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Voicechat.MODID, "manage_group"));

    public static final int OP_KICK = 0;
    public static final int OP_PROMOTE = 1;
    public static final int OP_DEMOTE = 2;
    public static final int OP_TRANSFER = 3;
    public static final int OP_RENAME = 4;
    public static final int OP_DELETE = 5;
    public static final int OP_SET_PASSWORD = 6;

    private int op;
    @Nullable
    private UUID targetUuid;
    @Nullable
    private String name;

    public ManageGroupPacket() {

    }

    public ManageGroupPacket(int op, @Nullable UUID targetUuid, @Nullable String name) {
        this.op = op;
        this.targetUuid = targetUuid;
        this.name = name;
    }

    public int getOp() {
        return op;
    }

    @Nullable
    public UUID getTargetUuid() {
        return targetUuid;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Override
    public ManageGroupPacket fromBytes(FriendlyByteBuf buf) {
        op = buf.readInt();
        targetUuid = null;
        if (buf.readBoolean()) {
            targetUuid = buf.readUUID();
        }
        name = null;
        if (buf.readBoolean()) {
            name = buf.readUtf(512);
        }
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(op);
        buf.writeBoolean(targetUuid != null);
        if (targetUuid != null) {
            buf.writeUUID(targetUuid);
        }
        buf.writeBoolean(name != null);
        if (name != null) {
            buf.writeUtf(name, 512);
        }
    }

    @Override
    public Type<ManageGroupPacket> type() {
        return MANAGE_GROUP;
    }

}