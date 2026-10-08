package org.reflections;

/**
 * Android replacement for org.reflections' Configuration contract. The real
 * library scans classpath URLs for .class files, which cannot work on Android
 * dex bytecode; this marker exists so the same-FQN {@link Reflections}
 * replacement (which serves a build-time generated index instead) satisfies
 * the mod-loader's signatures without changing it.
 */
public interface Configuration {
}
