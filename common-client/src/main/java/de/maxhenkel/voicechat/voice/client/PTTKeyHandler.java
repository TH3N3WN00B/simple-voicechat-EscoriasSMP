package de.maxhenkel.voicechat.voice.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.maxhenkel.voicechat.intercompatibility.ClientCompatibilityManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;

public class PTTKeyHandler {

    private boolean pttKeyDown;
    private boolean whisperKeyDown;
    private boolean announceKeyDown;

    public PTTKeyHandler() {
        ClientCompatibilityManager.INSTANCE.onKeyboardEvent(this::onKeyboardEvent);
        ClientCompatibilityManager.INSTANCE.onMouseEvent(this::onMouseEvent);
    }

    public void onKeyboardEvent(KeyEvent keyEvent) {
        InputConstants.Key pttKey = ClientCompatibilityManager.INSTANCE.getBoundKeyOf(KeyEvents.KEY_PTT);
        if (pttKey.getValue() != -1 && !pttKey.getType().equals(InputConstants.Type.MOUSE)) {
            pttKeyDown = InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), pttKey.getValue());
        }

        InputConstants.Key whisperKey = ClientCompatibilityManager.INSTANCE.getBoundKeyOf(KeyEvents.KEY_WHISPER);
        if (whisperKey.getValue() != -1 && !whisperKey.getType().equals(InputConstants.Type.MOUSE)) {
            whisperKeyDown = InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), whisperKey.getValue());
        }

        InputConstants.Key announceKey = ClientCompatibilityManager.INSTANCE.getBoundKeyOf(KeyEvents.KEY_ANNOUNCE);
        if (announceKey.getValue() != -1 && !announceKey.getType().equals(InputConstants.Type.MOUSE)) {
            announceKeyDown = InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), announceKey.getValue());
        }
    }

    public void onMouseEvent(MouseButtonInfo mouseButtonInfo, int action) {
        InputConstants.Key pttKey = ClientCompatibilityManager.INSTANCE.getBoundKeyOf(KeyEvents.KEY_PTT);
        if (pttKey.getValue() != -1 && pttKey.getType().equals(InputConstants.Type.MOUSE) && pttKey.getValue() == mouseButtonInfo.button()) {
            pttKeyDown = action != 0;
        }

        InputConstants.Key whisperKey = ClientCompatibilityManager.INSTANCE.getBoundKeyOf(KeyEvents.KEY_WHISPER);
        if (whisperKey.getValue() != -1 && whisperKey.getType().equals(InputConstants.Type.MOUSE) && whisperKey.getValue() == mouseButtonInfo.button()) {
            whisperKeyDown = action != 0;
        }

        InputConstants.Key announceKey = ClientCompatibilityManager.INSTANCE.getBoundKeyOf(KeyEvents.KEY_ANNOUNCE);
        if (announceKey.getValue() != -1 && announceKey.getType().equals(InputConstants.Type.MOUSE) && announceKey.getValue() == mouseButtonInfo.button()) {
            announceKeyDown = action != 0;
        }
    }

    public boolean isPTTDown() {
        return pttKeyDown;
    }

    public boolean isWhisperDown() {
        return whisperKeyDown;
    }

    public boolean isAnnounceDown() {
        return announceKeyDown;
    }

    public boolean isAnyDown() {
        return pttKeyDown || whisperKeyDown || announceKeyDown;
    }

}
