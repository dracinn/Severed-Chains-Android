package legend.game.modding.coremod.config;

import legend.game.saves.ConfigCategory;
import legend.game.saves.ConfigStorageLocation;
import legend.game.saves.EnumConfigEntry;

public class UpscalerConfigEntry extends EnumConfigEntry<UpscalerMode> {
  public UpscalerConfigEntry() {
    super(UpscalerMode.class, UpscalerMode.OFF, ConfigStorageLocation.GLOBAL, ConfigCategory.GRAPHICS);
  }

  @Override
  public boolean hasHelp() {
    return true;
  }
}
