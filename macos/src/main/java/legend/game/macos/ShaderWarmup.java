package legend.game.macos;

import legend.core.GameEngine;
import legend.core.renderer.RenderEngine;
import legend.core.renderer.Shader;
import legend.core.renderer.ShaderManager;
import legend.core.renderer.ShaderType;
import legend.core.renderer.opengl.GlApi;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL32C;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Drivers defer backend pipeline compilation until a program is first drawn,
 * so a program compiled at boot can still hitch on its first real use (first
 * battle, first submap). Draws one triangle per program into a scratch FBO and
 * the default framebuffer under opaque and blended state, moving that
 * finalization cost into the loading phase. Port of the Android ShaderWarmup
 * to desktop GL — TMD-family programs carry a geometry shader fed by adjacency
 * records, which core GL 4.1 always supports. Only valid on the GlApi path;
 * no-ops otherwise. Must run on the GL thread after the shaders are
 * registered.
 */
public final class ShaderWarmup {
  private static final Logger LOGGER = LogManager.getFormatterLogger(ShaderWarmup.class);
  private ShaderWarmup() { }

  public static void warmup() {
    if(!(GameEngine.RENDERER.api() instanceof GlApi)) {
      return;
    }

    final long start = System.nanoTime();

    final int fbo = GL30C.glGenFramebuffers();
    final int texture = GL11C.glGenTextures();
    final int depth = GL30C.glGenRenderbuffers();
    final int vbo = GL15C.glGenBuffers();
    final int ubo = GL15C.glGenBuffers();
    final int vao = GL30C.glGenVertexArrays();

    final int[] prevFbo = new int[1];
    GL11C.glGetIntegerv(GL30C.GL_FRAMEBUFFER_BINDING, prevFbo);
    final int[] prevViewport = new int[4];
    GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, prevViewport);

    try {
      GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
      GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA, 64, 64, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, (ByteBuffer)null);
      GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, depth);
      GL30C.glRenderbufferStorage(GL30C.GL_RENDERBUFFER, GL11C.GL_DEPTH_COMPONENT, 64, 64);

      GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
      GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, texture, 0);
      GL30C.glFramebufferRenderbuffer(GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL30C.GL_RENDERBUFFER, depth);

      // Degenerate primitive; attribute 0 is position in every program. The
      // TMD-family programs carry a geometry shader fed by adjacency records
      // (6 verts per primitive) — drawing plain triangles would be rejected.
      final ByteBuffer verts = ByteBuffer.allocateDirect(18 * 4).order(ByteOrder.nativeOrder());
      verts.asFloatBuffer().put(new float[18]);
      GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, vbo);
      GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, verts, GL15C.GL_STATIC_DRAW);
      GL30C.glBindVertexArray(vao);
      GL20C.glEnableVertexAttribArray(0);
      GL20C.glVertexAttribPointer(0, 3, GL11C.GL_FLOAT, false, 0, 0);

      // Every program has UBO blocks at fixed binding points; an unbound
      // binding makes the driver log a validation error per draw.
      GL15C.glBindBuffer(GL31C.GL_UNIFORM_BUFFER, ubo);
      GL15C.glBufferData(GL31C.GL_UNIFORM_BUFFER, 64L * 1024, GL15C.GL_STATIC_DRAW);
      for(int binding = 0; binding <= 5; binding++) {
        GL30C.glBindBufferBase(GL31C.GL_UNIFORM_BUFFER, binding, ubo);
      }

      final ShaderType<?>[] types = {
        RenderEngine.SIMPLE_SHADER, RenderEngine.COPY_SHADER, RenderEngine.SCREEN_SHADER,
        RenderEngine.STANDARD_SHADER, RenderEngine.TMD_SHADER, RenderEngine.BATTLE_TMD_SHADER,
      };

      for(final ShaderType<?> type : types) {
        final Shader<?> shader = ShaderManager.getShader(type);
        if(shader == null) {
          continue;
        }
        // GL_TRIANGLES_ADJACENCY for the geometry-shader TMD programs
        final int mode = type == RenderEngine.TMD_SHADER || type == RenderEngine.BATTLE_TMD_SHADER
          ? GL32C.GL_TRIANGLES_ADJACENCY : GL11C.GL_TRIANGLES;
        final int count = mode == GL11C.GL_TRIANGLES ? 3 : 6;
        shader.use();
        for(final int target : new int[] {fbo, 0}) {
          GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, target);
          GL11C.glViewport(0, 0, 64, 64);
          GL11C.glDisable(GL11C.GL_BLEND);
          GL11C.glEnable(GL11C.GL_DEPTH_TEST);
          GL11C.glDrawArrays(mode, 0, count);
          GL11C.glEnable(GL11C.GL_BLEND);
          GL11C.glBlendFunc(GL11C.GL_SRC_ALPHA, GL11C.GL_ONE_MINUS_SRC_ALPHA);
          GL11C.glDrawArrays(mode, 0, count);
        }
      }
    } finally {
      GL20C.glDisableVertexAttribArray(0);
      GL30C.glBindVertexArray(0);
      GL30C.glDeleteVertexArrays(vao);
      GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
      GL15C.glBindBuffer(GL31C.GL_UNIFORM_BUFFER, 0);
      for(int binding = 0; binding <= 5; binding++) {
        GL30C.glBindBufferBase(GL31C.GL_UNIFORM_BUFFER, binding, 0);
      }
      GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, prevFbo[0]);
      GL11C.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
      GL11C.glDisable(GL11C.GL_DEPTH_TEST);
      GL11C.glDisable(GL11C.GL_BLEND);
      GL30C.glDeleteFramebuffers(fbo);
      GL11C.glDeleteTextures(texture);
      GL30C.glDeleteRenderbuffers(depth);
      GL15C.glDeleteBuffers(vbo);
      GL15C.glDeleteBuffers(ubo);
    }

    LOGGER.info("Pipeline warmup done in %dms", (System.nanoTime() - start) / 1_000_000);
  }
}
