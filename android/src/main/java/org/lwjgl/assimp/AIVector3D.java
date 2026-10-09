package org.lwjgl.assimp;

/**
 * Android replacement for the LWJGL assimp vector. Backed by plain floats.
 */
public class AIVector3D {
  private final float x;
  private final float y;
  private final float z;

  private AIVector3D(final float x, final float y, final float z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  public float x() {
    return this.x;
  }

  public float y() {
    return this.y;
  }

  public float z() {
    return this.z;
  }

  public static class Buffer {
    private final AIVector3D[] elements;
    private int position;

    Buffer(final float[] data) {
      this.elements = new AIVector3D[data.length / 3];
      for(int i = 0; i < this.elements.length; i++) {
        this.elements[i] = new AIVector3D(data[i * 3], data[i * 3 + 1], data[i * 3 + 2]);
      }
    }

    public AIVector3D get() {
      return this.elements[this.position++];
    }

    public AIVector3D get(final int index) {
      return this.elements[index];
    }

    public boolean hasRemaining() {
      return this.position < this.elements.length;
    }
  }
}
