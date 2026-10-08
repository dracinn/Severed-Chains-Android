package org.bytedeco.javacv;

import java.nio.Buffer;

/**
 * Android replacement for javacv's Frame. Only the members upstream
 * VideoPlayer touches are provided.
 */
public class Frame {
  /** Decoded image buffers (RGB24 ByteBuffer in image[0]). */
  public Buffer[] image;
  /** Decoded audio buffers (interleaved PCM16 ShortBuffer in samples[0]). */
  public Buffer[] samples;
  /** Presentation timestamp in microseconds. */
  public long timestamp;
}
