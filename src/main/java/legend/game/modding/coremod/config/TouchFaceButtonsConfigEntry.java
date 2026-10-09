package legend.game.modding.coremod.config;

import legend.core.platform.input.TouchFaceButtonStyle;
import legend.game.saves.ConfigCategory;
import legend.game.saves.ConfigStorageLocation;
import legend.game.saves.EnumConfigEntry;

public class TouchFaceButtonsConfigEntry extends EnumConfigEntry<TouchFaceButtonStyle> {
  public TouchFaceButtonsConfigEntry() {
    super(TouchFaceButtonStyle.class, TouchFaceButtonStyle.PLAYSTATION, ConfigStorageLocation.GLOBAL, ConfigCategory.CONTROLS);
  }

  @Override
  public boolean hasHelp() {
    return true;
  }
}
