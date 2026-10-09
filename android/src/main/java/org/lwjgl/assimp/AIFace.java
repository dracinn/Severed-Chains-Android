package org.lwjgl.assimp;

import java.nio.IntBuffer;

/**
 * Android replacement for the LWJGL assimp face. Each face is one triangle.
 */
public class AIFace {
  private final int[] indices;

  AIFace(final int[] indices) {
    this.indices = indices;
  }

  public int mNumIndices() {
    return this.indices.length;
  }

  public IntBuffer mIndices() {
    return IntBuffer.wrap(this.indices);
  }

  public static class Buffer {
    private final AIFace[] faces;
    private int position;

    Buffer(final int[] indices) {
      this.faces = new AIFace[indices.length / 3];
      for(int i = 0; i < this.faces.length; i++) {
        this.faces[i] = new AIFace(new int[] {indices[i * 3], indices[i * 3 + 1], indices[i * 3 + 2]});
      }
    }

    public boolean hasRemaining() {
      return this.position < this.faces.length;
    }

    public AIFace get() {
      return this.faces[this.position++];
    }
  }
}
