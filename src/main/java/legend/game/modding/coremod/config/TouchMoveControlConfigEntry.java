package legend.game.modding.coremod.config;

import legend.core.platform.input.TouchMoveControl;
import legend.game.saves.ConfigCategory;
import legend.game.saves.ConfigStorageLocation;
import legend.game.saves.EnumConfigEntry;

public class TouchMoveControlConfigEntry extends EnumConfigEntry<TouchMoveControl> {
  public TouchMoveControlConfigEntry() {
    super(TouchMoveControl.class, TouchMoveControl.BOTH, ConfigStorageLocation.GLOBAL, ConfigCategory.CONTROLS);
  }

  @Override
  public boolean hasHelp() {
    return true;
  }
}
