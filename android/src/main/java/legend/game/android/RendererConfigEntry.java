package legend.game.android;

import legend.core.lang.I18nText;
import legend.core.lang.TextComponent;
import legend.game.inventory.screens.controls.Dropdown;
import legend.game.saves.ConfigCategory;
import legend.game.saves.ConfigCollection;
import legend.game.saves.ConfigStorageLocation;
import legend.game.saves.EnumConfigEntry;

/**
 * Graphics &gt; Renderer backend picker, visible from both the title-menu
 * and in-game Options screens. Mirrors the choice into the AndroidEnv pref
 * consulted at window creation — the renderer is fixed once the EGL context
 * exists, so changes apply on next launch. AUTO tries ANGLE's GLES-over-
 * Vulkan first (full ES 3.2 including geometry shaders, consistent across
 * vendors) and falls back to the system GLES driver on any failure; VULKAN
 * requests ANGLE explicitly and degrades to GLES with a log; GLES skips
 * ANGLE entirely.
 */
public class RendererConfigEntry extends EnumConfigEntry<RendererConfigEntry.Renderer> {
  public enum Renderer {
    AUTO,
    GLES,
    VULKAN,
  }

  public RendererConfigEntry() {
    super(Renderer.class, Renderer.AUTO, ConfigStorageLocation.GLOBAL, ConfigCategory.GRAPHICS);

    this.setEditControl((current, gameState) -> {
      final Renderer effective = fromPref(AndroidEnv.rendererBackend());
      final boolean vulkanAvailable = AngleLibs.dir() != null;

      final Dropdown<TextComponent> dropdown = new Dropdown<>((index, e) -> e);
      dropdown.onSelection(index -> gameState.setConfig(this, Renderer.values()[index]));

      for(final Renderer mode : Renderer.values()) {
        if(mode == Renderer.VULKAN && !vulkanAvailable) {
          continue; // no bundled ANGLE libs in this build
        }
        dropdown.addOption(new I18nText("lod_core.config.renderer_backend." + mode.name()));
        if(mode == effective) {
          dropdown.setSelectedIndex(dropdown.size() - 1);
        }
      }

      return dropdown;
    });
  }

  @Override
  public boolean hasHelp() {
    return true;
  }

  @Override
  public boolean availableInBattle() {
    return false;
  }

  @Override
  public void onChange(final ConfigCollection configCollection, final Renderer oldValue, final Renderer newValue) {
    super.onChange(configCollection, oldValue, newValue);
    AndroidEnv.setRendererBackend(switch(newValue) {
      case AUTO -> "auto";
      case GLES -> "gles";
      case VULKAN -> "vulkan";
    });
  }

  private static Renderer fromPref(final String pref) {
    return switch(pref) {
      case "gles" -> Renderer.GLES;
      case "vulkan" -> Renderer.VULKAN;
      default -> Renderer.AUTO;
    };
  }
}
