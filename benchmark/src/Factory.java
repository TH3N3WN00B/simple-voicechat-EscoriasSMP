import java.util.HashMap;
import java.util.Map;

/**
 * NetworkMessage packet factory. OLD = HashMap<Byte,Class> + reflection
 * (getDeclaredConstructor().newInstance()) and linear map scan for the inverse
 * mapping. NEW = plain switch (create) + instanceof chain (getPacketType).
 *
 * The 11 stub classes are trivial (empty default constructor) — identical
 * construction cost on both sides, so the delta isolates the dispatch overhead.
 */
class Packet {
}

class MicPacket             extends Packet { }
class PlayerSoundPacket     extends Packet { }
class GroupSoundPacket      extends Packet { }
class LocationSoundPacket   extends Packet { }
class AuthenticatePacket    extends Packet { }
class AuthenticateAckPacket extends Packet { }
class PingPacket            extends Packet { }
class KeepAlivePacket       extends Packet { }
class ConnectionCheckPacket extends Packet { }
class ConnectionCheckAckPacket extends Packet { }
class AnnouncementSoundPacket extends Packet { }

public class Factory {

    static final Map<Byte, Class<? extends Packet>> packetRegistry = new HashMap<>();

    static {
        packetRegistry.put((byte) 0x1, MicPacket.class);
        packetRegistry.put((byte) 0x2, PlayerSoundPacket.class);
        packetRegistry.put((byte) 0x3, GroupSoundPacket.class);
        packetRegistry.put((byte) 0x4, LocationSoundPacket.class);
        packetRegistry.put((byte) 0x5, AuthenticatePacket.class);
        packetRegistry.put((byte) 0x6, AuthenticateAckPacket.class);
        packetRegistry.put((byte) 0x7, PingPacket.class);
        packetRegistry.put((byte) 0x8, KeepAlivePacket.class);
        packetRegistry.put((byte) 0x9, ConnectionCheckPacket.class);
        packetRegistry.put((byte) 0xA, ConnectionCheckAckPacket.class);
        packetRegistry.put((byte) 0xB, AnnouncementSoundPacket.class);
    }

    static Packet createOld(byte type) throws Exception {
        Class<? extends Packet> packetClass = packetRegistry.get(type);
        if (packetClass == null) {
            return null;
        }
        return packetClass.getDeclaredConstructor().newInstance();
    }

    static Packet createNew(byte type) {
        switch (type) {
            case 0x1: return new MicPacket();
            case 0x2: return new PlayerSoundPacket();
            case 0x3: return new GroupSoundPacket();
            case 0x4: return new LocationSoundPacket();
            case 0x5: return new AuthenticatePacket();
            case 0x6: return new AuthenticateAckPacket();
            case 0x7: return new PingPacket();
            case 0x8: return new KeepAlivePacket();
            case 0x9: return new ConnectionCheckPacket();
            case 0xA: return new ConnectionCheckAckPacket();
            case 0xB: return new AnnouncementSoundPacket();
            default:  return null;
        }
    }

    static byte getPacketTypeOld(Packet packet) {
        for (Map.Entry<Byte, Class<? extends Packet>> entry : packetRegistry.entrySet()) {
            if (packet.getClass().equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return -1;
    }

    static byte getPacketTypeNew(Packet packet) {
        if (packet instanceof MicPacket)              return 0x1;
        if (packet instanceof PlayerSoundPacket)      return 0x2;
        if (packet instanceof GroupSoundPacket)       return 0x3;
        if (packet instanceof LocationSoundPacket)    return 0x4;
        if (packet instanceof AuthenticatePacket)     return 0x5;
        if (packet instanceof AuthenticateAckPacket)  return 0x6;
        if (packet instanceof PingPacket)             return 0x7;
        if (packet instanceof KeepAlivePacket)        return 0x8;
        if (packet instanceof ConnectionCheckPacket)  return 0x9;
        if (packet instanceof ConnectionCheckAckPacket) return (byte) 0xA;
        if (packet instanceof AnnouncementSoundPacket)  return (byte) 0xB;
        return -1;
    }
}