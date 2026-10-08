package org.lwjgl.openal;

/** ALC capability entry points (LWJGL-shaped). */
public final class ALC {
  private static final ALCCapabilities CAPABILITIES = new ALCCapabilities();

  private ALC() { }

  public static ALCCapabilities getCapabilities() {
    return CAPABILITIES;
  }

  public static ALCCapabilities createCapabilities(final long alcDevice) {
    return CAPABILITIES;
  }
}
