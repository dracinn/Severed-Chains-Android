package legend.game.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Uploads unsent crash-<ts>.log files (written by {@link GameLog}) to the
 * crash relay, which files them as deduplicated GitHub issues.
 *
 * Off unless the manifest meta-data sc.crash_relay_url is set (gradle
 * property crashRelayUrl). Consent lives in SharedPreferences: the first crash with
 * the mode unset prompts once — "always" uploads silently thereafter,
 * "never" never asks again. Upload failures leave the file pending so it
 * retries on the next launch.
 */
public final class CrashReporter {
  private static final String TAG = "SC-CrashReporter";
  private static final String PREFS = "crash_reporting";
  private static final String KEY_MODE = "mode";
  private static final String MODE_ALWAYS = "always";
  private static final String MODE_NEVER = "never";

  private CrashReporter() { }

  /** Call once from MainActivity after GameLog.init. */
  public static void init(final Activity activity) {
    relayUrl = metaData(activity, "sc.crash_relay_url");
    relayKey = metaData(activity, "sc.crash_relay_key");
    if(relayUrl.isEmpty()) {
      return;
    }

    new Thread(() -> {
      final List<GameLog.PendingCrash> pending = GameLog.pendingCrashes();
      if(pending.isEmpty()) {
        return;
      }

      final SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
      switch(prefs.getString(KEY_MODE, "ask")) {
        case MODE_NEVER:
          return;
        case MODE_ALWAYS:
          sendAll(pending);
          return;
        default:
          activity.runOnUiThread(() -> prompt(activity, prefs, pending));
      }
    }, "crash-reporter-scan").start();
  }

  private static void prompt(final Activity activity, final SharedPreferences prefs, final List<GameLog.PendingCrash> pending) {
    new AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
      .setTitle("Severed Chains crashed")
      .setMessage("The last session ended in a crash and a log was saved. Send it to the developers? Reports file a GitHub issue with the device, app version, and crash log — nothing else is collected.")
      .setPositiveButton("Always send", (dialog, which) -> {
        prefs.edit().putString(KEY_MODE, MODE_ALWAYS).apply();
        sendAll(pending);
      })
      .setNeutralButton("Send once", (dialog, which) -> sendAll(pending))
      .setNegativeButton("Never send", (dialog, which) -> prefs.edit().putString(KEY_MODE, MODE_NEVER).apply())
      .show();
  }

  private static void sendAll(final List<GameLog.PendingCrash> pending) {
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.execute(() -> {
      for(final GameLog.PendingCrash crash : pending) {
        try {
          send(crash);
          GameLog.markSent(crash);
          Log.i(TAG, "Uploaded " + crash.name);
        } catch(final IOException | RuntimeException e) {
          // Leave it pending; retried on the next launch
          Log.w(TAG, "Crash upload failed for " + crash.name, e);
          break;
        }
      }
      executor.shutdown();
    });
  }

  private static String relayUrl = "";
  private static String relayKey = "";

  private static String metaData(final Context context, final String name) {
    try {
      final android.content.pm.ApplicationInfo info = context.getPackageManager()
        .getApplicationInfo(context.getPackageName(), android.content.pm.PackageManager.GET_META_DATA);
      return info.metaData != null && info.metaData.getString(name) != null ? info.metaData.getString(name) : "";
    } catch(final android.content.pm.PackageManager.NameNotFoundException | RuntimeException e) {
      return "";
    }
  }

  private static void send(final GameLog.PendingCrash crash) throws IOException {
    final String body = GameLog.readPending(crash);
    final HttpURLConnection conn = (HttpURLConnection)new URL(relayUrl).openConnection();
    try {
      conn.setRequestMethod("POST");
      conn.setConnectTimeout(15_000);
      conn.setReadTimeout(15_000);
      conn.setDoOutput(true);
      conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
      conn.setRequestProperty("X-SC-Key", relayKey);
      conn.setRequestProperty("X-SC-Version", GameLog.appVersion());
      conn.setRequestProperty("X-SC-Device", Build.MANUFACTURER + " " + Build.MODEL + " api" + Build.VERSION.SDK_INT);
      conn.setRequestProperty("X-SC-Summary", summaryOf(body));
      conn.setRequestProperty("X-SC-Hash", hashOf(body));
      try(final OutputStream out = conn.getOutputStream()) {
        out.write(body.getBytes(StandardCharsets.UTF_8));
      }
      final int code = conn.getResponseCode();
      if(code / 100 != 2) {
        throw new IOException("relay returned HTTP " + code);
      }
    } finally {
      conn.disconnect();
    }
  }

  /**
   * "<Exception> at <top app frame>" for the issue title, e.g.
   * "NoClassDefFoundError at Audio.loadMenuSounds". Best-effort parse of the
   * stack trace inside the log; falls back to a generic title.
   */
  private static String summaryOf(final String log) {
    String exception = null;
    String frame = null;
    for(final String raw : log.split("\n")) {
      final String line = raw.trim();
      final String ex = exceptionIn(line);
      if(ex != null) {
        exception = ex; // last Caused-by line wins: the root cause dedups best
      } else if(frame == null && line.startsWith("at ")
          && (line.contains("legend.") || line.contains("lod.") || line.contains("org.legendofdragoon.") || line.contains("org.joml."))) {
        frame = line.substring(3, line.indexOf('('));
      }
    }

    String summary = exception != null ? exception : "crash";
    if(frame != null) {
      summary += " at " + frame;
    }
    // Header-safe: latin-1 printable only
    summary = summary.replaceAll("[^\\x20-\\x7E]", "?");
    return summary.length() > 120 ? summary.substring(0, 120) : summary;
  }

  /**
   * The exception simple name if the line is a stack-trace exception line
   * ("java.lang.X: msg" or "Caused by: java.lang.X: msg"), else null.
   */
  private static String exceptionIn(final String line) {
    String candidate = line;
    if(candidate.startsWith("Caused by:")) {
      candidate = candidate.substring("Caused by:".length()).trim();
    }
    if(!candidate.matches("([\\w$]+\\.)+[\\w$]*(Error|Exception|Throwable)(:.*)?")) {
      return null;
    }
    String name = candidate.substring(candidate.lastIndexOf('.') + 1);
    final int colon = name.indexOf(':');
    return colon >= 0 ? name.substring(0, colon) : name;
  }

  /**
   * Stable dedup key: sha-256 of the exception line plus the top app frame,
   * truncated to 12 hex chars. Same crash on different devices hashes equal.
   */
  private static String hashOf(final String log) {
    String exception = "";
    String frame = "";
    for(final String raw : log.split("\n")) {
      final String line = raw.trim();
      if(exceptionIn(line) != null) {
        exception = line;
      } else if(frame.isEmpty() && line.startsWith("at ")
          && (line.contains("legend.") || line.contains("lod.") || line.contains("org.legendofdragoon.") || line.contains("org.joml."))) {
        frame = line;
      }
    }
    try {
      final byte[] digest = MessageDigest.getInstance("SHA-256")
        .digest((exception + "\n" + frame).getBytes(StandardCharsets.UTF_8));
      final StringBuilder hex = new StringBuilder(12);
      for(int i = 0; i < 6; i++) {
        hex.append(Character.forDigit(digest[i] >> 4 & 0xf, 16));
        hex.append(Character.forDigit(digest[i] & 0xf, 16));
      }
      return hex.toString();
    } catch(final NoSuchAlgorithmException e) {
      return "";
    }
  }
}
