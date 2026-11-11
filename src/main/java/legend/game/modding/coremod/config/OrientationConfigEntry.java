package legend.game.modding.coremod.config;

import legend.core.GameEngine;
import legend.core.platform.input.Orientation;
import legend.game.saves.ConfigCategory;
import legend.game.saves.ConfigStorageLocation;
import legend.game.saves.EnumConfigEntry;

/** Android-only: hidden on PC since orientation is meaningless there. */
public class OrientationConfigEntry extends EnumConfigEntry<Orientation> {
  public OrientationConfigEntry() {
    super(Orientation.class, Orientation.AUTO, ConfigStorageLocation.GLOBAL, ConfigCategory.CONTROLS);
  }

  @Override
  public boolean hasEditControl() {
    return GameEngine.PLATFORM.isAndroid() && super.hasEditControl();
  }

  @Override
  public boolean hasHelp() {
    return true;
  }
}
