package legend.game.modding.coremod.config;

import legend.core.IoHelper;
import legend.game.inventory.screens.controls.NumberSpinner;
import legend.game.saves.ConfigCategory;
import legend.game.saves.ConfigEntry;
import legend.game.saves.ConfigStorageLocation;

public class TouchControlsOpacityConfigEntry extends ConfigEntry<Float> {
  public TouchControlsOpacityConfigEntry() {
    super(1.0f, ConfigStorageLocation.GLOBAL, ConfigCategory.CONTROLS, TouchControlsOpacityConfigEntry::serializer, data -> deserializer(data, 1.0f));

    this.setEditControl((number, gameState) -> {
      final NumberSpinner<Float> spinner = NumberSpinner.percentSpinner(number, 0.05f, 0.25f, 0.1f, 1.0f);
      spinner.onChange(val -> gameState.setConfig(this, val));
      return spinner;
    });
  }

  @Override
  public boolean hasHelp() {
    return true;
  }

  private static byte[] serializer(final float val) {
    return new byte[] {(byte)(Math.round(val * 100.0f))};
  }

  private static float deserializer(final byte[] data, final float defaultValue) {
    if(data.length == 1) {
      return IoHelper.readUByte(data, 0) / 100.0f;
    }

    return defaultValue;
  }
}
