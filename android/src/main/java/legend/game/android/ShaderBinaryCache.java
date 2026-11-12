package legend.game.android;

import android.util.Log;

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

import org.lwjgl.opengles.GLES20;
import org.lwjgl.opengles.GLES30;

/**
 * Persists linked GL programs to files/shader-cache so later boots can skip
 * compile+link via glProgramBinary (GL_OES_get_program_binary). Keyed on the
 * transpiled stage sources plus the driver identity, so a driver update or
 * shader change invalidates entries automatically. Drivers may still refuse
 * a binary (LINK_STATUS false) — callers fall back to normal compilation.
 * Must only be called on the GL thread.
 */
public final class ShaderBinaryCache {
  private static final String TAG = "ShaderBinCache";

  private static Boolean supported;
  private static String driverTag;
  private static File dir;

  private ShaderBinaryCache() { }

  private static boolean supported() {
    if(supported == null) {
      final String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
      supported = extensions != null && extensions.contains("GL_OES_get_program_binary")
        && GLES20.glGetInteger(GLES30.GL_NUM_PROGRAM_BINARY_FORMATS) > 0;
      Log.i(TAG, "GL_OES_get_program_binary supported=" + supported);
    }
    return supported;
  }

  private static String key(final String name, final String material) {
    if(driverTag == null) {
      driverTag = GLES20.glGetString(GLES20.GL_RENDERER) + "|" + GLES20.glGetString(GLES20.GL_VERSION) + "|" + GlesCompat.shaderSuffix();
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

    final int program = GLES20.glCreateProgram();
    GLES30.glProgramBinary(program, format, binary, length);
    if(GLES20.glGetProgrami(program, GLES20.GL_LINK_STATUS) == 0) {
      Log.w(TAG, "Binary rejected for " + name + ": " + GLES20.glGetProgramInfoLog(program));
      GLES20.glDeleteProgram(program);
      return 0;
    }

    Log.i(TAG, "Loaded cached program for " + name);
    return program;
  }

  /** Extracts and stores the program's binary; no-op if unsupported. */
  public static void store(final String name, final String material, final int program) {
    if(!supported()) {
      return;
    }

    final int length = GLES20.glGetProgrami(program, GLES30.GL_PROGRAM_BINARY_LENGTH);
    if(length <= 0) {
      return;
    }

    final ByteBuffer binary = ByteBuffer.allocateDirect(length).order(ByteOrder.nativeOrder());
    final int format = GLES30.glGetProgramBinary(program, binary);

    final byte[] bytes = new byte[8 + length];
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(format).putInt(length);
    binary.get(bytes, 8, length);

    final File file = new File(cacheDir(), key(name, material));
    try(final FileOutputStream out = new FileOutputStream(file)) {
      out.write(bytes);
    } catch(final IOException e) {
      Log.w(TAG, "Failed to write " + file, e);
    }
  }

  private static File cacheDir() {
    if(dir == null) {
      dir = new File(AndroidEnv.context().getFilesDir(), "shader-cache");
      dir.mkdirs();
    }
    return dir;
  }
}
