package legend.game.modding.coremod.config;

import legend.game.saves.ConfigCategory;
import legend.game.saves.ConfigStorageLocation;
import legend.game.saves.EnumConfigEntry;

public class PerfOverlayConfigEntry extends EnumConfigEntry<PerfOverlayMode> {
  public PerfOverlayConfigEntry() {
    super(PerfOverlayMode.class, PerfOverlayMode.OFF, ConfigStorageLocation.GLOBAL, ConfigCategory.USER_INTERFACE);
  }

  @Override
  public boolean hasHelp() {
    return true;
  }
}
