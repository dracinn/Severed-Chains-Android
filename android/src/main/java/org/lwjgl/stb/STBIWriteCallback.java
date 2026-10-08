package org.lwjgl.stb;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/** LWJGL write-callback shape for the STBImageWrite shim. */
public interface STBIWriteCallback {
  void invoke(long context, long data, int size);

  static STBIWriteCallback create(final STBIWriteCallback callback) {
    return callback;
  }

  default void free() {
  }

  static ByteBuffer getData(final long data, final int size) {
    final ByteBuffer buffer = (ByteBuffer)MemoryUtil.bufferForAddress(data);
    if(buffer == null) {
      return ByteBuffer.allocate(0);
    }
    return buffer;
  }
}
