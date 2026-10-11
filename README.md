# Severed Chains Android

An Android port of [Severed Chains](https://github.com/Legend-of-Dragoon-Modding/Severed-Chains) — the fan-made project that rebuilt The Legend of Dragoon from scratch in Java (not an emulator), with a full modding API.

This fork keeps the game complete and faithful on Android while making it comfortable to play on a touchscreen.

## Getting the game

1. Download the latest `android-release.apk` from the [Releases page](https://github.com/dracinn/Severed-Chains-Android/releases) and install it.
2. On first launch, the app walks you through adding your Legend of Dragoon disc images (ISOs/BINs).
3. Pick a campaign and play — saves, mods, and settings carry between runs.

## What's different in this port

**Touch controls built for mobile**

- On-screen controls styled after a modern glass-and-gold controller: shoulder buttons along the top, a face-button diamond, and Select/Start centered at the bottom.
- The left joystick floats to wherever your thumb lands instead of staying in a fixed spot.
- Face buttons can show PlayStation, Xbox, or Switch-style labels — your choice in the in-game Controls menu.
- An opacity slider lets you see through the controls when they'd cover text.
- The face-button group can be moved and resized to fit your hands — drag the middle of the diamond to move it, pinch to resize.

**Physical controllers** are still fully supported, just like upstream.

**Mods baked in**

- **Dragoon Modifier** — the official difficulty and customization mod. Pick from several difficulty modes (including Hard and Hell), show monster HP bars, enable enrage bonuses, and more.
- **Dragoon XP Share** — party members on the bench who have a dragoon spirit gain a share of dragoon XP too, adjustable in the config.

Dragoon Modifier is the [LoD Modding community](https://github.com/Legend-of-Dragoon-Modding/sc-dragoon-modifier)'s own mod, kept up to date with its upstream source.


**Versioning and updates**

- Each release reports its real version number, so installs upgrade cleanly.
- Launcher icon featuring Dart, because it's LoD after all.

## If the game won't start

Severed Chains renders with OpenGL ES 3.2, with an automatic fallback to a compatibility mode on GPUs that don't fully support it (e.g. older Mali). If the app closes itself while starting up, **just open it again** — a launch that dies mid-initialization is remembered, and the next one automatically retries in the safer mode.

If it still won't start:

- Try the **ANGLE** graphics driver: enable Developer Options, then *Developer options → Preferred graphics driver → select Severed Chains → ANGLE*. This runs OpenGL on top of Vulkan and often works around broken vendor drivers.
- Report the problem with the logs in `Documents/Severed Chains/logs/` — crash and debug logs there include your device model, GPU, and driver versions, which is what's needed to support new hardware. Debug APKs on the Releases page record the full engine log.

## Controls

Gamepads are supported out of the box — connect one and pick it in the in-game options. Onscreen controls are the default for touch play and can be styled and arranged as described above.

## Copyright Information

Even though it is not an emulator, Severed Chains cannot be played without the user providing the LoD disk images. Assets are extracted from the ROMs at runtime. This codebase does not include any official Legend of Dragoon code or assets.

## Credits

All credit for the engine goes to the [Legend of Dragoon Modding community](https://github.com/Legend-of-Dragoon-Modding/Severed-Chains). Visit their [player guide](https://legendofdragoon.org/projects/severed-chains/) and [Discord](https://discord.com/channels/307164262063669248/318595603636551701) for more about the original project.
