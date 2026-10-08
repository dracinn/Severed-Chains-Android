package org.lwjgl.system;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

/**
 * Minimal Android replacement for LWJGL MemoryStack. Buffers are plain direct
 * buffers; push/pop are no-ops since nothing depends on stack discipline for
 * correctness here (callers only use the allocated buffer within the try block).
 */
public class MemoryStack implements AutoCloseable {
  protected MemoryStack() { }

  public static MemoryStack stackPush() {
    return new MemoryStack();
  }

  public MemoryStack push() {
    return this;
  }

  public void pop() {
  }

  @Override
  public void close() {
  }

  public ByteBuffer malloc(final int size) {
    return BufferUtils.createByteBuffer(size);
  }

  public IntBuffer mallocInt(final int size) {
    return BufferUtils.createIntBuffer(size);
  }

  public FloatBuffer mallocFloat(final int size) {
    return BufferUtils.createFloatBuffer(size);
  }

  public ShortBuffer mallocShort(final int size) {
    return BufferUtils.createShortBuffer(size);
  }

  public LongBuffer mallocLong(final int size) {
    return BufferUtils.createLongBuffer(size);
  }

  public PointerBuffer mallocPointer(final int size) {
    return PointerBuffer.allocateDirect(size);
  }

  public ByteBuffer UTF8(final CharSequence text) {
    final byte[] bytes = text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    final ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length + 1);
    buffer.put(bytes).put((byte)0).flip();
    return buffer;
  }
}
