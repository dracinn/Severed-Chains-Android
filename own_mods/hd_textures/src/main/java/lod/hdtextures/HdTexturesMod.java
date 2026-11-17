package lod.hdtextures;

import legend.core.memory.types.IntRef;
import legend.core.renderer.Texture;
import legend.game.modding.events.submap.SubmapEnvironmentTextureEvent;
import legend.game.types.NewRootStruct;
import legend.game.unpacker.Loader;
import org.legendofdragoon.modloader.Mod;
import org.legendofdragoon.modloader.events.EventListener;

import java.nio.file.Files;
import java.nio.file.Path;

import static legend.core.GameEngine.EVENTS;

/**
 * HD submap environment texture pack loader.
 *
 * <p>Replaces the composited submap background and each full-canvas foreground
 * cutout via {@link SubmapEnvironmentTextureEvent}. Pack files live in
 * {@code mods/hd_textures/}:</p>
 *
 * <pre>
 *   bg/&lt;bin&gt;/&lt;fileIndex&gt;.png       composited background (any scale)
 *   fg/&lt;bin&gt;/&lt;fileIndex&gt;/&lt;slot&gt;.png   full-canvas foreground cutout
 * </pre>
 *
 * <p>{@code bin}/{@code fileIndex} come from the same {@link NewRootStruct}
 * lookup {@code RetailSubmap.loadEnv} performs, so early/late-game map
 * variants resolve exactly like retail. Extract pristine-resolution
 * reference PNGs with the {@code :extractSubmapBackgrounds} Gradle task
 * ({@code tools/bgextract}), upscale them offline (RealESRGAN etc.) and drop
 * the results into this layout.</p>
 */
@Mod(id = HdTexturesMod.MOD_ID, version = "^3.0.0")
public class HdTexturesMod {
  public static final String MOD_ID = "hd_textures";

  private static final Path PACK_DIR = Path.of("mods", MOD_ID);

  private NewRootStruct newRoot;

  public HdTexturesMod() {
    EVENTS.register(this);
  }

  @EventListener
  public void onEnvironmentTextures(final SubmapEnvironmentTextureEvent event) {
    if(this.newRoot == null) {
      this.newRoot = new NewRootStruct(Loader.loadFileSync("SUBMAP/NEWROOT.RDT"));
    }

    // Resolve the DRGN dir this cut loads from — identical to loadEnv
    final IntRef drgnIndex = new IntRef();
    final IntRef fileIndex = new IntRef();
    this.newRoot.getDrgnFile(event.submapCut, drgnIndex, fileIndex);

    final String dir = (20 + drgnIndex.get()) + "/" + fileIndex.get();
    final Path bg = PACK_DIR.resolve("bg/" + dir + ".png");

    if(Files.isRegularFile(bg)) {
      event.background = Texture.filteredPng("HD background " + event.submapCut, bg);
    }

    for(int i = 0; i < event.foregrounds.length; i++) {
      final Path fg = PACK_DIR.resolve("fg/" + dir + "/" + i + ".png");
      if(Files.isRegularFile(fg)) {
        event.foregrounds[i] = Texture.filteredPng("HD foreground " + event.submapCut + '/' + i, fg);
      }
    }
  }
}
