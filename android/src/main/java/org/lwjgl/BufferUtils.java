package org.lwjgl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

/** Minimal Android replacement for LWJGL BufferUtils (native-order direct buffers). */
public final class BufferUtils {
  private BufferUtils() { }

  public static ByteBuffer createByteBuffer(final int capacity) {
    return ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
  }

  public static ShortBuffer createShortBuffer(final int capacity) {
    return createByteBuffer(capacity * Short.BYTES).asShortBuffer();
  }

  public static IntBuffer createIntBuffer(final int capacity) {
    return createByteBuffer(capacity * Integer.BYTES).asIntBuffer();
  }

  public static FloatBuffer createFloatBuffer(final int capacity) {
    return createByteBuffer(capacity * Float.BYTES).asFloatBuffer();
  }

  public static LongBuffer createLongBuffer(final int capacity) {
    return createByteBuffer(capacity * Long.BYTES).asLongBuffer();
  }

  public static DoubleBuffer createDoubleBuffer(final int capacity) {
    return createByteBuffer(capacity * Double.BYTES).asDoubleBuffer();
  }

  public static PointerBuffer createPointerBuffer(final int capacity) {
    return PointerBuffer.allocateDirect(capacity);
  }
}
