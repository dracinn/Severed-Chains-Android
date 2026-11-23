package legend.game.android;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Bridge between Android platform services and the shared upstream code:
 * holds the application Context for asset/resource access and the current
 * render Surface handed over by the Activity.
 */
public final class AndroidEnv {
  private AndroidEnv() { }

  private static Context context;

  // ------------------------------------------------------------------
  // Surface lifecycle handshake between the UI thread (SurfaceHolder
  // callbacks) and the game thread (AndroidWindow's EGL surface owner).
  // ------------------------------------------------------------------
  private static final Object SURFACE_LOCK = new Object();
  private static Surface pendingSurface;
  private static int pendingWidth;
  private static int pendingHeight;
  private static long generation;
  private static boolean releaseRequested;
  private static boolean releaseAcked = true;

  /** The single game engine thread; survives Activity recreation. */
  private static boolean gameStarted;

  private static android.app.Activity activity;

  /** UI thread: remember the live Activity for orientation changes. */
  public static void setActivity(final android.app.Activity act) {
    activity = act;
  }


  /** Game thread -> UI thread: apply a requested orientation to the Activity. */
  public static void setOrientation(final legend.core.platform.input.Orientation orientation) {
    final android.app.Activity act = activity;
    if(act == null) {
      return;
    }

    act.runOnUiThread(() -> {
      final int mode = switch(orientation) {
        case PORTRAIT -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
        case LANDSCAPE -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
        default -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
      };
      if(act.getRequestedOrientation() != mode) {
        act.setRequestedOrientation(mode);
      }
    });
  }

  /** The display's actual current orientation — differs from the requested
   *  Orientation setting when it is AUTO, and lags behind a fresh lock request
   *  until the system finishes rotating. */
  public static boolean isPortrait() {
    return context.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;
  }

  /** Focusable IME target owned by the Activity; survives as static state. */
  @Nullable
  private static android.view.View imeView;

  public static void init(final Context context) {
    AndroidEnv.context = context.getApplicationContext();
  }

  public static void setImeView(final android.view.View view) {
    imeView = view;
  }

