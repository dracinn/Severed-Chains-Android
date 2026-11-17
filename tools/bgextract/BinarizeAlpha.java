import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.stream.Stream;

/**
 * Alpha-channel utilities for upscaled foreground cutouts.
 *
 * <p>The engine's submap foreground textures use binary alpha — non-zero-alpha
 * texels render opaque — but AI upscalers (RealESRGAN etc.) produce partial
 * alpha on RGBA images, which paints soft edges as solid dark/colored regions
 * in-game. They also bleed the RGB=0 stored under transparent pixels into the
 * matte interior, hazing the content.</p>
 *
 * <p>Workflow for fg PNGs:</p>
 * <pre>
 *   java BinarizeAlpha.java fill &lt;dir&gt;          # BEFORE upscaling: fill RGB under
 *                                                 # transparent px with nearest
 *                                                 # content color ("alpha bleed")
 *   realesrgan-ncnn-vulkan ...                    # upscale
 *   java BinarizeAlpha.java bin &lt;dir&gt; [th] [d]  # AFTER: threshold alpha to 0/255,
 *                                                 # optionally dilate matte d px
 * </pre>
 *
 * A dilation of ~1-2px at 4x scale closes the rim where the AI slightly
 * eroded the matte, covering the background's cyan placeholder pixels.
 */
public final class BinarizeAlpha {
  private BinarizeAlpha() { }

  public static void main(final String[] args) throws IOException {
    if(args.length < 2) {
      System.err.println("Usage: java BinarizeAlpha.java <fill|bin> <dir-or-png> [threshold=128] [dilatePx=0]");
      System.exit(1);
    }

    final String mode = args[0];
    final Path root = Path.of(args[1]);
    final int threshold = args.length > 2 ? Integer.parseInt(args[2]) : 128;
    final int dilatePx = args.length > 3 ? Integer.parseInt(args[3]) : 0;

    final Stream<Path> paths = Files.isDirectory(root)
      ? Files.walk(root).filter(p -> p.toString().endsWith(".png"))
      : Stream.of(root);

    int[] counts = {0, 0};
    for(final Path path : (Iterable<Path>)paths::iterator) {
      final BufferedImage img = ImageIO.read(path.toFile());
      if(img == null) {
        System.err.println("SKIP (not an image): " + path);
        continue;
      }

      final boolean changed;
      if("fill".equals(mode)) {
        changed = fillRgb(img);
      } else if("bin".equals(mode)) {
        changed = binarize(img, threshold) | dilate(img, dilatePx);
      } else {
        System.err.println("Unknown mode: " + mode);
        System.exit(1);
        return;
      }

      if(changed) {
        ImageIO.write(img, "png", path.toFile());
        counts[0]++;
      } else {
        counts[1]++;
      }
    }

    System.out.printf("Done: %d updated, %d unchanged%n", counts[0], counts[1]);
  }

  private static boolean binarize(final BufferedImage img, final int threshold) {
    boolean changed = false;
    for(int y = 0; y < img.getHeight(); y++) {
      for(int x = 0; x < img.getWidth(); x++) {
        final int px = img.getRGB(x, y);
        final int a = (px >>> 24) & 0xff;
        if(a == 0 || a == 255) {
          if(a == 0 && (px & 0xffffff) != 0) {
            img.setRGB(x, y, 0);
            changed = true;
          }
          continue;
        }
        img.setRGB(x, y, a >= threshold ? px | 0xff000000 : 0);
        changed = true;
      }
    }
    return changed;
  }

  /** Fills RGB under alpha=0 pixels with the nearest opaque pixel's color via multi-source BFS. Alpha is untouched. */
  private static boolean fillRgb(final BufferedImage img) {
    final int w = img.getWidth(), h = img.getHeight();
    final int[] cur = new int[w * h];
    img.getRGB(0, 0, w, h, cur, 0, w);

    final ArrayDeque<Integer> queue = new ArrayDeque<>();
    for(int i = 0; i < cur.length; i++) {
      if((cur[i] >>> 24) != 0) {
        queue.add(i);
      }
    }
    if(queue.isEmpty() || queue.size() == cur.length) {
      return false;
    }

    while(!queue.isEmpty()) {
      final int idx = queue.poll();
      final int x = idx % w, y = idx / w;
      for(int dy = -1; dy <= 1; dy++) {
        for(int dx = -1; dx <= 1; dx++) {
          final int nx = x + dx, ny = y + dy;
          if(nx < 0 || ny < 0 || nx >= w || ny >= h) {
            continue;
          }
          final int n = ny * w + nx;
          if((cur[n] >>> 24) == 0 && (cur[n] & 0xffffff) == 0) {
            cur[n] = cur[idx] & 0xffffff; // keep alpha 0, copy rgb
            queue.add(n);
          }
        }
      }
    }

    img.setRGB(0, 0, w, h, cur, 0, w);
    return true;
  }

  /** Expands the opaque region by {@code px} pixels, filling new edge pixels with the nearest opaque pixel's color. */
  private static boolean dilate(final BufferedImage img, final int px) {
    if(px <= 0) {
      return false;
    }

    final int w = img.getWidth(), h = img.getHeight();
    int[] cur = new int[w * h];
    img.getRGB(0, 0, w, h, cur, 0, w);

    boolean changed = false;
    for(int i = 0; i < px; i++) {
      final int[] next = cur.clone();
      for(int y = 0; y < h; y++) {
        for(int x = 0; x < w; x++) {
          final int idx = y * w + x;
          if((cur[idx] >>> 24) != 0) {
            continue;
          }
          for(int dy = -1; dy <= 1; dy++) {
            for(int dx = -1; dx <= 1; dx++) {
              final int nx = x + dx, ny = y + dy;
              if(nx >= 0 && ny >= 0 && nx < w && ny < h && (cur[ny * w + nx] >>> 24) != 0) {
                next[idx] = cur[ny * w + nx];
                dx = 1;
                dy = 1;
                changed = true;
              }
            }
          }
        }
      }
      cur = next;
    }

    if(changed) {
      img.setRGB(0, 0, w, h, cur, 0, w);
    }
    return changed;
  }
}
