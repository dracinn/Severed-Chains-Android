package legend.game.macos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import legend.game.Main;

/**
 * Entry point for the packaged macOS app bundle.
 *
 * Two data roots:
 * <ul>
 *   <li>{@code ~/Library/Application Support/Severed Chains} — the engine's
 *   working directory (chdir target). Holds internal state: config.conf/
 *   config.dcnf, the config/ preset dir, and copies of the bundle's
 *   read-only assets (gfx/, patches/, lang/, gamecontrollerdb.txt) that the
 *   engine resolves cwd-relative.</li>
 *   <li>{@code ~/Documents/Severed Chains} — the user-facing dir. Holds only
 *   what users manage directly: isos/, mods/, saves/, files/ (the unpacked
 *   game data) and logs/ (capped to the newest 10 files by GameLog).</li>
 * </ul>
 *
 * The sealed bundle is never written to. When the bundled ANGLE libraries
 * are present, enables the ANGLE (Metal/Vulkan) GLES context in
 * {@link AngleContext} via {@code lod.angle.lib}.
 */
public final class BundleMain {
  private BundleMain() { }

  public static void main(final String[] args) throws Throwable {
    final Path appDir = Path.of(BundleMain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
    final Path home = Path.of(System.getProperty("user.home"));
    final Path dataDir = home.resolve("Library").resolve("Application Support").resolve("Severed Chains");
    final Path docsDir = home.resolve("Documents").resolve("Severed Chains");
    Files.createDirectories(dataDir);
    Files.createDirectories(docsDir);

    // The engine resolves everything cwd-relative. chdir so java.io, NIO
    // opens and native code all land in the internal dir; the bundle and the
    // user-facing Documents dir stay clean.
    chdir(dataDir);
    BundleMain.dataDir = dataDir;
    BundleMain.docsDir = docsDir;

    for(final String name : new String[] {"isos", "mods", "saves", "files", "logs"}) {
      Files.createDirectories(docsDir.resolve(name));
    }

    migrateLayout(dataDir, docsDir);

    // Read-only assets ride inside the bundle; copy them next to the cwd so
    // cwd-relative lookups find them. ~9MB, cheap enough to refresh every
    // launch — same merge-then-overwrite pattern as builtin-mods below.
    for(final String name : new String[] {"gfx", "patches", "lang", "gamecontrollerdb.txt"}) {
      copyTree(appDir.resolve(name), dataDir.resolve(name));
    }

    // Built-in mod assets ride inside the bundle at builtin-mods/; merge them
    // into the user mods dir each launch (re-copy picks up updates), same as
    // the Android asset extraction.
    final Path builtinMods = appDir.resolve("builtin-mods");
    if(Files.isDirectory(builtinMods)) {
      copyTree(builtinMods, docsDir.resolve("mods"));
    }

    // Shared logs + crash reporting — mirrors the Android GameLog/CrashReporter.
    // log4j2.configurationFile must be set before the first LogManager call
    // (attachVerboseAppender), or the context boots on the default console
    // config and the XML — with its file appender — never loads.
    System.setProperty("log4j2.configurationFile", appDir.resolve("log4j2.xml").toAbsolutePath().toString());
    GameLog.init(docsDir);
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

  /** The internal working dir ({@code ~/Library/Application Support/Severed
   *  Chains}); also the cwd. Used by staged transforms for the few upstream
   *  call sites that absolutize relative paths — the JDK caches the
   *  boot-time cwd for toAbsolutePath/getAbsolutePath, so chdir alone leaves
   *  them pointing at the launch dir. */
  private static Path dataDir;

  public static Path dataDir() {
    return dataDir;
  }

  /** The user-facing data dir ({@code ~/Documents/Severed Chains}): isos,
   *  mods, saves, files and logs. Staged transforms point the engine's
   *  cwd-relative lookups for these at {@link #docsDir()}. */
  private static Path docsDir;

  public static Path docsDir() {
    return docsDir;
  }

  /** Moves leftovers from layouts that used Documents as the engine's cwd:
   *  config state joins the internal dir, stray logs join logs/, and the
   *  copied-in bundle assets are dropped (they're re-copied into the
   *  internal dir afterwards). */
  private static void migrateLayout(final Path dataDir, final Path docsDir) throws IOException {
    for(final String name : new String[] {"config", "config.conf", "config.dcnf"}) {
      final Path src = docsDir.resolve(name);
      final Path dst = dataDir.resolve(name);
      if(Files.exists(src) && !Files.exists(dst)) {
        Files.move(src, dst);
      }
    }

    final Path logs = docsDir.resolve("logs");
    for(final String name : new String[] {"debug.log", "unpacker.log"}) {
      final Path src = docsDir.resolve(name);
      if(Files.isRegularFile(src)) {
        Files.move(src, logs.resolve(name), StandardCopyOption.REPLACE_EXISTING);
      }
    }

    for(final String name : new String[] {"gfx", "patches", "lang", "gamecontrollerdb.txt"}) {
      deleteTree(docsDir.resolve(name));
    }
  }

  private static void deleteTree(final Path path) throws IOException {
    if(!Files.exists(path)) {
      return;
    }
    try(final java.util.stream.Stream<Path> files = Files.walk(path)) {
      for(final Path file : (Iterable<Path>)files.sorted(java.util.Comparator.reverseOrder())::iterator) {
        Files.delete(file);
      }
    }
  }

  /** Copies src into dst recursively, overwriting; missing src is a no-op. */
  private static void copyTree(final Path src, final Path dst) throws IOException {
    if(Files.isRegularFile(src)) {
      Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
      return;
    }
    if(!Files.isDirectory(src)) {
      return;
    }
    try(final java.util.stream.Stream<Path> files = Files.walk(src)) {
      for(final Path file : (Iterable<Path>)files::iterator) {
        if(Files.isRegularFile(file)) {
          final Path target = dst.resolve(src.relativize(file).toString());
          Files.createDirectories(target.getParent());
          Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
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
