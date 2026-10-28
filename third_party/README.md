# Vendored sources

## sc-dragoon-modifier (Dragoon Modifier)

Vendored from https://github.com/Legend-of-Dragoon-Modding/sc-dragoon-modifier
(subtree squash). The official difficulty/modifier mod.

Built into the app like the engine's own mods (`LodMod`, `TurnOrderMod`): its
`src/main/java` and `src/main/resources` are added to the shared sources and
classpath resources in `build.gradle` / `android/build.gradle`, and
`mods/dragoon_modifier/` (per-difficulty stat CSVs, portraits) is staged as
APK assets that `MainActivity` extracts to the working dir so the mod's
`Path.of("mods", "dragoon_modifier", ...)` reads resolve on device.

To pull upstream changes:

```
git subtree pull --prefix=third_party/sc-dragoon-modifier \
  https://github.com/Legend-of-Dragoon-Modding/sc-dragoon-modifier main --squash
```

## Additional-Additions (additional_additions mod)

Vendored from https://github.com/Legend-of-Dragoon-Modding/Additional-Additions
(AGPL-3.0, LICENSE included). Registers user-made additions loaded from
`.json` files in `mods/additional_additions/` (created under the working dir —
on Android that's the app files dir). Only the mod payload is vendored
(`src/main/java`, `src/main/resources`, `LICENSE`); the rest of that repo is a
dev-workspace mirror of Severed Chains. To update, re-copy those directories.
