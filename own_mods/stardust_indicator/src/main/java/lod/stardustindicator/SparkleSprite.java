package lod.stardustindicator;

import legend.core.gpu.Bpp;
import legend.core.gte.MV;
import legend.core.renderer.MeshObj;
import legend.core.renderer.QuadBuilder;
import legend.core.renderer.QueuedModelStandard;
import legend.core.renderer.Texture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static legend.core.GameEngine.RENDERER;
import static lod.stardustindicator.StardustIndicatorMod.MOD_ID;

/** Animated texture quad rendered in screenspace over a world position. */
public class SparkleSprite {
  private final List<Texture> frames = new ArrayList<>();
  private MeshObj quad;

  /** Loads every frame from {@code mods/<modid>/sparkle/<n>.png}. */
  public void load() {
    for(int i = 0; ; i++) {
      final Path path = Path.of("mods", MOD_ID, "sparkle", i + ".png");
      if(!Files.exists(path)) {
        break;
      }

      final Texture texture = Texture.png("SPARKLE", path);
      texture.persistent = true;
      this.frames.add(texture);
    }

    this.quad = new QuadBuilder(MOD_ID)
      .uvSize(1.0f, 1.0f)
      .bpp(Bpp.BITS_24)
      .size(1.0f, 1.0f)
      .pos(-0.5f, -0.5f, 0.0f)
      .rgb(1.0f, 1.0f, 1.0f)
      .build();
    this.quad.persistent = true;
  }

  public void render(final MV mv, final int frame) {
    if(this.frames.isEmpty()) {
      return;
    }

    RENDERER.queueModel(this.quad, mv, QueuedModelStandard.class)
      .useTextureAlpha()
      .texture(this.frames.get(frame % this.frames.size()));
  }

  public void unload() {
    this.frames.forEach(Texture::delete);
    this.frames.clear();
    this.quad.delete();
  }
}
