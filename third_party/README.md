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

To pull upstream changes:

```
git subtree pull --prefix=third_party/Stardust-Indicators \
  https://github.com/avionanx/Stardust-Indicators main --squash
```
