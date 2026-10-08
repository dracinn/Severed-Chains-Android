package org.lwjgl.opengles;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** LWJGL debug-message callback shape for the GLES shim layer. */
public interface GLDebugMessageCallback {
  void invoke(int source, int type, int id, int severity, int length, long message, long userParam);

  static GLDebugMessageCallback create(final GLDebugMessageCallback callback) {
    return callback;
  }

  default void free() {
  }

  static String getMessage(final int length, final long message) {
    final ByteBuffer buffer = (ByteBuffer)MemoryUtil.bufferForAddress(message);
    if(buffer == null) {
      return "";
    }

    final byte[] bytes = new byte[Math.min(length, buffer.remaining())];
    buffer.get(bytes);
    return new String(bytes, StandardCharsets.UTF_8).replace("\0", "");
  }
}
