import legend.core.gpu.Rect4i;
import legend.core.gpu.VramTextureSingle;
import legend.core.gpu.VramTextureLoader;
import legend.game.submap.EnvironmentFile;
import legend.game.submap.EnvironmentTextureMetrics24;
import legend.game.types.NewRootStruct;
import legend.game.submap.SubmapCutInfo;
import legend.game.tim.Tim;
import legend.game.unpacker.FileData;
import legend.game.unpacker.Loader;
import legend.game.unpacker.Unpacker;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts composited submap environment textures for an HD texture pack.
 *
 * <p>Replicates {@code RetailSubmap.prepareEnv} on the CPU: for every submap
 * cut variant (early/late game file selection) it resolves the TIMs, applies
 * palettes, and composites the background plus each full-canvas foreground
 * cutout, writing drop-in PNGs.</p>
 *
 * <p>Output layout (what the hd_textures mod reads):
 * <pre>
 *   &lt;out&gt;/bg/&lt;bin&gt;/&lt;fileIndex&gt;.png      composited background
 *   &lt;out&gt;/fg/&lt;bin&gt;/&lt;fileIndex&gt;/&lt;slot&gt;.png  full-canvas foreground cutout
 *   &lt;out&gt;/manifest.json                      cut → variant mapping
 *   &lt;out&gt;/scenes.md                          scene index in story order
 * </pre>
 *
 * Usage: BgExtract &lt;filesDir&gt; &lt;outDir&gt; [cut ...]
 */
public final class BgExtract {
  private BgExtract() { }

  private record Metrics(int tpage, int u, int v, int w, int h, int offsetX, int offsetY) { }

  private record Variant(int bin, int fileIndex) {
    String key() {
      return this.bin + "/" + this.fileIndex;
    }
  }

  /** Which variants a cut resolved to; {@code late} is null when identical to early or absent. */
  private record CutResult(int cut, Variant early, Variant late) { }

