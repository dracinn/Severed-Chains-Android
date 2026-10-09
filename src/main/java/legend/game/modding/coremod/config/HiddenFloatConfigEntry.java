package legend.game.modding.coremod.config;

import legend.core.IoHelper;
import legend.game.saves.ConfigCategory;
import legend.game.saves.ConfigEntry;
import legend.game.saves.ConfigStorageLocation;

/**
 * Persisted float with no menu control, for values written back by the
 * on-screen touch controls (position, scale). Serialized as value * 10000
 * in two big-endian bytes (0..6.5535 range, 0.01% granularity).
 */
public class HiddenFloatConfigEntry extends ConfigEntry<Float> {
  public HiddenFloatConfigEntry(final float defaultValue) {
    super(defaultValue, ConfigStorageLocation.GLOBAL, ConfigCategory.CONTROLS, HiddenFloatConfigEntry::serializer, data -> deserializer(data, defaultValue));
  }

  private static byte[] serializer(final float val) {
    final int scaled = Math.round(val * 10000.0f);
    return new byte[] {(byte)(scaled >> 8), (byte)scaled};
  }

  private static float deserializer(final byte[] data, final float defaultValue) {
    if(data.length == 2) {
      return (IoHelper.readUByte(data, 0) << 8 | IoHelper.readUByte(data, 1)) / 10000.0f;
    }

    return defaultValue;
  }
}
