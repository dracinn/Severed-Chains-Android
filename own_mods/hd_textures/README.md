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

   Output mirrors the pack layout plus a `manifest.json` cut→variant map
   and `scenes.md` — every scene in story order (Intro → Disc 4) listing
   each cut's early/late variants and foreground counts, so a pack can be
   built scene-by-scene.
2. **Upscale** — `tools/bgextract/upscale.sh` runs the whole pipeline for a
   list of variants (RealESRGAN `4x-UltraSharp-fp16` by default — Kim2091's
   UltraSharp, the sharpest/most-detailed model for painted game bgs;
   `realesrgan-x4plus` is smoother, `realesr-animevideov3` softer):

   ```
   tools/bgextract/upscale.sh build/hd-pack /tmp/pack 21/694 22/1108 ...
   # MODEL=… SCALE=… REALESRGAN=… env overrides; see the script header
   ```

   The script also handles **foreground alpha prep**, which the engine's
   `standard.fsh` requires: a texel discards only when `a==0` *and*
   (`useTextureAlpha` on, or `rgb==0`), and fg quads draw with
   `useTextureAlpha` off. AI upscalers break both halves — partial alpha on
   the matte edge and `rgb=0` bled into content. The pipeline in
   `tools/bgextract/BinarizeAlpha.java` fixes it (the script runs these for
   you):

   ```
   # BEFORE upscaling — fill rgb under transparent px with nearest content
   # colour ("alpha bleed") so the AI doesn't drag matte-edge black inward
   java tools/bgextract/BinarizeAlpha.java fill <extracted fg dir>
   # upscale (e.g. realesrgan-ncnn-vulkan -s 4)
   # AFTER — threshold alpha back to 0/255 and dilate the matte ~1-2px to
   # cover the AI's edge erosion (d leaves rgb=0 under a==0 for the discard)
   java tools/bgextract/BinarizeAlpha.java bin <upscaled fg dir> 128 2
   ```

   Backgrounds have no alpha and need no prep.
3. Copy the results into `mods/hd_textures/` preserving the directory
   structure.
4. Enable `hd_textures` in the game's mod list. Missing files fall back to
   the retail composite, so partial packs work.

## Notes

- `bg` and `fg` for the same `(bin, fileIndex)` must share the same scale —
  foregrounds are positioned in background canvas space.
- Submaps that animate their background via palette/texture swaps will
  freeze to the static pack image; leave those variants out of the pack.
- Covers field submaps only — battle stages, the world map and FMVs are
  different pipelines.
- Backgrounds load with linear filtering (`Texture.filteredPng`); any scale
  works, 4x is the sweet spot for ~1440p output. Foregrounds load
  unfiltered (`Texture.png`) — linear sampling blends transparent matte
  neighbours into non-zero-alpha texels, which the shader then draws as a
  dark halo around every cutout.
