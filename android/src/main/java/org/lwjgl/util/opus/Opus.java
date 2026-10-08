package org.lwjgl.util.opus;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * Android replacement for LWJGL's opus bindings, backed by libopus via the
 * {@code scopus} JNI library. Implements the members upstream uses with the
 * same signatures (see lwjgl-opus 3.4.3).
 */
public final class Opus {
  public static final int OPUS_APPLICATION_VOIP = 2048;
  public static final int OPUS_APPLICATION_AUDIO = 2049;
  public static final int OPUS_APPLICATION_RESTRICTED_LOWDELAY = 2051;
  public static final int OPUS_APPLICATION_RESTRICTED_SILK = 2052;
  public static final int OPUS_APPLICATION_RESTRICTED_CELT = 2053;

  public static final int OPUS_SET_BITRATE_REQUEST = 4002;
  public static final int OPUS_RESET_STATE = 4028;

  static {
    System.loadLibrary("scopus");
  }

  private Opus() { }

  /**
   * Mirrors LWJGL's CTLRequest carrier: a ctl request opcode plus its integer
   * argument, applied by {@link #opus_encoder_ctl(long, CTLRequest)}.
   */
  public abstract static class CTLRequest {
    final int request;
    final int value;

    CTLRequest(final int request, final int value) {
      this.request = request;
      this.value = value;
    }
  }

  public static CTLRequest OPUS_SET_BITRATE(final int bitrate) {
    return new CTLRequest(OPUS_SET_BITRATE_REQUEST, bitrate) { };
  }

  private static native long nopus_encoder_create(int Fs, int channels, int application, IntBuffer error);

  public static long opus_encoder_create(final int Fs, final int channels, final int application, final IntBuffer error) {
    return nopus_encoder_create(Fs, channels, application, error);
  }

  private static native int nopus_encoder_ctl(long st, int request);

  /** Vararg-less ctl (e.g. OPUS_RESET_STATE). */
  public static int opus_encoder_ctl(final long st, final int request) {
    return nopus_encoder_ctl(st, request);
  }

  private static native int nopus_encoder_ctl_i(long st, int request, int value);

  public static int opus_encoder_ctl(final long st, final CTLRequest request) {
    return nopus_encoder_ctl_i(st, request.request, request.value);
  }

  private static native int nopus_encode(long st, ShortBuffer pcm, int frameSize, ByteBuffer data);

  public static int opus_encode(final long st, final ShortBuffer pcm, final int frameSize, final ByteBuffer data) {
    return nopus_encode(st, pcm, frameSize, data);
  }

  private static native int nopus_encode_float(long st, FloatBuffer pcm, int frameSize, ByteBuffer data);

  public static int opus_encode_float(final long st, final FloatBuffer pcm, final int frameSize, final ByteBuffer data) {
    return nopus_encode_float(st, pcm, frameSize, data);
  }

  public static native void opus_encoder_destroy(long st);
}