  /** Mirror of RetailSubmap.cutToSubmap_800d610c — retail data, update if upstream changes. */
  private static final int[] CUT_TO_SUBMAP = {
    1, 1, 1, 1, 1, 2, 2, 2, 3, 3, 3, 3, 3, 4, 4, 4,
    4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
    4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 6, 6, 6,
    6, 6, 6, 6, 6, 6, 6, 1, 4, 3, 8, 7, 7, 7, 7, 7,
    7, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,
    8, 8, 8, 8, 8, 8, 9, 9, 9, 9, 9, 9, 9, 9, 9, 10,
    10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 11, 11, 11, 11, 11, 11,
    10, 10, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12,
    12, 12, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 14, 14, 14, 14,
    14, 14, 14, 14, 14, 14, 14, 14, 14, 15, 15, 15, 15, 15, 15, 15,
    15, 15, 15, 15, 15, 15, 15, 16, 16, 16, 16, 17, 17, 17, 17, 17,
    17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 18, 18, 18, 18, 18,
    18, 18, 18, 18, 18, 18, 18, 18, 18, 19, 19, 19, 19, 19, 19, 19,
    19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 20, 20, 20,
    20, 20, 20, 20, 20, 20, 20, 21, 21, 21, 19, 19, 20, 20, 20, 22,
    22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 23, 23, 23, 23, 23,
    23, 23, 23, 22, 20, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 25,
    25, 25, 25, 25, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 27,
    27, 27, 27, 27, 27, 27, 27, 27, 27, 28, 28, 28, 28, 29, 29, 29,
    29, 29, 29, 29, 29, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
    30, 27, 27, 31, 31, 31, 31, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 33, 33, 33, 33, 33, 33, 33, 33, 34, 34, 34, 34, 34,
    34, 34, 34, 34, 34, 34, 34, 34, 34, 34, 34, 34, 28, 28, 28, 35,
    35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 36, 36, 37, 37, 37,
    37, 37, 37, 37, 37, 34, 37, 37, 37, 38, 38, 38, 38, 38, 38, 38,
    38, 38, 38, 38, 38, 38, 39, 39, 39, 39, 39, 39, 39, 40, 40, 40,
    40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 41, 41, 41,
    41, 42, 42, 42, 42, 42, 42, 42, 42, 43, 43, 43, 43, 43, 43, 43,
    43, 43, 43, 43, 43, 43, 43, 43, 43, 44, 44, 44, 44, 44, 44, 44,
    44, 45, 45, 45, 58, 58, 58, 58, 58, 58, 58, 58, 58, 46, 46, 46,
    46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46,
    46, 46, 46, 46, 46, 46, 46, 46, 47, 58, 58, 58, 58, 58, 48, 48,
    49, 49, 49, 49, 49, 49, 49, 49, 49, 49, 49, 49, 49, 49, 49, 49,
    50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 51, 51, 51, 51, 51,
    51, 51, 51, 51, 51, 51, 51, 51, 51, 48, 48, 48, 48, 48, 48, 48,
    48, 56, 52, 53, 53, 53, 53, 53, 53, 54, 54, 54, 54, 54, 54, 54,
    54, 54, 54, 54, 54, 54, 54, 55, 55, 55, 55, 55, 55, 55, 55, 55,
    55, 55, 54, 53, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56,
    56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 8,
    2, 2, 10, 10, 9, 53, 28, 8, 10, 10, 14, 14, 14, 14, 14, 14,
    4, 4, 4, 4, 4, 4, 14, 14, 49, 49, 58, 58, 58, 58, 58, 58,
    13, 10, 18, 18, 18, 53, 20, 20, 20, 20, 20, 20, 20, 20, 32, 32,
    58, 58, 10, 57, 57, 57, 28, 58, 58, 58, 58, 58, 58, 58, 58, 58,
    58, 58, 58, 58, 36, 36, 3, 3, 3, 5, 20, 56, 53, 53, 20, 57,
    57, 51, 51, 51, 51, 19, 32, 54, 54, 54, 54, 51, 13, 50, 50, 50,
    30, 51, 51, 36, 36, 3, 10, 56, 56, 56, 56, 56, 56, 56, 56, 56,
    56, 56, 56, 56, 5, 5, 3, 3, 19, 48, 48, 48, 48, 48, 48, 48,
    48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48,
    48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48,
    48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 0,
  };

  /** Mirror of SItem.submapNames_8011c108 — retail data, update if upstream changes. */
  private static final String[] SUBMAP_NAMES = {
    "", "Forest", "Forest", "Seles", "Hellena Prison",
    "Prairie", "Cave", "", "Bale", "Indels Castle",
    "Town of Hoax", "Marshland", "Vol. Villude", "Nest of Dragon", "Lohan",
    "Shirley's Shrine", "", "Kazas", "Black Castle", "Fletz",
    "Twin Castle", "Barrens", "Donau", "Valley", "Giganto Home",
    "", "The Queen Fury", "Phantom Ship", "Lidiera", "Undersea Cavern",
    "Fueno", "Prison Island", "Furni", "Evergreen Frst", "Deningrad",
    "Crystal Palace", "Neet", "Wingly Forest", "Forbidden Land", "",
    "Mortal Dr Mt.", "", "Kashua Glacier", "Flanvel Tower", "Snowfield",
    "Fort Magrad", "Vellweb", "", "Death Frontier", "Ulara",
    "Zenebatos", "Mayfil", "", "Rouge", "Aglis",
    "Divine Tree", "Moon", "Intro Forest", "",
  };

