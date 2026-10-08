package org.lwjgl.opengles;

import org.lwjgl.system.MemoryUtil;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/** LWJGL GLES20 surface mapped to android.opengl.GLES20. */
public class GLES20 {
  public static final int GL_DEPTH_BUFFER_BIT = android.opengl.GLES20.GL_DEPTH_BUFFER_BIT;
  public static final int GL_STENCIL_BUFFER_BIT = android.opengl.GLES20.GL_STENCIL_BUFFER_BIT;
  public static final int GL_COLOR_BUFFER_BIT = android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
  public static final int GL_POINTS = android.opengl.GLES20.GL_POINTS;
  public static final int GL_LINES = android.opengl.GLES20.GL_LINES;
  public static final int GL_LINE_LOOP = android.opengl.GLES20.GL_LINE_LOOP;
  public static final int GL_LINE_STRIP = android.opengl.GLES20.GL_LINE_STRIP;
  public static final int GL_TRIANGLES = android.opengl.GLES20.GL_TRIANGLES;
  public static final int GL_TRIANGLE_STRIP = android.opengl.GLES20.GL_TRIANGLE_STRIP;
  public static final int GL_TRIANGLE_FAN = android.opengl.GLES20.GL_TRIANGLE_FAN;
  public static final int GL_NEVER = android.opengl.GLES20.GL_NEVER;
  public static final int GL_LESS = android.opengl.GLES20.GL_LESS;
  public static final int GL_EQUAL = android.opengl.GLES20.GL_EQUAL;
  public static final int GL_LEQUAL = android.opengl.GLES20.GL_LEQUAL;
  public static final int GL_GREATER = android.opengl.GLES20.GL_GREATER;
  public static final int GL_NOTEQUAL = android.opengl.GLES20.GL_NOTEQUAL;
  public static final int GL_GEQUAL = android.opengl.GLES20.GL_GEQUAL;
  public static final int GL_ALWAYS = android.opengl.GLES20.GL_ALWAYS;
  public static final int GL_ZERO = android.opengl.GLES20.GL_ZERO;
  public static final int GL_ONE = android.opengl.GLES20.GL_ONE;
  public static final int GL_SRC_ALPHA = android.opengl.GLES20.GL_SRC_ALPHA;
  public static final int GL_ONE_MINUS_SRC_ALPHA = android.opengl.GLES20.GL_ONE_MINUS_SRC_ALPHA;
  public static final int GL_CULL_FACE = android.opengl.GLES20.GL_CULL_FACE;
  public static final int GL_FRONT = android.opengl.GLES20.GL_FRONT;
  public static final int GL_BACK = android.opengl.GLES20.GL_BACK;
  public static final int GL_FRONT_AND_BACK = android.opengl.GLES20.GL_FRONT_AND_BACK;
  public static final int GL_NO_ERROR = android.opengl.GLES20.GL_NO_ERROR;
  public static final int GL_INVALID_ENUM = android.opengl.GLES20.GL_INVALID_ENUM;
  public static final int GL_INVALID_VALUE = android.opengl.GLES20.GL_INVALID_VALUE;
  public static final int GL_INVALID_OPERATION = android.opengl.GLES20.GL_INVALID_OPERATION;
  public static final int GL_INVALID_FRAMEBUFFER_OPERATION = android.opengl.GLES20.GL_INVALID_FRAMEBUFFER_OPERATION;
  public static final int GL_OUT_OF_MEMORY = android.opengl.GLES20.GL_OUT_OF_MEMORY;
  public static final int GL_UNSIGNED_BYTE = android.opengl.GLES20.GL_UNSIGNED_BYTE;
  public static final int GL_UNSIGNED_SHORT = android.opengl.GLES20.GL_UNSIGNED_SHORT;
  public static final int GL_UNSIGNED_INT = android.opengl.GLES20.GL_UNSIGNED_INT;
  public static final int GL_BYTE = android.opengl.GLES20.GL_BYTE;
  public static final int GL_SHORT = android.opengl.GLES20.GL_SHORT;
  public static final int GL_INT = android.opengl.GLES20.GL_INT;
  public static final int GL_FLOAT = android.opengl.GLES20.GL_FLOAT;
  public static final int GL_FIXED = android.opengl.GLES20.GL_FIXED;
  public static final int GL_DEPTH_TEST = android.opengl.GLES20.GL_DEPTH_TEST;
  public static final int GL_BLEND = android.opengl.GLES20.GL_BLEND;
  public static final int GL_SCISSOR_TEST = android.opengl.GLES20.GL_SCISSOR_TEST;
  public static final int GL_DITHER = android.opengl.GLES20.GL_DITHER;
  public static final int GL_TEXTURE_2D = android.opengl.GLES20.GL_TEXTURE_2D;
  public static final int GL_TEXTURE = 0x1702; // GL_TEXTURE (object label identifier, not on android GLES20)
  public static final int GL_TEXTURE0 = android.opengl.GLES20.GL_TEXTURE0;
  public static final int GL_TEXTURE_WRAP_S = android.opengl.GLES20.GL_TEXTURE_WRAP_S;
  public static final int GL_TEXTURE_WRAP_T = android.opengl.GLES20.GL_TEXTURE_WRAP_T;
  public static final int GL_TEXTURE_MAG_FILTER = android.opengl.GLES20.GL_TEXTURE_MAG_FILTER;
  public static final int GL_TEXTURE_MIN_FILTER = android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
  public static final int GL_TEXTURE_BINDING_2D = android.opengl.GLES20.GL_TEXTURE_BINDING_2D;
  public static final int GL_NEAREST = android.opengl.GLES20.GL_NEAREST;
  public static final int GL_LINEAR = android.opengl.GLES20.GL_LINEAR;
  public static final int GL_REPEAT = android.opengl.GLES20.GL_REPEAT;
  public static final int GL_CLAMP_TO_EDGE = android.opengl.GLES20.GL_CLAMP_TO_EDGE;
  public static final int GL_RGB = android.opengl.GLES20.GL_RGB;
  public static final int GL_RGBA = android.opengl.GLES20.GL_RGBA;
  public static final int GL_ALPHA = android.opengl.GLES20.GL_ALPHA;
  public static final int GL_LUMINANCE = android.opengl.GLES20.GL_LUMINANCE;
  public static final int GL_DEPTH_COMPONENT = android.opengl.GLES20.GL_DEPTH_COMPONENT;
  public static final int GL_ARRAY_BUFFER = android.opengl.GLES20.GL_ARRAY_BUFFER;
  public static final int GL_ELEMENT_ARRAY_BUFFER = android.opengl.GLES20.GL_ELEMENT_ARRAY_BUFFER;
  public static final int GL_ARRAY_BUFFER_BINDING = android.opengl.GLES20.GL_ARRAY_BUFFER_BINDING;
  public static final int GL_ELEMENT_ARRAY_BUFFER_BINDING = android.opengl.GLES20.GL_ELEMENT_ARRAY_BUFFER_BINDING;
  public static final int GL_STATIC_DRAW = android.opengl.GLES20.GL_STATIC_DRAW;
  public static final int GL_DYNAMIC_DRAW = android.opengl.GLES20.GL_DYNAMIC_DRAW;
  public static final int GL_STREAM_DRAW = android.opengl.GLES20.GL_STREAM_DRAW;
  public static final int GL_FRAMEBUFFER = android.opengl.GLES20.GL_FRAMEBUFFER;
  public static final int GL_FRAMEBUFFER_BINDING = android.opengl.GLES20.GL_FRAMEBUFFER_BINDING;
  public static final int GL_FRAMEBUFFER_COMPLETE = android.opengl.GLES20.GL_FRAMEBUFFER_COMPLETE;
  public static final int GL_COLOR_ATTACHMENT0 = android.opengl.GLES20.GL_COLOR_ATTACHMENT0;
  public static final int GL_DEPTH_ATTACHMENT = android.opengl.GLES20.GL_DEPTH_ATTACHMENT;
  public static final int GL_STENCIL_ATTACHMENT = android.opengl.GLES20.GL_STENCIL_ATTACHMENT;
  public static final int GL_DEPTH_COMPONENT16 = android.opengl.GLES20.GL_DEPTH_COMPONENT16;
  public static final int GL_FRAGMENT_SHADER = android.opengl.GLES20.GL_FRAGMENT_SHADER;
  public static final int GL_VERTEX_SHADER = android.opengl.GLES20.GL_VERTEX_SHADER;
  public static final int GL_COMPILE_STATUS = android.opengl.GLES20.GL_COMPILE_STATUS;
  public static final int GL_LINK_STATUS = android.opengl.GLES20.GL_LINK_STATUS;
  public static final int GL_INFO_LOG_LENGTH = android.opengl.GLES20.GL_INFO_LOG_LENGTH;
  public static final int GL_CURRENT_PROGRAM = android.opengl.GLES20.GL_CURRENT_PROGRAM;
  public static final int GL_FUNC_ADD = android.opengl.GLES20.GL_FUNC_ADD;
  public static final int GL_FUNC_SUBTRACT = android.opengl.GLES20.GL_FUNC_SUBTRACT;
  public static final int GL_FUNC_REVERSE_SUBTRACT = android.opengl.GLES20.GL_FUNC_REVERSE_SUBTRACT;
  public static final int GL_VENDOR = android.opengl.GLES20.GL_VENDOR;
  public static final int GL_RENDERER = android.opengl.GLES20.GL_RENDERER;
  public static final int GL_VERSION = android.opengl.GLES20.GL_VERSION;
  public static final int GL_SHADING_LANGUAGE_VERSION = android.opengl.GLES20.GL_SHADING_LANGUAGE_VERSION;
  public static final int GL_DONT_CARE = android.opengl.GLES20.GL_DONT_CARE;
  public static final int GL_UNPACK_ALIGNMENT = android.opengl.GLES20.GL_UNPACK_ALIGNMENT;
  public static final int GL_PACK_ALIGNMENT = android.opengl.GLES20.GL_PACK_ALIGNMENT;

