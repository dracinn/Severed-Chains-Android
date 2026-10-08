package org.lwjgl.system;

import org.lwjgl.BufferUtils;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal Android replacement for LWJGL MemoryUtil. memAddress() maps a NIO
 * buffer to a synthetic address so pointer-style GL APIs (e.g.
 * glTexImage2D(..., long)) can resolve the buffer again inside the shim layer.
 */
public final class MemoryUtil {
  public static final long NULL = 0L;

  private static final AtomicLong NEXT_ADDRESS = new AtomicLong(0x1_0000_0000L);
  private static final Map<Long, Buffer> BUFFERS = new ConcurrentHashMap<>();

  private MemoryUtil() { }

  public static ByteBuffer memAlloc(final int size) {
    return BufferUtils.createByteBuffer(size);
  }

  public static IntBuffer memAllocInt(final int size) {
    return BufferUtils.createIntBuffer(size);
  }

  public static FloatBuffer memAllocFloat(final int size) {
    return BufferUtils.createFloatBuffer(size);
  }

  /** Direct buffers are GC-managed on Android; nothing to free. */
  public static void memFree(final Buffer buffer) {
  }

  public static ByteBuffer memByteBuffer(final long address, final int capacity) {
    final Buffer buffer = BUFFERS.get(address);
    return (ByteBuffer)buffer;
  }

  public static long memAddress(final Buffer buffer) {
    if(buffer == null) {
      return NULL;
    }

    final long address = NEXT_ADDRESS.getAndIncrement();
    BUFFERS.put(address, buffer);
    return address;
  }

  public static long memAddressSafe(final Buffer buffer) {
    return memAddress(buffer);
  }

  /**
   * Used by the GL shims to resolve a synthetic address back to its buffer.
   * Removes the entry: every memAddress() call site resolves exactly once, and
   * without removal the map would grow forever (called per texture/message).
   */
  public static Buffer bufferForAddress(final long address) {
    return BUFFERS.remove(address);
  }

  public static <T extends Buffer> T memSlice(final T buffer) {
    @SuppressWarnings("unchecked")
    final T slice = (T)buffer.slice();
    return slice;
  }
}
