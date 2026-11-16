package legend.game.android;

import legend.core.lang.I18nText;
import legend.core.lang.TextComponent;
import legend.game.inventory.screens.controls.Dropdown;
import legend.game.saves.ConfigCategory;
import legend.game.saves.ConfigCollection;
import legend.game.saves.ConfigStorageLocation;
import legend.game.saves.EnumConfigEntry;

/**
 * Graphics &gt; Frame rate cap. NATIVE leaves the engine's per-scene pacing
 * alone (30Hz title/menus, 60Hz field/world map — the retail frame-skip
 * pattern). 30fps clamps the engine's vsync divisor so the whole game runs
 * its native 30fps path — full speed, lower tick rate (best power savings).
 * 45fps keeps the 60Hz game tick and drops GL submission every fourth tick —
 * the sim never slows while 45fps is presented.
 */
public class FrameRateCapConfigEntry extends EnumConfigEntry<FrameRateCapConfigEntry.FpsCap> {
  public enum FpsCap {
    NATIVE,
    FPS_45,
    FPS_30,
  }

  public FrameRateCapConfigEntry() {
    super(FpsCap.class, FpsCap.NATIVE, ConfigStorageLocation.GLOBAL, ConfigCategory.GRAPHICS);

    this.setEditControl((current, gameState) -> {
      final FpsCap effective = fromCap(FrameCap.cap());

      final Dropdown<TextComponent> dropdown = new Dropdown<>((index, e) -> e);
      dropdown.onSelection(index -> gameState.setConfig(this, FpsCap.values()[index]));

      for(final FpsCap mode : FpsCap.values()) {
        dropdown.addOption(new I18nText("lod_core.config.fps_cap." + mode.name()));
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
  public void onChange(final ConfigCollection configCollection, final FpsCap oldValue, final FpsCap newValue) {
    super.onChange(configCollection, oldValue, newValue);
    FrameCap.setCap(switch(newValue) {
      case FPS_45 -> 45;
      case FPS_30 -> 30;
      default -> 0;
    });
  }

  private static FpsCap fromCap(final int cap) {
    return switch(cap) {
      case 45 -> FpsCap.FPS_45;
      case 30 -> FpsCap.FPS_30;
      default -> FpsCap.NATIVE;
    };
  }
}