  public static void main(final String[] args) throws IOException {
    if(args.length < 2) {
      System.err.println("Usage: BgExtract <filesDir> <outDir> [cut ...]");
      System.exit(1);
    }

    Unpacker.ROOT = Path.of(args[0]).toAbsolutePath();
    final Path out = Path.of(args[1]).toAbsolutePath();

    final List<Integer> cuts = new ArrayList<>();
    for(int i = 2; i < args.length; i++) {
      cuts.add(Integer.parseInt(args[i]));
    }

    final NewRootStruct newRoot = new NewRootStruct(Loader.loadFileSync("SUBMAP/NEWROOT.RDT"));

    final Map<String, Variant> extracted = new LinkedHashMap<>();
    final Map<String, Integer> fgCounts = new LinkedHashMap<>();
    final List<CutResult> results = new ArrayList<>();
    final StringBuilder manifest = new StringBuilder("{\n  \"cuts\": {");
    boolean firstCut = true;

    for(int cut = 0; cut < 0x400; cut++) {
      if(!cuts.isEmpty() && !cuts.contains(cut)) {
        continue;
      }

      final SubmapCutInfo entry = newRoot.submapCutInfo_0000[cut];
      final int[] raws = {entry.earlyGameFile_00, entry.lateGameFile_02};
      final String[] names = {"early", "late"};

      if(entry.earlyGameFile_00 == entry.lateGameFile_02) {
        raws[1] = -1;
      }

      boolean wroteCut = false;
      Variant early = null, late = null;
      for(int v = 0; v < 2; v++) {
        final int raw = raws[v];
        if(raw < 0 || raw == 0xffff) {
          continue;
        }

        final int drgnIndex = raw >>> 13;
        final Variant variant = new Variant(21 + drgnIndex, (raw & 0x1fff) * 3 + (drgnIndex <= 3 ? 4 : 0));
        if(v == 0) {
          early = variant;
        } else {
          late = variant;
        }

        if(!extracted.containsKey(variant.key())) {
          try {
            final int fgWritten = extract(variant, cut, out);
            extracted.put(variant.key(), variant);
            fgCounts.put(variant.key(), fgWritten);
            System.out.printf("cut %d %s -> DRGN%d.BIN/%d%n", cut, names[v], variant.bin, variant.fileIndex);
          } catch(final Exception e) {
            extracted.put(variant.key(), null);
            System.out.printf("cut %d %s -> DRGN%d.BIN/%d FAILED (%s)%n", cut, names[v], variant.bin, variant.fileIndex, e.getMessage());
          }
        }

        if(!wroteCut) {
          manifest.append(firstCut ? "\n" : ",\n");
          firstCut = false;
          wroteCut = true;
          manifest.append("    \"").append(cut).append("\": {");
        }
        manifest.append("\"").append(names[v]).append("\": ").append(raw);
        if(v == 0 && raws[1] >= 0) {
          manifest.append(", ");
        } else {
          manifest.append("}");
        }
      }

      results.add(new CutResult(cut, early, late));
    }

    manifest.append("\n  }\n}\n");
    Files.createDirectories(out);
    Files.writeString(out.resolve("manifest.json"), manifest.toString());
    writeScenes(out, results, extracted, fgCounts);

    long ok = extracted.values().stream().filter(v -> v != null).count();
    System.out.printf("%nDone: %d variants extracted, %d failed%n", ok, extracted.size() - ok);
  }

