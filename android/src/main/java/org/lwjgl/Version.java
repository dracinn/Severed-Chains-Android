package org.lwjgl;

/** Stand-in for LWJGL's Version - LWJGL is not present on Android. */
public final class Version {
  private Version() { }

  public static String getVersion() {
    return "android-shim";
  }
}
