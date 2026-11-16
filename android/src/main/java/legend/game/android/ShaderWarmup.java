package legend.game.android;

import android.opengl.GLES20;
import android.opengl.GLES30;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import legend.core.renderer.RenderEngine;
import legend.core.renderer.Shader;
import legend.core.renderer.ShaderManager;
import legend.core.renderer.ShaderType;

/**
 * Mobile drivers defer backend pipeline compilation until a program is first
 * drawn, so a program compiled at boot can still hitch on its first real use
 * (first battle, first submap). Draws one triangle per program into a scratch
 * FBO and the default framebuffer under opaque and blended state, moving that
 * finalization cost into the loading phase. Must run on the GL thread after
 * the shaders are registered.
 */
public final class ShaderWarmup {
  private static final String TAG = "ShaderWarmup";

  private ShaderWarmup() { }

  public static void warmup() {
    final long start = System.nanoTime();

    final int[] ids = new int[5];
    GlNative.glGenFramebuffersA(1, ids, 0);
    GlNative.glGenTexturesA(1, ids, 1);
    GlNative.glGenRenderbuffersA(1, ids, 2);
    GlNative.glGenBuffersA(2, ids, 3);
    final int fbo = ids[0], texture = ids[1], depth = ids[2], vbo = ids[3], ubo = ids[4];
    final int[] vaoId = new int[1];
    GlNative.glGenVertexArraysA(1, vaoId, 0);
    final int vao = vaoId[0];

    final int[] prevFbo = new int[1];
    GlNative.glGetIntegervA(GLES20.GL_FRAMEBUFFER_BINDING, prevFbo, 0);
    final int[] prevViewport = new int[4];
    GlNative.glGetIntegervA(GLES20.GL_VIEWPORT, prevViewport, 0);

    try {
      GlNative.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
      GlNative.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 64, 64, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null, 0);
      GlNative.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, depth);
      GlNative.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, 64, 64);

      GlNative.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
      GlNative.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0);
      GlNative.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, depth);

      // Degenerate primitive; attribute 0 is position in every program. The
      // TMD-family programs carry a geometry shader fed by adjacency records
      // (6 verts per primitive) — drawing plain triangles would be rejected.
      final ByteBuffer verts = ByteBuffer.allocateDirect(18 * 4).order(ByteOrder.nativeOrder());
      verts.asFloatBuffer().put(new float[18]);
      GlNative.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
      GlNative.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.remaining(), verts, 0, GLES20.GL_STATIC_DRAW);
      GlNative.glBindVertexArray(vao);
      GlNative.glEnableVertexAttribArray(0);
      GlNative.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 0, 0L);

      // Every program has UBO blocks at fixed binding points; an unbound
      // binding makes the driver log a validation error per draw.
      GlNative.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, ubo);
      GlNative.glBufferData(GLES30.GL_UNIFORM_BUFFER, 64 * 1024, null, 0, GLES20.GL_STATIC_DRAW);
      for(int binding = 0; binding <= 5; binding++) {
        GlNative.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, binding, ubo);
      }

      // GL_TRIANGLES_ADJACENCY only exists on the ES 3.2 geometry-shader path;
      // on ES 3.1 the TMD programs are linked without a GS and draw triangles.
      final int adjacencyMode = GlesCompat.geometryShaders ? 0x000C : GLES20.GL_TRIANGLES;
      final int adjacencyCount = GlesCompat.geometryShaders ? 6 : 3;
      final ShaderType<?>[] types = {
        RenderEngine.SIMPLE_SHADER, RenderEngine.COPY_SHADER, RenderEngine.SCREEN_SHADER,
        RenderEngine.STANDARD_SHADER, RenderEngine.TMD_SHADER, RenderEngine.BATTLE_TMD_SHADER,
      };

      for(final ShaderType<?> type : types) {
        final Shader<?> shader = ShaderManager.getShader(type);
        if(shader == null) {
          continue;
        }
        final int mode = type == RenderEngine.TMD_SHADER || type == RenderEngine.BATTLE_TMD_SHADER ? adjacencyMode : GLES20.GL_TRIANGLES;
        final int count = mode == GLES20.GL_TRIANGLES ? 3 : adjacencyCount;
        shader.use();
        for(final int target : new int[] {fbo, 0}) {
          GlNative.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, target);
          GlNative.glViewport(0, 0, 64, 64);
          GlNative.glDisable(GLES20.GL_BLEND);
          GlNative.glEnable(GLES20.GL_DEPTH_TEST);
          GlNative.glDrawArrays(mode, 0, count);
          GlNative.glEnable(GLES20.GL_BLEND);
          GlNative.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
          GlNative.glDrawArrays(mode, 0, count);
        }
      }
    } finally {
      GlNative.glDisableVertexAttribArray(0);
      GlNative.glBindVertexArray(0);
      GlNative.glDeleteVertexArraysA(1, vaoId, 0);
      GlNative.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
      GlNative.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, 0);
      for(int binding = 0; binding <= 5; binding++) {
        GlNative.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, binding, 0);
      }
      GlNative.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFbo[0]);
      GlNative.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
      GlNative.glDisable(GLES20.GL_DEPTH_TEST);
      GlNative.glDisable(GLES20.GL_BLEND);
      GlNative.glDeleteFramebuffersA(1, new int[] {fbo}, 0);
      GlNative.glDeleteTexturesA(1, new int[] {texture}, 0);
      GlNative.glDeleteRenderbuffersA(1, new int[] {depth}, 0);
      GlNative.glDeleteBuffersA(1, new int[] {vbo}, 0);
      GlNative.glDeleteBuffersA(1, new int[] {ubo}, 0);
    }

    Log.i(TAG, "Pipeline warmup done in " + (System.nanoTime() - start) / 1_000_000 + "ms");
  }
}