  /** Replica of RetailSubmap.prepareEnv, writing PNGs instead of GPU textures. Returns the foreground count written. */
  private static int extract(final Variant variant, final int cut, final Path out) throws IOException {
    final Path bgPath = out.resolve("bg/" + variant.bin + "/" + variant.fileIndex + ".png");
    if(Files.exists(bgPath)) {
      final Path fgDir = out.resolve("fg/" + variant.bin + "/" + variant.fileIndex);
      try(final var s = Files.isDirectory(fgDir) ? Files.list(fgDir) : java.util.stream.Stream.<Path>empty()) {
        return (int)s.count();
      }
    }

    final List<FileData> files = Loader.loadDirectorySync("SECT/DRGN" + variant.bin + ".BIN/" + variant.fileIndex);
    if(files.size() < 4) {
      throw new IOException("only " + files.size() + " files");
    }

    final EnvironmentFile envFile = new EnvironmentFile(files.get(0));
    final int envCount = envFile.allTextureCount_14;
    final int bgCount = envFile.backgroundTextureCount_15;
    final int fgCount = envFile.foregroundTextureCount_16;

    final Tim[] tims = new Tim[files.size() - 3];
    for(int i = 0; i < tims.length; i++) {
      tims[i] = new Tim(files.get(i + 3));
    }

    // buildBackgroundRenderingPacket field mapping (texture fields only)
    final Metrics[] metrics = new Metrics[envCount];
    for(int i = 0; i < envCount; i++) {
      final EnvironmentTextureMetrics24 e = envFile.environments_18[i];
      int offsetY = e.textureOffsetY_12;
      if(cut == 111 && i == 8) { // GH#1201 marshlands boat fix
        offsetY++;
      }
      metrics[i] = new Metrics(e.tpage_20, e.vramPos_08.x, e.vramPos_08.y, e.vramPos_08.w, e.vramPos_08.h, e.textureOffsetX_10, offsetY);
    }

    // Resolve which TIM backs each env slot by tpage containment
    final Tim[] slotTims = new Tim[envCount];
    final Rect4i[] rects = new Rect4i[envCount];
    for(int i = 0; i < envCount; i++) {
      final Metrics m = metrics[i];
      final int tpX = (m.tpage & 0b1111) * 64;
      final int tpY = (m.tpage & 0b10000) != 0 ? 256 : 0;

      for(final Tim tim : tims) {
        if(tim.getImageRect().contains(tpX, tpY)) {
          slotTims[i] = tim;
          rects[i] = new Rect4i(m.offsetX, m.offsetY, m.w, m.h);
          break;
        }
      }
    }

    final Rect4i bgRect = Rect4i.bound(rects);
    final int[] bg = new int[bgRect.w * bgRect.h];

    // Composite background segments
    for(int i = 0; i < bgCount; i++) {
      if(slotTims[i] == null) {
        continue;
      }

      final Metrics m = metrics[i];
      final VramTextureSingle texture = VramTextureLoader.textureFromTim(slotTims[i]);
      final VramTextureSingle palette = VramTextureLoader.palettesFromTim(slotTims[i])[0];
      final int[] data = texture.applyPalette(palette, new Rect4i(m.u, m.v, m.w, m.h));

      blit(bg, bgRect.w, data, m.offsetX - bgRect.x, m.offsetY - bgRect.y, m.w, m.h);
    }

    writePng(bgPath, bg, bgRect.w, bgRect.h);

    // Full-canvas foreground cutouts (engine creates each fg at bgRect size)
    int fgWritten = 0;
    for(int j = 0; j < fgCount; j++) {
      final int i = bgCount + j;
      if(i >= envCount || slotTims[i] == null) {
        continue;
      }

      final Metrics m = metrics[i];
      final VramTextureSingle texture = VramTextureLoader.textureFromTim(slotTims[i]);
      final VramTextureSingle palette = VramTextureLoader.palettesFromTim(slotTims[i])[0];

      // Neet flashback cutout clamp (DRGN21/712)
      int w = m.w, h = m.h;
      if(m.u + w > texture.rect.w) {
        w = texture.rect.w - m.u;
      }
      if(m.v + h > texture.rect.h) {
        h = texture.rect.h - m.v;
      }
      if(w <= 0 || h <= 0) {
        continue;
      }

      final int[] data = texture.applyPalette(palette, new Rect4i(m.u, m.v, w, h));
      final int[] fg = new int[bgRect.w * bgRect.h];
      blit(fg, bgRect.w, data, m.offsetX - bgRect.x, m.offsetY - bgRect.y, w, h);

      writePng(out.resolve("fg/" + variant.bin + "/" + variant.fileIndex + "/" + j + ".png"), fg, bgRect.w, bgRect.h);
      fgWritten++;
    }

    return fgWritten;
  }

