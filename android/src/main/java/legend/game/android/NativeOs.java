package legend.game.android;

import java.io.IOException;

/** Thin JNI bridge for process-level OS calls. */
public final class NativeOs {
  static {
    System.loadLibrary("scopus");
  }

  private NativeOs() { }

  /** Sets the process working directory; upstream resolves all game paths relative to it. */
  public static native void chdir(String path) throws IOException;

  /**
   * Installs signal handlers (SIGSEGV/SIGABRT/SIGBUS/SIGILL/SIGFPE/SIGTRAP) that
   * write a marker file at {@code markerPath} on a fatal crash, then re-raise
   * the default handler so the system tombstone is still produced. Java checks
   * for the marker on the next launch.
   */
  public static native void installCrashHandler(String markerPath);
}
