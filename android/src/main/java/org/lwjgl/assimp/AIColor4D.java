package org.lwjgl.assimp;

/**
 * Android replacement for the LWJGL assimp colour. Backed by plain floats.
 */
public class AIColor4D {
  private final float r;
  private final float g;
  private final float b;
  private final float a;

  private AIColor4D(final float r, final float g, final float b, final float a) {
    this.r = r;
    this.g = g;
    this.b = b;
    this.a = a;
  }

  public float r() {
    return this.r;
  }

  public float g() {
    return this.g;
  }

  public float b() {
    return this.b;
  }

  public float a() {
    return this.a;
  }

  public static class Buffer {
    private final AIColor4D[] elements;
    private int position;

    Buffer(final float[] data) {
      this.elements = new AIColor4D[data.length / 4];
      for(int i = 0; i < this.elements.length; i++) {
        this.elements[i] = new AIColor4D(data[i * 4], data[i * 4 + 1], data[i * 4 + 2], data[i * 4 + 3]);
      }
    }

    public AIColor4D get() {
      return this.elements[this.position++];
    }

    public AIColor4D get(final int index) {
      return this.elements[index];
    }

    public boolean hasRemaining() {
      return this.position < this.elements.length;
    }
  }
}
