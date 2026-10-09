package org.lwjgl.assimp;

import java.nio.ByteBuffer;

/**
 * Android replacement for the LWJGL assimp embedded texture. Holds the
 * still-compressed image bytes (PNG), matching pcDataCompressed().
 */
public class AITexture {
  private final long address;
  private final ByteBuffer data;

  AITexture(final ByteBuffer data) {
    this.address = AIHandles.register(this);
    this.data = data;
  }

  long address() {
    return this.address;
  }

  public static AITexture create(final long address) {
    return AIHandles.get(address);
  }

  public ByteBuffer pcDataCompressed() {
    return this.data.duplicate();
  }
}
