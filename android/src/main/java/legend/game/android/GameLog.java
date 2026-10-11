package legend.game.android;

import android.Manifest;
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
import android.util.Log;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.OutputStreamAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Shared logs in Documents/Severed Chains/logs/.
 *
 * API 29+ writes via MediaStore (no permission). API 26-28 falls back to direct
 * file IO under WRITE_EXTERNAL_STORAGE (declared in the manifest with
 * maxSdkVersion=28 and requested at startup).
 *
 * Both builds write crash-<ts>.log on uncaught exceptions and prune files
 * older than 14 days at startup. Debug builds additionally attach a verbose
 * log4j appender writing debug-<date>.log.
 *
 * At startup: archives the previous run's engine log (filesDir/debug.log),
 * turns a native-crash marker into a crash log, and sends queued crash
 * reports to the webhook in Documents/Severed Chains/webhook.txt if present.
 */
public final class GameLog {
  private static final String TAG = "SC-GameLog";
  private static final String LOG_DIR = "Severed Chains/logs";
  private static final String REL_PATH = Environment.DIRECTORY_DOCUMENTS + "/" + LOG_DIR + "/";
  /** Marker the native signal handler writes on a fatal crash */
  public static final String NATIVE_CRASH_MARKER = "native-crash.marker";
  /** Queue dir (in filesDir) of reports to POST to the user's webhook */
  private static final String REPORT_QUEUE = "report-queue";
  private static final long MAX_AGE_MS = TimeUnit.DAYS.toMillis(14);

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
    Log.i(TAG, "Shared logs ready -> Documents/" + LOG_DIR + (debuggable ? " (verbose)" : ""));
    processPreviousRun(context);
    return true;
  }

  /**
   * Startup maintenance, run off the calling thread's critical path where
   * possible: archives the previous run's engine debug.log, converts a native
   * crash marker into a crash log, and flushes queued reports to the webhook.
   */
  private static void processPreviousRun(final Context context) {
    final File filesDir = context.getFilesDir();
    final File engineLog = new File(filesDir, "debug.log");
    final File marker = new File(filesDir, NATIVE_CRASH_MARKER);

    // A native crash marker means the last run died on a signal the Java
    // uncaught handler never saw. Turn it into a crash-<ts>.log and queue it.
    if(marker.isFile()) {
      String detail = "";
      try {
        detail = new String(java.nio.file.Files.readAllBytes(marker.toPath()));
      } catch(final IOException ignored) { }
      //noinspection ResultOfMethodCallIgnored
      marker.delete();
      writeCrashLog(new RuntimeException("Native crash (fatal signal)\n" + detail));
      queueReport("Native crash on " + Build.MODEL + "\n" + detail
        + "\n\nLast engine log lines:\n" + tail(engineLog, 50));
    }

    // Archive the previous run's engine log before the new run truncates it
    if(engineLog.isFile() && engineLog.length() > 0) {
      try(final OutputStream out = newLog("debug-" + timestamp() + ".log");
          final java.io.InputStream in = new java.io.FileInputStream(engineLog)) {
        in.transferTo(out);
      } catch(final IOException e) {
        Log.e(TAG, "Failed to archive engine log", e);
      }
    }

    flushReportQueue();
  }

  /** Last n lines of a file, or "" when unreadable/missing. */
  private static String tail(final File file, final int n) {
    if(!file.isFile()) {
      return "";
    }
    try {
      final String[] lines = new String(java.nio.file.Files.readAllBytes(file.toPath())).split("\n");
      final StringBuilder sb = new StringBuilder();
      for(int i = Math.max(0, lines.length - n); i < lines.length; i++) {
        sb.append(lines[i]).append('\n');
      }
      return sb.toString();
    } catch(final IOException e) {
      return "";
    }
  }

  /** Queues a crash report body to be POSTed to the webhook on next launch. */
  public static void queueReport(final String body) {
    try {
      final File dir = new File(appContext.getFilesDir(), REPORT_QUEUE);
      //noinspection ResultOfMethodCallIgnored
      dir.mkdirs();
      java.nio.file.Files.write(new File(dir, "report-" + System.currentTimeMillis() + ".txt").toPath(), body.getBytes());
    } catch(final IOException e) {
      Log.e(TAG, "Failed to queue crash report", e);
    }
  }

  /**
   * Reads the webhook URL from Documents/Severed Chains/webhook.txt (a Discord
   * webhook URL or a Telegram sendMessage bot URL like
   * https://api.telegram.org/bot&lt;token&gt;/sendMessage?chat_id=&lt;id&gt;)
   * and POSTs every queued report as a message. No-ops when the file or URL
   * is absent. Runs on a background thread.
   */
  public static void flushReportQueue() {
    final File dir = new File(appContext.getFilesDir(), REPORT_QUEUE);
    final File[] pending = dir.listFiles();
    if(pending == null || pending.length == 0) {
      return;
    }

    new Thread(() -> {
      final String url = readWebhookUrl();
      if(url == null) {
        return;
      }

      for(final File report : pending) {
        try {
          final String body = new String(java.nio.file.Files.readAllBytes(report.toPath()));
          if(postReport(url, body)) {
            //noinspection ResultOfMethodCallIgnored
            report.delete();
          }
        } catch(final IOException | RuntimeException e) {
          Log.e(TAG, "Failed to send report " + report.getName(), e);
        }
      }
    }, "sc-report-flush").start();
  }

  private static String readWebhookUrl() {
    if(Build.VERSION.SDK_INT < 29) {
      final File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Severed Chains/webhook.txt");
      try {
        if(f.isFile()) {
          final String s = new String(java.nio.file.Files.readAllBytes(f.toPath())).trim();
          return s.isEmpty() ? null : s;
        }
      } catch(final IOException ignored) { }
      return null;
    }

    final ContentResolver res = appContext.getContentResolver();
    final Uri files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
    try(final Cursor c = res.query(files, new String[] {MediaStore.MediaColumns._ID},
      MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + "=?",
      new String[] {Environment.DIRECTORY_DOCUMENTS + "/Severed Chains/", "webhook.txt"}, null)) {
      if(c == null || !c.moveToFirst()) {
        return null;
      }
      final Uri item = Uri.withAppendedPath(files, String.valueOf(c.getLong(0)));
      try(final java.io.InputStream in = res.openInputStream(item)) {
        if(in == null) {
          return null;
        }
        final String s = new String(in.readAllBytes()).trim();
        return s.isEmpty() ? null : s;
      }
    } catch(final RuntimeException | IOException e) {
      Log.e(TAG, "Failed to read webhook.txt", e);
      return null;
    }
  }

  private static boolean postReport(final String url, final String body) throws IOException {
    // Keep inside the Discord (2000) and Telegram (4096) message limits
    final String text = body.length() > 1900 ? body.substring(0, 1900) + "…" : body;
    final String json = url.contains("discord") ? "{\"content\":" : "{\"text\":";
    final java.net.HttpURLConnection conn = (java.net.HttpURLConnection)new java.net.URL(url).openConnection();
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setDoOutput(true);
    conn.getOutputStream().write((json + jsonEscape("Severed Chains crash report:\n```\n" + text + "\n```") + "}").getBytes());
    final int code = conn.getResponseCode();
    conn.disconnect();
    return code >= 200 && code < 300;
  }

  private static String jsonEscape(final String s) {
    final StringBuilder sb = new StringBuilder("\"");
    for(final char ch : s.toCharArray()) {
      switch(ch) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> sb.append(ch < 0x20 ? String.format("\\u%04x", (int)ch) : ch);
      }
    }
    return sb.append('"').toString();
  }

  public static boolean needsPermission(final Context context) {
    return Build.VERSION.SDK_INT < 29
      && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
  }

  private static boolean writable() {
    return Build.VERSION.SDK_INT >= 29 || !needsPermission(appContext);
  }

  /** Deletes log files older than 14 days in the shared logs dir. */
  private static void prune() {
    final long cutoff = System.currentTimeMillis() / 1000 - TimeUnit.DAYS.toSeconds(14);
    if(Build.VERSION.SDK_INT >= 29) {
      final ContentResolver res = appContext.getContentResolver();
      final Uri files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
      try(final Cursor c = res.query(files,
        new String[] {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_MODIFIED},
        MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DATE_MODIFIED + "<?",
        new String[] {REL_PATH, String.valueOf(cutoff)}, null)) {
        if(c == null) {
          return;
        }
        while(c.moveToNext()) {
          final Uri item = Uri.withAppendedPath(files, String.valueOf(c.getLong(0)));
          res.delete(item, null, null);
        }
      } catch(final RuntimeException e) {
        Log.e(TAG, "Failed to prune old logs", e);
      }
    } else {
      final File dir = legacyDir();
      final File[] files = dir.listFiles();
      if(files == null) {
        return;
      }
      for(final File f : files) {
        if(f.isFile() && f.lastModified() < System.currentTimeMillis() - MAX_AGE_MS) {
          f.delete();
        }
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

  private static String timestamp() {
    return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
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

  /** Writes a crash-<ts>.log with the given throwable + device info. Safe to call during a crash. */
  public static void writeCrashLog(final Throwable throwable) {
    if(!writable()) {
      return;
    }
    try(final OutputStream out = newLog("crash-" + timestamp() + ".log")) {
      final PrintWriter w = new PrintWriter(out);
      w.println("Severed Chains crash log");
      w.println("App: " + versionName() + (debuggable ? " debug" : " release"));
      w.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
      w.println("Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
      w.println("Time: " + new Date());
      w.println();
      throwable.printStackTrace(w);
      w.flush();
    } catch(final Throwable e) {
      Log.e(TAG, "Failed to write crash log", e);
    }
  }

  /** Installs the uncaught-exception crash writer. Call once at startup; chains to the previous handler. */
  public static void installCrashHandler() {
    final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
      writeCrashLog(throwable);
      final StringWriter sw = new StringWriter();
      throwable.printStackTrace(new PrintWriter(sw));
      queueReport("Uncaught exception on " + Build.MODEL + " (" + versionName() + ") in " + thread.getName()
        + "\n```\n" + sw + "\n```\n\nLast engine log lines:\n" + tail(new File(appContext.getFilesDir(), "debug.log"), 50));
      if(previous != null) {
        previous.uncaughtException(thread, throwable);
      }
    });
  }

  private static OutputStream verboseStream;

  /**
   * Debug builds only: attaches a log4j appender mirroring all log output at
   * DEBUG level to the shared logs dir, and bumps the root logger to DEBUG.
   * Called once the engine's log4j config is loaded (SdlPlatformManager is
   * constructed inside GameEngine init, after Main's static block binds the
   * XML config — the classpath config never reloads, so no re-attach hook is
   * needed).
   */
  public static synchronized void attachVerboseAppender() {
    if(!debuggable || appContext == null || !writable()) {
      return;
    }

    final LoggerContext ctx = (LoggerContext)LogManager.getContext(false);
    if(verboseStream == null) {
      try {
        verboseStream = newLog("debug-" + timestamp() + ".log");
      } catch(final IOException e) {
        Log.e(TAG, "Failed to open verbose log", e);
        return;
      }
    }

    final Configuration config = ctx.getConfiguration();
    if(config.getAppender("gameLog") != null) {
      return; // already attached to the current config
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
    Configurator.setRootLevel(Level.DEBUG);
    ctx.updateLoggers();
    Log.i(TAG, "Verbose engine logging enabled -> Documents/" + LOG_DIR);
  }
}
