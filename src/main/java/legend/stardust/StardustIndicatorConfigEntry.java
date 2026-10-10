package legend.stardust;

import legend.game.saves.BoolConfigEntry;
import legend.game.saves.ConfigCategory;
import legend.game.saves.ConfigStorageLocation;

public class StardustIndicatorConfigEntry extends BoolConfigEntry {
  public StardustIndicatorConfigEntry() {
    super(true, ConfigStorageLocation.GLOBAL, ConfigCategory.GAMEPLAY);
  }

  @Override
  public boolean hasHelp() {
    return true;
  }
}
