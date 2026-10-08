package org.lwjgl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;

/** Minimal replacement for LWJGL PointerBuffer: a buffer of 64-bit "pointers" (handles). */
public class PointerBuffer {
  protected final LongBuffer buffer;

  protected PointerBuffer(final LongBuffer buffer) {
    this.buffer = buffer;
  }

  public static PointerBuffer allocateDirect(final int capacity) {
    return new PointerBuffer(ByteBuffer.allocateDirect(capacity * Long.BYTES).order(ByteOrder.nativeOrder()).asLongBuffer());
  }

  public long get() {
    return this.buffer.get();
  }

  public long get(final int index) {
    return this.buffer.get(index);
  }

  public PointerBuffer put(final long value) {
    this.buffer.put(value);
    return this;
  }

  public int position() {
    return this.buffer.position();
  }

  public PointerBuffer position(final int position) {
    this.buffer.position(position);
    return this;
  }

  public int remaining() {
    return this.buffer.remaining();
  }

  public int limit() {
    return this.buffer.limit();
  }

  public String getStringUTF8(final int index) {
    // Only used for native string results; nothing on Android produces these.
    return "";
  }
}
