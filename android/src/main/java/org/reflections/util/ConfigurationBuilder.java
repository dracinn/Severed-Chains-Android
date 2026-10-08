package org.reflections.util;

import org.reflections.Configuration;

import java.net.URL;
import java.util.Collection;

/**
 * Android replacement for org.reflections' ConfigurationBuilder. Collectors
 * are no-ops: org.reflections.Reflections serves a build-time generated
 * annotation index instead of scanning the URLs/classloaders gathered here.
 */
public class ConfigurationBuilder implements Configuration {
  public ConfigurationBuilder() {
  }

  public ConfigurationBuilder addUrls(final URL... urls) {
    return this;
  }

  public ConfigurationBuilder addUrls(final Collection<URL> urls) {
    return this;
  }

  public ConfigurationBuilder addClassLoaders(final ClassLoader... classLoaders) {
    return this;
  }
}
