package legend.game.modding.coremod.config;

import legend.core.platform.input.GamepadButtonLabels;
import legend.game.saves.ConfigCategory;
import legend.game.saves.ConfigStorageLocation;
import legend.game.saves.EnumConfigEntry;

public class GamepadButtonLabelsConfigEntry extends EnumConfigEntry<GamepadButtonLabels> {
  public GamepadButtonLabelsConfigEntry() {
    super(GamepadButtonLabels.class, GamepadButtonLabels.AUTO, ConfigStorageLocation.GLOBAL, ConfigCategory.CONTROLS);
  }

  @Override
  public boolean hasHelp() {
    return true;
  }
}
