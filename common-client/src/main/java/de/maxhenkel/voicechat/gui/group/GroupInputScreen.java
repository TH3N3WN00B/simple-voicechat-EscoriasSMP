package de.maxhenkel.voicechat.gui.group;

import de.maxhenkel.voicechat.Voicechat;
import de.maxhenkel.voicechat.gui.VoiceChatScreenBase;
import de.maxhenkel.voicechat.net.ClientServerNetManager;
import de.maxhenkel.voicechat.net.ManageGroupPacket;
import de.maxhenkel.voicechat.voice.common.ClientGroup;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import javax.annotation.Nullable;

public abstract class GroupInputScreen extends VoiceChatScreenBase {

    protected static final int PADDING = 7;
    protected static final int LABEL_GAP = 5;

    protected final ClientGroup group;
    protected EditBox input;
    protected Button ok;

    protected GroupInputScreen(Component title, int xSize, int ySize, ClientGroup group) {
        super(title, xSize, ySize);
        this.group = group;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();

        input = new EditBox(font, guiLeft + PADDING, guiTop + HEADER_SIZE + font.lineHeight + LABEL_GAP, xSize - PADDING * 2, 14, Component.empty());
        input.setMaxLength(Voicechat.MAX_GROUP_NAME_LENGTH);
        input.setFilter(s -> s.isEmpty() || Voicechat.GROUP_REGEX.matcher(s).matches());
        input.setValue(getInitialValue());
        addRenderableWidget(input);

        ok = Button.builder(Component.translatable("message.voicechat.confirm"), button -> {
            onConfirm(input.getValue());
        }).bounds(guiLeft + PADDING, guiTop + ySize - 27, xSize - PADDING * 2, 20).build();
        addRenderableWidget(ok);
    }

    protected abstract String getInitialValue();

    protected abstract void onConfirm(String value);

    protected abstract Component label();

    @Override
    public void tick() {
        super.tick();
        ok.active = isInputValid(input.getValue());
    }

    protected boolean isInputValid(String value) {
        return Voicechat.GROUP_REGEX.matcher(value).matches();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        renderTransparentBackground(guiGraphics);
    }

    @Override
    public void renderForeground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        guiGraphics.drawString(font, label(), guiLeft + PADDING + 1, guiTop + HEADER_SIZE, FONT_COLOR, false);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.isEscape()) {
            minecraft.setScreen(new GroupScreen(group));
            return true;
        }
        if (super.keyPressed(keyEvent)) {
            return true;
        }
        if (keyEvent.isConfirmation()) {
            onConfirm(input.getValue());
            return true;
        }
        return false;
    }

    public static class GroupNameInputScreen extends GroupInputScreen {

        protected static final Component TITLE = Component.translatable("gui.voicechat.group_rename.title");
        protected static final Component LABEL = Component.translatable("gui.voicechat.group_rename.label");
        protected final String initialValue;

        public GroupNameInputScreen(ClientGroup group, String initialValue) {
            super(TITLE, 195, 76, group);
            this.initialValue = initialValue;
        }

        @Override
        protected String getInitialValue() {
            return initialValue;
        }

        @Override
        protected void onConfirm(String value) {
            ClientServerNetManager.sendToServer(new ManageGroupPacket(ManageGroupPacket.OP_RENAME, null, value));
            minecraft.setScreen(new GroupScreen(group));
        }

        @Override
        protected Component label() {
            return LABEL;
        }
    }

    public static class GroupPasswordInputScreen extends GroupInputScreen {

        protected static final Component TITLE = Component.translatable("gui.voicechat.group_password.title");
        protected static final Component LABEL = Component.translatable("gui.voicechat.group_password.label");

        public GroupPasswordInputScreen(ClientGroup group) {
            super(TITLE, 195, 76, group);
        }

        @Override
        protected String getInitialValue() {
            return "";
        }

        @Override
        protected boolean isInputValid(String value) {
            return value.isEmpty() || Voicechat.GROUP_REGEX.matcher(value).matches();
        }

        @Override
        protected void onConfirm(String value) {
            ClientServerNetManager.sendToServer(new ManageGroupPacket(ManageGroupPacket.OP_SET_PASSWORD, null, value.isEmpty() ? null : value));
            minecraft.setScreen(new GroupScreen(group));
        }

        @Override
        protected Component label() {
            return LABEL;
        }
    }

    protected static final int HEADER_SIZE = 16;

}