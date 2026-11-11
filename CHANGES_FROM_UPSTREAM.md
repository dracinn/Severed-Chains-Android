# Changes from upstream Severed Chains

This document describes everything this fork does differently from
[upstream Severed Chains](https://github.com/Legend-of-Dragoon-Modding/Severed-Chains),
from the start of the port to today. It covers *what* changed and *why*,
not how it's implemented — the goal is that upstream stays the source of
truth for the game itself, and everything listed here is either required
to run on Android or is an optional extra on top.

## The port itself

- The game runs natively on Android as a real app — not an emulator, not
  a wrapper around the desktop build. It's the same engine, same game
  logic, same mods, built in-tree so upstream updates can be merged
  continuously.
- Upstream's source is preserved: Android adaptations are applied at
  build time over pristine upstream files rather than by forking the
  game's code, so upstream bug fixes and features keep flowing in.
- Minimum supported Android version is 8.0 (API 26); 64-bit ARM and
  x86_64 devices are both built.

## First-run setup and storage

- A dedicated setup screen walks new players through the first launch:
  granting storage access and adding disc images, with clear progress
  while the game unpacks.
- First-run unpacking is roughly ten times faster than upstream's
  default path, using low-memory streaming reads instead of loading
  whole files at once — important on phone storage and memory limits.
- Once storage access is granted, `Documents/Severed Chains/` exposes
  three shared folders you can reach from a file manager or PC:
  - `ISOs` — drop disc images here and they're picked up automatically.
  - `Saves` — your saves live here, so they survive uninstalls and can
    be backed up. Existing internal saves migrate across on next launch.
  - `mods` — the in-game mod loader also scans this folder.
- Shared `logs` folder under the same directory collects crash and
  debug logs you can actually attach to a bug report — including
  hardware and GL capability details for diagnosing device issues.
- If storage access is never granted (or is revoked), everything falls
  back to app-private storage and the game still works.

## On-screen touch controls

- A full virtual controller designed for the game: face-button diamond,
  shoulder buttons along the top edge, Select/Start centered at the
  bottom, plus movement controls on the left.
- Movement control is your choice: a floating analog stick that anchors
  wherever your thumb lands, a classic d-pad with slide and diagonal
  support, or both at once.
- The face-button cluster and d-pad can be dragged anywhere and
  pinch-resized; the layout persists between sessions.
- A controls-opacity slider lets you dim the overlay so it doesn't
  cover dialogue and menus.
- Face buttons can show PlayStation, Xbox, or Switch-style glyphs —
  and the same choice drives both the on-screen buttons and the
  physical-controller prompts in game, so the labels always match what
  you're holding or touching.
- Controls restyle to a dark-glass-and-gold look that fits the game's
  art direction.

## Portrait and landscape

- Both orientations are fully supported. In portrait the game view sits
  at the top of the screen at its proper 4:3 aspect and the touch
  controls occupy the space below — no wasted black band, no squished
  image.
- Graphics settings are remembered per orientation, so you can run
  different resolution and widescreen options in portrait vs landscape
  without them fighting.
- Touch-control layouts are also per-orientation: portrait uses a fixed
  layout fitted to the lower area, while landscape keeps your custom
  movable layout.
- Widescreen options behave sensibly per orientation — 3D widescreen
  is available in landscape (where there's actually horizontal room)
  instead of being locked off.
- Touch input stays consistent across rotation: controls release
  cleanly when you rotate mid-press and hit zones always line up with
  what's drawn.

## Display and rendering

- Text renders at the screen's native resolution regardless of the
  internal render resolution, so menus and dialogue are sharp on
  high-DPI phone screens even when the 3D scene runs at PS1-style
  resolutions.
- Extra internal-resolution options fill in the 240p integer-scaling
  ladder, so you can pick the sharpest option for your exact display.
- A sharpening option improves upscaling when the internal render
  resolution is below native.
- The status bar and navigation bar are fully transparent and the game
  draws edge-to-edge, including around display cutouts.
- A MangoHud-style performance overlay option shows FPS and frame
  timing for tuning settings.

## Device compatibility work

- Two renderer paths: the default needs OpenGL ES 3.2, and a
  compatibility renderer supports devices stuck on ES 3.1 (e.g. older
  Mali GPUs) — selectable from the setup screen.
- Optional ANGLE backend for devices whose native GL drivers misbehave.
- Self-healing GL fallbacks: if the renderer crashes, the app
  remembers and steps down to a safer path on next launch instead of
  crash-looping.
- Multiple fixes for Qualcomm Adreno and Mali driver quirks: depth
  buffer precision, degenerate-triangle draws, shader I/O differences,
  and PS1-accurate depth ordering on both renderer paths.
- Workarounds for Android platform gaps that upstream relies on:
  hidden-API restrictions on newer Android versions, missing modern
  Java APIs on older ones, and filesystem behaviour differences.

## Physical controllers

- Full gamepad support like upstream, plus haptic rumble where the
  game uses it.
- Nintendo-style controllers show the correct A/B/X/Y lettering
  instead of the Xbox layout.
- Physical controllers and touch controls coexist — touching the
  screen doesn't disable the gamepad and vice versa.

## Platform behaviour

- The game pauses its audio properly when the app goes to the
  background and resumes when you return.
- Version display on the title screen shows the app release
  (`3.0.0-<release>`) instead of an upstream CI build tag.
- An update checker lets the game tell you when a new release is out.
- Signed release APKs, a debug APK, and a performance-tuned APK are
  published for every tag; the version number derives from the release
  tag so installs always upgrade cleanly.
- Launcher icon set drawn from the game's character art.

## Built-in mods

Everything here loads through the normal mod system — nothing is
hardcoded into the game.

- **Dragoon Modifier** (LoD Modding community) — difficulty modes,
  monster HP bars, enrage bonuses, and more.
- **Additional Additions** (LoD Modding community) — loads custom
  additions made with the Additional Additions Editor.
- **Icon Turn Order** (Zychronix) — turn-order display with character
  and monster portraits in battle.
- **Lloyd Mod** (Zychronix) — Lloyd as a fully playable character.
  This fork adds a bonus: entering the Konami code on the submap
  (up up down down left right left right B A) recruits him early.
- **Stardust Indicator** (this fork) — a pulsing gold sparkle marks
  uncollected stardust on the submap, plus a Stardust Tracker entry in
  the Goods menu showing what you've found.
- **Save Point Heal** (this fork) — save points restore the active
  party's HP and MP when you walk into them, no menu needed; works
  with mod-added party members too.

## Stability and diagnostics

- Engine crashes write a shared crash log with the tail of the debug
  log attached, in the shared logs folder — no adb needed to report a
  crash.
- Routine engine logging stays out of logcat so real errors aren't
  buried.
- Compatibility fixes covering Android 8 through 15, verified on
  real devices and emulators across API levels.

## Testing and development tooling

- A file-driven menu automation interface (debug builds only) allows
  scripted navigation of the in-game menus for automated testing —
  reporting where you are, which control is focused, and driving
  navigation without screenshots.
