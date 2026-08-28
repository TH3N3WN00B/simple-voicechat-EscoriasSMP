package de.maxhenkel.voicechat.voice.server;

import de.maxhenkel.voicechat.api.Group.Type;
import de.maxhenkel.voicechat.voice.common.ClientGroup;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class GroupTest {

    @Test
    void testConstructorDefaults() {
        UUID id = UUID.randomUUID();
        Group group = new Group(id, "Test");
        assertEquals(id, group.getId());
        assertEquals("Test", group.getName());
        assertNull(group.getPassword());
        assertFalse(group.isPersistent());
        assertFalse(group.isHidden());
        assertTrue(group.isNormal());
    }

    @Test
    void testPasswordIsStoredOpaque() {
        Group group = new Group(UUID.randomUUID(), "Test", "secret");
        assertNotNull(group.getPassword());
        assertEquals("secret", group.getPassword());
        assertNull(new Group(UUID.randomUUID(), "Test").getPassword());
    }

    @Test
    void testGroupTypes() {
        Group open = new Group(UUID.randomUUID(), "A", null, false, false, Type.OPEN);
        Group isolated = new Group(UUID.randomUUID(), "B", null, false, false, Type.ISOLATED);
        Group normal = new Group(UUID.randomUUID(), "C", null, false, false, Type.NORMAL);

        assertTrue(open.isOpen());
        assertFalse(open.isNormal());
        assertFalse(open.isIsolated());

        assertTrue(isolated.isIsolated());
        assertTrue(normal.isNormal());
    }

    @Test
    void testToClientGroup() {
        UUID id = UUID.randomUUID();
        Group group = new Group(id, "Secure", "pw", true, true, Type.ISOLATED);

        ClientGroup clientGroup = group.toClientGroup();
        assertEquals(id, clientGroup.getId());
        assertEquals("Secure", clientGroup.getName());
        assertTrue(clientGroup.hasPassword());
        assertTrue(clientGroup.isPersistent());
        assertTrue(clientGroup.isHidden());
        assertEquals(Type.ISOLATED, clientGroup.getType());
        assertNull(clientGroup.getOwnerUuid());
        assertTrue(clientGroup.getAdmins().isEmpty());
    }

    @Test
    void testOwnerAndAdmins() {
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        UUID member = UUID.randomUUID();

        Group group = new Group(id, "Squad");
        assertFalse(group.isOwner(owner));
        assertFalse(group.isAdmin(admin));

        group.setOwner(owner);
        assertTrue(group.isOwner(owner));
        assertTrue(group.isAdmin(owner));

        group.addAdmin(admin);
        assertTrue(group.isAdmin(admin));
        assertTrue(group.isAdmin(owner));
        assertFalse(group.isAdmin(member));

        group.removeAdmin(admin);
        assertFalse(group.isAdmin(admin));

        group.setOwner(admin);
        assertTrue(group.isOwner(admin));
        assertFalse(group.isAdmin(owner));
    }

    @Test
    void testClientGroupRolesRoundtrip() {
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        java.util.List<UUID> admins = java.util.List.of(UUID.randomUUID(), UUID.randomUUID());

        ClientGroup clientGroup = new ClientGroup(id, "Squad", false, false, false, Type.NORMAL, owner, admins);
        assertEquals(owner, clientGroup.getOwnerUuid());
        assertTrue(clientGroup.isOwner(owner));
        assertTrue(clientGroup.isAdmin(admins.get(0)));
        assertTrue(clientGroup.isAdmin(owner));

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        clientGroup.toBytes(buf);
        ClientGroup decoded = ClientGroup.fromBytes(buf);
        assertEquals(clientGroup, decoded);
        assertEquals(owner, decoded.getOwnerUuid());
        assertEquals(admins, decoded.getAdmins());
        assertTrue(decoded.isAdmin(admins.get(1)));
        assertFalse(buf.isReadable());
    }
}