package legend.mods.retrobuttons;

import org.legendofdragoon.modloader.Mod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Installs a "Retro" font variant into gfx/fonts/ so it can be picked in the
 * font settings dropdown. The font's button glyphs are the Retro icons from
 * the Input Prompts Pack, one set per input source (Xbox, PlayStation,
 * Switch). Mods load before the font config is read, so extracting in the
 * constructor is early enough.
 */
@Mod(id = RetroButtonsMod.MOD_ID, version = "^3.0.0")
public class RetroButtonsMod {
  public static final String MOD_ID = "retro_buttons";

  private static final String[] FILES = {"retro.json", "retro.png"};

  public RetroButtonsMod() throws IOException {
    final Path fontsDir = Path.of("gfx", "fonts");
    Files.createDirectories(fontsDir);

    for(final String file : FILES) {
      try(final InputStream in = this.getClass().getClassLoader().getResourceAsStream(MOD_ID + "/fonts/" + file)) {
        if(in == null) {
          throw new IOException("Missing bundled font resource " + file);
        }
        Files.copy(in, fontsDir.resolve(file), StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }
}
