package org.lwjgl.util.opus;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * Android replacement for LWJGL's opusfile bindings, backed by libopusfile
 * via the {@code scopus} JNI library (see lwjgl-opus 3.4.3 signatures).
 */
public final class OpusFile {
  static {
    System.loadLibrary("scopus");
  }

  private OpusFile() { }

  private static native long nop_open_memory(ByteBuffer data, IntBuffer error);

  /** The data buffer must stay alive for the life of the returned handle. */
  public static long op_open_memory(final ByteBuffer data, final IntBuffer error) {
    return nop_open_memory(data, error);
  }

  public static native void op_free(long of);

  public static native int op_channel_count(long of, int li);

  public static native long op_pcm_total(long of, int li);

  private static native int nop_read(long of, ShortBuffer pcm, IntBuffer li);

  public static int op_read(final long of, final ShortBuffer pcm, final IntBuffer li) {
    return nop_read(of, pcm, li);
  }
}
