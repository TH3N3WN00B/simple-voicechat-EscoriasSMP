[Modrinth](https://modrinth.com/mod/simple-voice-chat)
|
[CurseForge](https://legacy.curseforge.com/minecraft/mc-mods/simple-voice-chat)
|
[Discord](https://discord.gg/4dH2zwTmyX)
|
[Wiki](https://modrepo.de/minecraft/voicechat/wiki)
|
[FAQ](https://modrepo.de/minecraft/voicechat/faq)
|
[Credits](https://modrepo.de/minecraft/voicechat/credits)
|
[API](https://modrepo.de/minecraft/voicechat/api)

# Simple Voice Chat

A proximity voice chat for Minecraft with a variety of [addons](https://modrepo.de/minecraft/voicechat/addons) that offer additional features and functionalities.

:warning: **NOTE** This mod requires special setup on the server in order to work.
Please read the [wiki](https://modrepo.de/minecraft/voicechat/wiki/setup) for more information.

---

## EscoriasSMP Fork

This repository is a **fork** of [Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat) (branch `1.21.11`) by [Max Henkel (henkelmax)](https://github.com/henkelmax), customized for the **EscoriasSMP** server.

**All credit for the original mod goes to `henkelmax` and the Simple Voice Chat contributors.** This fork only adds a few changes on top of the original:

### Changes in this fork

- **Improved group management**: groups now have an *owner* and *administrators*. Owners and admins can
  - promote/demote group members (`/voicechat group promote <player>` / `/voicechat group demote <player>`),
  - kick members (`/voicechat group kick <player>`),
  - transfer ownership (`/voicechat group transfer <player>`),
  - rename the group (`/voicechat group rename <name>`),
  - and change the group password after creation (`/voicechat group password <password>`).
  No group member can be removed without the owner's or an admin's action.
- **Server-side mute**: operators can mute a player with `/voicechat mute <player>` (their voice is ignored) and unmute them with `/voicechat unmute <player>`.
- **Megaphone**: a wider, configurable voice range (`megaphone_distance` in the server config, `128` by default).
  Bind the *Megaphone* key and hold it while talking to be heard further away than normal proximity voice.
- **Announce mode (voice broadcast)**: `/voicechat announce` is an **operator-only** toggle. While enabled, the player's voice is heard by **everyone** on the server — including players inside groups or isolated rooms. No chat message is sent, it is a pure voice broadcast.
  Requires the `voicechat.announce` permission (granted to operators by default). The legacy client-side *Announce* key still works as an alternative while held.
- **Performance & stability**:
  - O(1) UDP sender lookup on the server (reverse address index), replacing a per-packet scan over all connections.
  - Bounded UDP packet queue with backpressure — no memory blow-up under overload.
  - Bounded caches and stale state is cleaned up on player disconnects (cooldown timers, plugin audio listeners, mute/announce state).
  - Client audio channels are fully closed on voice disconnect (no leaked OpenAL speakers/buffers), and less garbage is allocated in the hot audio paths.

Everything else (proximity voice, groups, permissions, Opus/RNNoise, audio recording, etc.) works exactly like in the original Simple Voice Chat.

**Original project:** https://github.com/henkelmax/simple-voice-chat

---

<p align="center">
    <a href="https://discord.gg/4dH2zwTmyX">
        <img src="assets/discord.svg" width="300">
    </a>
    <br/>
    <i>Please join the Discord if you have questions!</i>
</p>

## Downloads

- [Fabric](https://modrinth.com/mod/simple-voice-chat/versions?l=fabric)
- [NeoForge](https://modrinth.com/mod/simple-voice-chat/versions?l=neoforge)
- [Forge](https://modrinth.com/mod/simple-voice-chat/versions?l=forge)
- [Bukkit/Spigot/Paper](https://modrinth.com/plugin/simple-voice-chat/versions?l=bukkit)
- [Quilt](https://modrinth.com/mod/simple-voice-chat/versions?l=quilt)
- [Velocity](https://modrinth.com/mod/simple-voice-chat/versions?l=velocity)
- [BungeeCord/Waterfall](https://modrinth.com/mod/simple-voice-chat/versions?l=bungeecord)

## Features

- Push to talk
- Voice activation
- Proximity voice chat
- Password protected groups
- Automatic voice activity detection
- Automatic microphone gain adjustment
- [Opus codec](https://opus-codec.org/)
- [RNNoise](https://jmvalin.ca/demo/rnnoise/) recurrent neural network noise suppression
- OpenAL audio
- Cross compatibility between Fabric, NeoForge, Forge, Quilt, Bukkit, Spigot and Paper
- Support for Velocity, BungeeCord and Waterfall
- Compatibility with [ModMenu](https://modrinth.com/mod/modmenu) (Use [ClothConfig](https://modrinth.com/mod/cloth-config) for a better configuration UI)
- Configurable push to talk key
- Microphone and speaker test playback
- Configurable voice distance
- Whispering
- Individual player volume adjustment
- Microphone amplification
- 3D sound
- AES encryption
- Audio recording with separate audio tracks
- A powerful [API](https://modrepo.de/minecraft/voicechat/api)
- Many [addons](https://modrepo.de/minecraft/voicechat/addons)

## Icons

|                  Icon                   | Description                                           |
|:---------------------------------------:|-------------------------------------------------------|
|      ![](assets/icon_talking.png)       | You are talking                                       |
|     ![](assets/icon_whispering.png)     | You are whispering                                    |
|   ![](assets/icon_other_talking.png)    | Player is talking                                     |
|  ![](assets/icon_other_whispering.png)  | Player is whispering                                  |
|  ![](assets/icon_microphone_muted.png)  | Microphone muted                                      |
|      ![](assets/icon_disabled.png)      | Voice chat disabled                                   |
|    ![](assets/icon_disconnected.png)    | Voice chat not connected<br/>Voice chat not installed |

## The GUI

You can open the voice chat GUI by pressing the `V` key.
This allows you to open the settings, group chats, mute yourself, disable the voice chat, start/stop a recording and hide all icons.

![](assets/screenshot_voice_chat_menu.png)

### Group Chats

Group chats allow you to talk to players that are not in your vicinity.
To open the group chat interface, either press the group button in the voice chat GUI or just press the group key.

To create a new group, just type a name in the text field and press the button at the bottom.

![](assets/screenshot_create_group.png)

Creating or joining a group will bring you into the group chat interface.
You will also see the heads of the group members in the top left corner of your screen.
Talking players will be outlined.
You can disable these icons by pressing the third button from the left.

![](assets/screenshot_group.png)

Players that are not in a group will see a group icon next to your head, indicating that you are in a group.

You can invite players to your group chat by entering the command `/voicechat invite <playername>` or from the social interactions screen.

### Settings

You can access the voice chat settings by pressing the `V` key and pressing the settings button.

This menu offers the ability to set up your voice chat audio settings.

By clicking the microphone button, you can test your microphone activation.

![](assets/screenshot_settings.png)

## Important Notes

You need to open a port on the server. This is port `24454` `UDP` by default.
Without opening this port, the voice chat will not work.
This port can be changed in the server config.
More information [here](https://modrepo.de/minecraft/voicechat/wiki/setup).

The voice chat is encrypted, but we don't guarantee the security of it. Use at your own risk!
