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
 * </pre>
 *
 * Usage: BgExtract &lt;filesDir&gt; &lt;outDir&gt; [cut ...]
 */
public final class BgExtract {
  private BgExtract() { }

  private record Metrics(int tpage, int u, int v, int w, int h, int offsetX, int offsetY) { }

  private record Variant(int bin, int fileIndex) { }

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
      for(int v = 0; v < 2; v++) {
        final int raw = raws[v];
        if(raw < 0 || raw == 0xffff) {
          continue;
        }

        final int drgnIndex = raw >>> 13;
        final Variant variant = new Variant(21 + drgnIndex, (raw & 0x1fff) * 3 + (drgnIndex <= 3 ? 4 : 0));

        if(!extracted.containsKey(variant.bin + "/" + variant.fileIndex)) {
          try {
            extract(variant, cut, out);
            extracted.put(variant.bin + "/" + variant.fileIndex, variant);
            System.out.printf("cut %d %s -> DRGN%d.BIN/%d%n", cut, names[v], variant.bin, variant.fileIndex);
          } catch(final Exception e) {
            extracted.put(variant.bin + "/" + variant.fileIndex, null);
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
    }

    manifest.append("\n  }\n}\n");
    Files.createDirectories(out);
    Files.writeString(out.resolve("manifest.json"), manifest.toString());

    long ok = extracted.values().stream().filter(v -> v != null).count();
    System.out.printf("%nDone: %d variants extracted, %d failed%n", ok, extracted.size() - ok);
  }

  /** Replica of RetailSubmap.prepareEnv, writing PNGs instead of GPU textures. */
  private static void extract(final Variant variant, final int cut, final Path out) throws IOException {
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

    writePng(out.resolve("bg/" + variant.bin + "/" + variant.fileIndex + ".png"), bg, bgRect.w, bgRect.h);

    // Full-canvas foreground cutouts (engine creates each fg at bgRect size)
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
    }
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
