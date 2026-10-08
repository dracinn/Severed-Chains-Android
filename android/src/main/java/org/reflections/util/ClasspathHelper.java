package org.reflections.util;

import java.net.URL;
import java.util.Collection;
import java.util.List;

/**
 * Android replacement for org.reflections' ClasspathHelper. Dex classloaders
 * expose no scannable package URLs; the returned URLs are ignored anyway
 * (org.reflections.Reflections serves a generated index), so this is empty.
 */
public final class ClasspathHelper {
  private ClasspathHelper() { }

  public static Collection<URL> forPackage(final String name, final ClassLoader... classLoaders) {
    return List.of();
  }
}
