package org.lwjgl.opengles;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** LWJGL GLES32 surface mapped to android.opengl.GLES32 (KHR_debug subset). */
public class GLES32 extends GLES30 {
  public static final int GL_GEOMETRY_SHADER = android.opengl.GLES32.GL_GEOMETRY_SHADER;
  public static final int GL_TRIANGLES_ADJACENCY = android.opengl.GLES32.GL_TRIANGLES_ADJACENCY;
  public static final int GL_LINES_ADJACENCY = android.opengl.GLES32.GL_LINES_ADJACENCY;
  public static final int GL_BUFFER = android.opengl.GLES32.GL_BUFFER;
  public static final int GL_PROGRAM = android.opengl.GLES32.GL_PROGRAM;
  public static final int GL_SHADER = android.opengl.GLES32.GL_SHADER;
  public static final int GL_VERTEX_ARRAY = android.opengl.GLES32.GL_VERTEX_ARRAY;
  public static final int GL_FRAMEBUFFER = android.opengl.GLES32.GL_FRAMEBUFFER;
  public static final int GL_CONTEXT_FLAG_DEBUG_BIT = android.opengl.GLES32.GL_CONTEXT_FLAG_DEBUG_BIT;
  public static final int GL_DEBUG_OUTPUT = android.opengl.GLES32.GL_DEBUG_OUTPUT;
  public static final int GL_DEBUG_OUTPUT_SYNCHRONOUS = android.opengl.GLES32.GL_DEBUG_OUTPUT_SYNCHRONOUS;
  public static final int GL_DEBUG_SEVERITY_HIGH = android.opengl.GLES32.GL_DEBUG_SEVERITY_HIGH;
  public static final int GL_DEBUG_SEVERITY_MEDIUM = android.opengl.GLES32.GL_DEBUG_SEVERITY_MEDIUM;
  public static final int GL_DEBUG_SEVERITY_LOW = android.opengl.GLES32.GL_DEBUG_SEVERITY_LOW;
  public static final int GL_DEBUG_SEVERITY_NOTIFICATION = android.opengl.GLES32.GL_DEBUG_SEVERITY_NOTIFICATION;
  public static final int GL_DEBUG_TYPE_ERROR = android.opengl.GLES32.GL_DEBUG_TYPE_ERROR;
  public static final int GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR = android.opengl.GLES32.GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR;
  public static final int GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR = android.opengl.GLES32.GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR;
  public static final int GL_DEBUG_TYPE_PORTABILITY = android.opengl.GLES32.GL_DEBUG_TYPE_PORTABILITY;
  public static final int GL_DEBUG_TYPE_PERFORMANCE = android.opengl.GLES32.GL_DEBUG_TYPE_PERFORMANCE;
  public static final int GL_DEBUG_TYPE_OTHER = android.opengl.GLES32.GL_DEBUG_TYPE_OTHER;
  public static final int GL_DEBUG_SOURCE_API = android.opengl.GLES32.GL_DEBUG_SOURCE_API;
  public static final int GL_DEBUG_SOURCE_APPLICATION = android.opengl.GLES32.GL_DEBUG_SOURCE_APPLICATION;
  public static final int GL_DEBUG_SOURCE_OTHER = android.opengl.GLES32.GL_DEBUG_SOURCE_OTHER;
  public static final int GL_DEBUG_SOURCE_SHADER_COMPILER = android.opengl.GLES32.GL_DEBUG_SOURCE_SHADER_COMPILER;
  public static final int GL_DEBUG_SOURCE_THIRD_PARTY = android.opengl.GLES32.GL_DEBUG_SOURCE_THIRD_PARTY;
  public static final int GL_DEBUG_SOURCE_WINDOW_SYSTEM = android.opengl.GLES32.GL_DEBUG_SOURCE_WINDOW_SYSTEM;
  public static final int GL_TEXTURE_2D_MULTISAMPLE = android.opengl.GLES32.GL_TEXTURE_2D_MULTISAMPLE;
  public static final int GL_SAMPLE_ALPHA_TO_COVERAGE = android.opengl.GLES32.GL_SAMPLE_ALPHA_TO_COVERAGE;

  private static final Map<Long, String> LABEL_NAMES = new ConcurrentHashMap<>();

  public static void glDebugMessageCallback(final GLDebugMessageCallback callback, final long userParam) {
    if(callback == null) {
      legend.game.android.DebugGl.setCallback(null);
      return;
    }

    // android.opengl.GLES32.glDebugMessageCallback is an unimplemented framework
    // stub; register KHR_debug through JNI instead.
    legend.game.android.DebugGl.setCallback(callback);
    if(!legend.game.android.DebugGl.install()) {
      android.util.Log.w("SC-GL", "KHR_debug callback unavailable");
    }
  }

  public static void glDebugMessageControl(final int source, final int type, final int severity, final int[] ids, final boolean enabled) {
    android.opengl.GLES32.glDebugMessageControl(source, type, severity, ids.length, ids, 0, enabled);
  }

  public static void glObjectLabel(final int identifier, final int name, final CharSequence label) {
    final String text = label.toString();
    android.opengl.GLES32.glObjectLabel(identifier, name, text.length(), text);
    LABEL_NAMES.put(((long)identifier << 32) | (name & 0xffff_ffffL), text);
  }

  public static void glObjectLabel(final int identifier, final int name, final Buffer label) {
    final ByteBuffer buf = (ByteBuffer)label;
    final byte[] bytes = new byte[buf.remaining()];
    buf.get(bytes);
    final String text = new String(bytes, StandardCharsets.UTF_8).replace("\0", "");
    glObjectLabel(identifier, name, text);
  }

  public static String glGetObjectLabel(final int identifier, final int name) {
    final String label = LABEL_NAMES.get(((long)identifier << 32) | (name & 0xffff_ffffL));
    return label != null ? label : "";
  }

  public static void glTexStorage2DMultisample(final int target, final int samples, final int internalformat, final int width, final int height, final boolean fixedsamplelocations) {
    android.opengl.GLES31.glTexStorage2DMultisample(target, samples, internalformat, width, height, fixedsamplelocations);
  }

  public static void glFramebufferTexture(final int target, final int attachment, final int texture, final int level) {
    android.opengl.GLES32.glFramebufferTexture(target, attachment, texture, level);
  }
}
