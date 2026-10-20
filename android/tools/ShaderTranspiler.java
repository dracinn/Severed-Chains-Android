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
        final String hash = shaderHash(source, stage);

        // ES 3.20 output (geometry shader included) for ES 3.2+ contexts,
        // plus an ES 3.10 variant of non-geometry stages for devices like
        // Mali Midgard that top out at ES 3.1. Runtime picks by suffix via
        // GlesCompat.shaderSuffix().
        Files.writeString(outDir.resolve(hash + "-320.gles"), patchEs32(name, stage, decompile(spirv(source, stage), 320)));
        if(stage != 1) {
          Files.writeString(outDir.resolve(hash + "-310.gles"), patchEs31(name, stage, decompile(spirv(source, stage), 310)));
        }
        System.out.println("Transpiled " + name + " -> " + hash + "-*.gles");
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

  /**
   * Post-process the ES 3.10 decompile of the TMD-family shaders to recover the
   * geometry shader's PS1 depth behaviour without a GS stage. Upstream's GS
   * flattens each face's clip-space Z to the face average (usePs1Depth), so a
   * polygon lands at one constant depth instead of interpenetrating its
   * neighbours - exactly what PS1 ordering-table rendering did. On the GS-less
   * path we emit the provoking vertex's NDC Z as a flat varying (flat gives a
   * constant per face) and write gl_FragDepth from it when usePs1Depth is on.
   * The -320 outputs are untouched.
   */
  private static String patchEs31(final String name, final int stage, final String gles) throws IOException {
    if((name.equals("tmd.vsh") || name.equals("battle_tmd.vsh")) && stage == 0) {
      String out = patch(gles, "out float io_depthOffset;",
        "out float io_depthOffset;\nflat out highp float io_faceDepth;");
      return patch(out, "io_depth = gl_Position.z;",
        "io_depth = gl_Position.z;\n    io_faceDepth = gl_Position.z / gl_Position.w;");
    }

    if((name.equals("tmd.fsh") || name.equals("battle_tmd.fsh")) && stage == 2) {
      String out = patch(gles, "in highp float io_depthOffset;",
        "in highp float io_depthOffset;\nflat in highp float io_faceDepth;\nuniform int usePs1Depth;");
      return patch(out, "gl_FragDepth = gl_FragCoord.z;",
        "if(usePs1Depth != 0)\n        {\n            gl_FragDepth = (io_faceDepth * 0.5) + 0.5;\n        }\n        else\n        {\n            gl_FragDepth = gl_FragCoord.z;\n        }");
    }

    return gles;
  }

  /**
   * Post-process the ES 3.20 decompile of the TMD-family shaders so the PS1
   * depth flatten doesn't depend on the geometry shader's gl_Position.z write
   * being honoured by the driver. Upstream writes the face-average clip-space
   * Z while keeping each vertex's own W, so the rasterized depth still varies
   * (correct only where W == 1) and a couple of older Adreno GLES drivers
   * appear to drop the write entirely. Instead we carry the face-average NDC
   * Z through a flat varying and write gl_FragDepth from it when usePs1Depth
   * is on, matching the ES 3.1 patch. Models rendered without the flag take
   * the unchanged gl_FragCoord.z / mode-2 linearized paths.
   */
  private static String patchEs32(final String name, final int stage, final String gles) throws IOException {
    // Rewrite the VS_OUT/GS_OUT interface blocks to scalar varyings first:
    // the block-varying transport into and out of the geometry stage is
    // miscompiled on a range of mobile drivers (Adreno V@05x, Mali-G57 -
    // block members arrive garbled while plain varyings and builtins are
    // fine). Scalar varyings take a different, reliable driver path. GS_OUT
    // members get a g-prefix on both sides of the GS->FS boundary so they
    // don't collide with the same-named VS_OUT scalars inside the GS.
    String out = flattenIoBlocks(gles);

    if(name.equals("tmd.gsh") && stage == 1) {
      // emit()'s depth param would shadow the flattened depth[] input.
      out = patch(out, "void emit(int i, float depth)", "void emit(int i, float ps1depth)");
      out = patch(out, "        gl_Position.z = depth;", "        gl_Position.z = ps1depth;");
      // Plain varying, not a GS_OUT member: Adreno's linker rejects flat
      // members inside location-qualified interface blocks.
      out = patch(out, "uniform int usePs1Depth;",
        "uniform int usePs1Depth;\nflat out highp float io_faceDepth;\nhighp float _faceDepthNdc;");
      out = patch(out, "    gDepthOffset = depthOffset[i];",
        "    gDepthOffset = depthOffset[i];\n    io_faceDepth = _faceDepthNdc;");
      return patch(out, "    avgDepth /= float(vertexCount);",
        "    avgDepth /= float(vertexCount);\n    _faceDepthNdc = (gl_in[0].gl_Position.z / gl_in[0].gl_Position.w + gl_in[2].gl_Position.z / gl_in[2].gl_Position.w + gl_in[4].gl_Position.z / gl_in[4].gl_Position.w + (vertexCount == 4 ? gl_in[1].gl_Position.z / gl_in[1].gl_Position.w : 0.0)) / float(vertexCount);");
    }

    // battle_tmd has no geometry stage, so the FS reads the provoking
    // vertex's NDC depth through a plain flat varying like the 310 patch.
    if(name.equals("battle_tmd.vsh") && stage == 0) {
      out = patch(out, "void main()", "flat out highp float io_faceDepth;\n\nvoid main()");
      return patch(out, "    depth = gl_Position.z;",
        "    depth = gl_Position.z;\n    io_faceDepth = gl_Position.z / gl_Position.w;");
    }

    if((name.equals("tmd.fsh") || name.equals("battle_tmd.fsh")) && stage == 2) {
      out = patch(out, "uniform int tmdTranslucency;",
        "flat in highp float io_faceDepth;\nuniform int tmdTranslucency;\nuniform int usePs1Depth;");
      return patch(out, "gl_FragDepth = gl_FragCoord.z;",
        "if(usePs1Depth != 0)\n        {\n            gl_FragDepth = (io_faceDepth * 0.5) + 0.5;\n        }\n        else\n        {\n            gl_FragDepth = gl_FragCoord.z;\n        }");
    }

    return out;
  }

  private static final java.util.regex.Pattern IO_BLOCK =
    java.util.regex.Pattern.compile("layout\\(location = \\d+\\) (in|out) (\\w+)\\s*\\{\\n(.*?)\\}\\s*(\\w*)(\\[\\d*\\])?\\s*;", java.util.regex.Pattern.DOTALL);
  private static final java.util.regex.Pattern BLOCK_MEMBER =
    java.util.regex.Pattern.compile("\\s*(flat\\s+)?(\\w+(?:\\s+\\w+)*)\\s+(\\w+);");

  /**
   * Converts stage-I/O interface blocks into scalar varyings matched by
   * name (the blocks' location-qualified member transport is what breaks
   * on buggy drivers). Members keep their own names, except GS_OUT members
   * which gain a g-prefix on both sides of the boundary so the GS's inputs
   * and outputs don't share identifiers.
   */
  private static String flattenIoBlocks(final String gles) throws IOException {
    final java.util.regex.Matcher blockMatcher = IO_BLOCK.matcher(gles);
    final StringBuilder replaced = new StringBuilder(gles.length());
    final java.util.List<String[]> accesses = new java.util.ArrayList<>(); // instance, member -> rewritten name, array flag

    while(blockMatcher.find()) {
      final String dir = blockMatcher.group(1);
      final String prefix = blockMatcher.group(2).equals("GS_OUT") ? "g" : "";
      final String array = blockMatcher.group(5) == null ? "" : blockMatcher.group(5);
      final String instance = blockMatcher.group(4);
      if(instance.isEmpty()) {
        throw new IOException("ES 3.2 shader patch missed: anonymous I/O block");
      }

      final StringBuilder decls = new StringBuilder();
      for(final String line : blockMatcher.group(3).split("\\n")) {
        if(line.isBlank()) {
          continue;
        }
        final java.util.regex.Matcher member = BLOCK_MEMBER.matcher(line);
        if(!member.matches()) {
          throw new IOException("ES 3.2 shader patch missed: I/O block member '" + line + "'");
        }
        final String memberName = member.group(3);
        final String scalarName = prefix.isEmpty() ? memberName : prefix + Character.toUpperCase(memberName.charAt(0)) + memberName.substring(1);
        decls.append(member.group(1) == null ? "" : member.group(1))
          .append(dir).append(' ').append(member.group(2)).append(' ')
          .append(scalarName).append(array).append(";\n");
        accesses.add(new String[] {instance, memberName, scalarName, array.isEmpty() ? "" : "array"});
      }
      blockMatcher.appendReplacement(replaced, java.util.regex.Matcher.quoteReplacement(decls.toString()));
    }
    blockMatcher.appendTail(replaced);
    if(accesses.isEmpty()) {
      return gles;
    }

    String out = replaced.toString();
    for(final String[] access : accesses) {
      final String inst = java.util.regex.Pattern.quote(access[0]);
      if(access[3].isEmpty()) {
        out = out.replaceAll("\\b" + inst + "\\." + access[1] + "\\b", access[2]);
      } else {
        out = out.replaceAll("\\b" + inst + "\\[([^\\]]+)\\]\\." + access[1] + "\\b",
          java.util.regex.Matcher.quoteReplacement(access[2]) + "[$1]");
      }
    }
    return out;
  }

  private static String patch(final String text, final String needle, final String replacement) throws IOException {
    if(!text.contains(needle)) {
      throw new IOException("ES 3.1 shader patch missed: '" + needle + "'");
    }
    return text.replace(needle, replacement);
  }

  private static ByteBuffer spirv(final String source, final int stageOrdinal) throws IOException {
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

    return spirv;
  }

  private static String decompile(final ByteBuffer spirvBuffer, final int version) {
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
      spvc_compiler_options_set_uint(options, SPVC_COMPILER_OPTION_GLSL_VERSION, version);

      // Named shader I/O blocks are ES 3.2 core / GL_EXT_shader_io_blocks on
      // ES 3.1 - flatten them so the 310 variant links without the extension.
      // The 320 target stays unflattened here because this SPIRV-Cross build
      // crashes compiling flattened geometry-stage IO; patchEs32 rewrites
      // those blocks to scalar varyings textually instead.
      final boolean flattenIo = version < 320;
      spvc_compiler_options_set_bool(options, SPVC_COMPILER_OPTION_GLSL_FORCE_FLATTENED_IO_BLOCKS, flattenIo);
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

      if(flattenIo) {
        // Flattened members are emitted as <blockVar>_<member>, so give every
        // block variable the same name across stages or the linked stages
        // don't match (vs_out_* in the VS, _<id>_* in the FS). Plain
        // (non-block) varyings keep their names and link untouched.
        for(final int resourceType : new int[] {SPVC_RESOURCE_TYPE_STAGE_INPUT, SPVC_RESOURCE_TYPE_STAGE_OUTPUT}) {
          spvc_resources_get_resource_list_for_type(resources, resourceType, resourceListBuffer, resourceCountBuffer);
          renameBlockVars(compiler, resourceListBuffer.get(0), resourceCountBuffer.get(0));
        }
      }

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

  /** Renames struct-typed (interface block) stage I/O variables to a common name. */
  private static void renameBlockVars(final long compiler, final long list, final long count) {
    for(int i = 0; i < count; i++) {
      final SpvcReflectedResource resource = SpvcReflectedResource.create(list + i * SpvcReflectedResource.SIZEOF);
      final long type = spvc_compiler_get_type_handle(compiler, resource.base_type_id());

      if(spvc_type_get_basetype(type) == SPVC_BASETYPE_STRUCT) {
        spvc_compiler_set_name(compiler, resource.id(), "io");
      }
    }
  }
}
