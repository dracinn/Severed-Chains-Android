package org.lwjgl.openal;

import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ALC 1.0 against a single fake device ("Android"). Device/context handles
 * are non-zero synthetic values; real output goes through AudioTrack in AL10.
 */
public final class ALC10 {
  public static final int ALC_DEVICE_SPECIFIER = 0x0405;
  public static final int ALC_DEFAULT_DEVICE_SPECIFIER = 0x0404;

  private ALC10() { }

  private static final AtomicLong NEXT_HANDLE = new AtomicLong(0x1000);

  public static String deviceName() {
    return "Android";
  }

  public static long alcOpenDevice(final String name) {
    return NEXT_HANDLE.getAndIncrement();
  }

  public static boolean alcCloseDevice(final long device) {
    return true;
  }

  public static long alcCreateContext(final long device, final int[] attributes) {
    return NEXT_HANDLE.getAndIncrement();
  }

  public static void alcDestroyContext(final long context) {
  }

  public static boolean alcMakeContextCurrent(final long context) {
    return true;
  }

  public static int alcGetError(final long device) {
    return 0;
  }

  public static String alcGetString(final long device, final int param) {
    return switch(param) {
      case ALC_DEVICE_SPECIFIER, ALC_DEFAULT_DEVICE_SPECIFIER,
        ALC11.ALC_ALL_DEVICES_SPECIFIER, ALC11.ALC_DEFAULT_ALL_DEVICES_SPECIFIER -> deviceName();
      default -> null;
    };
  }

  public static void alcGetIntegerv(final long device, final int param, final IntBuffer out) {
    if(out.remaining() > 0) {
      out.put(0, param == EXTDisconnect.ALC_CONNECTED ? 1 : 0);
    }
  }
}
