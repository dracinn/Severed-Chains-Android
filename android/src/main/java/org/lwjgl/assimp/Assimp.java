package org.lwjgl.assimp;

import java.nio.file.Path;

/**
 * Android replacement for the LWJGL assimp entry point. Parses the file
 * in pure Java (GLB only) — no native library.
 */
public final class Assimp {
  private Assimp() { }

  public static AIScene aiImportFile(final String file, final int flags) {
    try {
      return GlbImporter.load(Path.of(file));
    } catch(final Exception e) {
      throw new RuntimeException("Failed to import " + file, e);
    }
  }

  public static void aiReleaseImport(final AIScene scene) {
    // Java-side data; nothing to release.
  }
}
