package de.maxhenkel.voicechat.gui.group;

import de.maxhenkel.voicechat.Voicechat;
import de.maxhenkel.voicechat.gui.GameProfileUtils;
import de.maxhenkel.voicechat.gui.volume.AdjustVolumeSlider;
import de.maxhenkel.voicechat.gui.volume.PlayerVolumeEntry;
import de.maxhenkel.voicechat.gui.widgets.ListScreenEntryBase;
import de.maxhenkel.voicechat.voice.client.ClientManager;
import de.maxhenkel.voicechat.voice.client.ClientPlayerStateManager;
import de.maxhenkel.voicechat.voice.client.ClientVoicechat;
import de.maxhenkel.voicechat.voice.common.ClientGroup;
import de.maxhenkel.voicechat.voice.common.PlayerState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.UUID;

public class GroupEntry extends ListScreenEntryBase<GroupEntry> {

    protected static final Identifier TALK_OUTLINE = Identifier.fromNamespaceAndPath(Voicechat.MODID, "icons/talk_outline");
    protected static final Identifier SPEAKER_OFF = Identifier.fromNamespaceAndPath(Voicechat.MODID, "icons/speaker_small_off");

    protected static final int PADDING = 4;
    protected static final int BG_FILL = ARGB.color(255, 74, 74, 74);
    protected static final int PLAYER_NAME_COLOR = ARGB.color(255, 255, 255, 255);

    protected final Screen parent;
    protected final Minecraft minecraft;
    protected PlayerState state;
    protected final ClientGroup group;
    protected final AdjustVolumeSlider volumeSlider;

    public GroupEntry(Screen parent, PlayerState state, ClientGroup group) {
        this.parent = parent;
        this.minecraft = Minecraft.getInstance();
        this.state = state;
        this.group = group;
        this.volumeSlider = new AdjustVolumeSlider(0, 0, 100, 20, new PlayerVolumeEntry.AdjustPlayerVolumeEntry(state.getUuid(), state.getName()));
        this.children.add(volumeSlider);
    }

    @Override
    public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float delta) {
        int left = getContentX();
        int top = getContentY();
        int width = getContentWidth();
        int height = getContentHeight();
        guiGraphics.fill(left, top, left + width, top + height, BG_FILL);

        guiGraphics.pose().pushMatrix();
        int outlineSize = height - PADDING * 2;

        guiGraphics.pose().translate(left + PADDING, top + PADDING);
        float scale = outlineSize / 10F;
        guiGraphics.pose().scale(scale, scale);

        if (!state.isDisabled()) {
            ClientVoicechat client = ClientManager.getClient();
            if (client != null && client.getTalkCache().isTalking(state.getUuid())) {
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, TALK_OUTLINE, 16, 16, 0, 0, 0, 0, 10, 10);
            }
        }

        PlayerSkin skin = GameProfileUtils.getSkin(state.getUuid());
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, skin.body().texturePath(), 1, 1, 8, 8, 8, 8, 64, 64);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, skin.body().texturePath(), 1, 1, 40, 8, 8, 8, 64, 64);

        if (state.isDisabled()) {
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().translate(1F, 1F);
            guiGraphics.pose().scale(0.5F, 0.5F);
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, SPEAKER_OFF, 0, 0, 16, 16);
            guiGraphics.pose().popMatrix();
        }
        guiGraphics.pose().popMatrix();

        Component name = Component.literal(state.getName());
        guiGraphics.drawString(minecraft.font, name, left + PADDING + outlineSize + PADDING, top + height / 2 - minecraft.font.lineHeight / 2, PLAYER_NAME_COLOR, false);

        int nameWidth = PADDING + outlineSize + PADDING + minecraft.font.width(name) + PADDING;
        if (group != null && group.isOwner(state.getUuid())) {
            Component ownerTag = Component.translatable("message.voicechat.group_owner_tag").withStyle(ChatFormatting.GOLD);
            guiGraphics.drawString(minecraft.font, ownerTag, left + nameWidth, top + height / 2 - minecraft.font.lineHeight / 2, ARGB.color(255, 255, 170, 0), false);
        } else if (group != null && group.isAdmin(state.getUuid())) {
            Component adminTag = Component.translatable("message.voicechat.group_admin_tag").withStyle(ChatFormatting.AQUA);
            guiGraphics.drawString(minecraft.font, adminTag, left + nameWidth, top + height / 2 - minecraft.font.lineHeight / 2, ARGB.color(255, 85, 200, 255), false);
        }

        if (hovered && !ClientManager.getPlayerStateManager().getOwnID().equals(state.getUuid())) {
            volumeSlider.setWidth(Math.min(width - (PADDING + outlineSize + PADDING + minecraft.font.width(name) + PADDING + PADDING), 100));
            volumeSlider.setPosition(left + (width - volumeSlider.getWidth() - PADDING), top + (height - volumeSlider.getHeight()) / 2);
            volumeSlider.render(guiGraphics, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent evt, boolean bl) {
        boolean handled = super.mouseClicked(evt, bl);
        if (!handled && evt.button() == 0 && canManage(state)) {
            openManageScreen();
            return true;
        }
        return handled;
    }

    private boolean canManage(PlayerState state) {
        ClientPlayerStateManager stateManager = ClientManager.getPlayerStateManager();
        UUID own = stateManager.getOwnID();
        if (group == null || own.equals(state.getUuid())) {
            return false;
        }
        boolean owner = group.isOwner(own);
        boolean admin = group.isAdmin(own);
        if (!owner && !admin) {
            return false;
        }
        return owner || !group.isOwner(state.getUuid());
    }

    private void openManageScreen() {
        minecraft.setScreen(new GroupManageMemberScreen(group, state.getUuid(), state.getName()));
    }

    public PlayerState getState() {
        return state;
    }

    public void setState(PlayerState state) {
        this.state = state;
    }
}
