package org.lwjgl.stb;

/** Rect-pack scratch nodes for the STBRectPack shim. */
public class STBRPNode {
  public static class Buffer {
    private final STBRPNode[] nodes;

    Buffer(final int count) {
      this.nodes = new STBRPNode[count];
      for(int i = 0; i < count; i++) {
        this.nodes[i] = new STBRPNode();
      }
    }

    public STBRPNode get(final int index) {
      return this.nodes[index];
    }

    public void free() {
    }
  }

  public static Buffer malloc(final int count) {
    return new Buffer(count);
  }
}
