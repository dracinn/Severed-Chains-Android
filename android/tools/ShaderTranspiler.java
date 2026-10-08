import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.util.spvc.SpvcReflectedResource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.stream.Stream;

import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.util.spvc.Spv.*;
import static org.lwjgl.util.spvc.Spvc.*;

/**
 * Host-side shader transpiler for the Android build. Replicates the exact
 * pipeline upstream ShaderManager performs at runtime on desktop:
 *   GLSL 450 -> shaderc -> SPIR-V -> SPIRV-Cross -> GLSL ES 3.20
 * with Location decorations stripped from plain uniforms and Binding
 * stripped from uniform blocks (upstream does this because auto-assigned
 * indices collide across stages).
 *
 * Usage: ShaderTranspiler <inDir> <outDir>
 * For each <name>.vsh/.gsh/.fsh in inDir, writes outDir/<sha256>.gles where the
 * hash matches legend.core.renderer.ShaderManager.shaderHash on Android.
 */
public final class ShaderTranspiler {
  public static void main(final String[] args) throws Exception {
    final Path inDir = Paths.get(args[0]);
    final Path outDir = Paths.get(args[1]);
    Files.createDirectories(outDir);

    try(final Stream<Path> files = Files.list(inDir)) {
      for(final Path file : files.sorted().toList()) {
        final String name = file.getFileName().toString();
        final int stage = switch(name.substring(name.lastIndexOf('.') + 1)) {
          case "vsh" -> 0; // ShaderStage.VERTEX ordinal
          case "gsh" -> 1; // ShaderStage.GEOMETRY ordinal
          case "fsh" -> 2; // ShaderStage.FRAGMENT ordinal
          default -> -1;
        };
        if(stage < 0) {
          continue;
        }

        final String source = Files.readString(file);
        final String gles = transpile(source, stage);
        final String outName = shaderHash(source, stage) + ".gles";
        Files.writeString(outDir.resolve(outName), gles);
        System.out.println("Transpiled " + name + " -> " + outName);
      }
    }
  }

  private static String shaderHash(final String source, final int stageOrdinal) throws Exception {
    final MessageDigest digest = MessageDigest.getInstance("SHA-256");
    digest.update(source.getBytes(StandardCharsets.UTF_8));
    digest.update((byte)stageOrdinal);
    final byte[] hash = digest.digest();
    final StringBuilder out = new StringBuilder(64);
    for(final byte b : hash) {
      out.append(Character.forDigit(b >> 4 & 0xf, 16));
      out.append(Character.forDigit(b & 0xf, 16));
    }
    return out.toString();
  }

  private static String transpile(final String source, final int stageOrdinal) throws IOException {
    final int shadercStage = switch(stageOrdinal) {
      case 0 -> shaderc_vertex_shader;
      case 1 -> shaderc_geometry_shader;
      case 2 -> shaderc_fragment_shader;
      default -> throw new IllegalArgumentException("bad stage");
    };

    final long compiler = shaderc_compiler_initialize();
    final long options = shaderc_compile_options_initialize();

    shaderc_compile_options_set_target_env(options, shaderc_target_env_opengl, shaderc_env_version_opengl_4_5);
    shaderc_compile_options_set_auto_map_locations(options, true);
    shaderc_compile_options_set_auto_bind_uniforms(options, true);

    final long result = shaderc_compile_into_spv(compiler, source, shadercStage, "shader.glsl", "main", options);
    if(shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
      throw new IOException("Shaderc compilation failed: " + shaderc_result_get_error_message(result));
    }

    final ByteBuffer nativeBuf = shaderc_result_get_bytes(result);
    final ByteBuffer spirv = ByteBuffer.allocateDirect(nativeBuf.remaining());
    spirv.put(nativeBuf);
    spirv.flip();

    shaderc_result_release(result);
    shaderc_compile_options_release(options);
    shaderc_compiler_release(compiler);

    return decompile(spirv);
  }

  private static String decompile(final ByteBuffer spirvBuffer) {
    try(final MemoryStack stack = MemoryStack.stackPush()) {
      final IntBuffer spirvInts = spirvBuffer.asIntBuffer();

      final PointerBuffer contextBuffer = stack.mallocPointer(1);
      final PointerBuffer parsedIrBuffer = stack.mallocPointer(1);
      final PointerBuffer compilerBuffer = stack.mallocPointer(1);

      spvc_context_create(contextBuffer);
      final long context = contextBuffer.get(0);

      spvc_context_parse_spirv(context, spirvInts, spirvInts.remaining(), parsedIrBuffer);
      final long parsedIr = parsedIrBuffer.get(0);

      spvc_context_create_compiler(context, SPVC_BACKEND_GLSL, parsedIr, SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, compilerBuffer);
      final long compiler = compilerBuffer.get(0);

      final PointerBuffer optionsBuffer = stack.mallocPointer(1);
      spvc_compiler_create_compiler_options(compiler, optionsBuffer);
      final long options = optionsBuffer.get(0);

      spvc_compiler_options_set_bool(options, SPVC_COMPILER_OPTION_GLSL_ES, true);
      spvc_compiler_options_set_uint(options, SPVC_COMPILER_OPTION_GLSL_VERSION, 320);
      spvc_compiler_install_compiler_options(compiler, options);

      final PointerBuffer resourcesBuffer = stack.mallocPointer(1);
      Spvc.spvc_compiler_create_shader_resources(compiler, resourcesBuffer);
      final long resources = resourcesBuffer.get(0);

      final PointerBuffer resourceListBuffer = stack.mallocPointer(1);
      final PointerBuffer resourceCountBuffer = stack.mallocPointer(1);

      spvc_resources_get_resource_list_for_type(resources, SPVC_RESOURCE_TYPE_GL_PLAIN_UNIFORM, resourceListBuffer, resourceCountBuffer);
      removeDecorations(compiler, resourceListBuffer.get(0), resourceCountBuffer.get(0), SpvDecorationLocation);

      spvc_resources_get_resource_list_for_type(resources, SPVC_RESOURCE_TYPE_UNIFORM_BUFFER, resourceListBuffer, resourceCountBuffer);
      removeDecorations(compiler, resourceListBuffer.get(0), resourceCountBuffer.get(0), SpvDecorationBinding);

      final PointerBuffer sourceBuffer = stack.mallocPointer(1);
      spvc_compiler_compile(compiler, sourceBuffer);

      final String gles = sourceBuffer.getStringUTF8(0);
      spvc_context_destroy(context);
      return gles;
    }
  }

  private static void removeDecorations(final long compiler, final long list, final long count, final int decoration) {
    for(int i = 0; i < count; i++) {
      final SpvcReflectedResource resource = SpvcReflectedResource.create(list + i * SpvcReflectedResource.SIZEOF);
      spvc_compiler_unset_decoration(compiler, resource.id(), decoration);
    }
  }
}
