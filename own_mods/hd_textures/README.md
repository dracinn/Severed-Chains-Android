# hd_textures — HD submap environment packs

Replaces a submap's composited background and foreground cutouts with
higher-resolution textures via `SubmapEnvironmentTextureEvent`. No engine
changes: the mod swaps in pack PNGs at load time, so collision, camera
bounds and cutout alignment stay pixel-exact at any texture scale.

## Pack layout

Drop PNGs into `mods/hd_textures/` (macOS: `~/Documents/Severed Chains/mods/hd_textures/`):

```
bg/<bin>/<fileIndex>.png      composited background, any integer scale of retail size
fg/<bin>/<fileIndex>/<i>.png  full-canvas foreground cutout <i>, same canvas scale as bg
```

`<bin>/<fileIndex>` is the `SECT/DRGN<bin>.BIN/<fileIndex>` directory the
submap's environment loads from — the mod resolves it per cut with the same
`NewRootStruct.getDrgnFile` logic the engine uses (early/late-game variants
included).

Foregrounds are **full-canvas** textures: same dimensions as the background,
with the cutout composited at its screen position and transparency
elsewhere — the engine overlays them on the background for parallax/depth.

## Authoring a pack

1. Extract retail-resolution reference PNGs (composited exactly as the
   engine does — `tools/bgextract` replicates `RetailSubmap.prepareEnv`):

   ```
   ./gradlew extractSubmapBackgrounds \
     -PbgExtractFilesDir="$HOME/Documents/Severed Chains/files" \
     -PbgExtractOutDir=build/hd-pack
   # single cut: -PbgExtractCuts=12
   ```

   Output mirrors the pack layout plus a `manifest.json` cut→variant map.
2. Upscale the PNGs offline (RealESRGAN, waifu2x, …) at a uniform integer
   scale and copy into `mods/hd_textures/` preserving the directory
   structure.
3. Enable `hd_textures` in the game's mod list. Missing files fall back to
   the retail composite, so partial packs work.

## Notes

- `bg` and `fg` for the same `(bin, fileIndex)` must share the same scale —
  foregrounds are positioned in background canvas space.
- Submaps that animate their background via palette/texture swaps will
  freeze to the static pack image; leave those variants out of the pack.
- Covers field submaps only — battle stages, the world map and FMVs are
  different pipelines.
- Textures load with linear filtering (`Texture.filteredPng`); any scale
  works, 4x is the sweet spot for ~1440p output.
