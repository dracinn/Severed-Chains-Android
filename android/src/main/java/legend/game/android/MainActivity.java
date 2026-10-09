package legend.game.android;

import android.app.Activity;
import android.content.Intent;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.util.Log;
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

  private SurfaceView surfaceView;
  private TouchControlsView touchControls;
  private SetupView setupView;
  private boolean gameUi;
  private int mousePointerId = -1;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Keep the screen on and visible above the keyguard while running
    this.setShowWhenLocked(true);
    this.setTurnScreenOn(true);
    this.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    AndroidEnv.init(this);

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

    final ImeTargetView imeView = new ImeTargetView(this);
    AndroidEnv.setImeView(imeView);

    final FrameLayout root = new FrameLayout(this);
    root.addView(this.surfaceView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    root.addView(imeView, new FrameLayout.LayoutParams(1, 1));
    root.addView(this.touchControls, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    this.setContentView(root);

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

    if(this.touchControls.handleTouch(event)) {
      return true;
    }

    this.handleMouseTouch(event);
    return true;
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

  /** Extract bundled assets (gfx/) to the working directory once per version. */
  private void extractAssets() throws IOException {
    final long version;
    try {
      version = this.getPackageManager().getPackageInfo(this.getPackageName(), 0).getLongVersionCode();
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
    this.extractAssetDir("log4j2.xml");
    marker.createNewFile();
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