  /** Game thread -> UI thread: show the soft keyboard (Textbox focus). */
  public static void showIme() {
    final android.view.View view = imeView;
    if(view == null) {
      return;
    }

    view.post(() -> {
      view.setFocusable(true);
      view.setFocusableInTouchMode(true);
      Log.d("SC-Env", "showIme: requestFocus=" + view.requestFocus());
      // The served-view binding happens a few messages after focus; retry
      // briefly so showSoftInput isn't dropped on the first attempt.
      view.postDelayed(new Runnable() {
        private int tries;
        @Override
        public void run() {
          final android.view.inputmethod.InputMethodManager imm =
            (android.view.inputmethod.InputMethodManager)view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
          // SHOW_FORCED, not SHOW_IMPLICIT: this device counts a hard keyboard
          // (show_ime_with_hard_keyboard=0), which suppresses implicit shows.
          final boolean ok = imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_FORCED);
          Log.d("SC-Env", "showSoftInput try " + this.tries + " -> " + ok);
          if(!ok && !view.isFocused()) {
            return;
          }
          if(!ok && ++this.tries < 10) {
            view.postDelayed(this, 100);
          }
        }
      }, 50);
    });
  }

  /** Game thread -> UI thread: hide the soft keyboard. */
  public static void hideIme() {
    final android.view.View view = imeView;
    if(view == null) {
      return;
    }

    view.post(() -> {
      final android.view.inputmethod.InputMethodManager imm =
        (android.view.inputmethod.InputMethodManager)view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
      Log.d("SC-Env", "hideIme: hideSoftInputFromWindow=" + imm.hideSoftInputFromWindow(view.getWindowToken(), 0));
      // The editor view keeping focus lets the IME re-show spontaneously
      view.clearFocus();
      view.setFocusable(false);
      view.setFocusableInTouchMode(false);
    });
  }

  public static Context context() {
    return context;
  }

  public static final class Pending {
    public final Surface surface;
    public final int width;
    public final int height;
    public final long generation;

    Pending(final Surface surface, final int width, final int height, final long generation) {
      this.surface = surface;
      this.width = width;
      this.height = height;
      this.generation = generation;
    }
  }

  /** UI thread: a new/updated surface is available for the game thread. */
  public static void setSurface(final Surface surface, final int width, final int height) {
    Log.i("SC-Env", "setSurface: " + width + "x" + height + " valid=" + surface.isValid());
    synchronized(SURFACE_LOCK) {
      pendingSurface = surface;
      pendingWidth = width;
      pendingHeight = height;
      generation++;
      SURFACE_LOCK.notifyAll();
    }
  }

  /**
   * UI thread: the current surface is being destroyed. Android requires it
   * to be unused once surfaceDestroyed returns, so block briefly until the
   * game thread acknowledges it released the EGL surface. Never blocks long
   * enough to ANR.
   */
  public static void surfaceDestroyed() {
    synchronized(SURFACE_LOCK) {
      releaseRequested = true;
      releaseAcked = false;

      final long deadline = System.nanoTime() + 2_000_000_000L;
      while(!releaseAcked) {
        final long remaining = deadline - System.nanoTime();
        if(remaining <= 0) {
          Log.w("SC-Env", "Timed out waiting for EGL surface release ack");
          break;
        }

        try {
          SURFACE_LOCK.wait(remaining / 1_000_000);
        } catch(final InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }
      Log.i("SC-Env", "surfaceDestroyed: release acked=" + releaseAcked);
    }
  }

  /** Game thread: does the UI thread want the window surface released? */
  public static boolean isReleaseRequested() {
    synchronized(SURFACE_LOCK) {
      return releaseRequested;
    }
  }

  /** Game thread: window EGL surface destroyed; the surface may now go away. */
  public static void ackRelease() {
    synchronized(SURFACE_LOCK) {
      releaseRequested = false;
      releaseAcked = true;
      SURFACE_LOCK.notifyAll();
    }
  }

  /** Game thread: currently-pending surface, or null. Non-blocking. */
  public static Pending peekPendingSurface() {
    synchronized(SURFACE_LOCK) {
      return pendingSurface == null ? null : new Pending(pendingSurface, pendingWidth, pendingHeight, generation);
    }
  }

  /** Blocks until a currently-valid Surface is available for EGL. */
  public static Surface awaitSurface() {
    synchronized(SURFACE_LOCK) {
      while(true) {
        if(pendingSurface != null && pendingSurface.isValid()) {
          return pendingSurface;
        }

        try {
          SURFACE_LOCK.wait();
        } catch(final InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      }
    }
  }

  public static int surfaceWidth() {
    synchronized(SURFACE_LOCK) {
      return pendingWidth;
    }
  }

  public static int surfaceHeight() {
    synchronized(SURFACE_LOCK) {
      return pendingHeight;
    }
  }

  // ------------------------------------------------------------------
  // Game thread ownership
  // ------------------------------------------------------------------

  /** Starts the engine once per process; safe to call after Activity recreate. */
  public static synchronized boolean startGameOnce() {
    if(gameStarted) {
      return false;
    }

    gameStarted = true;
    final Thread gameThread = new Thread(() -> {
      try {
        syncPublicStorage();
        legend.game.Main.main(new String[0]);
        // Main.main only returns on a clean engine shutdown — the title
        // screen's Quit closes the window which ends the platform run loop.
        // The process keeps the dead Activity otherwise, so finish the task.
        quitToLauncher();
      } catch(final Throwable t) {
        Log.e("SC-Main", "Game engine crashed", t);
        // This catch swallows engine fatals before the uncaught handler sees
        // them — write the shared crash log here too.
        GameLog.writeCrashLog(t);
      }
    }, "GameEngine");
    gameThread.start();
    return true;
  }

  /** Game thread -> UI thread: engine exited cleanly, close the app. */
  private static void quitToLauncher() {
    final android.app.Activity act = activity;
    if(act != null) {
      act.runOnUiThread(act::finishAndRemoveTask);
    }
  }

  // ------------------------------------------------------------------
  // Shared drop-in folders under Documents/Severed Chains
  // ------------------------------------------------------------------

  private static final File PUBLIC_ROOT =
    new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Severed Chains");

  /** Public folders users can manage without root or a PC. */
  public static final File PUBLIC_SAVES_DIR = new File(PUBLIC_ROOT, "Saves");
  public static final File PUBLIC_ISOS_DIR = new File(PUBLIC_ROOT, "ISOs");

  /** Whether the app can read arbitrary files in shared storage. */
  public static boolean hasSharedStorageAccess() {
    if(Build.VERSION.SDK_INT >= 30) {
      return Environment.isExternalStorageManager();
    }
    return context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
  }

  /**
   * Directories the unpacker and setup screen scan for disc images: the
   * internal isos/ dir plus the public ISOs/ drop-in when storage access
   * is granted.
   */
  public static List<Path> isoDirs() {
    final List<Path> dirs = new ArrayList<>(2);
    dirs.add(new File(context.getFilesDir(), "isos").toPath());
    if(hasSharedStorageAccess()) {
      dirs.add(PUBLIC_ISOS_DIR.toPath());
    }
    return dirs;
  }

  /**
   * The engine's save root: the public Saves/ folder when storage access
   * is granted (saves become file-manager accessible and survive
   * uninstalls), otherwise the internal filesDir/saves.
   */
  public static Path savesPath() {
    final String loc = saveLocation();
    if("sd".equals(loc)) {
      final File sd = sdSavesTarget();
      if(sd != null) {
        return sd.toPath();
      }
    } else if("internal".equals(loc)) {
      return new File(context.getFilesDir(), "saves").toPath();
    }
    // "documents" and "auto" share the default: public when granted
    if(hasSharedStorageAccess()) {
      return PUBLIC_SAVES_DIR.toPath();
    }
    return new File(context.getFilesDir(), "saves").toPath();
  }

  // ------------------------------------------------------------------
  // Save-location preference (internal / shared Documents / SD card)
  // ------------------------------------------------------------------

  private static final String PREFS = "severed_chains";

  public static android.content.SharedPreferences prefs() {
    return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }
  private static final String PREF_SAVE_LOCATION = "save_location";
  private static final String PREF_SD_SAVES = "saves_on_sd"; // legacy boolean

  /** "auto" (default), "internal", "documents" or "sd". */
  public static String saveLocation() {
    final var prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    final String loc = prefs.getString(PREF_SAVE_LOCATION, null);
    if(loc != null) {
      return loc;
    }
    return prefs.getBoolean(PREF_SD_SAVES, false) ? "sd" : "auto";
  }

  public static void setSaveLocation(final String loc) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PREF_SAVE_LOCATION, loc).apply();
  }

  // ------------------------------------------------------------------
  // Renderer backend (system GLES / ANGLE GLES-over-Vulkan)
  // ------------------------------------------------------------------

  private static final String PREF_RENDERER_BACKEND = "renderer_backend";
  private static final String PREF_LAST_GL_BACKEND = "last_gl_backend";
  /** Written by GlNative when a run dies mid-GL-init — see GlesCompat. */
  private static final String PREF_GL_INIT_OK = "gl_init_ok";
  /** Sticky: a single ANGLE init death stops auto mode from retrying Vulkan. */
  private static final String PREF_ANGLE_FAILED = "angle_failed";

  /** "auto" (default), "gles" or "vulkan". */
  public static String rendererBackend() {
    return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .getString(PREF_RENDERER_BACKEND, "auto");
  }

  public static void setRendererBackend(final String backend) {
    // Changing the setting is an explicit user decision — give Vulkan a
    // fresh chance under auto even if it crashed once before.
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .putString(PREF_RENDERER_BACKEND, backend)
      .remove(PREF_ANGLE_FAILED)
      .apply();
  }

  /**
   * Backend to actually attempt: the system GLES driver is the default for
   * both AUTO and GLES — ANGLE's GLES-over-Vulkan is strictly opt-in via an
   * explicit VULKAN pick. Even then a recorded init death degrades to GLES
   * so a crashing driver can't bootloop the app; re-selecting the backend
   * clears {@link #PREF_ANGLE_FAILED} and gives Vulkan a fresh chance.
   */
  public static String effectiveBackend() {
    if(!"vulkan".equals(rendererBackend()) || AngleLibs.dir() == null) {
      return "gles";
    }
    final var prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    if(prefs.getBoolean(PREF_ANGLE_FAILED, false)) {
      return "gles";
    }
    final boolean crashedOnAngle = !prefs.getBoolean(PREF_GL_INIT_OK, true)
      && "vulkan".equals(prefs.getString(PREF_LAST_GL_BACKEND, ""));
    if(crashedOnAngle) {
      prefs.edit().putBoolean(PREF_ANGLE_FAILED, true).apply();
      return "gles";
    }
    return "vulkan";
  }

  /** Records which backend init began; cleared when the first frame lands. */
  public static void markGlBackend(final String backend) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .putString(PREF_LAST_GL_BACKEND, backend)
      .putBoolean(PREF_GL_INIT_OK, false)
      .apply();
  }

  public static void glBackendOk() {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .putBoolean(PREF_GL_INIT_OK, true)
      .apply();
  }

  // ------------------------------------------------------------------
  // Game-data storage (unpacked files/ assets — the multi-GB payload)
  // ------------------------------------------------------------------

  private static final String PREF_GAME_DATA_LOCATION = "game_data_location";
  /** Location the files/ tree lived at before the last change — pending move. */
  private static final String PREF_DATA_MIGRATE_FROM = "game_data_migrate_from";

  /** "auto" (default), "internal" or "sd". Auto never leaves internal. */
  public static String gameDataLocation() {
    return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .getString(PREF_GAME_DATA_LOCATION, "auto");
  }

  public static void setGameDataLocation(final String location) {
    final String from = effectiveDataLocation(gameDataLocation());
    final String to = effectiveDataLocation(location);
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .putString(PREF_GAME_DATA_LOCATION, location)
      // Only flag a move when the effective root actually changes —
      // auto↔internal flips share a root and must never "migrate".
      .putString(PREF_DATA_MIGRATE_FROM, from.equals(to) ? null : from)
      .apply();
  }

  /** auto resolves to internal for game data — the plan is explicit that
   *  nothing moves to a card just because one was inserted. */
  private static String effectiveDataLocation(final String location) {
    return "sd".equals(location) ? "sd" : "internal";
  }

  /** Root dir that contains the extracted files/ tree for a location
   *  value, or null when that location isn't usable right now. SD must
   *  survive a real write probe — a flaky card mounted read-only counts
   *  as unavailable, not as a destination. */
  @Nullable
  public static File dataRootFor(final String location) {
    if("sd".equals(location)) {
      final File dir = sdAppFilesDir();
      return dir != null && isWritableDir(dir) ? dir : null;
    }
    return context.getFilesDir();
  }

  /** Parent dir of the live files/ tree: the SD app dir when selected and
   *  writable, internal storage otherwise. */
  public static File gameDataRoot() {
    if("sd".equals(effectiveDataLocation(gameDataLocation()))) {
      final File sd = dataRootFor("sd");
      if(sd != null) {
        return sd;
      }
    }
    return context.getFilesDir();
  }

  /** The extracted-assets root itself ({@code <gameDataRoot>/files}). Read
   *  by staged transforms on Unpacker/ScriptPatcher/Finderator/LloydTemplate
   *  — every files/ access in the engine funnels through this. */
  public static Path gameFilesDir() {
    return new File(gameDataRoot(), "files").toPath();
  }

  /**
   * Fail-fast guard called before Unpacker.unpack() (staged transform on
   * GameEngine). The extracted files/ tree is roughly the size of the source
   * disc images, so require that much free space on the game-data volume
   * plus headroom — otherwise the unpack dies partway through with a useless
   * "No space left on device" (and, worse, a worker-thread crash report).
   */
  public static void checkUnpackSpace() throws legend.game.unpacker.UnpackerException {
    long needed = 256L * 1024 * 1024; // scripts, patches, version markers
    try {
      for(final Path dir : isoDirs()) {
        if(!Files.isDirectory(dir)) {
          continue;
        }
        try(final java.util.stream.Stream<Path> entries = Files.list(dir)) {
          needed += entries.filter(Files::isRegularFile).mapToLong(p -> {
            try {
              return Files.size(p);
            } catch(final IOException | RuntimeException e) {
              return 0;
            }
          }).sum();
        }
      }
    } catch(final IOException | RuntimeException e) {
      // Files.list/Files.size can throw SecurityException on some ROMs'
      // storage stacks (issue #54). Can't inventory ISOs — let the
      // unpacker report its own error.
      return;
    }
    if(needed <= 256L * 1024 * 1024) {
      return; // no ISOs found — the unpacker's own "insert discs" flow reports
    }

    final Path target = gameFilesDir();
    long free;
    try {
      free = new android.os.StatFs(target.toString()).getAvailableBytes();
    } catch(final RuntimeException e) {
      try {
        free = Files.getFileStore(target).getUsableSpace();
      } catch(final IOException | RuntimeException ex) {
        // SecurityException from getFileStore is what crashed on the Odin
        // M2 (issue #54) — a probe that can't probe must not be fatal.
        return;
      }
    }

    if(free < needed) {
      throw new legend.game.unpacker.UnpackerException(String.format(java.util.Locale.US,
        "Not enough free space — unpacking needs about %.1f GB but only %.1f GB is available. Free up space or pick a different game-data location in Settings.",
        needed / 1_073_741_824.0, free / 1_073_741_824.0));
    }
  }

  /**
   * Presence probes spread across the unpacked tree. files/ can lose content
   * AFTER the version marker is written — SD eject, failed migration, storage
   * cleaners, a copy that died partway — and upstream's loader swallows the
   * failure so the game later NPEs far from the cause (issue #53 crashed in
   * RetailSubmap because SUBMAP/NEWROOT.RDT was missing). "version" is last:
   * it is written only after every file, so its absence means the unpack
   * never finished.
   */
  private static final String[] GAME_FILE_SENTINELS = {
    "version",
    "SCUS_944.91",
    "SECT/DRGN0.BIN",
    "SECT/DRGN21.BIN",
    "OVL/TTLE.OV_",
    "OVL/SMAP.OV_",
    "SUBMAP/NEWROOT.RDT",
    "SUBMAP/savepoint/0",
  };

  /**
   * Probes the files/ tree for core content. On a miss the version marker is
   * removed (so the next Unpacker.unpack() rewrites the whole tree) and false
   * is returned.
   */
  public static boolean verifyGameFiles() {
    final Path files = gameFilesDir();
    for(final String sentinel : GAME_FILE_SENTINELS) {
      try {
        if(Files.exists(files.resolve(sentinel))) {
          continue;
        }
      } catch(final RuntimeException e) {
        // SecurityException/UncheckedIOException on the probe means the
        // volume can't be read at all — do NOT trigger a repair (that
        // would delete the version marker on a transient error and force
        // a full re-unpack). The unpacker reports the real failure.
        return true;
      }
      Log.w("SC-Env", "files/ tree is incomplete (missing " + sentinel + ") — flagging for re-unpack");
      try {
        Files.deleteIfExists(files.resolve("version"));
      } catch(final IOException | RuntimeException e) {
        Log.w("SC-Env", "Failed to clear unpack version marker", e);
      }
      return false;
    }
    return true;
  }

  /**
   * checkUnpackSpace + Unpacker.unpack() + integrity verification, staged via
   * a transform on GameEngine. If the tree fails verification the version
   * marker is cleared and unpack is retried once — a broken tree then throws
   * a user-readable UnpackerException instead of crashing later with a
   * null-field NPE in some random subsystem.
   */
  public static void unpackOrRepair() throws legend.game.unpacker.UnpackerException {
    checkUnpackSpace();
    legend.game.unpacker.Unpacker.unpack();
    for(int attempt = 0; attempt < 2 && !verifyGameFiles(); attempt++) {
      checkUnpackSpace();
      legend.game.unpacker.Unpacker.unpack();
    }
    if(!verifyGameFiles()) {
      throw new legend.game.unpacker.UnpackerException(
        "Game data is incomplete — the extracted files are missing content. Re-run disc setup to rebuild them.");
    }
  }

  /** Location the files/ tree should be moved from on this boot, or null. */
  @Nullable
  public static String pendingDataMigration() {
    return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .getString(PREF_DATA_MIGRATE_FROM, null);
  }

  public static void clearDataMigration() {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .remove(PREF_DATA_MIGRATE_FROM).apply();
  }

  /** Abort a pending move whose destination vanished (card pulled): put the
   *  location pref back to the source and drop the flag. */
  public static void revertDataMigration(final String from) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .putString(PREF_GAME_DATA_LOCATION, from)
      .remove(PREF_DATA_MIGRATE_FROM)
      .apply();
  }

  /** Root of a mounted removable (SD card) volume, or null when none exists. */
  @Nullable
  public static File sdCardRoot() {
    if(Build.VERSION.SDK_INT >= 30) {
      final android.os.storage.StorageManager sm = context.getSystemService(android.os.storage.StorageManager.class);
      if(sm != null) {
        for(final android.os.storage.StorageVolume volume : sm.getStorageVolumes()) {
          final File dir = volume.getDirectory();
          if(volume.isRemovable() && dir != null && Environment.MEDIA_MOUNTED.equals(volume.getState())) {
            return dir;
          }
        }
      }
      return null;
    }
    // Pre-API 30: secondary getExternalFilesDirs entries point at
    // /storage/<uuid>/Android/data/<pkg>/files — walk up to the volume root.
    final File[] dirs = context.getExternalFilesDirs(null);
    for(int i = 1; i < dirs.length; i++) {
      File root = dirs[i];
      for(int j = 0; root != null && j < 4; j++) {
        root = root.getParentFile();
      }
      if(root != null) {
        return root;
      }
    }
    return null;
  }

  /** Where SD saves go when enabled — public dir if the ROM allows writes
   *  to the volume root, otherwise the app-private dir on the SD (always
   *  writable, but deleted on uninstall). Null when no SD is mounted. */
  @Nullable
  public static File sdSavesTarget() {
    final File root = sdCardRoot();
    if(root != null) {
      final File dir = new File(root, "Severed Chains");
      if(isWritableDir(dir)) {
        return new File(dir, "Saves");
      }
    }
    final File appDir = sdAppFilesDir();
    return appDir != null ? new File(appDir, "Saves") : null;
  }

  /** FUSE-mounted external storage can report canWrite() while writes fail
   *  with EROFS — probe with a real file create instead. */
  private static boolean isWritableDir(final File dir) {
    if(!dir.isDirectory() && !dir.mkdirs()) {
      return false;
    }
    final File probe = new File(dir, ".sc-write-test");
    try {
      if(probe.createNewFile()) {
        //noinspection ResultOfMethodCallIgnored
        probe.delete();
        return true;
      }
      return false;
    } catch(final IOException e) {
      return false;
    }
  }

  /** App-private files dir on a removable volume — writable without grants. */
  @Nullable
  private static File sdAppFilesDir() {
    final File[] dirs = context.getExternalFilesDirs(null);
    for(int i = 1; i < dirs.length; i++) {
      final File dir = dirs[i];
      if(dir != null && Environment.isExternalStorageRemovable(dir)
          && Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(dir))) {
        return dir;
      }
    }
    return null;
  }

  /** The Saves/ folder on the SD card when the user opted in. */
  @Nullable
  public static File sdSavesDir() {
    return "sd".equals(saveLocation()) ? sdSavesTarget() : null;
  }

  /** Where picked disc images are staged: public ISOs/ when accessible. */
  public static File isoDestDir() {
    if(hasSharedStorageAccess()) {
      return PUBLIC_ISOS_DIR;
    }
    return new File(context.getFilesDir(), "isos");
  }

  /**
   * Bring the public folders in line before the engine starts:
   * - saves and disc images previously stored internally are moved into
   *   Saves/ and ISOs/ so everything lives in one user-visible place
   */
  public static void syncPublicStorage() {
    if(!hasSharedStorageAccess()) {
      return;
    }

    final File savesDir = savesPath().toFile();
    //noinspection ResultOfMethodCallIgnored
    savesDir.mkdirs();
    //noinspection ResultOfMethodCallIgnored
    PUBLIC_ISOS_DIR.mkdirs();

    moveContents(new File(context.getFilesDir(), "saves"), savesDir);
    moveContents(new File(context.getFilesDir(), "isos"), PUBLIC_ISOS_DIR);
  }

  /**
   * Move every entry of {@code src} into {@code dst}: rename where possible
   * (same filesystem — instant for large disc images), copy+delete
   * otherwise. Existing destination entries win: directories merge
   * recursively, conflicting files keep the internal copy.
   */
  private static void moveContents(final File src, final File dst) {
    final File[] children = src.listFiles();
    if(children == null) {
      return;
    }

    for(final File child : children) {
      final File dest = new File(dst, child.getName());
      if(dest.exists()) {
        if(child.isDirectory() && dest.isDirectory()) {
          moveContents(child, dest);
          //noinspection ResultOfMethodCallIgnored
          child.delete();
        }
        continue;
      }

      if(child.renameTo(dest)) {
        Log.i("SC-Env", "Moved " + child.getName() + " to " + dst.getName() + "/");
        continue;
      }

      if(copyTree(child, dest)) {
        deleteTree(child);
        Log.i("SC-Env", "Copied " + child.getName() + " to " + dst.getName() + "/");
      } else {
        Log.w("SC-Env", "Failed to move " + child + " to " + dst);
        //noinspection ResultOfMethodCallIgnored
        dest.delete();
      }
    }
  }

  private static boolean copyTree(final File src, final File dst) {
    if(src.isDirectory()) {
      //noinspection ResultOfMethodCallIgnored
      dst.mkdirs();
      final File[] children = src.listFiles();
      if(children != null) {
        for(final File child : children) {
          if(!copyTree(child, new File(dst, child.getName()))) {
            return false;
          }
        }
      }
      return true;
    }

    try(final InputStream in = new FileInputStream(src);
        final OutputStream out = new FileOutputStream(dst)) {
      in.transferTo(out);
      //noinspection ResultOfMethodCallIgnored
      dst.setLastModified(src.lastModified());
      return true;
    } catch(final IOException e) {
      return false;
    }
  }

  private static void deleteTree(final File file) {
    final File[] children = file.listFiles();
    if(children != null) {
      for(final File child : children) {
        deleteTree(child);
      }
    }
    //noinspection ResultOfMethodCallIgnored
    file.delete();
  }

  @Nullable
  public static String readAsset(final String name) {
    try(final InputStream in = context.getAssets().open(name)) {
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      in.transferTo(out);
      return out.toString("UTF-8");
    } catch(final IOException e) {
      return null;
    }
  }

  @Nullable
  public static byte[] readAssetBytes(final String name) {
    try(final InputStream in = context.getAssets().open(name)) {
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      in.transferTo(out);
      return out.toByteArray();
    } catch(final IOException e) {
      return null;
    }
  }
}
