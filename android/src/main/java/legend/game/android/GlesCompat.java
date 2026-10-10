package legend.game.android;

import android.util.Log;

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

    geometryShaders &= !forceEs31;

    Log.i(TAG, "GL_VERSION=" + glVersion + " geometryShaders=" + geometryShaders + " forceEs31=" + forceEs31);
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
