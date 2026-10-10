package legend.core.renderer;

import legend.core.memory.types.IntRef;
import legend.game.android.AndroidEnv;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * Android variant: shaders are transpiled (GLSL450 -> SPIR-V -> GLSL ES 3.20)
 * at build time by the transpileShaders Gradle task using LWJGL shaderc/SPIRV-Cross
 * on the host JVM, and shipped as assets keyed by the hash of the desktop source.
 * This variant looks up the pre-transpiled source instead of running natives.
 *
 * If upstream shader sources change, the assets are regenerated automatically
 * on the next build as long as transpileShaders stays wired into preBuild.
 */
public final class ShaderManager {
  private ShaderManager() { }

  private static final Logger LOGGER = LogManager.getFormatterLogger(ShaderManager.class);

  private static final Map<ShaderType, Shader> shaders = new HashMap<>();
  private static final Map<String, ShaderUniformBuffer> uniformBuffers = new HashMap<>();

  /**
   * Must produce the same hash as ShaderTranspiler.shaderAssetName on the host.
   */
  static String shaderHash(final String source, final ShaderStage stage) {
    try {
      final MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(source.getBytes(StandardCharsets.UTF_8));
      digest.update((byte)stage.ordinal());
      final byte[] hash = digest.digest();
      final StringBuilder out = new StringBuilder(64);
      for(final byte b : hash) {
        out.append(Character.forDigit(b >> 4 & 0xf, 16));
        out.append(Character.forDigit(b & 0xf, 16));
      }
      return out.toString();
    } catch(final NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  public static String transpileShader(final String source, final ShaderStage shaderStage, final IntRef uniformIndex) {
    // Shaders ship as ES 3.10 and ES 3.20 variants; pick by context level
    // (GlesCompat). Geometry shaders only exist in the 320 variant and are
    // only compiled when the context supports them.
    final String name = "shaders/" + shaderHash(source, shaderStage) + legend.game.android.GlesCompat.shaderSuffix() + ".gles";
    LOGGER.info("Loading pre-transpiled shader asset %s", name);

    final String gles = AndroidEnv.readAsset(name);
    if(gles == null) {
      throw new RuntimeException("Shader asset missing: " + name + " - shaders changed upstream; rebuild with the transpileShaders task");
    }

    return gles;
  }

  public static <Options extends ShaderOptions> Shader<Options> getShader(final ShaderType<Options> type) {
    return shaders.get(type);
  }

  public static <Options extends ShaderOptions> Shader<Options> addShader(final ShaderType<Options> type) {
    final Shader<Options> shader = type.shaderConstructor.apply(type.optionsConstructor);
    shaders.put(type, shader);
    return shader;
  }

  public static ShaderUniformBuffer getUniformBuffer(final String name) {
    return uniformBuffers.get(name);
  }

  public static ShaderUniformBuffer addUniformBuffer(final String name, final ShaderUniformBuffer uniformBuffer) {
    uniformBuffers.put(name, uniformBuffer);
    return uniformBuffer;
  }

  public static void reload() throws IOException {
    for(final Shader<?> shader : shaders.values()) {
      shader.reload();
    }
  }

  public static void delete() {
    for(final Shader<?> shader : shaders.values()) {
      shader.delete();
    }

    for(final ShaderUniformBuffer buffer : uniformBuffers.values()) {
      buffer.delete();
    }

    shaders.clear();
    uniformBuffers.clear();
  }
}
