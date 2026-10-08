package org.lwjgl.openal;

import java.util.List;

/** ALC string-list helpers (LWJGL-shaped). */
public final class ALUtil {
  private ALUtil() { }

  public static List<String> getStringList(final long device, final int param) {
    return List.of(ALC10.deviceName());
  }
}
