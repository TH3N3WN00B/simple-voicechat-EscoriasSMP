package de.maxhenkel.voicechat.gui.group;

import de.maxhenkel.voicechat.gui.VoiceChatScreenBase;
import de.maxhenkel.voicechat.net.ClientServerNetManager;
import de.maxhenkel.voicechat.net.ManageGroupPacket;
import de.maxhenkel.voicechat.voice.client.ClientManager;
import de.maxhenkel.voicechat.voice.client.ClientPlayerStateManager;
import de.maxhenkel.voicechat.voice.common.ClientGroup;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GroupManageMemberScreen extends VoiceChatScreenBase {

    protected static final Component TITLE = Component.translatable("gui.voicechat.group_manage_member.title");

    protected static final int HEADER_SIZE = 16;
    protected static final int PADDING = 7;
    protected static final int BUTTON_HEIGHT = 20;
    protected static final int BUTTON_GAP = 4;

    protected final ClientGroup group;
    protected final UUID memberUuid;
    protected final String memberName;

    public GroupManageMemberScreen(ClientGroup group, UUID memberUuid, String memberName) {
        super(TITLE, 220, 0);
        this.group = group;
        this.memberUuid = memberUuid;
        this.memberName = memberName;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();

        ClientPlayerStateManager stateManager = ClientManager.getPlayerStateManager();
        UUID own = stateManager.getOwnID();
        boolean owner = group.isOwner(own);
        boolean admin = group.isAdmin(own);
        boolean isSelf = own.equals(memberUuid);
        boolean isOwnerMember = group.isOwner(memberUuid);
        boolean isAdminMember = group.getAdmins().contains(memberUuid);

        List<Button> actions = new ArrayList<>();

        if (!isSelf && !isOwnerMember && admin) {
            actions.add(Button.builder(Component.translatable("message.voicechat.group_kick_button"), button -> {
                ClientServerNetManager.sendToServer(new ManageGroupPacket(ManageGroupPacket.OP_KICK, memberUuid, null));
                minecraft.setScreen(new GroupScreen(group));
            }).bounds(0, 0, 100, BUTTON_HEIGHT).build());
        }
        if (!isSelf && !isOwnerMember && !isAdminMember && owner) {
            actions.add(Button.builder(Component.translatable("message.voicechat.group_promote_button"), button -> {
                ClientServerNetManager.sendToServer(new ManageGroupPacket(ManageGroupPacket.OP_PROMOTE, memberUuid, null));
                minecraft.setScreen(new GroupScreen(group));
            }).bounds(0, 0, 100, BUTTON_HEIGHT).build());
        }
        if (!isSelf && isAdminMember && owner) {
            actions.add(Button.builder(Component.translatable("message.voicechat.group_demote_button"), button -> {
                ClientServerNetManager.sendToServer(new ManageGroupPacket(ManageGroupPacket.OP_DEMOTE, memberUuid, null));
                minecraft.setScreen(new GroupScreen(group));
            }).bounds(0, 0, 100, BUTTON_HEIGHT).build());
        }
        if (!isSelf && !isOwnerMember && owner) {
            actions.add(Button.builder(Component.translatable("message.voicechat.group_transfer_button"), button -> {
                ClientServerNetManager.sendToServer(new ManageGroupPacket(ManageGroupPacket.OP_TRANSFER, memberUuid, null));
                minecraft.setScreen(new GroupScreen(group));
            }).bounds(0, 0, 100, BUTTON_HEIGHT).build());
        }
        actions.add(Button.builder(Component.translatable("message.voicechat.back"), button -> {
            minecraft.setScreen(new GroupScreen(group));
        }).bounds(0, 0, 100, BUTTON_HEIGHT).build());

        ySize = HEADER_SIZE + PADDING + actions.size() * (BUTTON_HEIGHT + BUTTON_GAP) + PADDING;
        guiLeft = (width - xSize) / 2;
        guiTop = (height - ySize) / 2;

        for (int i = 0; i < actions.size(); i++) {
            Button button = actions.get(i);
            button.setX(guiLeft + PADDING);
            button.setY(guiTop + HEADER_SIZE + PADDING + i * (BUTTON_HEIGHT + BUTTON_GAP));
            button.setWidth(xSize - PADDING * 2);
            addRenderableWidget(button);
        }
    }

    @Override
    public void renderForeground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        MutableComponent title = Component.translatable("gui.voicechat.group_manage_member.title", Component.literal(memberName));
        guiGraphics.drawString(font, title, guiLeft + xSize / 2 - font.width(title) / 2, guiTop + 4, FONT_COLOR, false);
    }

}