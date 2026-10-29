package lod.portraitpack;

import legend.core.gte.MV;
import legend.core.renderer.DepthComparator;
import legend.game.textures.Image;
import legend.game.textures.TextureAtlas;
import legend.game.textures.TextureAtlasIcon;
import legend.game.textures.TexturePacker;
import legend.game.types.Renderable58;
import legend.game.types.RenderableMetrics14;
import org.legendofdragoon.modloader.Mod;
import org.legendofdragoon.modloader.registries.RegistryId;

import java.nio.file.Path;

import static legend.core.GameEngine.getTextureAtlas;
import static legend.game.Menus.uiFile_800bdc3c;
import static legend.lodmod.Legacy.CHAR_IDS;

/**
 * HD character portrait pack. Ships 384px re-rendered portraits (carried over
 * from the vendored dragoon_modifier pack) and substitutes them for the stock
 * 48px save-card extractions everywhere portraits render:
 *
 * <ul>
 *   <li>Atlas-path portraits ({@code getTextureAtlas().getIcon(charId)}) are
 *   swapped to icons from this mod's own atlas via staged-source transforms in
 *   android/build.gradle — BattleHud, shop tabs, CharacterCard, CharSwap,
 *   PostBattle, UseItem and list right-icons.</li>
 *   <li>Glyph-path portraits (the save/load card faces drawn from the
 *   portraits_cfac UiType) are swapped by a renderUi hook that calls
 *   {@link #renderPortrait}.</li>
 * </ul>
 */
@Mod(id = PortraitPackMod.MOD_ID, version = "^3.0.0")
public class PortraitPackMod {
  public static final String MOD_ID = "portraitpack";

  private static final String[] PORTRAIT_FILES = {
    "dart", "lavitz", "shana", "rose", "haschel", "albert", "meru", "miranda", "kongol",
  };

  private static TextureAtlas portraits;

  private static TextureAtlas getAtlas() {
    if(portraits == null) {
      final TexturePacker packer = new TexturePacker("Portraits");

      for(final String name : PORTRAIT_FILES) {
        final Path file = Path.of("mods", MOD_ID, name + ".png");
        if(file.toFile().isFile()) {
          packer.add(new RegistryId("lod", name), Image.load(file));
        }
      }

      portraits = packer.pack(2048, 1024);
      portraits.setPersistent(true);
    }

    return portraits;
  }

  /** HD portrait icon for a character template id, falling back to the stock atlas icon. */
  public static TextureAtlasIcon getPortraitIcon(final RegistryId charId) {
    final TextureAtlasIcon icon = getAtlas().getIcon(charId);
    return icon != null ? icon : getTextureAtlas().getIcon(charId);
  }

  /**
   * Drop-in replacement for glyph-sheet portraits (save/load cards). Called from
   * the transformed renderUi loop once per metrics quad. Each portrait glyph
   * entry carries several quads — copies of the face sampled from different
   * texture pages (only one page's copy is visible in retail), plus small tiles
   * covering part of the face. The glyph quads are all suppressed; the HD
   * portrait is drawn on every full-face (48x48 at 0,0) quad so the visible
   * copy is always replaced.
   */
  public static boolean renderPortrait(final Renderable58 renderable, final MV transforms, final RenderableMetrics14 metrics) {
    if(uiFile_800bdc3c == null || renderable.uiType_20 != uiFile_800bdc3c.portraits_cfac()) {
      return false;
    }

    final int charIndex = renderable.glyph_04;
    if(charIndex < 0 || charIndex >= CHAR_IDS.length) {
      return false;
    }

    if(metrics.x_02 != 0 || metrics.y_03 != 0 || metrics.width_08 != 48 || metrics.height_0a != 48) {
      return true;
    }

    final TextureAtlasIcon icon = getAtlas().getIcon(CHAR_IDS[charIndex]);
    if(icon == null) {
      return false;
    }

    icon.render(transforms)
      .colour(renderable.colour)
      .translucentDepthComparator(DepthComparator.LESS_THAN_OR_EQUAL)
      .opaqueDepthComparator(DepthComparator.LESS_THAN_OR_EQUAL);
    return true;
  }
}
