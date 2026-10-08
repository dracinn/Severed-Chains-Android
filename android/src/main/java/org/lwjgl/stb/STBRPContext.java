package org.lwjgl.stb;

/** Opaque rect-pack context for the STBRectPack shim. */
public class STBRPContext {
  int width;
  int height;

  STBRPContext() { }

  public static STBRPContext malloc() {
    return new STBRPContext();
  }

  public void free() {
  }
}
