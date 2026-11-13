package legend.game.macos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import legend.game.Main;

/**
 * Entry point for the packaged macOS app bundle. Redirects writable game
 * data into {@code ~/Documents/Severed Chains} via symlinks in the app dir
 * and, when the bundled ANGLE libraries are present, enables the ANGLE
 * (Metal) GLES context in {@link AngleContext} via {@code lod.angle.lib}.
 */
public final class BundleMain {
  private BundleMain() { }

  public static void main(final String[] args) throws Throwable {
    final Path appDir = Path.of(BundleMain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
    // -Duser.dir only sets the property: NIO resolves relative paths through
    // it, but java.io (RandomAccessFile, File streams, log4j appenders) opens
    // raw relative paths against the real cwd — "/" under `open`. chdir so
    // both agree on the app dir.
    chdir(appDir);
    final Path dataDir = Path.of(System.getProperty("user.home"), "Documents", "Severed Chains");
    Files.createDirectories(dataDir);

    for(final String name : new String[] {"saves", "isos", "mods", "files", "config"}) {
      Files.createDirectories(dataDir.resolve(name));
      linkDataDir(appDir, dataDir, name);
    }
    for(final String name : new String[] {"config.dcnf", "config.conf", "debug.log", "unpacker.log"}) {
      linkDataDir(appDir, dataDir, name);
    }

    // Built-in mod assets ride inside the bundle at builtin-mods/; merge them
    // into the user mods dir each launch (re-copy picks up updates), same as
    // the Android asset extraction.
    final Path builtinMods = appDir.resolve("builtin-mods");
    if(Files.isDirectory(builtinMods)) {
      try(final java.util.stream.Stream<Path> files = Files.walk(builtinMods)) {
        for(final Path src : (Iterable<Path>)files::iterator) {
          if(Files.isRegularFile(src)) {
            final Path dst = dataDir.resolve("mods").resolve(builtinMods.relativize(src).toString());
            Files.createDirectories(dst.getParent());
            Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
          }
        }
      }
    }

    // Shared logs + crash reporting — mirrors the Android GameLog/CrashReporter.
    // log4j2.configurationFile must be set before the first LogManager call
    // (attachVerboseAppender), or the context boots on the default console
    // config and the XML — with its file appender — never loads.
    System.setProperty("log4j2.configurationFile", appDir.resolve("log4j2.xml").toAbsolutePath().toString());
    GameLog.init(dataDir);
    GameLog.installCrashHandler();
    GameLog.attachVerboseAppender();
    CrashReporter.init();

    final Path egl = appDir.resolve("angle").resolve("libEGL.dylib");
    if(!Boolean.getBoolean("lod.angle.disable") && Files.exists(egl)) {
      System.setProperty("lod.angle.lib", egl.toAbsolutePath().toString());
      // org.lwjgl.opengles.libname is set as a JVM option in the packaged
      // app so LWJGL binds ANGLE instead of the system GL.
    }
    // ANGLE statically links the Khronos Vulkan loader; point it at the
    // bundled MoltenVK ICD so the Vulkan (ES 3.2-capable) backend works.
    final Path vkIcd = appDir.resolve("angle").resolve("icd").resolve("MoltenVK_icd.json");
    if(Files.exists(vkIcd)) {
      final String icd = vkIcd.toAbsolutePath().toString();
      setEnv("VK_ICD_FILENAMES", icd);
      setEnv("VK_DRIVER_FILES", icd);
    }

    Main.main(args);
  }

  /** Symlinks {@code appDir/name} to {@code dataDir/name}, moving existing data across on first run. */
  private static void linkDataDir(final Path appDir, final Path dataDir, final String name) throws IOException {
    final Path appPath = appDir.resolve(name);
    if(Files.isSymbolicLink(appPath)) {
      return;
    }
    if(Files.exists(appPath)) {
      final Path target = dataDir.resolve(name);
      if(!Files.exists(target)) {
        Files.move(appPath, target);
      } else {
        return;
      }
    }
    Files.createSymbolicLink(appPath, dataDir.resolve(name));
  }

  private static void setEnv(final String name, final String value) throws Throwable {
    final java.lang.foreign.Linker linker = java.lang.foreign.Linker.nativeLinker();
    try(final java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
      final java.lang.foreign.SymbolLookup libc = java.lang.foreign.SymbolLookup.libraryLookup("libSystem.B.dylib", arena);
      final java.lang.foreign.MemorySegment setenv = libc.find("setenv").orElseThrow(() -> new IllegalStateException("setenv not found"));
      final java.lang.invoke.MethodHandle handle = linker.downcallHandle(setenv,
        java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_INT,
          java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT));
      handle.invoke(arena.allocateFrom(name), arena.allocateFrom(value), 0);
    }
  }

  private static void chdir(final Path dir) throws Throwable {
    final java.lang.foreign.Linker linker = java.lang.foreign.Linker.nativeLinker();
    try(final java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
      final java.lang.foreign.SymbolLookup libc = java.lang.foreign.SymbolLookup.libraryLookup("libSystem.B.dylib", arena);
      final java.lang.foreign.MemorySegment chdir = libc.find("chdir").orElseThrow(() -> new IllegalStateException("chdir not found"));
      final java.lang.invoke.MethodHandle handle = linker.downcallHandle(chdir, java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS));
      final int result = (int)handle.invoke(arena.allocateFrom(dir.toAbsolutePath().toString()));
      if(result != 0) {
        throw new IllegalStateException("chdir(" + dir + ") failed: " + result);
      }
    }
  }
}
