package legend.game.android;

import android.Manifest;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.OutputStreamAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.NullConfiguration;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Shared logs in Documents/Severed Chains/logs/.
 *
 * API 29+ writes via MediaStore (no permission). API 26-28 falls back to direct
 * file IO under WRITE_EXTERNAL_STORAGE (declared in the manifest with
 * maxSdkVersion=28 and requested at startup).
 *
 * Both builds write crash-<ts>.log on engine crashes and uncaught exceptions
 * (with the tail of the engine's private debug.log appended) and prune files
 * older than 14 days at startup. Debug builds additionally attach a verbose
 * log4j appender writing debug-<date>.log.
 */
public final class GameLog {
  private static final String TAG = "SC-GameLog";
  private static final String LOG_DIR = "Severed Chains/logs";
  private static final String REL_PATH = Environment.DIRECTORY_DOCUMENTS + "/" + LOG_DIR + "/";
  private static final int MAX_LOGS = 10;
  /** Lines of the engine's debug.log appended to each crash log. */
  private static final int LOG_TAIL_LINES = 300;
  /** How far back from the end of debug.log to read when looking for them. */
  private static final int LOG_TAIL_BYTES = 256 * 1024;

  private static Context appContext;
  private static boolean debuggable;

  private GameLog() { }

  /** Call once from MainActivity.onCreate. Returns false if shared storage isn't writable. */
  public static boolean init(final Context context) {
    appContext = context.getApplicationContext();
    debuggable = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    if(!writable()) {
      Log.w(TAG, "Shared log storage unavailable (permission not granted)");
      return false;
    }
    prune();
    pruneCrashes();
    Log.i(TAG, "Shared logs ready -> Documents/" + LOG_DIR + (debuggable ? " (verbose)" : ""));
    return true;
  }

  public static boolean needsPermission(final Context context) {
    return Build.VERSION.SDK_INT < 29
      && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
  }

  private static boolean writable() {
    return Build.VERSION.SDK_INT >= 29 || !needsPermission(appContext);
  }

  /** Keeps only the newest {@link #MAX_LOGS} non-crash files in the shared
   *  logs dir. crash-* logs are exempt — they're capped separately by {@link
   *  #pruneCrashes} so pending uploads aren't lost to routine pruning. */
  private static void prune() {
    pruneMatching(MediaStore.MediaColumns.DISPLAY_NAME + " NOT LIKE 'crash-%'",
      f -> !f.getName().startsWith("crash-"));
  }

  /** Keeps only the newest {@link #MAX_LOGS} crash logs (pending + .sent).
   *  Runs at init and after each successful upload via {@link #markSent}. */
  private static void pruneCrashes() {
    pruneMatching(MediaStore.MediaColumns.DISPLAY_NAME + " LIKE 'crash-%'",
      f -> f.getName().startsWith("crash-"));
  }

  private static void pruneMatching(final String nameSelection, final java.util.function.Predicate<File> legacyFilter) {
    if(Build.VERSION.SDK_INT >= 29) {
      final ContentResolver res = appContext.getContentResolver();
      final Uri files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
      final List<Long> ids = new ArrayList<>();
      try(final Cursor c = res.query(files,
        new String[] {MediaStore.MediaColumns._ID},
        MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + nameSelection,
        new String[] {REL_PATH}, MediaStore.MediaColumns.DATE_MODIFIED + " DESC")) {
        if(c == null) {
          return;
        }
        while(c.moveToNext()) {
          ids.add(c.getLong(0));
        }
      } catch(final RuntimeException e) {
        Log.e(TAG, "Failed to prune old logs", e);
        return;
      }
      for(int i = MAX_LOGS; i < ids.size(); i++) {
        res.delete(Uri.withAppendedPath(files, String.valueOf(ids.get(i))), null, null);
      }
    } else {
      final File dir = legacyDir();
      final File[] files = dir.listFiles(f -> f.isFile() && legacyFilter.test(f));
      if(files == null) {
        return;
      }
      Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
      for(int i = MAX_LOGS; i < files.length; i++) {
        //noinspection ResultOfMethodCallIgnored
        files[i].delete();
      }
    }
  }

  /** Opens an OutputStream for a new file in the shared logs dir. Caller closes it. */
  public static OutputStream newLog(final String name) throws IOException {
    if(Build.VERSION.SDK_INT >= 29) {
      final ContentValues values = new ContentValues();
      values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
      // No MIME_TYPE: "text/plain" makes MediaStore rewrite the display name
      // to *.log.txt; leaving it unset keeps the .log extension intact.
      values.put(MediaStore.MediaColumns.RELATIVE_PATH, REL_PATH);
      final Uri uri = appContext.getContentResolver()
        .insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
      if(uri == null) {
        throw new IOException("MediaStore insert returned null");
      }
      final OutputStream out = appContext.getContentResolver().openOutputStream(uri);
      if(out == null) {
        throw new IOException("openOutputStream returned null for " + uri);
      }
      return out;
    }
    final File dir = legacyDir();
    if(!dir.isDirectory() && !dir.mkdirs()) {
      throw new IOException("Failed to create " + dir);
    }
    return new FileOutputStream(new File(dir, name));
  }

  private static File legacyDir() {
    return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), LOG_DIR);
  }

  /** A crash-<ts>.log that hasn't been uploaded; sent ones carry a .sent suffix. */
  public static final class PendingCrash {
    public final String name;
    final Uri uri;   // non-null on API 29+
    final File file; // non-null below 29

    private PendingCrash(final String name, final Uri uri, final File file) {
      this.name = name;
      this.uri = uri;
      this.file = file;
    }
  }

  /** Unsent crash-<ts>.log files in the shared logs dir, oldest first. */
  public static List<PendingCrash> pendingCrashes() {
    final List<PendingCrash> out = new ArrayList<>();
    if(appContext == null) {
      return out;
    }
    if(Build.VERSION.SDK_INT >= 29) {
      final ContentResolver res = appContext.getContentResolver();
      final Uri files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
      try(final Cursor c = res.query(files,
        new String[] {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME},
        MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?",
        new String[] {REL_PATH, "crash-%.log"}, MediaStore.MediaColumns.DATE_MODIFIED)) {
        if(c == null) {
          return out;
        }
        while(c.moveToNext()) {
          out.add(new PendingCrash(c.getString(1), Uri.withAppendedPath(files, String.valueOf(c.getLong(0))), null));
        }
      } catch(final RuntimeException e) {
        Log.e(TAG, "Failed to list crash logs", e);
      }
    } else {
      final File[] files = legacyDir().listFiles(f -> f.isFile() && f.getName().startsWith("crash-") && f.getName().endsWith(".log"));
      if(files == null) {
        return out;
      }
      Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
      for(final File f : files) {
        out.add(new PendingCrash(f.getName(), null, f));
      }
    }
    return out;
  }

  /** Reads a pending crash log's text (bounded by the log size cap). */
  public static String readPending(final PendingCrash crash) throws IOException {
    final InputStream in = crash.uri != null
      ? appContext.getContentResolver().openInputStream(crash.uri)
      : new FileInputStream(crash.file);
    if(in == null) {
      throw new IOException("openInputStream returned null for " + crash.name);
    }
    try(in) {
      final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
      final byte[] tmp = new byte[8192];
      int n;
      while((n = in.read(tmp)) != -1) {
        buf.write(tmp, 0, n);
      }
      return buf.toString(StandardCharsets.UTF_8.name());
    }
  }

  /** Renames a crash log to <name>.sent so it isn't re-uploaded. */
  public static void markSent(final PendingCrash crash) {
    try {
      if(crash.uri != null) {
        final ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, crash.name + ".sent");
        appContext.getContentResolver().update(crash.uri, values, null, null);
      } else {
        crash.file.renameTo(new File(crash.file.getParentFile(), crash.name + ".sent"));
      }
    } catch(final RuntimeException e) {
      Log.w(TAG, "Failed to mark crash log sent: " + crash.name, e);
    }
    pruneCrashes();
  }

  private static String timestamp() {
    return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
  }

  /** versionName (versionCode), e.g. "0.1.34 (134)" - used in log headers and crash reports. */
  public static String appVersion() {
    return versionName();
  }

  private static String versionName() {
    if(appContext == null) {
      return "?";
    }
    try {
      final PackageInfo pi = appContext.getPackageManager().getPackageInfo(appContext.getPackageName(), 0);
      final long code = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
      return pi.versionName + " (" + code + ")";
    } catch(final PackageManager.NameNotFoundException e) {
      return "?";
    }
  }

  /** Device/SoC/OS snapshot for bug reports; also dumped into the verbose log header. */
  private static String deviceBlock() {
    if(appContext == null) {
      return "";
    }

    long ramMb = -1;
    try {
      final ActivityManager am = (ActivityManager)appContext.getSystemService(Context.ACTIVITY_SERVICE);
      final ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
      am.getMemoryInfo(mem);
      ramMb = mem.totalMem / (1024 * 1024);
    } catch(final RuntimeException e) {
      Log.w(TAG, "Failed to read memory info", e);
    }

    final DisplayMetrics dm = appContext.getResources().getDisplayMetrics();

    return "App: " + versionName() + (debuggable ? " debug" : " release") + "\n"
      + "Device: " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")\n"
      + "Product/board/hardware: " + Build.PRODUCT + " / " + Build.BOARD + " / " + Build.HARDWARE + "\n"
      + "Fingerprint: " + Build.FINGERPRINT + "\n"
      + "Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ", patch " + Build.VERSION.SECURITY_PATCH + ")\n"
      + "ABIs: " + String.join(", ", Build.SUPPORTED_ABIS) + "\n"
      + "RAM: " + ramMb + " MB\n"
      + "Display: " + dm.widthPixels + "x" + dm.heightPixels + " @" + dm.densityDpi + "dpi\n"
      + "Runtime: " + System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version") + "\n"
      + "GL fallback level: " + GlesCompat.fallbackLevel + "\n";
  }

  /** GL/EGL caps recorded once the context exists; goes into crash reports. */
  private static String glBlock = "";

  /**
   * Records GL/EGL capabilities for support triage of unsupported GPUs.
   * Called from AndroidWindow after the EGL context is current so all
   * queries are valid; also mirrored to logcat and the verbose log.
   */
  public static void logGlInfo(final String glVendor, final String glRenderer, final String glVersion,
                               final String glsl, final String glExtensions,
                               final String eglVendor, final String eglVersion,
                               final String eglClientApis, final String eglExtensions) {
    glBlock = "GL_VENDOR: " + glVendor + "\n"
      + "GL_RENDERER: " + glRenderer + "\n"
      + "GL_VERSION: " + glVersion + "\n"
      + "GLSL version: " + glsl + "\n"
      + "Geometry shaders (ES 3.2 path): " + GlesCompat.geometryShaders + "\n"
      + "EGL_VENDOR: " + eglVendor + "\n"
      + "EGL_VERSION: " + eglVersion + "\n"
      + "EGL_CLIENT_APIS: " + eglClientApis + "\n"
      + "GL_EXTENSIONS: " + glExtensions + "\n"
      + "EGL_EXTENSIONS: " + eglExtensions + "\n";
    Log.i(TAG, "Graphics caps:\n" + glBlock);
    writeVerbose("=== Graphics ===\n" + glBlock);
  }

  /** Writes a crash-<ts>.log with the given throwable + device/GL info. Safe to call during a crash. */
  public static void writeCrashLog(final Throwable throwable) {
    if(!writable()) {
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
      Log.e(TAG, "Failed to write crash log", e);
    }
  }

  /**
   * The last {@link #LOG_TAIL_LINES} lines of the engine's debug.log, or "" if
   * it can't be read. debug.log lives in private app storage and is
   * overwritten on every launch, so the crash log is the only place players
   * can get at it.
   */
  private static String debugLogTail() {
    final String baseDir = System.getProperty("legend.baseDir");
    if(baseDir == null) {
      return "";
    }
    try(final RandomAccessFile file = new RandomAccessFile(new File(baseDir, "debug.log"), "r")) {
      final long start = Math.max(0, file.length() - LOG_TAIL_BYTES);
      final byte[] bytes = new byte[(int)(file.length() - start)];
      file.seek(start);
      file.readFully(bytes);
      final String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\n", -1);
      // Reading from mid-file, the first line is probably partial
      final int first = Math.max(start > 0 ? 1 : 0, lines.length - LOG_TAIL_LINES);
      return String.join("\n", Arrays.asList(lines).subList(first, lines.length));
    } catch(final IOException | RuntimeException e) {
      Log.w(TAG, "Failed to read debug.log", e);
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

  /** Writes directly into the verbose debug log if it's open (no-op on release). */
  private static synchronized void writeVerbose(final String text) {
    if(verboseStream == null) {
      return;
    }
    try {
      verboseStream.write(text.getBytes(StandardCharsets.UTF_8));
      verboseStream.flush();
    } catch(final IOException e) {
      Log.w(TAG, "Verbose log write failed", e);
    }
  }

  private static boolean watchingReconfigure;

  /**
   * Debug builds only: attaches a log4j appender mirroring all log output at
   * DEBUG level to the shared logs dir, and bumps the root logger to DEBUG.
   * Called from SdlPlatformManager's constructor. On device that runs before
   * the engine's log4j2.xml is loaded (log4j is still on its
   * DefaultConfiguration), and loading the XML swaps in a new Configuration
   * without our appender, so re-attach whenever the configuration changes.
   */
  public static synchronized void attachVerboseAppender() {
    if(!debuggable || appContext == null || !writable()) {
      return;
    }

    final LoggerContext ctx = (LoggerContext)LogManager.getContext(false);
    if(verboseStream == null) {
      try {
        verboseStream = newLog("debug-" + timestamp() + ".log");
        writeVerbose("=== Severed Chains Android " + timestamp() + " ===\n=== Device ===\n" + deviceBlock());
      } catch(final IOException e) {
        Log.e(TAG, "Failed to open verbose log", e);
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
    Log.i(TAG, "Verbose engine logging enabled -> Documents/" + LOG_DIR);
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
