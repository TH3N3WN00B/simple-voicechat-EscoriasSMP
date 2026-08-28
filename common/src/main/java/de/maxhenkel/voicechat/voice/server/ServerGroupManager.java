package de.maxhenkel.voicechat.voice.server;

import de.maxhenkel.voicechat.Voicechat;
import de.maxhenkel.voicechat.intercompatibility.CommonCompatibilityManager;
import de.maxhenkel.voicechat.net.AddGroupPacket;
import de.maxhenkel.voicechat.net.JoinedGroupPacket;
import de.maxhenkel.voicechat.net.ManageGroupPacket;
import de.maxhenkel.voicechat.net.NetManager;
import de.maxhenkel.voicechat.net.RemoveGroupPacket;
import de.maxhenkel.voicechat.permission.PermissionManager;
import de.maxhenkel.voicechat.plugins.PluginManager;
import de.maxhenkel.voicechat.voice.common.PlayerState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerGroupManager {

    private final Map<UUID, Group> groups;
    private final Server server;

    public ServerGroupManager(Server server) {
        this.server = server;
        groups = new ConcurrentHashMap<>();

        CommonCompatibilityManager.INSTANCE.getNetManager().joinGroupChannel.setServerListener((player, packet) -> {
            if (!Voicechat.SERVER_CONFIG.groupsEnabled.get()) {
                return;
            }
            if (!PermissionManager.INSTANCE.GROUPS_PERMISSION.hasPermission(player)) {
                player.displayClientMessage(Component.translatable("message.voicechat.no_group_permission"), true);
                return;
            }
            joinGroup(groups.get(packet.getGroup()), player, packet.getPassword());
        });
        CommonCompatibilityManager.INSTANCE.getNetManager().createGroupChannel.setServerListener((player, packet) -> {
            if (!Voicechat.SERVER_CONFIG.groupsEnabled.get()) {
                return;
            }
            if (!PermissionManager.INSTANCE.GROUPS_PERMISSION.hasPermission(player)) {
                player.displayClientMessage(Component.translatable("message.voicechat.no_group_permission"), true);
                return;
            }
            if (!Voicechat.GROUP_REGEX.matcher(packet.getName()).matches()) {
                Voicechat.LOGGER.warn("Player {} tried to create a group with an invalid name", player.getName().getString());
                return;
            }
            if (packet.getPassword() != null && !Voicechat.GROUP_REGEX.matcher(packet.getPassword()).matches()) {
                Voicechat.LOGGER.warn("Player {} tried to create a group with an invalid password", player.getDisplayName());
                return;
            }
            addGroup(new Group(UUID.randomUUID(), packet.getName(), packet.getPassword(), false, false, packet.getType(), player.getUUID(), new HashSet<>()), player);
        });
        CommonCompatibilityManager.INSTANCE.getNetManager().leaveGroupChannel.setServerListener((player, packet) -> {
            leaveGroup(player);
        });
        CommonCompatibilityManager.INSTANCE.getNetManager().manageGroupChannel.setServerListener((player, packet) -> {
            if (!Voicechat.SERVER_CONFIG.groupsEnabled.get()) {
                return;
            }
            if (!PermissionManager.INSTANCE.GROUPS_PERMISSION.hasPermission(player)) {
                player.displayClientMessage(Component.translatable("message.voicechat.no_group_permission"), true);
                return;
            }
            handleManageGroup(player, packet);
        });
    }

    public void onPlayerCompatibilityCheckSucceeded(ServerPlayer player) {
        Voicechat.LOGGER.debug("Synchronizing {} groups with {}", groups.size(), player.getName().getString());
        for (Group group : groups.values()) {
            NetManager.sendToClient(player, new AddGroupPacket(group.toClientGroup()));
        }
    }

    public void onPlayerLoggedOut(ServerPlayer player) {
        cleanupGroups();
    }

    private PlayerStateManager getStates() {
        return server.getPlayerStateManager();
    }

    public void addGroup(Group group, @Nullable ServerPlayer player) {
        if (PluginManager.instance().onCreateGroup(player, group)) {
            return;
        }
        groups.put(group.getId(), group);
        broadcastAddGroup(group);

        if (player == null) {
            return;
        }

        PlayerStateManager manager = getStates();
        manager.setGroup(player, group.getId());

        NetManager.sendToClient(player, new JoinedGroupPacket(group.getId(), false));
    }

    public void joinGroup(@Nullable Group group, ServerPlayer player, @Nullable String password) {
        if (group != null && group.getPassword() != null && !group.getPassword().equals(password)) {
            NetManager.sendToClient(player, new JoinedGroupPacket(null, true));
            return;
        }

        if (PluginManager.instance().onJoinGroup(player, group)) {
            return;
        }

        if (group == null) {
            NetManager.sendToClient(player, new JoinedGroupPacket(null, false));
            return;
        }

        PlayerStateManager manager = getStates();
        manager.setGroup(player, group.getId());

        NetManager.sendToClient(player, new JoinedGroupPacket(group.getId(), false));
    }

    public void leaveGroup(ServerPlayer player) {
        if (PluginManager.instance().onLeaveGroup(player)) {
            return;
        }

        Group group = getPlayerGroup(player);

        PlayerStateManager manager = getStates();
        manager.setGroup(player, null);
        NetManager.sendToClient(player, new JoinedGroupPacket(null, false));

        if (group != null && group.isOwner(player.getUUID())) {
            transferOwnershipIfNeeded(group);
        }

        cleanupGroups();
    }

    public void cleanupGroups() {
        PlayerStateManager manager = getStates();
        List<UUID> usedGroups = manager.getStates().stream().filter(PlayerState::hasGroup).map(PlayerState::getGroup).distinct().toList();
        List<UUID> groupsToRemove = groups.entrySet().stream().filter(entry -> !entry.getValue().isPersistent()).map(Map.Entry::getKey).filter(uuid -> !usedGroups.contains(uuid)).toList();
        for (UUID uuid : groupsToRemove) {
            removeGroup(uuid);
        }
        for (Group group : groups.values()) {
            UUID owner = group.getOwnerUuid();
            if (owner == null) {
                continue;
            }
            ServerPlayer ownerPlayer = server.getServer().getPlayerList().getPlayer(owner);
            if (ownerPlayer != null) {
                continue;
            }
            ServerPlayer member = manager.getStates().stream()
                    .filter(state -> state.hasGroup() && group.getId().equals(state.getGroup()))
                    .map(state -> server.getServer().getPlayerList().getPlayer(state.getUuid()))
                    .filter(Objects::nonNull)
                    .findFirst().orElse(null);
            if (member != null) {
                group.setOwner(member.getUUID());
                broadcastAddGroup(group);
            }
        }
    }

    private void handleManageGroup(ServerPlayer player, ManageGroupPacket packet) {
        Group group = getPlayerGroup(player);
        if (group == null) {
            player.displayClientMessage(Component.translatable("message.voicechat.not_in_group"), true);
            return;
        }
        switch (packet.getOp()) {
            case ManageGroupPacket.OP_KICK:
                kickMember(player, group, packet.getTargetUuid());
                break;
            case ManageGroupPacket.OP_PROMOTE:
                promoteMember(player, group, packet.getTargetUuid());
                break;
            case ManageGroupPacket.OP_DEMOTE:
                demoteMember(player, group, packet.getTargetUuid());
                break;
            case ManageGroupPacket.OP_TRANSFER:
                transferOwnership(player, group, packet.getTargetUuid());
                break;
            case ManageGroupPacket.OP_RENAME:
                renameGroup(player, group, packet.getName());
                break;
            case ManageGroupPacket.OP_DELETE:
                deleteGroup(player, group);
                break;
            case ManageGroupPacket.OP_SET_PASSWORD:
                setGroupPassword(player, group, packet.getName());
                break;
        }
    }

    private boolean isMember(UUID playerUuid, Group group) {
        PlayerState state = server.getPlayerStateManager().getState(playerUuid);
        return state != null && state.hasGroup() && group.getId().equals(state.getGroup());
    }

    @Nullable
    private ServerPlayer getOnlinePlayer(UUID playerUuid) {
        return server.getServer().getPlayerList().getPlayer(playerUuid);
    }

    public void kickMember(ServerPlayer actor, Group group, @Nullable UUID targetUuid) {
        if (!group.isAdmin(actor.getUUID())) {
            actor.displayClientMessage(Component.translatable("message.voicechat.group_no_permission"), true);
            return;
        }
        if (targetUuid == null || targetUuid.equals(actor.getUUID())) {
            return;
        }
        if (group.isOwner(targetUuid)) {
            actor.displayClientMessage(Component.translatable("message.voicechat.group_cannot_kick_owner"), true);
            return;
        }
        if (!isMember(targetUuid, group)) {
            actor.displayClientMessage(Component.translatable("message.voicechat.group_not_in_group"), true);
            return;
        }
        ServerPlayer target = getOnlinePlayer(targetUuid);
        if (target == null) {
            return;
        }
        getStates().setGroup(target, null);
        NetManager.sendToClient(target, new JoinedGroupPacket(null, false));
        target.displayClientMessage(Component.translatable("message.voicechat.kicked_from_group", Component.literal(group.getName())), false);
        actor.displayClientMessage(Component.translatable("message.voicechat.group_kick_success", target.getName()), true);
    }

    public void promoteMember(ServerPlayer actor, Group group, @Nullable UUID targetUuid) {
        if (!group.isOwner(actor.getUUID())) {
            actor.displayClientMessage(Component.translatable("message.voicechat.group_owner_only"), true);
            return;
        }
        if (targetUuid == null) {
            return;
        }
        if (!isMember(targetUuid, group)) {
            actor.displayClientMessage(Component.translatable("message.voicechat.group_not_in_group"), true);
            return;
        }
        if (group.isAdmin(targetUuid)) {
            return;
        }
        group.addAdmin(targetUuid);
        broadcastAddGroup(group);
        ServerPlayer target = getOnlinePlayer(targetUuid);
        actor.displayClientMessage(Component.translatable("message.voicechat.group_promote_success", target != null ? target.getName() : Component.literal(targetUuid.toString())), true);
    }

    public void demoteMember(ServerPlayer actor, Group group, @Nullable UUID targetUuid) {
        if (!group.isOwner(actor.getUUID())) {
            actor.displayClientMessage(Component.translatable("message.voicechat.group_owner_only"), true);
            return;
        }
        if (targetUuid == null) {
            return;
        }
        if (!isMember(targetUuid, group)) {
            actor.displayClientMessage(Component.translatable("message.voicechat.group_not_in_group"), true);
            return;
        }
        if (group.isOwner(targetUuid) || !group.getAdmins().contains(targetUuid)) {
            return;
        }
        group.removeAdmin(targetUuid);
        broadcastAddGroup(group);
        ServerPlayer target = getOnlinePlayer(targetUuid);
        actor.displayClientMessage(Component.translatable("message.voicechat.group_demote_success", target != null ? target.getName() : Component.literal(targetUuid.toString())), true);
    }

    public void transferOwnership(ServerPlayer actor, Group group, @Nullable UUID targetUuid) {
        if (!group.isOwner(actor.getUUID())) {
            actor.displayClientMessage(Component.translatable("message.voicechat.group_owner_only"), true);
            return;
        }
        if (targetUuid == null || targetUuid.equals(actor.getUUID())) {
            return;
        }
        if (!isMember(targetUuid, group)) {
            actor.displayClientMessage(Component.translatable("message.voicechat.group_not_in_group"), true);
            return;
        }
        group.setOwner(targetUuid);
        broadcastAddGroup(group);
        ServerPlayer target = getOnlinePlayer(targetUuid);
        actor.displayClientMessage(Component.translatable("message.voicechat.group_transfer_success", target != null ? target.getName() : Component.literal(targetUuid.toString())), true);
    }

    public void renameGroup(ServerPlayer actor, Group group, @Nullable String name) {
        if (!group.isAdmin(actor.getUUID())) {
            actor.displayClientMessage(Component.translatable("message.voicechat.group_no_permission"), true);
            return;
        }
        if (name == null || !Voicechat.GROUP_REGEX.matcher(name).matches()) {
            actor.displayClientMessage(Component.translatable("message.voicechat.invalid_group_name"), true);
            return;
        }
        group.setName(name);
        broadcastAddGroup(group);
        actor.displayClientMessage(Component.translatable("message.voicechat.group_rename_success", Component.literal(group.getName())), true);
    }

    public void setGroupPassword(ServerPlayer actor, Group group, @Nullable String name) {
        if (!group.isAdmin(actor.getUUID())) {
            actor.displayClientMessage(Component.translatable("message.voicechat.group_no_permission"), true);
            return;
        }
        String password = name;
        if (password != null && password.isEmpty()) {
            password = null;
        }
        if (password != null && !Voicechat.GROUP_REGEX.matcher(password).matches()) {
            actor.displayClientMessage(Component.translatable("message.voicechat.invalid_group_name"), true);
            return;
        }
        group.setPassword(password);
        broadcastAddGroup(group);
        actor.displayClientMessage(Component.translatable("message.voicechat.group_password_success", password == null ? Component.translatable("message.voicechat.group_password_none") : Component.literal(password)), true);
    }

    public void deleteGroup(ServerPlayer actor, Group group) {
        if (!group.isOwner(actor.getUUID())) {
            actor.displayClientMessage(Component.translatable("message.voicechat.group_owner_only"), true);
            return;
        }
        List<UUID> memberUuids = getStates().getStates().stream().filter(state -> state.hasGroup() && group.getId().equals(state.getGroup())).map(PlayerState::getUuid).toList();
        for (UUID memberUuid : memberUuids) {
            ServerPlayer member = getOnlinePlayer(memberUuid);
            if (member == null) {
                continue;
            }
            getStates().setGroup(member, null);
            NetManager.sendToClient(member, new JoinedGroupPacket(null, false));
            member.displayClientMessage(Component.translatable("message.voicechat.group_deleted", Component.literal(group.getName())), false);
        }
        groups.remove(group.getId());
        broadcastRemoveGroup(group.getId());
        actor.displayClientMessage(Component.translatable("message.voicechat.group_delete_success"), true);
    }

    private void transferOwnershipIfNeeded(Group group) {
        UUID newOwnerUuid = getStates().getStates().stream()
                .filter(state -> state.hasGroup() && group.getId().equals(state.getGroup()))
                .map(PlayerState::getUuid)
                .findFirst().orElse(null);
        if (newOwnerUuid != null) {
            group.setOwner(newOwnerUuid);
            broadcastAddGroup(group);
        }
    }

    public boolean removeGroup(UUID groupId) {
        Group group = groups.get(groupId);
        if (group == null) {
            return false;
        }

        PlayerStateManager manager = getStates();
        if (manager.getStates().stream().anyMatch(state -> state.hasGroup() && state.getGroup().equals(groupId))) {
            return false;
        }

        if (PluginManager.instance().onRemoveGroup(group)) {
            return false;
        }

        groups.remove(groupId);
        broadcastRemoveGroup(groupId);
        // TODO Handle kicking players from group instead of preventing it
        return true;
    }

    @Nullable
    public Group getGroup(UUID groupID) {
        return groups.get(groupID);
    }

    private void broadcastAddGroup(Group group) {
        AddGroupPacket packet = new AddGroupPacket(group.toClientGroup());
        server.getServer().getPlayerList().getPlayers().forEach(p -> NetManager.sendToClient(p, packet));
    }

    private void broadcastRemoveGroup(UUID group) {
        RemoveGroupPacket packet = new RemoveGroupPacket(group);
        server.getServer().getPlayerList().getPlayers().forEach(p -> NetManager.sendToClient(p, packet));
    }

    @Nullable
    public Group getPlayerGroup(ServerPlayer player) {
        PlayerState state = server.getPlayerStateManager().getState(player.getUUID());
        if (state == null) {
            return null;
        }
        UUID groupId = state.getGroup();
        if (groupId == null) {
            return null;
        }
        return getGroup(groupId);
    }

    public Map<UUID, Group> getGroups() {
        return groups;
    }
}