  protected static int elementSize(final Buffer buffer) {
    if(buffer instanceof FloatBuffer || buffer instanceof IntBuffer) {
      return 4;
    }
    if(buffer instanceof ShortBuffer) {
      return 2;
    }
    return 1;
  }

  public static void glActiveTexture(final int texture) {
    android.opengl.GLES20.glActiveTexture(texture);
  }

  public static void glAttachShader(final int program, final int shader) {
    android.opengl.GLES20.glAttachShader(program, shader);
  }

  public static void glBindBuffer(final int target, final int buffer) {
    android.opengl.GLES20.glBindBuffer(target, buffer);
  }

  public static void glBindFramebuffer(final int target, final int framebuffer) {
    android.opengl.GLES20.glBindFramebuffer(target, framebuffer);
  }

  public static void glBindTexture(final int target, final int texture) {
    android.opengl.GLES20.glBindTexture(target, texture);
  }

  public static void glBlendEquation(final int mode) {
    android.opengl.GLES20.glBlendEquation(mode);
  }

  public static void glBlendFunc(final int sfactor, final int dfactor) {
    android.opengl.GLES20.glBlendFunc(sfactor, dfactor);
  }

  public static void glBufferData(final int target, final Buffer data, final int usage) {
    android.opengl.GLES20.glBufferData(target, data.remaining() * elementSize(data), data, usage);
  }

