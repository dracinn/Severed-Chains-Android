package legend.game.android;

import java.io.File;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Targets for JarPatcher's bytecode rewrites inside third-party jars
 * (mod-loader) that call JDK APIs missing or behaving differently on Android.
 */
public final class JdkReflect {
  private JdkReflect() { }

  private static URL fallbackLocation;

  /**
   * {@code Class.getProtectionDomain()} can return a domain whose CodeSource
   * is null on Android (no policy domains for app code). Passthrough - the
   * downstream links ({@link #codeSource}, {@link #codeSourceLocation}) carry
   * the null-safety.
   */
  public static ProtectionDomain protectionDomain(final Class<?> clazz) {
    return clazz.getProtectionDomain();
  }

  /** {@code ProtectionDomain.getCodeSource()} - falls back to a source at the app's working dir. */
  public static CodeSource codeSource(final ProtectionDomain domain) {
    if(domain != null) {
      final CodeSource source = domain.getCodeSource();
      if(source != null) {
        return source;
      }
    }

    return new CodeSource(fallbackLocation(), (java.security.CodeSigner[])null);
  }

  /** {@code CodeSource.getLocation()} - falls back to the app's working dir URL. */
  public static URL codeSourceLocation(final CodeSource source) {
    if(source != null && source.getLocation() != null) {
      return source.getLocation();
    }

    return fallbackLocation();
  }

  /**
   * Upstream code sources are the game jar; on Android the classes live in
   * dex inside base.apk. Returning the APK path preserves upstream semantics:
   * IoHelper.findJarResources opens it as a JarFile and finds the mod
   * resources (lod_core/lang/...) that sit at the APK root.
   */
  private static synchronized URL fallbackLocation() {
    if(fallbackLocation == null) {
      try {
        fallbackLocation = new File(AndroidEnv.context().getPackageCodePath()).toURI().toURL();
      } catch(final java.net.MalformedURLException e) {
        throw new RuntimeException(e);
      }
    }

    return fallbackLocation;
  }

  /**
   * {@code AccessibleObject.canAccess(obj)} (Java 9+) is absent on this ART.
   * Mirrors the JDK contract: true if access checks were suppressed
   * (setAccessible) or the member is public on a public type and obj is a
   * valid receiver for instance members.
   */
  public static boolean canAccess(final AccessibleObject member, final Object obj) {
    if(member.isAccessible()) {
      return true;
    }

    if(!(member instanceof final Member m)) {
      return true;
    }

    final Class<?> declaring = m.getDeclaringClass();
    if(!Modifier.isStatic(m.getModifiers()) && (obj == null || !declaring.isInstance(obj))) {
      return false;
    }

    return Modifier.isPublic(m.getModifiers()) && Modifier.isPublic(declaring.getModifiers());
  }

  /**
   * {@code LogManager.getLogger()} infers the caller class via StackWalker,
   * which ART lacks. The rewritten call site jumps straight here, so frame 2
   * of this thread's stack trace is the caller (0 = getStackTrace, 1 = this
   * method).
   */
  public static Logger getLogger() {
    return LogManager.getLogger(callerClass());
  }

  /** See {@link #getLogger()}. */
  public static Logger getFormatterLogger() {
    return LogManager.getFormatterLogger(callerClass());
  }

  private static Class<?> callerClass() {
    for(final StackTraceElement frame : Thread.currentThread().getStackTrace()) {
      if(frame.getClassName().startsWith("java.lang.Thread") || frame.getClassName().equals(JdkReflect.class.getName())) {
        continue;
      }

      try {
        return Class.forName(frame.getClassName(), false, JdkReflect.class.getClassLoader());
      } catch(final ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }

    throw new IllegalStateException("No caller on stack");
  }
}
