# In-tree mods

Mods in this directory are **this fork's own** — written and maintained here,
not vendored from third parties (those live under `../third_party/`). They are
free to redistribute and share the `third_party` staging layout:

```
own_mods/<name>/
  src/main/java/        compiled into the shared source set; `@Mod` classes are
                        picked up by the annotation index at build time
  src/main/resources/   classpath resources (lang files, etc.)
  mods/<modid>/         runtime assets; staged into APK assets and extracted to
                        the app working dir, where mods read them via
                        Path.of("mods", "<modid>", ...)
```

## stardust_indicator

Renders a pulsing gold sparkle over every uncollected stardust on the current
submap. Stardust world positions are keyed by submap cut in
`StardustIndicatorMod.STARDUST`; collected stardust are filtered via
`scriptFlags2` and removed live on `ScriptFlags2ChangedEvent`. The sparkle is
queued into the world render batch as a camera-facing billboard, so character
models and terrain occlude it naturally.

**Stardust tracker**: the mod also adds a "Stardust Tracker" entry to the Goods
menu — a two-column `ItemList` checklist of all 50 stardust matching the goods
screen's chrome. Found stardust show the mod's sparkle atlas icon in the row's
icon slot; missing stardust show an empty slot, and the description pane gives
each location's hint. Entries are read live from `scriptFlags2`. On Android the
goods row and its selection handler are injected into `GoodsScreen` by a
staged-source transform in `android/build.gradle` (`sharedLiteralTransforms`);
on other builds the same screen opens from Options → Stardust Indicator →
"Stardust Tracker" via the `stardust_tracker` config entry's edit control.

Sparkle frames in `mods/stardustindicator/sparkle/` are procedurally generated
(`tools/gen_sparkle.py`) — the art is original to this fork.

## portrait_pack

Substitutes generated 384px portraits for the stock 48px glyph-sheet
faces everywhere a retail character portrait renders. `mods/portraitpack/*.png`
are generated from official Sony promotional media references in the
late-90s pre-rendered CG style — not direct crops or upscales. Eight use the
legendofdragoon.org official Sony fan-kit profile renders as low-denoise
img2img structural references. Miranda is absent from that kit and uses her
correct official blonde/red-armored 84x96 portrait, neurally restored 4x
with Real-ESRGAN before img2img so the low-denoise pass sees real edges
instead of pixel blocks; the RPGFan
"Artwork-022" image is Lenus, not Miranda, and is not used.
`tools/gen_portraits.py` reproduces the set with DreamShaper 8 used only as
low-denoise restoration/detail synthesis over the official references; the
exact selected strengths and seeds are recorded in the script. The mod packs
them into a dedicated
`TextureAtlas` (the shared 512x512 atlas can't hold nine 384px images);
`getPortraitIcon` falls back to the stock atlas icon for characters without HD
art, so desktop builds and modded characters degrade cleanly.

Two staged-source transform families in `android/build.gradle` do the
substitution:

- atlas-path sites (`BattleHud`, `SItem`, `EquipmentShopExtension`,
  `UseItemScreen`, `CharacterCard`, `PostBattleScreen`, `CharSwapScreen`) swap
  `getTextureAtlas().getIcon(...)` for `PortraitPackMod.getPortraitIcon(...)`;
- the glyph path in `Menus.renderUi` calls `PortraitPackMod.renderPortrait`
  per metrics quad. Portrait glyph entries bundle several quads — face copies
  sampled from different texture pages plus partial tiles — so the hook draws
  the HD icon on every full-face (48x48 at 0,0) quad and suppresses the rest.
  `glyph_04` is the retail character index, resolved via `Legacy.CHAR_IDS`
  (save cards render with no live `GameState52c`).
