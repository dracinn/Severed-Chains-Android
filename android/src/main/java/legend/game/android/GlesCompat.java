package legend.game.android;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import legend.core.renderer.VertexOrder;

/**
 * Compatibility shims for GPUs without full OpenGL ES 3.2 support
 * (notably Mali Midgard, which tops out at ES 3.1).
 *
 * Upstream's TMD pipeline uses a geometry shader over GL_TRIANGLES_ADJACENCY
 * for near-plane face culling and flat per-face PS1 depth. On ES 3.1 the
 * geometry stage is dropped entirely (see the GlesApi transform in
 * android/build.gradle) and adjacency index buffers are stripped down to
 * plain triangles here. Near-plane culling is replicated by a NaN vertex
 * trick injected into tmd.vsh; flat per-face depth is not reproducible
 * without a GS and degrades to per-vertex depth.
 */
public final class GlesCompat {
  private GlesCompat() { }

  private static final String TAG = "GlesCompat";

  /** Geometry shader / adjacency topology support (ES 3.2+). */
  public static boolean geometryShaders = true;

  /** Test hook set from the force_es31 launch extra by MainActivity. */
  public static boolean forceEs31;

  /**
   * Persisted self-healing level, escalated when a run dies between
   * markGlInit() and glInitOk(). A native driver crash (SIGSEGV inside
   * libGLESv2_*) never reaches Java's uncaught handler, so a marker file is
   * the only signal it happened. Levels: 0 = normal, 1 = force ES 3.1 path.
   */
  public static int fallbackLevel;
  private static final int MAX_LEVEL = 1;
  private static File levelFile;
  private static File markerFile;

  /** Call once from MainActivity.onCreate before the game starts. */
  public static void init(final Context context) {
    levelFile = new File(context.getFilesDir(), "gl-fallback-level");
    markerFile = new File(context.getFilesDir(), "gl-init-in-progress");

    fallbackLevel = Math.max(0, readLevel(levelFile));
    final int crashed = readLevel(markerFile);
    //noinspection ResultOfMethodCallIgnored
    markerFile.delete();
    if(crashed >= fallbackLevel) {
      fallbackLevel = Math.min(crashed + 1, MAX_LEVEL);
      writeLevel(levelFile, fallbackLevel);
      Log.w(TAG, "Previous run died during GL init - escalating to fallback level " + fallbackLevel);
    }
  }

  /** Written before any EGL/GLES work; a surviving marker at next init means a crash. */
  public static void markGlInit() {
    if(markerFile != null) {
      writeLevel(markerFile, fallbackLevel);
    }
  }

  /** Called after the first successful frame - GL init survived, marker cleared. */
  public static void glInitOk() {
    if(markerFile != null) {
      //noinspection ResultOfMethodCallIgnored
      markerFile.delete();
    }
  }

  /** Whether the ES 3.1 pipeline is in effect for any reason. */
  public static boolean es31Forced() {
    return forceEs31 || fallbackLevel > 0;
  }

  private static int readLevel(final File f) {
    if(f == null || !f.isFile()) {
      return -1;
    }

    try(final FileInputStream in = new FileInputStream(f)) {
      final byte[] buf = new byte[(int)f.length()];
      final int read = in.read(buf);
      return read > 0 ? Integer.parseInt(new String(buf, 0, read, StandardCharsets.UTF_8).trim()) : -1;
    } catch(final IOException | NumberFormatException e) {
      return -1;
    }
  }

  private static void writeLevel(final File f, final int level) {
    try(final FileOutputStream out = new FileOutputStream(f)) {
      out.write(String.valueOf(level).getBytes(StandardCharsets.UTF_8));
    } catch(final IOException e) {
      Log.w(TAG, "Failed to write " + f.getName(), e);
    }
  }

  /**
   * Detect ES feature level from the GL_VERSION string, e.g.
   * "OpenGL ES 3.2 v1.r28p0". Called once the context is current.
   */
  public static void detect(final String glVersion) {
    // GL_VERSION may be null in broken contexts; fail safe (keep GS on -
    // shader compile errors then surface the real problem upstream-style).
    if(glVersion != null) {
      final java.util.regex.Matcher matcher = java.util.regex.Pattern
        .compile("OpenGL ES (\\d+)\\.(\\d+)")
        .matcher(glVersion);
      if(matcher.find()) {
        final int major = Integer.parseInt(matcher.group(1));
        final int minor = Integer.parseInt(matcher.group(2));
        geometryShaders = major > 3 || major == 3 && minor >= 2;
      }
    }

    geometryShaders &= !es31Forced();

    Log.i(TAG, "GL_VERSION=" + glVersion + " geometryShaders=" + geometryShaders + " es31Forced=" + es31Forced());
  }

  /** Shader asset variant: ES 3.2 devices get 320, anything less gets 310. */
  public static String shaderSuffix() {
    return geometryShaders ? "-320" : "-310";
  }

  /** GL_TRIANGLES_ADJACENCY doesn't exist below ES 3.2. */
  public static VertexOrder adaptOrder(final VertexOrder order) {
    return geometryShaders || order != VertexOrder.TRIANGLES_ADJACENCY ? order : VertexOrder.TRIANGLES;
  }

  /**
   * Upstream adjacency layout (TmdObjLoader): six indices per record with the
   * real triangle at positions 0, 2, 4 and the adjacent (quad fourth) vertex
   * interleaved. Strip to plain triangles by keeping 0, 2, 4 of each record.
   */
  public static int[] adaptIndices(final VertexOrder order, final int[] indices) {
    if(geometryShaders || order != VertexOrder.TRIANGLES_ADJACENCY) {
      return indices;
    }

    final int[] stripped = new int[indices.length / 2];
    for(int i = 0; i < stripped.length; i++) {
      stripped[i] = indices[i / 3 * 6 + i % 3 * 2];
    }
    return stripped;
  }
}
