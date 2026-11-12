package legend.game.android;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import legend.core.platform.AndroidInput;
import org.lwjgl.openal.AL10;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;

/**
 * Entry point. Sets up the app working directory (gfx extraction + isos/),
 * owns the SurfaceView whose Surface backs the EGL window, and starts the
 * upstream engine on a dedicated game thread (which owns the GL context and
 * runs the upstream render loop).
 */
public final class MainActivity extends Activity {
  private static final String TAG = "SC-Main";
  private static final int REQ_LOG_STORAGE = 1;

  private SurfaceView surfaceView;
  private FrameLayout gameRoot;
  private TouchControlsView touchControls;
  private PerfOverlayView perfOverlay;
  private SetupView setupView;
  private boolean gameUi;
  private int mousePointerId = -1;
  private final int[] viewLoc = new int[2];

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Keep the screen on and visible above the keyguard while running
    if(android.os.Build.VERSION.SDK_INT >= 27) {
      this.setShowWhenLocked(true);
      this.setTurnScreenOn(true);
    } else {
      this.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        | android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }
    this.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    // Extend content under the hidden bars — but keep the display cutout
    // letterbox at the top (default mode, no draw under the notch). Nav and
    // status bars are made transparent so their hidden surfaces don't
    // composite black over our content.
    if(android.os.Build.VERSION.SDK_INT >= 30) {
      this.getWindow().setDecorFitsSystemWindows(false);
    }
    // decorFits(false) alone is ignored in this compat-letterboxed window —
    // the legacy layout flags drive the same inset bypass
    this.getWindow().getDecorView().setSystemUiVisibility(
      android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    // Full-bleed window incl. the cutout; the portrait content root is
    // pushed below the status area in applySurfaceLayout() instead.
    // FLAG_LAYOUT_NO_LIMITS is required on this device (moto g play) —
    // without it all rendering is confined to appBounds and the bottom
    // nav-inset region (84px) can never be painted, even by surfaces.
    this.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
      | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
    if(android.os.Build.VERSION.SDK_INT >= 28) {
      final android.view.WindowManager.LayoutParams lp = this.getWindow().getAttributes();
      lp.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
      this.getWindow().setAttributes(lp);
    }
    this.getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
    this.getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
    if(android.os.Build.VERSION.SDK_INT >= 29) {
      this.getWindow().setStatusBarContrastEnforced(false);
      this.getWindow().setNavigationBarContrastEnforced(false);
    }

    AndroidEnv.init(this);
    AndroidEnv.setActivity(this);

    // Shared logs in Documents/Severed Chains/logs: crash logs in every build,
    // verbose engine logs in debug builds. API 26-28 need WRITE_EXTERNAL_STORAGE
    // at runtime; MediaStore covers 29+. If the user denies, logging is a no-op.
    if(GameLog.needsPermission(this)) {
      this.requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_LOG_STORAGE);
    }
    GameLog.init(this);
    GameLog.installCrashHandler();
    // Escalates the persisted GL fallback level if the previous run died
    // during GL init (native driver crashes never reach the Java handler).
    GlesCompat.init(this);

    // Test hook: --ez force_es31 true exercises the GS-less ES 3.1 render
    // path on hardware that supports ES 3.2 (e.g. for Mali testing).
    GlesCompat.forceEs31 = this.getIntent().getBooleanExtra("force_es31", false);

    // Upstream log4j2.xml uses fileName="debug.log", staged with the Android
    // rewrite to ${sys:legend.baseDir}/debug.log (relative paths resolve
    // against /, which is read-only on Android).
    System.setProperty("legend.baseDir", this.getFilesDir().getAbsolutePath());

