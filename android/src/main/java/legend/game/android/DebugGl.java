package legend.game.android;

import org.lwjgl.opengles.GLDebugMessageCallback;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Bridges native KHR_debug messages into the LWJGL-style callback the engine registers. */
public final class DebugGl {
  static {
    System.loadLibrary("scopus");
  }

  private static volatile GLDebugMessageCallback callback;

  private DebugGl() { }

  /** Must be called on the GL thread with a current context. */
  public static native boolean install();

  public static void setCallback(final GLDebugMessageCallback callback) {
    DebugGl.callback = callback;
  }

  private static int suppressedAdrenoDraws;

  /** Called from native code on the GL thread. */
  public static void onMessage(final int source, final int type, final int id, final int severity, final String message) {
    // Adreno reports glDrawArrays/glDrawElements with fewer than 3 vertices as a
    // HIGH-severity vendor diagnostic (id 0x7fffffff). That's a legal no-op draw
    // — upstream model data legitimately produces degenerate parts (e.g.
    // "SobjModel (index 6) part 27"), desktop drivers stay silent, and upstream
    // would otherwise spam a GameOverlay notification per occurrence.
    if(id == 0x7fff_ffff && message.contains("less than 3 vertices")) {
      suppressedAdrenoDraws++;
      if(suppressedAdrenoDraws == 1) {
        android.util.Log.d("SC-GL", "Suppressing benign Adreno diagnostic: " + message);
      }
      return;
    }

    final GLDebugMessageCallback cb = callback;
    if(cb == null) {
      return;
    }

    final byte[] bytes = (message + "\0").getBytes(StandardCharsets.UTF_8);
    final ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
    buffer.put(bytes).flip();
    cb.invoke(source, type, id, severity, bytes.length, MemoryUtil.memAddress(buffer), 0L);
  }
}
