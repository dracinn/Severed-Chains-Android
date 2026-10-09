# Vendored sources

## Stardust-Indicators

Vendored from https://github.com/avionanx/Stardust-Indicators (subtree squash)
at commit e8ebfa3 ("Update to devbuild").

Built into the app like the engine's own mods (`LodMod`, `TurnOrderMod`): its
`src/main/java` and `src/main/resources` are added to the shared sources and
classpath resources in `build.gradle` / `android/build.gradle`, and
`mods/stardustindicators/sparkle/` is staged as APK assets that
`MainActivity` extracts to the working dir so the mod's
`Path.of("mods", "stardustindicators", ...)` reads resolve on device.

## tlot (The Legend of Tides)

Vendored from https://github.com/avionanx/tlot (subtree squash).

Built into the app the same way: `src/main/java` and `src/main/resources`
join the shared sources/classpath resources, and `mods/tlot/` (models,
textures, item scripts) is staged as APK assets extracted to the working
dir so the mod's `Path.of("mods", "tlot", ...)` reads resolve on device.

To pull upstream changes:

```
git subtree pull --prefix=third_party/Stardust-Indicators \
  https://github.com/avionanx/Stardust-Indicators main --squash
git subtree pull --prefix=third_party/tlot \
  https://github.com/avionanx/tlot main --squash
```
