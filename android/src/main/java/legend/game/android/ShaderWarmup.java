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
    GLES20.glGenFramebuffers(1, ids, 0);
    GLES20.glGenTextures(1, ids, 1);
    GLES20.glGenRenderbuffers(1, ids, 2);
    GLES20.glGenBuffers(2, ids, 3);
    final int fbo = ids[0], texture = ids[1], depth = ids[2], vbo = ids[3], ubo = ids[4];
    final int[] vaoId = new int[1];
    GLES30.glGenVertexArrays(1, vaoId, 0);
    final int vao = vaoId[0];

    final int[] prevFbo = new int[1];
    GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, prevFbo, 0);
    final int[] prevViewport = new int[4];
    GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, prevViewport, 0);

    try {
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
      GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 64, 64, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
      GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, depth);
      GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, 64, 64);

      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
      GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0);
      GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, depth);

      // Degenerate primitive; attribute 0 is position in every program. The
      // TMD-family programs carry a geometry shader fed by adjacency records
      // (6 verts per primitive) — drawing plain triangles would be rejected.
      final ByteBuffer verts = ByteBuffer.allocateDirect(18 * 4).order(ByteOrder.nativeOrder());
      verts.asFloatBuffer().put(new float[18]);
      GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
      GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.remaining(), verts, GLES20.GL_STATIC_DRAW);
      GLES30.glBindVertexArray(vao);
      GLES20.glEnableVertexAttribArray(0);
      GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 0, 0);

      // Every program has UBO blocks at fixed binding points; an unbound
      // binding makes the driver log a validation error per draw.
      GLES20.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, ubo);
      GLES20.glBufferData(GLES30.GL_UNIFORM_BUFFER, 64 * 1024, null, GLES20.GL_STATIC_DRAW);
      for(int binding = 0; binding <= 5; binding++) {
        GLES30.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, binding, ubo);
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
          GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, target);
          GLES20.glViewport(0, 0, 64, 64);
          GLES20.glDisable(GLES20.GL_BLEND);
          GLES20.glEnable(GLES20.GL_DEPTH_TEST);
          GLES20.glDrawArrays(mode, 0, count);
          GLES20.glEnable(GLES20.GL_BLEND);
          GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
          GLES20.glDrawArrays(mode, 0, count);
        }
      }
    } finally {
      GLES20.glDisableVertexAttribArray(0);
      GLES30.glBindVertexArray(0);
      GLES30.glDeleteVertexArrays(1, vaoId, 0);
      GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
      GLES20.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, 0);
      for(int binding = 0; binding <= 5; binding++) {
        GLES30.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, binding, 0);
      }
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFbo[0]);
      GLES20.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
      GLES20.glDisable(GLES20.GL_DEPTH_TEST);
      GLES20.glDisable(GLES20.GL_BLEND);
      GLES20.glDeleteFramebuffers(1, new int[] {fbo}, 0);
      GLES20.glDeleteTextures(1, new int[] {texture}, 0);
      GLES20.glDeleteRenderbuffers(1, new int[] {depth}, 0);
      GLES20.glDeleteBuffers(1, new int[] {vbo}, 0);
      GLES20.glDeleteBuffers(1, new int[] {ubo}, 0);
    }

    Log.i(TAG, "Pipeline warmup done in " + (System.nanoTime() - start) / 1_000_000 + "ms");
  }
}
