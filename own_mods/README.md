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
