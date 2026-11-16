package org.lwjgl.opengles;

import java.nio.Buffer;
import java.nio.IntBuffer;

/** LWJGL GLES30 surface mapped to android.opengl.GLES30. */
public class GLES30 extends GLES20 {
  public static final int GL_INVALID_INDEX = android.opengl.GLES30.GL_INVALID_INDEX;
  public static final int GL_R32UI = android.opengl.GLES30.GL_R32UI;
  public static final int GL_RED_INTEGER = android.opengl.GLES30.GL_RED_INTEGER;
  public static final int GL_RGB8 = android.opengl.GLES30.GL_RGB8;
  public static final int GL_RGBA8 = android.opengl.GLES30.GL_RGBA8;
  public static final int GL_R8 = android.opengl.GLES30.GL_R8;
  public static final int GL_RG8 = android.opengl.GLES30.GL_RG8;
  public static final int GL_RG = android.opengl.GLES30.GL_RG;
  public static final int GL_RED = android.opengl.GLES30.GL_RED;
  public static final int GL_DEPTH_COMPONENT24 = android.opengl.GLES30.GL_DEPTH_COMPONENT24;
  public static final int GL_DEPTH_COMPONENT32F = android.opengl.GLES30.GL_DEPTH_COMPONENT32F;
  public static final int GL_DEPTH24_STENCIL8 = android.opengl.GLES30.GL_DEPTH24_STENCIL8;
  public static final int GL_DEPTH_STENCIL_ATTACHMENT = android.opengl.GLES30.GL_DEPTH_STENCIL_ATTACHMENT;
  public static final int GL_UNIFORM_BUFFER = android.opengl.GLES30.GL_UNIFORM_BUFFER;
  public static final int GL_VERTEX_ARRAY_BINDING = android.opengl.GLES30.GL_VERTEX_ARRAY_BINDING;
  public static final int GL_R16F = android.opengl.GLES30.GL_R16F;
  public static final int GL_RG16F = android.opengl.GLES30.GL_RG16F;
  public static final int GL_RGB16F = android.opengl.GLES30.GL_RGB16F;
  public static final int GL_RGBA16F = android.opengl.GLES30.GL_RGBA16F;
  public static final int GL_R32F = android.opengl.GLES30.GL_R32F;
  public static final int GL_RGBA32F = android.opengl.GLES30.GL_RGBA32F;
  public static final int GL_R8UI = android.opengl.GLES30.GL_R8UI;
  public static final int GL_R16UI = android.opengl.GLES30.GL_R16UI;
  public static final int GL_RG32UI = android.opengl.GLES30.GL_RG32UI;
  public static final int GL_RGBA32UI = android.opengl.GLES30.GL_RGBA32UI;
  public static final int GL_R8I = android.opengl.GLES30.GL_R8I;
  public static final int GL_UNSIGNED_INT_24_8 = android.opengl.GLES30.GL_UNSIGNED_INT_24_8;
  public static final int GL_HALF_FLOAT = android.opengl.GLES30.GL_HALF_FLOAT;
  public static final int GL_TEXTURE_BINDING_2D_ARRAY = android.opengl.GLES30.GL_TEXTURE_BINDING_2D_ARRAY;
  public static final int GL_TEXTURE_2D_ARRAY = android.opengl.GLES30.GL_TEXTURE_2D_ARRAY;
  public static final int GL_READ_FRAMEBUFFER = android.opengl.GLES30.GL_READ_FRAMEBUFFER;
  public static final int GL_DRAW_FRAMEBUFFER = android.opengl.GLES30.GL_DRAW_FRAMEBUFFER;
  public static final int GL_DEPTH_STENCIL = android.opengl.GLES30.GL_DEPTH_STENCIL;

  public static void glBindVertexArray(final int array) {
    legend.game.android.GlNative.glBindVertexArray(array);
  }

  public static int glGenVertexArrays() {
    final int[] out = new int[1];
    legend.game.android.GlNative.glGenVertexArraysA(1, out, 0);
    return out[0];
  }

  public static void glGenVertexArrays(final IntBuffer arrays) {
    legend.game.android.GlNative.glGenVertexArrays(remaining(arrays), arrays, byteOff(arrays));
  }

  public static void glDeleteVertexArrays(final int array) {
    legend.game.android.GlNative.glDeleteVertexArraysA(1, new int[] {array}, 0);
  }

  public static void glBindBufferBase(final int target, final int index, final int buffer) {
    legend.game.android.GlNative.glBindBufferBase(target, index, buffer);
  }

  public static void glDrawRangeElements(final int mode, final int start, final int end, final int count, final int type, final long indices) {
    legend.game.android.GlNative.glDrawRangeElements(mode, start, end, count, type, indices);
  }

  public static void glDrawRangeElements(final int mode, final int start, final int end, final int count, final int type, final Buffer indices) {
    legend.game.android.GlNative.glDrawRangeElementsB(mode, start, end, count, type, indices, byteOff(indices));
  }

  public static int glGetUniformBlockIndex(final int program, final CharSequence uniformBlockName) {
    return legend.game.android.GlNative.glGetUniformBlockIndex(program, uniformBlockName.toString());
  }

  public static void glUniformBlockBinding(final int program, final int uniformBlockIndex, final int uniformBlockBinding) {
    legend.game.android.GlNative.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
  }

  public static void glReadBuffer(final int src) {
    legend.game.android.GlNative.glReadBuffer(src);
  }

  public static void glDrawBuffers(final int[] bufs) {
    legend.game.android.GlNative.glDrawBuffersA(bufs.length, bufs, 0);
  }

  public static void glDrawBuffers(final int n, final IntBuffer bufs) {
    legend.game.android.GlNative.glDrawBuffers(n, bufs, byteOff(bufs));
  }

  public static void glBlitFramebuffer(final int srcX0, final int srcY0, final int srcX1, final int srcY1, final int dstX0, final int dstY0, final int dstX1, final int dstY1, final int mask, final int filter) {
    legend.game.android.GlNative.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
  }

  public static void glTexStorage2D(final int target, final int levels, final int internalformat, final int width, final int height) {
    legend.game.android.GlNative.glTexStorage2D(target, levels, internalformat, width, height);
  }

  public static final int GL_PROGRAM_BINARY_RETRIEVABLE_HINT = 0x8257;
  public static final int GL_PROGRAM_BINARY_LENGTH = 0x8741;
  public static final int GL_NUM_PROGRAM_BINARY_FORMATS = 0x87FE;

  public static void glProgramParameteri(final int program, final int pname, final int value) {
    legend.game.android.GlNative.glProgramParameteri(program, pname, value);
  }

  public static void glProgramBinary(final int program, final int binaryFormat, final Buffer binary, final int length) {
    legend.game.android.GlNative.glProgramBinary(program, binaryFormat, binary, byteOff(binary), length);
  }

  /** Returns the program binary format, or -1 if the query failed. */
  public static int glGetProgramBinary(final int program, final Buffer binary) {
    final int[] length = new int[1];
    final int[] format = new int[1];
    legend.game.android.GlNative.glGetProgramBinary(program, remaining(binary), length, 0, format, 0, binary, 0);
    return format[0];
  }
}