  public static void glBufferData(final int target, final long size, final int usage) {
    android.opengl.GLES20.glBufferData(target, (int)size, null, usage);
  }

  public static void glBufferData(final int target, final float[] data, final int usage) {
    android.opengl.GLES20.glBufferData(target, data.length * 4, FloatBuffer.wrap(data), usage);
  }

  public static void glBufferData(final int target, final int[] data, final int usage) {
    android.opengl.GLES20.glBufferData(target, data.length * 4, IntBuffer.wrap(data), usage);
  }

  public static void glBufferData(final int target, final short[] data, final int usage) {
    android.opengl.GLES20.glBufferData(target, data.length * 2, ShortBuffer.wrap(data), usage);
  }

  public static void glBufferData(final int target, final byte[] data, final int usage) {
    android.opengl.GLES20.glBufferData(target, data.length, ByteBuffer.wrap(data), usage);
  }

  public static void glBufferSubData(final int target, final long offset, final Buffer data) {
    android.opengl.GLES20.glBufferSubData(target, (int)offset, data.remaining() * elementSize(data), data);
  }

  public static void glBufferSubData(final int target, final long offset, final float[] data) {
    android.opengl.GLES20.glBufferSubData(target, (int)offset, data.length * 4, FloatBuffer.wrap(data));
  }

