package lod.iconturnorder;

import legend.game.saves.BoolConfigEntry;
import legend.game.saves.ConfigCategory;
import legend.game.saves.ConfigStorageLocation;

public class ShowIconTurnOrderConfig extends BoolConfigEntry {
  public ShowIconTurnOrderConfig() { super (false, ConfigStorageLocation.CAMPAIGN, ConfigCategory.GAMEPLAY); }
}
