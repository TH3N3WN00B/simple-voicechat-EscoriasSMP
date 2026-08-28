package de.maxhenkel.voicechat.voice.server;

import de.maxhenkel.voicechat.voice.common.ClientGroup;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Group {

    private UUID id;
    private String name;
    @Nullable
    private String password;
    private boolean persistent;
    private boolean hidden;
    private de.maxhenkel.voicechat.api.Group.Type type;
    @Nullable
    private UUID ownerUuid;
    private Set<UUID> admins;

    public Group(UUID id, String name, @Nullable String password, boolean persistent, boolean hidden, de.maxhenkel.voicechat.api.Group.Type type) {
        this.id = id;
        this.name = name;
        this.password = password;
        this.persistent = persistent;
        this.hidden = hidden;
        this.type = type;
        this.ownerUuid = null;
        this.admins = new HashSet<>();
    }

    public Group(UUID id, String name, @Nullable String password, boolean persistent, boolean hidden, de.maxhenkel.voicechat.api.Group.Type type, @Nullable UUID ownerUuid, Set<UUID> admins) {
        this(id, name, password, persistent, hidden, type);
        this.ownerUuid = ownerUuid;
        this.admins = admins == null ? new HashSet<>() : admins;
    }

    public Group(UUID id, String name, @Nullable String password, boolean persistent) {
        this(id, name, password, persistent, false, de.maxhenkel.voicechat.api.Group.Type.NORMAL);
    }

    public Group(UUID id, String name, @Nullable String password) {
        this(id, name, password, false);
    }

    public Group(UUID id, String name) {
        this(id, name, null);
    }

    public Group() {

    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Nullable
    public String getPassword() {
        return password;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public boolean isHidden() {
        return hidden;
    }

    public de.maxhenkel.voicechat.api.Group.Type getType() {
        return type;
    }

    public boolean isOpen() {
        return type == de.maxhenkel.voicechat.api.Group.Type.OPEN;
    }

    public boolean isNormal() {
        return type == de.maxhenkel.voicechat.api.Group.Type.NORMAL;
    }

    public boolean isIsolated() {
        return type == de.maxhenkel.voicechat.api.Group.Type.ISOLATED;
    }

    @Nullable
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwner(@Nullable UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
        if (ownerUuid != null) {
            admins.remove(ownerUuid);
        }
    }

    public boolean isOwner(UUID playerUuid) {
        return ownerUuid != null && ownerUuid.equals(playerUuid);
    }

    public boolean isAdmin(UUID playerUuid) {
        return admins.contains(playerUuid) || isOwner(playerUuid);
    }

    public Set<UUID> getAdmins() {
        return admins;
    }

    public List<UUID> getAdminList() {
        return new ArrayList<>(admins);
    }

    public void addAdmin(UUID playerUuid) {
        if (!isOwner(playerUuid)) {
            admins.add(playerUuid);
        }
    }

    public void removeAdmin(UUID playerUuid) {
        admins.remove(playerUuid);
    }

    public void setPassword(@Nullable String password) {
        this.password = password;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ClientGroup toClientGroup() {
        return new ClientGroup(id, name, password != null, persistent, hidden, type, ownerUuid, getAdminList());
    }

}
