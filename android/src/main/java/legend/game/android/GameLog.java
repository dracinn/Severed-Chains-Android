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
 */
public final class GameLog {
  private static final String TAG = "SC-GameLog";
  private static final String LOG_DIR = "Severed Chains/logs";
  private static final String REL_PATH = Environment.DIRECTORY_DOCUMENTS + "/" + LOG_DIR + "/";
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
    return true;
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
