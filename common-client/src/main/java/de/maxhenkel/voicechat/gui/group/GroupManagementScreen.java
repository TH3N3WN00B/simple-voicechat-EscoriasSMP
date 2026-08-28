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

public class GroupManagementScreen extends VoiceChatScreenBase {

    protected static final Component TITLE = Component.translatable("gui.voicechat.group_management.title");

    protected static final int HEADER_SIZE = 16;
    protected static final int PADDING = 7;
    protected static final int BUTTON_HEIGHT = 20;
    protected static final int BUTTON_GAP = 4;

    protected final ClientGroup group;

    public GroupManagementScreen(ClientGroup group) {
        super(TITLE, 220, 0);
        this.group = group;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();

        ClientPlayerStateManager stateManager = ClientManager.getPlayerStateManager();
        UUID own = stateManager.getOwnID();
        boolean owner = group.isOwner(own);
        boolean admin = group.isAdmin(own);

        List<Button> actions = new ArrayList<>();

        if (admin) {
            actions.add(Button.builder(Component.translatable("message.voicechat.group_rename_button"), button -> {
                minecraft.setScreen(new GroupInputScreen.GroupNameInputScreen(group, group.getName()));
            }).bounds(0, 0, 100, BUTTON_HEIGHT).build());
            actions.add(Button.builder(Component.translatable("message.voicechat.group_password_button"), button -> {
                minecraft.setScreen(new GroupInputScreen.GroupPasswordInputScreen(group));
            }).bounds(0, 0, 100, BUTTON_HEIGHT).build());
        }
        if (owner) {
            actions.add(Button.builder(Component.translatable("message.voicechat.group_delete_button"), button -> {
                ClientServerNetManager.sendToServer(new ManageGroupPacket(ManageGroupPacket.OP_DELETE, null, null));
                minecraft.setScreen(null);
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
        MutableComponent title = Component.translatable("gui.voicechat.group_management.title", Component.literal(group.getName()));
        guiGraphics.drawString(font, title, guiLeft + xSize / 2 - font.width(title) / 2, guiTop + 4, FONT_COLOR, false);
    }

}