  public static void glBufferSubData(final int target, final long offset, final int[] data) {
    android.opengl.GLES20.glBufferSubData(target, (int)offset, data.length * 4, IntBuffer.wrap(data));
  }

  public static int glCheckFramebufferStatus(final int target) {
    return android.opengl.GLES20.glCheckFramebufferStatus(target);
  }

  public static void glClear(final int mask) {
    android.opengl.GLES20.glClear(mask);
  }

  public static void glClearColor(final float red, final float green, final float blue, final float alpha) {
    android.opengl.GLES20.glClearColor(red, green, blue, alpha);
  }

  public static void glCompileShader(final int shader) {
    android.opengl.GLES20.glCompileShader(shader);
  }

  public static int glCreateProgram() {
    return android.opengl.GLES20.glCreateProgram();
  }

  public static int glCreateShader(final int type) {
    return android.opengl.GLES20.glCreateShader(type);
  }

  public static void glDeleteBuffers(final int buffer) {
    android.opengl.GLES20.glDeleteBuffers(1, new int[] {buffer}, 0);
  }

  public static void glDeleteBuffers(final IntBuffer buffers) {
    android.opengl.GLES20.glDeleteBuffers(buffers.remaining(), buffers);
  }

  public static void glDeleteBuffers(final int[] buffers) {
    android.opengl.GLES20.glDeleteBuffers(buffers.length, buffers, 0);
  }

  public static void glDeleteFramebuffers(final int framebuffer) {
    android.opengl.GLES20.glDeleteFramebuffers(1, new int[] {framebuffer}, 0);
  }

  public static void glDeleteProgram(final int program) {
    android.opengl.GLES20.glDeleteProgram(program);
  }

  public static void glDeleteShader(final int shader) {
    android.opengl.GLES20.glDeleteShader(shader);
  }

  public static void glDeleteTextures(final int texture) {
    android.opengl.GLES20.glDeleteTextures(1, new int[] {texture}, 0);
  }

  public static void glDeleteTextures(final IntBuffer textures) {
    android.opengl.GLES20.glDeleteTextures(textures.remaining(), textures);
  }

  public static void glDepthFunc(final int func) {
    android.opengl.GLES20.glDepthFunc(func);
  }

  public static void glDepthMask(final boolean flag) {
    android.opengl.GLES20.glDepthMask(flag);
  }

  public static void glDisable(final int cap) {
    android.opengl.GLES20.glDisable(cap);
  }

  public static void glDrawArrays(final int mode, final int first, final int count) {
    android.opengl.GLES20.glDrawArrays(mode, first, count);
  }

  public static void glDrawElements(final int mode, final int count, final int type, final long indices) {
    android.opengl.GLES20.glDrawElements(mode, count, type, (int)indices);
  }

  public static void glDrawElements(final int mode, final int count, final int type, final Buffer indices) {
    android.opengl.GLES20.glDrawElements(mode, count, type, indices);
  }

