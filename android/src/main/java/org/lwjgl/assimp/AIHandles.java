package org.lwjgl.assimp;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stand-in for LWJGL's struct addresses: hands out synthetic long handles so
 * {@code AI*.create(long)} can resolve objects the same way it does against
 * the real native bindings.
 */
final class AIHandles {
  private AIHandles() { }

  private static final AtomicLong NEXT = new AtomicLong(1);
  private static final Map<Long, Object> OBJECTS = new HashMap<>();

  static long register(final Object obj) {
    final long address = NEXT.getAndIncrement();
    OBJECTS.put(address, obj);
    return address;
  }

  @SuppressWarnings("unchecked")
  static <T> T get(final long address) {
    return (T)OBJECTS.get(address);
  }

  static void free(final long address) {
    OBJECTS.remove(address);
  }
}
