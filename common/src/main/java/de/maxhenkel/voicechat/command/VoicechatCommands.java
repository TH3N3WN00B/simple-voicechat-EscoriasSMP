package de.maxhenkel.voicechat.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import de.maxhenkel.voicechat.Voicechat;
import de.maxhenkel.voicechat.intercompatibility.CommonCompatibilityManager;
import de.maxhenkel.voicechat.permission.Permission;
import de.maxhenkel.voicechat.permission.PermissionManager;
import de.maxhenkel.voicechat.voice.common.PlayerState;
import de.maxhenkel.voicechat.voice.server.ClientConnection;
import de.maxhenkel.voicechat.voice.server.Group;
import de.maxhenkel.voicechat.voice.server.PingManager;
import de.maxhenkel.voicechat.voice.server.Server;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class VoicechatCommands {

    public static final String VOICECHAT_COMMAND = "voicechat";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> literalBuilder = Commands.literal(VOICECHAT_COMMAND);

        literalBuilder.executes(commandSource -> help(dispatcher, commandSource));
        literalBuilder.then(Commands.literal("help").executes(commandSource -> help(dispatcher, commandSource)));

        literalBuilder.then(Commands.literal("test").requires((commandSource) -> checkPermission(commandSource, PermissionManager.INSTANCE.ADMIN_PERMISSION)).then(Commands.argument("target", EntityArgument.player()).executes((commandSource) -> {
            if (checkNoVoicechat(commandSource)) {
                return 0;
            }
            ServerPlayer player = EntityArgument.getPlayer(commandSource, "target");
            Server server = Voicechat.SERVER.getServer();
            if (server == null) {
                commandSource.getSource().sendSuccess(() -> Component.translatable("message.voicechat.voice_chat_unavailable"), false);
                return 1;
            }

            if (!Voicechat.SERVER.isCompatible(player)) {
                commandSource.getSource().sendSuccess(() -> Component.translatable("message.voicechat.player_no_voicechat", player.getDisplayName(), CommonCompatibilityManager.INSTANCE.getModName()), false);
                return 1;
            }

            ClientConnection clientConnection = server.getConnections().get(player.getUUID());
            if (clientConnection == null) {
                commandSource.getSource().sendSuccess(() -> Component.translatable("message.voicechat.client_not_connected"), false);
                return 1;
            }
            try {
                commandSource.getSource().sendSuccess(() -> Component.translatable("message.voicechat.sending_ping"), false);

                server.getPingManager().sendPing(clientConnection, 500, 10, new PingManager.PingListener() {

                    @Override
                    public void onPong(int attempts, long pingMilliseconds) {
                        if (attempts <= 1) {
                            commandSource.getSource().sendSuccess(() -> Component.translatable("message.voicechat.ping_received", pingMilliseconds), false);
                        } else {
                            commandSource.getSource().sendSuccess(() -> Component.translatable("message.voicechat.ping_received_attempt", pingMilliseconds, attempts), false);
                        }
                    }

                    @Override
                    public void onFailedAttempt(int attempts) {
                        commandSource.getSource().sendSuccess(() -> Component.translatable("message.voicechat.ping_retry"), false);
                    }

                    @Override
                    public void onTimeout(int attempts) {
                        commandSource.getSource().sendSuccess(() -> Component.translatable("message.voicechat.ping_timed_out", attempts), false);
                    }
                });
                commandSource.getSource().sendSuccess(() -> Component.translatable("message.voicechat.ping_sent_waiting"), false);
            } catch (Exception e) {
                commandSource.getSource().sendSuccess(() -> Component.translatable("message.voicechat.failed_to_send_ping", e.getMessage()), false);
                Voicechat.LOGGER.warn("Failed to send ping", e);
                return 1;
            }
            return 1;
        })));

        literalBuilder.then(Commands.literal("invite").then(Commands.argument("target", EntityArgument.player()).executes((commandSource) -> {
            if (checkNoVoicechat(commandSource)) {
                return 0;
            }
            ServerPlayer source = commandSource.getSource().getPlayerOrException();

            Server server = Voicechat.SERVER.getServer();
            if (server == null) {
                commandSource.getSource().sendSuccess(() -> Component.translatable("message.voicechat.voice_chat_unavailable"), false);
                return 1;
            }

            PlayerState state = server.getPlayerStateManager().getState(source.getUUID());

            if (state == null || !state.hasGroup()) {
                commandSource.getSource().sendSuccess(() -> Component.translatable("message.voicechat.not_in_group"), false);
                return 1;
            }

            ServerPlayer player = EntityArgument.getPlayer(commandSource, "target");
            Group group = server.getGroupManager().getGroup(state.getGroup());

            if (group == null) {
                return 1;
            }

            if (!Voicechat.SERVER.isCompatible(player)) {
                commandSource.getSource().sendSuccess(() -> Component.translatable("message.voicechat.player_no_voicechat", player.getDisplayName(), CommonCompatibilityManager.INSTANCE.getModName()), false);
                return 1;
            }

            String passwordSuffix = group.getPassword() == null ? "" : " \"" + group.getPassword() + "\"";
            player.sendSystemMessage(Component.translatable("message.voicechat.invite", source.getDisplayName(), Component.literal(group.getName()).withStyle(ChatFormatting.GRAY), ComponentUtils.wrapInSquareBrackets(Component.translatable("message.voicechat.accept_invite").withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/voicechat join " + group.getId().toString() + passwordSuffix)).withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.voicechat.accept_invite.hover"))))).withStyle(ChatFormatting.GREEN)));

            commandSource.getSource().sendSuccess(() -> Component.translatable("message.voicechat.invite_successful", player.getDisplayName()), false);

            return 1;
        })));

        literalBuilder.then(Commands.literal("join").then(Commands.argument("group_id", UuidArgument.uuid()).executes((commandSource) -> {
            if (checkNoVoicechat(commandSource)) {
                return 0;
            }
            UUID groupID = UuidArgument.getUuid(commandSource, "group_id");
            return joinGroupById(commandSource.getSource(), groupID, null);
        })));

        literalBuilder.then(Commands.literal("join").then(Commands.argument("group_id", UuidArgument.uuid()).then(Commands.argument("password", StringArgumentType.string()).executes((commandSource) -> {
            if (checkNoVoicechat(commandSource)) {
                return 0;
            }
            UUID groupID = UuidArgument.getUuid(commandSource, "group_id");
            String password = StringArgumentType.getString(commandSource, "password");
            return joinGroupById(commandSource.getSource(), groupID, password.isEmpty() ? null : password);
        }))));

        literalBuilder.then(Commands.literal("join").then(Commands.argument("group_name", StringArgumentType.string()).suggests(GroupNameSuggestionProvider.INSTANCE).executes((commandSource) -> {
            if (checkNoVoicechat(commandSource)) {
                return 0;
            }
            String groupName = StringArgumentType.getString(commandSource, "group_name");
            return joinGroupByName(commandSource.getSource(), groupName, null);
        })));

        literalBuilder.then(Commands.literal("join").then(Commands.argument("group_name", StringArgumentType.string()).suggests(GroupNameSuggestionProvider.INSTANCE).then(Commands.argument("password", StringArgumentType.string()).executes((commandSource) -> {
            if (checkNoVoicechat(commandSource)) {
                return 0;
            }
            String groupName = StringArgumentType.getString(commandSource, "group_name");
            String password = StringArgumentType.getString(commandSource, "password");
            return joinGroupByName(commandSource.getSource(), groupName, password.isEmpty() ? null : password);
        }))));

        literalBuilder.then(Commands.literal("leave").executes((commandSource) -> {
            if (checkNoVoicechat(commandSource)) {
                return 0;
            }
            if (!Voicechat.SERVER_CONFIG.groupsEnabled.get()) {
                commandSource.getSource().sendFailure(Component.translatable("message.voicechat.groups_disabled"));
                return 1;
            }

            Server server = Voicechat.SERVER.getServer();
            if (server == null) {
                commandSource.getSource().sendSuccess(() -> Component.translatable("message.voicechat.voice_chat_unavailable"), false);
                return 1;
            }
            ServerPlayer source = commandSource.getSource().getPlayerOrException();

            PlayerState state = server.getPlayerStateManager().getState(source.getUUID());
            if (state == null || !state.hasGroup()) {
                commandSource.getSource().sendSuccess(() -> Component.translatable("message.voicechat.not_in_group"), false);
                return 1;
            }

            server.getGroupManager().leaveGroup(source);
            commandSource.getSource().sendSuccess(() -> Component.translatable("message.voicechat.leave_successful"), false);
            return 1;
        }));

        literalBuilder.then(Commands.literal("group").then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.string()).executes((commandSource) -> {
            if (checkNoVoicechat(commandSource)) {
                return 0;
            }
            String name = StringArgumentType.getString(commandSource, "name");
            return createGroupCommand(commandSource.getSource(), name, null);
        }).then(Commands.argument("password", StringArgumentType.string()).executes((commandSource) -> {
            if (checkNoVoicechat(commandSource)) {
                return 0;
            }
            String name = StringArgumentType.getString(commandSource, "name");
            String password = StringArgumentType.getString(commandSource, "password");
            return createGroupCommand(commandSource.getSource(), name, password.isEmpty() ? null : password);
        })))));

        literalBuilder.then(Commands.literal("group").then(Commands.literal("list").executes((commandSource) -> {
            Server server = getServer(commandSource.getSource());
            if (server == null) {
                return 1;
            }
            for (Group group : server.getGroupManager().getGroups().values()) {
                long members = server.getPlayerStateManager().getStates().stream().filter(state -> state.hasGroup() && group.getId().equals(state.getGroup())).count();
                commandSource.getSource().sendSuccess(() -> Component.literal("- ").append(Component.literal(group.getName()).withStyle(ChatFormatting.GRAY)).append(Component.literal(" (%d%s)".formatted(members, group.getPassword() != null ? ", " + Component.translatable("message.voicechat.group_password_tag").getString() : ""))), false);
            }
            return 1;
        })));

        literalBuilder.then(Commands.literal("group").then(Commands.literal("kick").then(Commands.argument("target", EntityArgument.player()).executes((commandSource) -> {
            if (checkNoVoicechat(commandSource)) {
                return 0;
            }
            return manageGroupMember(commandSource.getSource(), EntityArgument.getPlayer(commandSource, "target").getUUID(), 0);
        }))));

        literalBuilder.then(Commands.literal("group").then(Commands.literal("promote").then(Commands.argument("target", EntityArgument.player()).executes((commandSource) -> {
            if (checkNoVoicechat(commandSource)) {
                return 0;
            }
            return manageGroupMember(commandSource.getSource(), EntityArgument.getPlayer(commandSource, "target").getUUID(), 1);
        }))));

        literalBuilder.then(Commands.literal("group").then(Commands.literal("demote").then(Commands.argument("target", EntityArgument.player()).executes((commandSource) -> {
            if (checkNoVoicechat(commandSource)) {
                return 0;
            }
            return manageGroupMember(commandSource.getSource(), EntityArgument.getPlayer(commandSource, "target").getUUID(), 2);
        }))));

        literalBuilder.then(Commands.literal("group").then(Commands.literal("transfer").then(Commands.argument("target", EntityArgument.player()).executes((commandSource) -> {
            if (checkNoVoicechat(commandSource)) {
                return 0;
            }
            return manageGroupMember(commandSource.getSource(), EntityArgument.getPlayer(commandSource, "target").getUUID(), 3);
        }))));

        literalBuilder.then(Commands.literal("group").then(Commands.literal("rename").then(Commands.argument("name", StringArgumentType.string()).executes((commandSource) -> {
            if (checkNoVoicechat(commandSource)) {
                return 0;
            }
            return manageGroupName(commandSource.getSource(), StringArgumentType.getString(commandSource, "name"), 4);
        }))));

        literalBuilder.then(Commands.literal("group").then(Commands.literal("password").executes((commandSource) -> {
            if (checkNoVoicechat(commandSource)) {
                return 0;
            }
            return manageGroupName(commandSource.getSource(), null, 6);
        }).then(Commands.argument("password", StringArgumentType.string()).executes((commandSource) -> {
            if (checkNoVoicechat(commandSource)) {
                return 0;
            }
            String password = StringArgumentType.getString(commandSource, "password");
            return manageGroupName(commandSource.getSource(), password.isEmpty() ? null : password, 6);
        }))));

        literalBuilder.then(Commands.literal("group").then(Commands.literal("delete").executes((commandSource) -> {
            if (checkNoVoicechat(commandSource)) {
                return 0;
            }
            Server server = getServer(commandSource.getSource());
            if (server == null) {
                return 1;
            }
            ServerPlayer source = commandSource.getSource().getPlayerOrException();
            Group group = server.getGroupManager().getPlayerGroup(source);
            if (group == null) {
                commandSource.getSource().sendSuccess(() -> Component.translatable("message.voicechat.not_in_group"), false);
                return 1;
            }
            server.getGroupManager().deleteGroup(source, group);
            return 1;
        })));

        literalBuilder.then(Commands.literal("list").executes((commandSource) -> {
            Server server = getServer(commandSource.getSource());
            if (server == null) {
                return 1;
            }
            MinecraftServer minecraftServer = server.getServer();
            for (ServerPlayer player : minecraftServer.getPlayerList().getPlayers()) {
                PlayerState state = server.getPlayerStateManager().getState(player.getUUID());
                MutableComponent message = Component.literal("- ").append(player.getDisplayName());
                if (state != null) {
                    if (state.isMuted()) {
                        message.append(" ").append(Component.translatable("message.voicechat.muted_tag").withStyle(ChatFormatting.DARK_RED));
                    }
                    if (state.isDisabled()) {
                        message.append(" ").append(Component.translatable("message.voicechat.disabled_tag").withStyle(ChatFormatting.GRAY));
                    }
                    if (state.hasGroup()) {
                        Group group = server.getGroupManager().getGroup(state.getGroup());
                        if (group != null) {
                            message.append(" ").append(Component.literal("[%s]".formatted(group.getName())).withStyle(ChatFormatting.GRAY));
                        }
                    }
                }
                if (!Voicechat.SERVER.isCompatible(player)) {
                    message.append(" ").append(Component.translatable("message.voicechat.no_voicechat_tag").withStyle(ChatFormatting.DARK_RED));
                }
                commandSource.getSource().sendSuccess(() -> message, false);
            }
            return 1;
        }));

        literalBuilder.then(Commands.literal("mute").requires((commandSource) -> checkPermission(commandSource, PermissionManager.INSTANCE.ADMIN_PERMISSION)).then(Commands.argument("target", EntityArgument.player()).executes((commandSource) -> {
            Server server = getServer(commandSource.getSource());
            if (server == null) {
                return 1;
            }
            ServerPlayer target = EntityArgument.getPlayer(commandSource, "target");
            server.setServerMuted(target, true);
            commandSource.getSource().sendSuccess(() -> Component.translatable("message.voicechat.mute_success", target.getName()), false);
            return 1;
        })));

        literalBuilder.then(Commands.literal("unmute").requires((commandSource) -> checkPermission(commandSource, PermissionManager.INSTANCE.ADMIN_PERMISSION)).then(Commands.argument("target", EntityArgument.player()).executes((commandSource) -> {
            Server server = getServer(commandSource.getSource());
            if (server == null) {
                return 1;
            }
            ServerPlayer target = EntityArgument.getPlayer(commandSource, "target");
            server.setServerMuted(target, false);
            commandSource.getSource().sendSuccess(() -> Component.translatable("message.voicechat.unmute_success", target.getName()), false);
            return 1;
        })));

        literalBuilder.then(Commands.literal("announce").requires((commandSource) -> checkPermission(commandSource, PermissionManager.INSTANCE.ANNOUNCE_PERMISSION)).executes((commandSource) -> {
            Server server = getServer(commandSource.getSource());
            if (server == null) {
                return 1;
            }
            ServerPlayer player;
            try {
                player = commandSource.getSource().getPlayerOrException();
            } catch (CommandSyntaxException e) {
                commandSource.getSource().sendFailure(Component.translatable("message.voicechat.announce_only_players"));
                return 1;
            }
            boolean announceMode = server.toggleAnnounceMode(player);
            commandSource.getSource().sendSuccess(() -> Component.translatable(announceMode ? "message.voicechat.announce_mode_enabled" : "message.voicechat.announce_mode_disabled"), false);
            return 1;
        }));

        dispatcher.register(literalBuilder);
    }

    private static int createGroupCommand(CommandSourceStack source, String name, @Nullable String password) throws CommandSyntaxException {
        Server server = joinGroup(source);
        if (server == null) {
            return 1;
        }
        if (!Voicechat.GROUP_REGEX.matcher(name).matches()) {
            source.sendFailure(Component.translatable("message.voicechat.invalid_group_name"));
            return 1;
        }
        if (password != null && !Voicechat.GROUP_REGEX.matcher(password).matches()) {
            source.sendFailure(Component.translatable("message.voicechat.invalid_group_name"));
            return 1;
        }
        ServerPlayer player = source.getPlayerOrException();
        server.getGroupManager().addGroup(new Group(UUID.randomUUID(), name, password, false, false, de.maxhenkel.voicechat.api.Group.Type.NORMAL, player.getUUID(), new HashSet<>()), player);
        source.sendSuccess(() -> Component.translatable("message.voicechat.create_successful", Component.literal(name).withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static int manageGroupMember(CommandSourceStack source, UUID targetUuid, int op) throws CommandSyntaxException {
        Server server = getServer(source);
        if (server == null) {
            return 1;
        }
        ServerPlayer actor = source.getPlayerOrException();
        Group group = server.getGroupManager().getPlayerGroup(actor);
        if (group == null) {
            source.sendFailure(Component.translatable("message.voicechat.not_in_group"));
            return 1;
        }
        switch (op) {
            case 0:
                server.getGroupManager().kickMember(actor, group, targetUuid);
                break;
            case 1:
                server.getGroupManager().promoteMember(actor, group, targetUuid);
                break;
            case 2:
                server.getGroupManager().demoteMember(actor, group, targetUuid);
                break;
            case 3:
                server.getGroupManager().transferOwnership(actor, group, targetUuid);
                break;
        }
        return 1;
    }

    private static int manageGroupName(CommandSourceStack source, @Nullable String name, int op) throws CommandSyntaxException {
        Server server = getServer(source);
        if (server == null) {
            return 1;
        }
        ServerPlayer actor = source.getPlayerOrException();
        Group group = server.getGroupManager().getPlayerGroup(actor);
        if (group == null) {
            source.sendFailure(Component.translatable("message.voicechat.not_in_group"));
            return 1;
        }
        if (op == 4) {
            server.getGroupManager().renameGroup(actor, group, name);
        } else if (op == 6) {
            server.getGroupManager().setGroupPassword(actor, group, name);
        }
        return 1;
    }

    private static Server getServer(CommandSourceStack source) {
        Server server = Voicechat.SERVER.getServer();
        if (server == null) {
            source.sendSuccess(() -> Component.translatable("message.voicechat.voice_chat_unavailable"), false);
            return null;
        }
        return server;
    }

    private static Server joinGroup(CommandSourceStack source) throws CommandSyntaxException {
        if (!Voicechat.SERVER_CONFIG.groupsEnabled.get()) {
            source.sendFailure(Component.translatable("message.voicechat.groups_disabled"));
            return null;
        }

        Server server = Voicechat.SERVER.getServer();
        if (server == null) {
            source.sendSuccess(() -> Component.translatable("message.voicechat.voice_chat_unavailable"), false);
            return null;
        }
        ServerPlayer player = source.getPlayerOrException();

        if (!PermissionManager.INSTANCE.GROUPS_PERMISSION.hasPermission(player)) {
            source.sendSuccess(() -> Component.translatable("message.voicechat.no_group_permission"), false);
            return null;
        }

        return server;
    }

    private static int joinGroupByName(CommandSourceStack source, String groupName, @Nullable String password) throws CommandSyntaxException {
        Server server = joinGroup(source);
        if (server == null) {
            return 1;
        }

        List<Group> groups = server.getGroupManager().getGroups().values().stream().filter(group -> group.getName().equals(groupName)).collect(Collectors.toList());

        if (groups.isEmpty()) {
            source.sendFailure(Component.translatable("message.voicechat.group_does_not_exist"));
            return 1;
        }

        if (groups.size() > 1) {
            source.sendFailure(Component.translatable("message.voicechat.group_name_not_unique"));
            return 1;
        }

        return joinGroup(source, server, groups.get(0).getId(), password);
    }

    private static int joinGroupById(CommandSourceStack source, UUID groupID, @Nullable String password) throws CommandSyntaxException {
        Server server = joinGroup(source);
        if (server == null) {
            return 1;
        }
        return joinGroup(source, server, groupID, password);
    }

    private static int joinGroup(CommandSourceStack source, Server server, UUID groupID, @Nullable String password) throws CommandSyntaxException {
        Group group = server.getGroupManager().getGroup(groupID);

        if (group == null) {
            source.sendFailure(Component.translatable("message.voicechat.group_does_not_exist"));
            return 1;
        }

        server.getGroupManager().joinGroup(group, source.getPlayerOrException(), password);
        source.sendSuccess(() -> Component.translatable("message.voicechat.join_successful", Component.literal(group.getName()).withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static int help(CommandDispatcher<CommandSourceStack> dispatcher, CommandContext<CommandSourceStack> commandSource) {
        if (checkNoVoicechat(commandSource)) {
            return 0;
        }
        CommandNode<CommandSourceStack> voicechatCommand = dispatcher.getRoot().getChild(VOICECHAT_COMMAND);
        Map<CommandNode<CommandSourceStack>, String> map = dispatcher.getSmartUsage(voicechatCommand, commandSource.getSource());
        for (Map.Entry<CommandNode<CommandSourceStack>, String> entry : map.entrySet()) {
            commandSource.getSource().sendSuccess(() -> Component.literal("/%s %s".formatted(VOICECHAT_COMMAND, entry.getValue())), false);
        }
        return map.size();
    }

    private static boolean checkNoVoicechat(CommandContext<CommandSourceStack> commandSource) {
        try {
            ServerPlayer player = commandSource.getSource().getPlayerOrException();
            if (Voicechat.SERVER.isCompatible(player)) {
                return false;
            }
            commandSource.getSource().sendFailure(Component.literal(Voicechat.TRANSLATIONS.voicechatNeededForCommandMessage.get().formatted(CommonCompatibilityManager.INSTANCE.getModName())));
            return true;
        } catch (Exception e) {
            commandSource.getSource().sendFailure(Component.literal(Voicechat.TRANSLATIONS.playerCommandMessage.get()));
            return true;
        }
    }

    private static boolean checkPermission(CommandSourceStack stack, Permission permission) {
        try {
            return permission.hasPermission(stack.getPlayerOrException());
        } catch (CommandSyntaxException e) {
            return stack.permissions().hasPermission(new net.minecraft.server.permissions.Permission.HasCommandLevel(PermissionLevel.ADMINS));
        }
    }

}
