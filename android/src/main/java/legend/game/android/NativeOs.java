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
}