    // Upstream resolves everything relative to the working directory
    // (files/, isos/, saves/, gfx/, config.dcnf). Android locks user.dir to /,
    // so make the real process cwd the app dir. user.dir must be set BEFORE
    // the default filesystem initializes: it bakes user.dir in at
    // construction and uses it for toAbsolutePath() (which Files APIs like
    // createDirectories call internally). Set early, relative paths resolve
    // via baked user.dir; if the FS was already initialized in kernel-cwd
    // mode, the chdir below keeps them resolving through the kernel. Either
    // way they land in the app dir.
    System.setProperty("user.dir", this.getFilesDir().getAbsolutePath());
    FileSystems.getDefault();
    Path.of("init").getFileSystem();
    try {
      NativeOs.chdir(this.getFilesDir().getAbsolutePath());
    } catch(final IOException e) {
      throw new RuntimeException("Failed to set working directory", e);
    }
    JdkCompat.setBaseDir(this.getFilesDir().toPath());



    try {
      this.extractAssets();
    } catch(final IOException e) {
      Log.e(TAG, "Failed to extract assets", e);
    }

    new File(this.getFilesDir(), "isos").mkdirs();
    new File(this.getFilesDir(), "saves").mkdirs();

    // Soft keyboard must never resize/pan the surface: a resize would churn
    // the EGL surface handshake every time the IME opens.
    this.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);

    // First run (or a rerun after an incomplete unpack): stage disc images
    // before the engine starts. -e force_setup 1 reopens the screen to
    // manage discs on an unpacked install.
    if(this.isUnpacked() && !this.getIntent().getBooleanExtra("force_setup", false)) {
      this.showGame();
    } else {
      this.showSetup();
    }

    // Track physical gamepad hotplug
    final InputManager inputManager = (InputManager)this.getSystemService(INPUT_SERVICE);
    inputManager.registerInputDeviceListener(new InputManager.InputDeviceListener() {
      @Override
      public void onInputDeviceAdded(final int deviceId) {
        if(AndroidInput.isGamepad(InputDevice.getDevice(deviceId))) {
          AndroidInput.gamepadAdded(deviceId);
        }
      }

      @Override
      public void onInputDeviceRemoved(final int deviceId) {
        AndroidInput.gamepadRemoved(deviceId);
      }

      @Override
      public void onInputDeviceChanged(final int deviceId) {
      }
    }, null);
  }

  private boolean isUnpacked() {
    return new File(this.getFilesDir(), "files/version").isFile();
  }

  private void showSetup() {
    this.setupView = new SetupView(this, new File(this.getFilesDir(), "isos"), this::showGame);
    this.setContentView(this.setupView);
  }

  private void showGame() {
    this.setupView = null;
    this.gameUi = true;

    this.surfaceView = new SurfaceView(this);
    this.touchControls = new TouchControlsView(this);
    this.perfOverlay = new PerfOverlayView(this);

    final ImeTargetView imeView = new ImeTargetView(this);
    AndroidEnv.setImeView(imeView);

    final FrameLayout root = new FrameLayout(this);
    this.gameRoot = root;
    // The decor's theme background only paints the appBounds region — set
    // the window background itself so the band covers the nav-bar area too
    this.getWindow().setBackgroundDrawable(new android.graphics.drawable.GradientDrawable(
      android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
      new int[] {0xff151210, 0xff302a24}));
    root.addView(this.surfaceView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    root.addView(imeView, new FrameLayout.LayoutParams(1, 1));
    root.addView(this.touchControls, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    root.addView(this.perfOverlay, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    this.setContentView(root);
    this.applySurfaceLayout();

    this.surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
      @Override
      public void surfaceCreated(final SurfaceHolder holder) {
        Log.i(TAG, "surfaceCreated: valid=" + holder.getSurface().isValid());
      }

      @Override
      public void surfaceChanged(final SurfaceHolder holder, final int format, final int width, final int height) {
        Log.i(TAG, "surfaceChanged: " + width + "x" + height + " valid=" + holder.getSurface().isValid());
        AndroidEnv.setSurface(holder.getSurface(), width, height);
        AndroidEnv.startGameOnce();
      }

      @Override
      public void surfaceDestroyed(final SurfaceHolder holder) {
        Log.i(TAG, "surfaceDestroyed");
        // Blocks until the game thread releases the EGL surface (or ~2s)
        AndroidEnv.surfaceDestroyed();
      }
    });
  }

  /** In portrait the game surface is a 4:3 band across the top of the screen;
   *  the touch controls own the space below it. Landscape keeps fullscreen. */
  private void applySurfaceLayout() {
    if(this.surfaceView == null) {
      return;
    }
    final boolean portrait = this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    if(portrait) {
      final int widthPx = this.getResources().getDisplayMetrics().widthPixels;
      this.surfaceView.setLayoutParams(new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, Math.round(widthPx * 0.75f), Gravity.TOP));
    } else {
      this.surfaceView.setLayoutParams(new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    // Portrait keeps content below the status bar/cutout while still filling
    // the nav area — a plain margin on the full-bleed root does both.
    if(this.gameRoot != null && this.gameRoot.getLayoutParams() instanceof FrameLayout.LayoutParams rlp) {
      rlp.topMargin = portrait ? this.statusBarHeightPx() : 0;
      this.gameRoot.setLayoutParams(rlp);
    }
  }

  private int statusBarHeightPx() {
    final int id = this.getResources().getIdentifier("status_bar_height", "dimen", "android");
    return id != 0 ? this.getResources().getDimensionPixelSize(id) : Math.round(48 * this.getResources().getDisplayMetrics().density);
  }


  @Override
  public void onConfigurationChanged(final Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    this.applySurfaceLayout();
  }

  // ------------------------------------------------------------------
  // Input routing -> AndroidInput queue (consumed by tickInput on the game thread)
  // ------------------------------------------------------------------

  @Override
  public boolean dispatchKeyEvent(final KeyEvent event) {
    final int keyCode = event.getKeyCode();
    if(keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
      return super.dispatchKeyEvent(event);
    }

    // While the setup view is showing, keys/d-pad belong to the UI toolkit
    if(!this.gameUi) {
      return super.dispatchKeyEvent(event);
    }

    if(AndroidInput.handleKeyEvent(event)) {
      return true;
    }

    return super.dispatchKeyEvent(event);
  }

  @Override
  public boolean dispatchGenericMotionEvent(final MotionEvent event) {
    if(!this.gameUi) {
      return super.dispatchGenericMotionEvent(event);
    }

    if(AndroidInput.handleGenericMotionEvent(event)) {
      return true;
    }

    return super.dispatchGenericMotionEvent(event);
  }

  @Override
  public boolean dispatchTouchEvent(final MotionEvent event) {
    if(!this.gameUi) {
      return super.dispatchTouchEvent(event);
    }

    // Events arrive in window coordinates but hit-testing and the mouse path
    // both expect view-local space — translate for the portrait top margin
    this.touchControls.getLocationInWindow(this.viewLoc);
    event.offsetLocation(-this.viewLoc[0], -this.viewLoc[1]);

    try {
      if(this.touchControls.handleTouch(event)) {
        return true;
      }

      this.handleMouseTouch(event);
      return true;
    } finally {
      event.offsetLocation(this.viewLoc[0], this.viewLoc[1]);
    }
  }

  /** Single-finger touch on the game surface acts as mouse move + button 1. */
  private void handleMouseTouch(final MotionEvent event) {
    switch(event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN -> {
        this.mousePointerId = event.getPointerId(0);
        AndroidInput.mouseMove(event.getX(), event.getY());
        AndroidInput.mouseButton(1, true);
      }

      case MotionEvent.ACTION_MOVE -> {
        if(this.mousePointerId != -1) {
          final int idx = event.findPointerIndex(this.mousePointerId);
          if(idx >= 0) {
            AndroidInput.mouseMove(event.getX(idx), event.getY(idx));
          }
        }
      }

      case MotionEvent.ACTION_UP -> {
        if(this.mousePointerId != -1) {
          final int idx = event.findPointerIndex(this.mousePointerId);
          if(idx >= 0) {
            AndroidInput.mouseMove(event.getX(idx), event.getY(idx));
          }
          AndroidInput.mouseButton(1, false);
          this.mousePointerId = -1;
        }
      }

      case MotionEvent.ACTION_POINTER_UP -> {
        if(event.getPointerId(event.getActionIndex()) == this.mousePointerId) {
          AndroidInput.mouseButton(1, false);
          this.mousePointerId = -1;
        }
      }

      case MotionEvent.ACTION_CANCEL -> {
        if(this.mousePointerId != -1) {
          AndroidInput.mouseButton(1, false);
          this.mousePointerId = -1;
        }
      }
    }
  }

  @Override
  public void onWindowFocusChanged(final boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    AndroidInput.focus(hasFocus);
    if(hasFocus && android.os.Build.VERSION.SDK_INT >= 30 && this.getWindow().getInsetsController() != null) {
      this.getWindow().getInsetsController().setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
      this.getWindow().getInsetsController().hide(android.view.WindowInsets.Type.systemBars());
      // hide() rewrites the legacy visibility flags — re-assert the layout
      // flags so the decor still fills the stable (full) frame
      this.getWindow().getDecorView().setSystemUiVisibility(
        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
          | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
          | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    } else if(hasFocus && android.os.Build.VERSION.SDK_INT < 30) {
      // No WindowInsetsController before API 30 — hide via legacy flags
      this.getWindow().getDecorView().setSystemUiVisibility(
        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
          | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
          | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
          | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
          | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
          | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    AL10.pauseAll();
  }

  @Override
  protected void onResume() {
    super.onResume();
    AL10.resumeAll();
  }

  @Override
  protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
    if(this.setupView != null) {
      this.setupView.onPickerResult(requestCode, resultCode, data);
    }
  }

  /** Extract bundled assets (gfx/) to the working directory once per install. */
  private void extractAssets() throws IOException {
    final String version;
    try {
      // Same-versionCode updates (dispatch/dev builds) still change
      // lastUpdateTime, so bundled assets can never go stale.
      final android.content.pm.PackageInfo info = this.getPackageManager().getPackageInfo(this.getPackageName(), 0);
      final long code = android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
      version = code + "-" + info.lastUpdateTime;
    } catch(final android.content.pm.PackageManager.NameNotFoundException e) {
      throw new IOException(e);
    }

    final File marker = new File(this.getFilesDir(), ".assets-" + version);
    if(marker.exists()) {
      return;
    }

    this.extractAssetDir("gfx");
    this.extractAssetDir("lang");
    this.extractAssetDir("patches");
    // mods/ mirrors APK assets exactly; wipe it first so files from mods
    // removed in a newer build don't linger on upgraded installs.
    deleteRecursively(new File(this.getFilesDir(), "mods"));
    this.extractAssetDir("mods");
    this.extractAssetDir("log4j2.xml");
    marker.createNewFile();
  }

  private static void deleteRecursively(final File file) {
    final File[] children = file.listFiles();
    if(children != null) {
      for(final File child : children) {
        deleteRecursively(child);
      }
    }
    //noinspection ResultOfMethodCallIgnored
    file.delete();
  }

  private void extractAssetDir(final String path) throws IOException {
    final String[] children = this.getAssets().list(path);
    if(children == null) {
      return;
    }
    if(children.length == 0) {
      // file
      final File out = new File(this.getFilesDir(), path);
      out.getParentFile().mkdirs();
      try(final InputStream in = this.getAssets().open(path); final OutputStream os = new FileOutputStream(out)) {
        in.transferTo(os);
      }
      return;
    }
    for(final String child : children) {
      this.extractAssetDir(path + "/" + child);
    }
  }
}