  public static void glEnable(final int cap) {
    android.opengl.GLES20.glEnable(cap);
  }

  public static void glEnableVertexAttribArray(final int index) {
    android.opengl.GLES20.glEnableVertexAttribArray(index);
  }

  public static void glDisableVertexAttribArray(final int index) {
    android.opengl.GLES20.glDisableVertexAttribArray(index);
  }

  public static void glFramebufferTexture2D(final int target, final int attachment, final int textarget, final int texture, final int level) {
    android.opengl.GLES20.glFramebufferTexture2D(target, attachment, textarget, texture, level);
  }

  public static int glGenBuffers() {
    final int[] out = new int[1];
    android.opengl.GLES20.glGenBuffers(1, out, 0);
    return out[0];
  }

  public static void glGenBuffers(final IntBuffer buffers) {
    android.opengl.GLES20.glGenBuffers(buffers.remaining(), buffers);
  }

  public static int glGenFramebuffers() {
    final int[] out = new int[1];
    android.opengl.GLES20.glGenFramebuffers(1, out, 0);
    return out[0];
  }

  public static int glGenTextures() {
    final int[] out = new int[1];
    android.opengl.GLES20.glGenTextures(1, out, 0);
    return out[0];
  }

  public static void glGenTextures(final IntBuffer textures) {
    android.opengl.GLES20.glGenTextures(textures.remaining(), textures);
  }

  public static int glGetError() {
    return android.opengl.GLES20.glGetError();
  }

  public static int glGetInteger(final int pname) {
    final int[] out = new int[1];
    android.opengl.GLES20.glGetIntegerv(pname, out, 0);
    return out[0];
  }

  public static void glGetIntegerv(final int pname, final IntBuffer params) {
    android.opengl.GLES20.glGetIntegerv(pname, params);
  }

  public static void glGetIntegerv(final int pname, final int[] params) {
    android.opengl.GLES20.glGetIntegerv(pname, params, 0);
  }

  public static int glGetProgrami(final int program, final int pname) {
    final int[] out = new int[1];
    android.opengl.GLES20.glGetProgramiv(program, pname, out, 0);
    return out[0];
  }

  public static String glGetProgramInfoLog(final int program) {
    return android.opengl.GLES20.glGetProgramInfoLog(program);
  }

  public static int glGetShaderi(final int shader, final int pname) {
    final int[] out = new int[1];
    android.opengl.GLES20.glGetShaderiv(shader, pname, out, 0);
    return out[0];
  }

  public static String glGetShaderInfoLog(final int shader) {
    return android.opengl.GLES20.glGetShaderInfoLog(shader);
  }

  public static String glGetString(final int name) {
    return android.opengl.GLES20.glGetString(name);
  }

  public static int glGetUniformLocation(final int program, final CharSequence name) {
    return android.opengl.GLES20.glGetUniformLocation(program, name.toString());
  }

  public static void glLineWidth(final float width) {
    android.opengl.GLES20.glLineWidth(width);
  }

  public static void glLinkProgram(final int program) {
    android.opengl.GLES20.glLinkProgram(program);
  }

  public static void glPixelStorei(final int pname, final int param) {
    android.opengl.GLES20.glPixelStorei(pname, param);
  }

  public static void glReadPixels(final int x, final int y, final int width, final int height, final int format, final int type, final Buffer pixels) {
    android.opengl.GLES20.glReadPixels(x, y, width, height, format, type, pixels);
  }

  public static void glScissor(final int x, final int y, final int width, final int height) {
    android.opengl.GLES20.glScissor(x, y, width, height);
  }

  public static void glShaderSource(final int shader, final CharSequence source) {
    android.opengl.GLES20.glShaderSource(shader, source.toString());
  }

