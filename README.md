# Severed Chains Android

> [!WARNING]
> **This is an unofficial fork.** It is not affiliated with, endorsed by, or supported by the [Legend of Dragoon Modding community](https://github.com/Legend-of-Dragoon-Modding) or the official Severed Chains project. Please report Android-specific issues on this repository — not upstream.

## About Severed Chains

An Android port of [Severed Chains](https://github.com/Legend-of-Dragoon-Modding/Severed-Chains) — the fan-made project that rebuilt The Legend of Dragoon from scratch in Java (not an emulator), with a full modding API.

All credit for the engine goes to the [Legend of Dragoon Modding community](https://github.com/Legend-of-Dragoon-Modding/Severed-Chains). Visit their [player guide](https://legendofdragoon.org/projects/severed-chains/) and [Discord](https://discord.com/channels/307164262063669248/318595603636551701) for more about the original project. This fork keeps the game complete and faithful on Android while making it comfortable to play on a touchscreen.

**Copyright Information**

Even though it is not an emulator, Severed Chains cannot be played without the user providing the LoD disk images. Assets are extracted from the ROMs at runtime. This codebase does not include any official Legend of Dragoon code or assets.

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

**Shared folders — mods, saves, and ISOs**

Game data normally lives in the app's private storage, which isn't reachable without root — so once you grant storage access, `Documents/Severed Chains/` gets three public folders:

- `Mods` — drop `.jar` mods in with any file manager; they're copied into the engine's mods dir at startup and appear in the in-game mod list. Removing a jar here doesn't uninstall it (the internal copy stays) — disable or remove mods from the game's own mod menu.
- `Saves` — your campaign saves live here once access is granted, so you can back them up, restore them, and keep them even if the app is uninstalled. Saves made before granting access are moved across automatically on next launch.
- `ISOs` — disc images are detected here automatically: the setup screen and the unpacker scan it alongside the internal folder. Discs picked through the file picker are copied into `ISOs` when access is granted (and ones already inside the app move across on next launch), so you can add, remove, or replace them from a file manager or PC.

To grant access: the setup screen asks once on first run, and its **Enable access** button is always there as a fallback (or grant *All files access* for Severed Chains in system Settings → Apps → Special app access). Uses *All files access* on Android 11+ and the legacy storage permission on Android 9 and below (Android 10 sits in the scoped-storage gap — its file-manager route doesn't apply). If access is revoked while saves live in `Saves`, the game falls back to its internal folder — re-grant to see them again.


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
