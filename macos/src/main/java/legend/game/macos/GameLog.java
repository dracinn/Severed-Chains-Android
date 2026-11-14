package legend.game.macos;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.OutputStreamAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.NullConfiguration;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Shared logs in ~/Documents/Severed Chains/logs/. Filesystem port of the
 * Android GameLog: no MediaStore, and there is no debuggable flag on a jpackage
 * bundle, so the verbose appender is always attached.
 *
 * Writes crash-<ts>.log on engine crashes and uncaught exceptions (with the
 * tail of the engine's debug.log appended), a verbose debug-<ts>.log mirroring
 * all log4j output at DEBUG level, and prunes to the newest
 * {@link #MAX_LOGS} files at startup.
 */
public final class GameLog {
  private static final int MAX_LOGS = 10;
  /** Lines of the engine's debug.log appended to each crash log. */
  private static final int LOG_TAIL_LINES = 300;
  /** How far back from the end of debug.log to read when looking for them. */
  private static final int LOG_TAIL_BYTES = 256 * 1024;

  private static Path logsDir;
  private static Path debugLog;

  private GameLog() { }

  /** Call once from BundleMain with the user-facing data dir. */
  public static void init(final Path docsDir) {
    logsDir = docsDir.resolve("logs");
    debugLog = logsDir.resolve("debug.log");
    System.setProperty("legend.baseDir", logsDir.toAbsolutePath().toString());
    try {
      Files.createDirectories(logsDir);
      prune();
      pruneCrashes();
    } catch(final IOException e) {
      System.err.println("GameLog init failed: " + e);
    }
  }

  /** Keeps only the newest {@link #MAX_LOGS} files in the shared logs dir.
   *  crash-* logs are exempt — they're capped separately by {@link
   *  #pruneCrashes} so pending uploads aren't lost to routine pruning. */
  private static void prune() {
    pruneMatching(f -> !f.getFileName().toString().startsWith("crash-"));
  }

  /** Keeps only the newest {@link #MAX_LOGS} crash logs (pending + .sent).
   *  Runs at init and after each successful upload via {@link #markSent}. */
  private static void pruneCrashes() {
    pruneMatching(f -> f.getFileName().toString().startsWith("crash-"));
  }

  private static void pruneMatching(final java.util.function.Predicate<Path> include) {
    final List<Path> files = new ArrayList<>();
    try(final DirectoryStream<Path> stream = Files.newDirectoryStream(logsDir)) {
      for(final Path f : stream) {
        if(Files.isRegularFile(f) && include.test(f)) {
          files.add(f);
        }
      }
    } catch(final IOException e) {
      System.err.println("GameLog prune failed: " + e);
      return;
    }
    files.sort(Comparator.comparing((final Path f) -> {
      try {
        return Files.getLastModifiedTime(f).toMillis();
      } catch(final IOException e) {
        return 0L;
      }
    }).reversed());
    for(int i = MAX_LOGS; i < files.size(); i++) {
      try {
        Files.deleteIfExists(files.get(i));
      } catch(final IOException e) {
        System.err.println("GameLog prune failed: " + e);
      }
    }
  }

  /** Opens an OutputStream for a new file in the shared logs dir. Caller closes it. */
  public static OutputStream newLog(final String name) throws IOException {
    Files.createDirectories(logsDir);
    return Files.newOutputStream(logsDir.resolve(name));
  }

  /** The shared logs dir; null until {@link #init} runs. */
  public static Path logDir() {
    return logsDir;
  }

  /** A crash-<ts>.log that hasn't been uploaded; sent ones carry a .sent suffix. */
  public static final class PendingCrash {
    public final String name;
    final Path file;

    private PendingCrash(final String name, final Path file) {
      this.name = name;
      this.file = file;
    }
  }

  /** Unsent crash-<ts>.log files in the shared logs dir, oldest first. */
  public static List<PendingCrash> pendingCrashes() {
    final List<PendingCrash> out = new ArrayList<>();
    if(logsDir == null) {
      return out;
    }
    try(final DirectoryStream<Path> files = Files.newDirectoryStream(logsDir, "crash-*.log")) {
      for(final Path f : files) {
        out.add(new PendingCrash(f.getFileName().toString(), f));
      }
    } catch(final IOException e) {
      System.err.println("GameLog pendingCrashes failed: " + e);
    }
    out.sort(Comparator.comparing(c -> {
      try {
        return Files.getLastModifiedTime(c.file).toMillis();
      } catch(final IOException e) {
        return 0L;
      }
    }));
    return out;
  }

  /** Reads a pending crash log's text. */
  public static String readPending(final PendingCrash crash) throws IOException {
    return Files.readString(crash.file);
  }

  /** Renames a crash log to <name>.sent so it isn't re-uploaded, then prunes
   *  the crash logs to the newest {@link #MAX_LOGS}. */
  public static void markSent(final PendingCrash crash) {
    try {
      Files.move(crash.file, crash.file.resolveSibling(crash.name + ".sent"));
    } catch(final IOException e) {
      System.err.println("GameLog markSent failed: " + e);
    }
    pruneCrashes();
  }

  private static String timestamp() {
    return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
  }

  /** App version used in log headers and crash reports; from -Dsc.version. */
  public static String appVersion() {
    return System.getProperty("sc.version", "?");
  }

  /** Machine/OS snapshot for bug reports; also dumped into the verbose log header. */
  private static String deviceBlock() {
    return "App: " + appVersion() + "\n"
      + "Device: " + sysctl("hw.model") + " (" + System.getProperty("os.arch") + ")\n"
      + "macOS: " + System.getProperty("os.version") + "\n"
      + "RAM: " + sysctl("hw.memsize") + " bytes\n"
      + "Runtime: " + System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version") + "\n";
  }

  private static String sysctl(final String key) {
    try {
      final Process p = new ProcessBuilder("sysctl", "-n", key).start();
      return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    } catch(final IOException e) {
      return "?";
    }
  }

  /** GL/EGL caps recorded once the context exists; goes into crash reports. */
  private static String glBlock = "";

  /**
   * Records GL/EGL capabilities for support triage. Called from AngleContext
   * after the EGL context is current so all queries are valid.
   */
  public static void logGlInfo(final String glVendor, final String glRenderer, final String glVersion,
                               final String glsl, final String glExtensions,
                               final String eglVendor, final String eglVersion,
                               final String eglClientApis, final String eglExtensions) {
    glBlock = "GL_VENDOR: " + glVendor + "\n"
      + "GL_RENDERER: " + glRenderer + "\n"
      + "GL_VERSION: " + glVersion + "\n"
      + "GLSL version: " + glsl + "\n"
      + "EGL_VENDOR: " + eglVendor + "\n"
      + "EGL_VERSION: " + eglVersion + "\n"
      + "EGL_CLIENT_APIS: " + eglClientApis + "\n"
      + "GL_EXTENSIONS: " + glExtensions + "\n"
      + "EGL_EXTENSIONS: " + eglExtensions + "\n";
    writeVerbose("=== Graphics ===\n" + glBlock);
  }

  /** Writes a crash-<ts>.log with the given throwable + device/GL info. Safe to call during a crash. */
  public static void writeCrashLog(final Throwable throwable) {
    if(logsDir == null) {
      return;
    }
    try(final OutputStream out = newLog("crash-" + timestamp() + ".log")) {
      final PrintWriter w = new PrintWriter(out);
      w.println("Severed Chains crash log");
      w.print(deviceBlock());
      if(!glBlock.isEmpty()) {
        w.println("=== Graphics ===");
        w.print(glBlock);
      }
      w.println("Time: " + new Date());
      w.println();
      throwable.printStackTrace(w);
      final String tail = debugLogTail();
      if(!tail.isEmpty()) {
        w.println();
        w.println("=== End of debug.log ===");
        w.print(tail);
      }
      w.flush();
    } catch(final Throwable e) {
      System.err.println("GameLog crash log failed: " + e);
    }
  }

  /**
   * The last {@link #LOG_TAIL_LINES} lines of the engine's debug.log, or "" if
   * it can't be read.
   */
  private static String debugLogTail() {
    if(debugLog == null) {
      return "";
    }
    try(final RandomAccessFile file = new RandomAccessFile(debugLog.toFile(), "r")) {
      final long start = Math.max(0, file.length() - LOG_TAIL_BYTES);
      final byte[] bytes = new byte[(int)(file.length() - start)];
      file.seek(start);
      file.readFully(bytes);
      final String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\n", -1);
      // Reading from mid-file, the first line is probably partial
      final int first = Math.max(start > 0 ? 1 : 0, lines.length - LOG_TAIL_LINES);
      return String.join("\n", Arrays.asList(lines).subList(first, lines.length));
    } catch(final IOException | RuntimeException e) {
      return "";
    }
  }

  /** Installs the uncaught-exception crash writer. Call once at startup; chains to the previous handler. */
  public static void installCrashHandler() {
    final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
      writeCrashLog(throwable);
      if(previous != null) {
        previous.uncaughtException(thread, throwable);
      }
    });
  }

  private static OutputStream verboseStream;

  /** Writes directly into the verbose debug log if it's open. */
  private static synchronized void writeVerbose(final String text) {
    if(verboseStream == null) {
      return;
    }
    try {
      verboseStream.write(text.getBytes(StandardCharsets.UTF_8));
      verboseStream.flush();
    } catch(final IOException e) {
      // Best-effort only
    }
  }

  private static boolean watchingReconfigure;

  /**
   * Attaches a log4j appender mirroring all log output at DEBUG level to the
   * shared logs dir, and bumps the root logger to DEBUG. Called from
   * BundleMain before the engine boots — the engine's log4j2.xml swaps in a
   * new Configuration without our appender, so re-attach whenever the
   * configuration changes.
   */
  public static synchronized void attachVerboseAppender() {
    if(logsDir == null) {
      return;
    }

    final LoggerContext ctx = (LoggerContext)LogManager.getContext(false);
    if(verboseStream == null) {
      try {
        verboseStream = newLog("debug-" + timestamp() + ".log");
        writeVerbose("=== Severed Chains macOS " + timestamp() + " ===\n=== Device ===\n" + deviceBlock());
      } catch(final IOException e) {
        System.err.println("GameLog verbose log failed: " + e);
        return;
      }
    }

    if(!watchingReconfigure) {
      watchingReconfigure = true;
      ctx.addPropertyChangeListener(event -> {
        if(LoggerContext.PROPERTY_CONFIG.equals(event.getPropertyName())) {
          attachTo(ctx);
        }
      });
    }

    attachTo(ctx);
  }

  private static synchronized void attachTo(final LoggerContext ctx) {
    final Configuration config = ctx.getConfiguration();
    // NullConfiguration is what LogManager.shutdown() leaves behind
    if(config instanceof NullConfiguration || config.getAppender("gameLog") != null) {
      return;
    }

    final PatternLayout layout = PatternLayout.newBuilder()
      .withConfiguration(config)
      .withPattern("%d{yyy-MM-dd HH:mm:ss.SSS} [%t %c:%L] %-5level: %msg%n%throwable")
      .build();
    final OutputStreamAppender appender = OutputStreamAppender.newBuilder()
      .setName("gameLog")
      .setConfiguration(config)
      .setLayout(layout)
      .setTarget(verboseStream)
      .setImmediateFlush(true)
      .build();
    appender.start();
    config.addAppender(appender);
    config.getRootLogger().addAppender(appender, Level.DEBUG, null);
    // Set the level on this config directly; Configurator.setRootLevel would
    // look the context up again from inside the reconfigure callback
    config.getRootLogger().setLevel(Level.DEBUG);
    ctx.updateLoggers();
  }
}
