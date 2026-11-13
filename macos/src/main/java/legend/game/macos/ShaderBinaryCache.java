package legend.game.macos;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL41C;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Persists linked GL programs to <baseDir>/shader-cache so later boots can
 * skip compile+link via glProgramBinary. Port of the Android ShaderBinaryCache
 * to desktop GL: program binaries are core in GL 4.1, so the only gate is a
 * nonzero GL_NUM_PROGRAM_BINARY_FORMATS. Keyed on the stage sources plus the
 * driver identity, so a driver update or shader change invalidates entries
 * automatically. Drivers may still refuse a binary (LINK_STATUS false) —
 * callers fall back to normal compilation. Must only be called on the GL
 * thread.
 */
public final class ShaderBinaryCache {
  private static final Logger LOGGER = LogManager.getFormatterLogger(ShaderBinaryCache.class);
  private static Boolean supported;
  private static String driverTag;
  private static File dir;

  private ShaderBinaryCache() { }

  private static boolean supported() {
    if(supported == null) {
      supported = GL41C.glGetInteger(GL41C.GL_NUM_PROGRAM_BINARY_FORMATS) > 0;
      LOGGER.info("glProgramBinary supported=%s", supported);
    }
    return supported;
  }

  private static String key(final String name, final String material) {
    if(driverTag == null) {
      driverTag = GL20C.glGetString(GL20C.GL_RENDERER) + "|" + GL20C.glGetString(GL20C.GL_VERSION);
    }
    try {
      final MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(material.getBytes(StandardCharsets.UTF_8));
      digest.update(driverTag.getBytes(StandardCharsets.UTF_8));
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

  /** Returns a ready-to-use program id, or 0 on miss/failure. */
  public static int tryLoad(final String name, final String material) {
    if(!supported()) {
      return 0;
    }

    final File file = new File(cacheDir(), key(name, material));
    final byte[] bytes;
    try(final FileInputStream in = new FileInputStream(file)) {
      bytes = new byte[(int)file.length()];
      int read = 0;
      while(read < bytes.length) {
        final int n = in.read(bytes, read, bytes.length - read);
        if(n < 0) {
          break;
        }
        read += n;
      }
    } catch(final IOException e) {
      return 0;
    }

    if(bytes.length < 8) {
      return 0;
    }

    final ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    final int format = header.getInt();
    final int length = header.getInt();
    if(length != bytes.length - 8) {
      return 0;
    }

    final ByteBuffer binary = ByteBuffer.allocateDirect(length).order(ByteOrder.nativeOrder());
    binary.put(bytes, 8, length);
    ((Buffer)binary).flip();

    final int program = GL20C.glCreateProgram();
    GL41C.glProgramBinary(program, format, binary);
    if(GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == 0) {
      LOGGER.warn("Binary rejected for %s: %s", name, GL20C.glGetProgramInfoLog(program));
      GL20C.glDeleteProgram(program);
      return 0;
    }

    return program;
  }

  /** Extracts and stores the program's binary; no-op if unsupported. */
  public static void store(final String name, final String material, final int program) {
    if(!supported()) {
      return;
    }

    final int length = GL20C.glGetProgrami(program, GL41C.GL_PROGRAM_BINARY_LENGTH);
    if(length <= 0) {
      return;
    }

    final ByteBuffer binary = ByteBuffer.allocateDirect(length).order(ByteOrder.nativeOrder());
    final int[] format = new int[1];
    GL41C.glGetProgramBinary(program, null, format, binary);

    final byte[] bytes = new byte[8 + length];
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(format[0]).putInt(length);
    binary.get(bytes, 8, length);

    final File file = new File(cacheDir(), key(name, material));
    try(final FileOutputStream out = new FileOutputStream(file)) {
      out.write(bytes);
    } catch(final IOException e) {
      LOGGER.warn("Failed to write %s", file);
    }
  }

  private static File cacheDir() {
    if(dir == null) {
      dir = new File(System.getProperty("legend.baseDir", "."), "shader-cache");
      dir.mkdirs();
    }
    return dir;
  }
}
