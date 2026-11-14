import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.*;

/**
 * Rasterises a TTF into the engine's font atlas format (PNG + JSON glyph
 * metrics) at build time. Run by the `generateCopyrightFont` Gradle task:
 *   java -Djava.awt.headless=true tools/fontgen/FontGen.java <ttf> <outDir>
 *
 * Cells are packed by advance width plus per-glyph overhang margins: glyphs
 * whose ink escapes the advance box (e.g. 'j' hooks left, 'Q' tails right)
 * get their cell widened and the quad's `x` shifted so the ink stays inside
 * its own texture region instead of bleeding into the neighbour glyph.
 */
public class FontGen {
  public static void main(final String[] args) throws Exception {
    final File ttf = new File(args[0]);
    final File outDir = new File(args[1]);
    outDir.mkdirs();

    final float renderPx = 96f;
    final float emUnits = 12f;               // engine line height in design units
    final int wrapWidth = 1024;
    final int rowGap = 2;

    final Font base = Font.createFont(Font.TRUETYPE_FONT, ttf).deriveFont(renderPx);
    final BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D pg = probe.createGraphics();
    pg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    pg.setFont(base);
    final FontMetrics fm = pg.getFontMetrics();
    final int asc = fm.getAscent(), desc = fm.getDescent();
    final int texH = asc + desc;
    final double unitsPerPx = emUnits / texH;

    // charset: printable ASCII + a few symbols
    final java.util.List<Character> chars = new java.util.ArrayList<>();
    for(int c = 0x20; c <= 0x7e; c++) {
      chars.add((char)c);
    }
    chars.add('©'); chars.add('®'); chars.add('™');

    // per-glyph: [advPx, padLeft, cellW]
    final int[][] metrics = new int[chars.size()][3];
    int atlasW = 0, rows = 1;
    int x = 0;
    for(int i = 0; i < chars.size(); i++) {
      final char c = chars.get(i);
      final int advPx = (int)Math.ceil(fm.charWidth(c));
      final Rectangle2D bounds = c == ' '
        ? new Rectangle2D.Double()
        : base.createGlyphVector(pg.getFontRenderContext(), String.valueOf(c)).getVisualBounds();
      final int padL = (int)Math.ceil(Math.max(0.0, -bounds.getX())) + 1;
      final int padR = (int)Math.ceil(Math.max(0.0, bounds.getMaxX() - advPx)) + 1;
      final int cellW = advPx + padL + padR;
      if(x + cellW > wrapWidth) { x = 0; rows++; }
      metrics[i] = new int[] {advPx, padL, cellW};
      x += cellW;
      atlasW = Math.max(atlasW, x);
    }
    pg.dispose();

    final int atlasH = rows * texH + (rows - 1) * rowGap;
    final BufferedImage atlas = new BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D g = atlas.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    g.setFont(base);
    g.setColor(Color.WHITE);

    final StringBuilder json = new StringBuilder("{\n  \"name\": \"Title Copyright\",\n  \"glyphs\": {\n");
    int cx = 0, cy = 0;
    final java.util.List<String> entries = new java.util.ArrayList<>();
    for(int i = 0; i < chars.size(); i++) {
      final char c = chars.get(i);
      final int advPx = metrics[i][0], padL = metrics[i][1], cellW = metrics[i][2];
      if(cx + cellW > wrapWidth) { cx = 0; cy += texH + rowGap; }
      if(c != ' ') {
        g.drawString(String.valueOf(c), cx + padL, cy + asc);
      }
      final String key;
      if(c == '"' || c == '\\') {
        key = "\\" + c;
      } else if(c < 0x7f) {
        key = String.valueOf(c);
      } else {
        key = String.format("0x%x", (int)c);
      }
      entries.add(String.format(
        "    \"%s\": {\"colour\": true, \"texW\": %d, \"texH\": %d, \"w\": %d, \"texU\": %d, \"x\": %d, \"h\": 12, \"texV\": %d, \"y\": 0}",
        key,
        c == ' ' ? 0 : cellW, c == ' ' ? 0 : texH,
        Math.max(1, (int)Math.round(cellW * unitsPerPx)),
        cx, -(int)Math.round(padL * unitsPerPx), cy));
      cx += cellW;
    }
    g.dispose();

    json.append(String.join(",\n", entries));
    json.append("\n  }\n}\n");

    ImageIO.write(atlas, "png", new File(outDir, "copyright.png"));
    java.nio.file.Files.writeString(new File(outDir, "copyright.json").toPath(), json);

    // report line widths in engine units so .size() can be tuned
    final String l1 = "Legend of Dragoon is a trademark of Sony Computer Entertainment America Inc.";
    final String l2 = "© 2000 Sony Computer Entertainment Inc.";
    int w1 = 0, w2 = 0;
    for(final char c : l1.toCharArray()) {
      w1 += Math.max(1, (int)Math.ceil(fm.charWidth(c)) * unitsPerPx);
    }
    for(final char c : l2.toCharArray()) {
      w2 += Math.max(1, (int)Math.ceil(fm.charWidth(c)) * unitsPerPx);
    }
    System.out.println("atlas " + atlasW + "x" + atlasH + ", " + chars.size() + " glyphs, line1=" + w1 + " units, line2=" + w2 + " units");
  }
}
