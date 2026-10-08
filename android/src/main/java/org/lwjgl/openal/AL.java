package org.lwjgl.openal;

/** AL capability entry points (LWJGL-shaped). */
public final class AL {
  private AL() { }

  public static ALCapabilities createCapabilities(final ALCCapabilities alcCapabilities) {
    return new ALCapabilities();
  }
}
