package org.reflections;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Android replacement for org.reflections.Reflections. Runtime bytecode
 * scanning cannot work over Android dex, so the generateAnnotationIndex Gradle
 * task scans the staged upstream sources for modloader annotations
 * (org.legendofdragoon.modloader.*, currently {@code @Mod} and
 * {@code @EventListener}) and writes META-INF/sc/annotation-index.txt into
 * shared-classes.jar. This class serves getTypesAnnotatedWith from that index,
 * keeping org.legendofdragoon:mod-loader's ModManager/EventManager untouched.
 */
public class Reflections {
  private static final Logger LOGGER = LogManager.getFormatterLogger(Reflections.class);
  private static final String INDEX_RESOURCE = "META-INF/sc/annotation-index.txt";

  public Reflections(final Configuration configuration) {
  }

  public Set<Class<?>> getTypesAnnotatedWith(final Class<? extends Annotation> annotation) {
    final Set<Class<?>> types = new HashSet<>();
    final ClassLoader loader = Reflections.class.getClassLoader();

    try(final InputStream in = loader.getResourceAsStream(INDEX_RESOURCE)) {
      if(in == null) {
        LOGGER.warn("Annotation index resource %s not found; no annotated types returned", INDEX_RESOURCE);
        return types;
      }

      for(final String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
        final int sep = line.indexOf(':');
        if(sep > 0 && line.regionMatches(0, annotation.getName(), 0, sep)) {
          try {
            types.add(Class.forName(line.substring(sep + 1).trim(), false, loader));
          } catch(final ClassNotFoundException e) {
            LOGGER.warn("Indexed class %s not found", line.substring(sep + 1));
          }
        }
      }
    } catch(final IOException e) {
      LOGGER.warn("Failed to read annotation index", e);
    }

    return types;
  }
}
