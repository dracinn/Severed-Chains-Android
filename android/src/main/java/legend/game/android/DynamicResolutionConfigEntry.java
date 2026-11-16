package legend.game.android;

import legend.game.saves.BoolConfigEntry;
import legend.game.saves.ConfigCategory;
import legend.game.saves.ConfigCollection;
import legend.game.saves.ConfigStorageLocation;

/**
 * Options &gt; Dynamic resolution toggle. Mirrors the AndroidEnv pref the
 * {@link DynamicResolution} controller consults — the resolution config
 * stays the maximum; the controller only steps down from it under
 * sustained frame misses and steps back up when headroom returns.
 */
public class DynamicResolutionConfigEntry extends BoolConfigEntry {
  public DynamicResolutionConfigEntry() {
    super(false, ConfigStorageLocation.GLOBAL, ConfigCategory.GRAPHICS);
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
  public void onChange(final ConfigCollection configCollection, final Boolean oldValue, final Boolean newValue) {
    super.onChange(configCollection, oldValue, newValue);
    DynamicResolution.setEnabled(newValue);
  }
}