  /** Story-order scene index: groups extracted cuts by submap id (the game's own save-location order) under disc headings. */
  private static void writeScenes(final Path out, final List<CutResult> results, final Map<String, Variant> extracted, final Map<String, Integer> fgCounts) throws IOException {
    final Map<Integer, List<CutResult>> bySubmap = new LinkedHashMap<>();
    for(final CutResult r : results) {
      if(r.early() == null && r.late() == null) {
        continue; // no environment for this cut — nothing to pack
      }

      // NEWROOT has 1024 slots but the retail table covers 800; higher cuts are padding
      final int submap = r.cut() < CUT_TO_SUBMAP.length ? CUT_TO_SUBMAP[r.cut()] : -1;
      bySubmap.computeIfAbsent(submap, k -> new ArrayList<>()).add(r);
    }

    // Story order: intro (57) first, then discs in id order, then unnamed specials
    final List<Integer> order = new ArrayList<>();
    order.add(57);
    for(int i = 1; i <= 56; i++) {
      order.add(i);
    }
    order.add(0);
    order.add(58);
    order.add(-1);

    final StringBuilder md = new StringBuilder();
    md.append("# Submap scenes — story order\n\n");
    md.append("`21/40` = DRGN bin/dir → `bg/21/40.png` + `fg/21/40/*.png`. `+N fg` = N full-canvas foreground cutouts that must be upscaled alongside the background. `missing` = DRGN dir absent from this files/ tree; `—` = cut has no environment for that variant.\n");

    String lastDisc = null;
    for(final int submap : order) {
      final List<CutResult> cuts = bySubmap.get(submap);
      if(cuts == null) {
        continue;
      }

      final String disc = discLabel(submap);
      if(!disc.equals(lastDisc)) {
        md.append("\n## ").append(disc).append("\n");
        lastDisc = disc;
      }

      final String name = submap >= 0 && submap < SUBMAP_NAMES.length ? SUBMAP_NAMES[submap] : "";
      md.append("\n### ").append(submap).append(name.isEmpty() ? " — (unnamed)" : " — " + name).append("\n\n");
      md.append("| Cut | Early | Late |\n|----:|-------|------|\n");
      for(final CutResult r : cuts) {
        md.append("| ").append(r.cut())
          .append(" | ").append(formatVariant(r.early(), extracted, fgCounts))
          .append(" | ").append(r.late() == null && r.early() != null ? "same" : formatVariant(r.late(), extracted, fgCounts))
          .append(" |\n");
      }
    }

    Files.writeString(out.resolve("scenes.md"), md.toString());
  }

  private static String formatVariant(final Variant v, final Map<String, Variant> extracted, final Map<String, Integer> fgCounts) {
    if(v == null) {
      return "—";
    }
    if(!extracted.containsKey(v.key()) || extracted.get(v.key()) == null) {
      return v.key() + " (missing)";
    }

    final Integer fg = fgCounts.get(v.key());
    return v.key() + (fg != null && fg > 0 ? " (+" + fg + " fg)" : "");
  }

  private static String discLabel(final int submap) {
    if(submap == 57) {
      return "Intro";
    }
    if(submap >= 1 && submap <= 18) {
      return "Disc 1 — Serdio";
    }
    if(submap >= 19 && submap <= 31) {
      return "Disc 2 — Tiberoa / Mille Seseau";
    }
    if(submap >= 32 && submap <= 41) {
      return "Disc 3 — Mille Seseau";
    }
    if(submap >= 42 && submap <= 56) {
      return "Disc 4 — Gloriano / Endiness";
    }
    return "Other";
  }

  private static void blit(final int[] canvas, final int canvasW, final int[] src, final int dx, final int dy, final int w, final int h) {
    for(int y = 0; y < h; y++) {
      for(int x = 0; x < w; x++) {
        final int px = src[y * w + x];
        final int cx = dx + x;
        final int cy = dy + y;
        if(px != 0 && cx >= 0 && cy >= 0 && cx < canvasW && cy < canvas.length / canvasW) {
          canvas[cy * canvasW + cx] = px;
        }
      }
    }
  }

  /** Engine pixels are 0xAABBGGRR with 0 = transparent; PNGs want ARGB. */
  private static void writePng(final Path path, final int[] data, final int w, final int h) throws IOException {
    final BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    for(int i = 0; i < data.length; i++) {
      final int px = data[i];
      final int a = px == 0 ? 0 : 255;
      img.setRGB(i % w, i / w, a << 24 | (px & 0xff) << 16 | px & 0xff00 | (px >>> 16) & 0xff);
    }

    Files.createDirectories(path.getParent());
    ImageIO.write(img, "png", path.toFile());
  }
}
