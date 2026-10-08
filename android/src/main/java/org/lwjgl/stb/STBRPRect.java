package org.lwjgl.stb;

/** Rect descriptor for the STBRectPack shim (x/y out, w/h in). */
public class STBRPRect {
  private short x, y, w, h;
  private boolean wasPacked;

  public int x() { return this.x & 0xffff; }
  public int y() { return this.y & 0xffff; }
  public int w() { return this.w & 0xffff; }
  public int h() { return this.h & 0xffff; }
  public void x(final int value) { this.x = (short)value; }
  public void y(final int value) { this.y = (short)value; }
  public void w(final int value) { this.w = (short)value; }
  public void h(final int value) { this.h = (short)value; }
  public boolean wasPacked() { return this.wasPacked; }
  void setPacked(final boolean packed) { this.wasPacked = packed; }

  public static class Buffer {
    private final STBRPRect[] rects;

    Buffer(final int count) {
      this.rects = new STBRPRect[count];
      for(int i = 0; i < count; i++) {
        this.rects[i] = new STBRPRect();
      }
    }

    public STBRPRect get(final int index) {
      return this.rects[index];
    }

    public int limit() {
      return this.rects.length;
    }

    public void free() {
    }
  }

  public static Buffer malloc(final int count) {
    return new Buffer(count);
  }
}