  private static int sizedInternalFormat(final int internalformat, final int type) {
    // GLES3 rejects unsized internal formats that desktop GL accepts.
    if(internalformat == GL_DEPTH_COMPONENT) {
      return switch(type) {
        case GL_FLOAT -> android.opengl.GLES30.GL_DEPTH_COMPONENT32F;
        case GL_UNSIGNED_INT -> android.opengl.GLES30.GL_DEPTH_COMPONENT24;
        default -> GL_DEPTH_COMPONENT16;
      };
    }

    return internalformat;
  }

  public static void glTexImage2D(final int target, final int level, final int internalformat, final int width, final int height, final int border, final int format, final int type, final Buffer pixels) {
    android.opengl.GLES20.glTexImage2D(target, level, sizedInternalFormat(internalformat, type), width, height, border, format, type, pixels);
  }

  public static void glTexImage2D(final int target, final int level, final int internalformat, final int width, final int height, final int border, final int format, final int type, final long pixels) {
    android.opengl.GLES20.glTexImage2D(target, level, sizedInternalFormat(internalformat, type), width, height, border, format, type, pixels == 0L ? null : MemoryUtil.bufferForAddress(pixels));
  }

  public static void glTexSubImage2D(final int target, final int level, final int xoffset, final int yoffset, final int width, final int height, final int format, final int type, final Buffer pixels) {
    android.opengl.GLES20.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
  }

  public static void glTexSubImage2D(final int target, final int level, final int xoffset, final int yoffset, final int width, final int height, final int format, final int type, final int[] pixels) {
    android.opengl.GLES20.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, IntBuffer.wrap(pixels));
  }

  public static void glTexSubImage2D(final int target, final int level, final int xoffset, final int yoffset, final int width, final int height, final int format, final int type, final long pixels) {
    android.opengl.GLES20.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels == 0L ? null : MemoryUtil.bufferForAddress(pixels));
  }

  public static void glTexParameteri(final int target, final int pname, final int param) {
    android.opengl.GLES20.glTexParameteri(target, pname, param);
  }

  public static void glUniform1f(final int location, final float v0) {
    android.opengl.GLES20.glUniform1f(location, v0);
  }

  public static void glUniform1i(final int location, final int v0) {
    android.opengl.GLES20.glUniform1i(location, v0);
  }

  public static void glUniform2f(final int location, final float v0, final float v1) {
    android.opengl.GLES20.glUniform2f(location, v0, v1);
  }

  public static void glUniform2fv(final int location, final FloatBuffer value) {
    android.opengl.GLES20.glUniform2fv(location, value.remaining() / 2, value);
  }

  public static void glUniform3f(final int location, final float v0, final float v1, final float v2) {
    android.opengl.GLES20.glUniform3f(location, v0, v1, v2);
  }

  public static void glUniform3fv(final int location, final FloatBuffer value) {
    android.opengl.GLES20.glUniform3fv(location, value.remaining() / 3, value);
  }

  public static void glUniform4f(final int location, final float v0, final float v1, final float v2, final float v3) {
    android.opengl.GLES20.glUniform4f(location, v0, v1, v2, v3);
  }

  public static void glUniform4fv(final int location, final FloatBuffer value) {
    android.opengl.GLES20.glUniform4fv(location, value.remaining() / 4, value);
  }

  public static void glUniformMatrix4fv(final int location, final boolean transpose, final FloatBuffer value) {
    android.opengl.GLES20.glUniformMatrix4fv(location, value.remaining() / 16, transpose, value);
  }

  public static void glUseProgram(final int program) {
    android.opengl.GLES20.glUseProgram(program);
  }

  public static void glVertexAttribPointer(final int index, final int size, final int type, final boolean normalized, final int stride, final long pointer) {
    android.opengl.GLES20.glVertexAttribPointer(index, size, type, normalized, stride, (int)pointer);
  }

  public static void glVertexAttribPointer(final int index, final int size, final int type, final boolean normalized, final int stride, final Buffer pointer) {
    android.opengl.GLES20.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
  }

  public static void glViewport(final int x, final int y, final int width, final int height) {
    android.opengl.GLES20.glViewport(x, y, width, height);
  }
}
