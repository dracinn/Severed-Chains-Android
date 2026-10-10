package legend.core.platform;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.util.Log;
import android.view.Surface;
import legend.core.platform.input.InputClass;
import legend.core.platform.input.InputMod;
import legend.core.renderer.RenderApi;
import legend.core.renderer.opengles.GlesApi;
import legend.game.android.AndroidEnv;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

/**
 * Android Window: wraps the SurfaceView's Surface plus a self-managed EGL
 * context (created on the game thread which owns the upstream render loop).
 * Mirrors SdlWindow's contract: tick() = swapBuffers + onDraw.
 */
public class AndroidWindow extends Window {
  private static final String TAG = "SC-Window";

  private final AndroidPlatformManager manager;
  private final RenderApi renderApi;
  private final Action render;

  private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
  private EGLContext context = EGL14.EGL_NO_CONTEXT;
  private EGLConfig config;
  private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
  private EGLSurface pbuffer = EGL14.EGL_NO_SURFACE;
  private Surface surface;
  private long boundGeneration = -1;
  private int width;
  private int height;

  private boolean shouldClose;
  boolean hasFocus = true;

  /** Currently-held mods */
  final Set<InputMod> mods = EnumSet.noneOf(InputMod.class);
  /** The last way the user interacted with this window */
  InputClass currentInputClass = InputClass.KEYBOARD;

  public AndroidWindow(final AndroidPlatformManager manager) {
    this.manager = manager;

    // The Surface can be destroyed between the Activity callback and us
    // reaching eglCreateWindowSurface; keep retrying with the next valid one.
    while(true) {
      this.surface = AndroidEnv.awaitSurface();
      try {
        this.createEglContext();
        break;
      } catch(final RuntimeException e) {
        this.teardownEgl();
        Log.w(TAG, "Surface went stale before eglCreateWindowSurface; waiting for the next one");
      }
    }

    final AndroidEnv.Pending pending = AndroidEnv.peekPendingSurface();
    this.boundGeneration = pending != null ? pending.generation : -1;

    this.renderApi = new GlesApi();

    this.render = this.manager.addAction(new Action(this::tick, 60));
  }

  private void createEglContext() {
    this.display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
    if(this.display == EGL14.EGL_NO_DISPLAY) {
      throw new RuntimeException("eglGetDisplay failed");
    }

    final int[] version = new int[2];
    if(!EGL14.eglInitialize(this.display, version, 0, version, 1)) {
      throw new RuntimeException("eglInitialize failed: " + EGL14.eglGetError());
    }
    Log.i(TAG, "EGL " + version[0] + "." + version[1]);

    final int[] configAttribs = {
      EGL14.EGL_RENDERABLE_TYPE, 0x40, // EGL_OPENGL_ES3_BIT_KHR
      EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT | EGL14.EGL_PBUFFER_BIT,
      EGL14.EGL_RED_SIZE, 8,
      EGL14.EGL_GREEN_SIZE, 8,
      EGL14.EGL_BLUE_SIZE, 8,
      EGL14.EGL_ALPHA_SIZE, 8,
      EGL14.EGL_DEPTH_SIZE, 24,
      EGL14.EGL_STENCIL_SIZE, 8,
      EGL14.EGL_NONE,
    };

    final EGLConfig[] configs = new EGLConfig[1];
    final int[] numConfigs = new int[1];
    if(!EGL14.eglChooseConfig(this.display, configAttribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
      throw new RuntimeException("eglChooseConfig failed: " + EGL14.eglGetError());
    }
    this.config = configs[0];

    // Request ES 3.2; fall back to a generic ES 3.x context. Debug builds ask
    // for a debug context so upstream's GL debug callback (GlesApi.init) runs.
    final boolean debuggable = (AndroidEnv.context().getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    final int[] ctx32Attribs = debuggable
      ? new int[] {
        EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
        0x30FB /* EGL_CONTEXT_MINOR_VERSION */, 2,
        0x30FC /* EGL_CONTEXT_FLAGS_KHR */, 0x1 /* EGL_CONTEXT_OPENGL_DEBUG_BIT_KHR */,
        EGL14.EGL_NONE,
      }
      : new int[] {
        EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
        0x30FB /* EGL_CONTEXT_MINOR_VERSION */, 2,
        EGL14.EGL_NONE,
      };
    this.context = EGL14.eglCreateContext(this.display, this.config, EGL14.EGL_NO_CONTEXT, ctx32Attribs, 0);
    if(this.context == EGL14.EGL_NO_CONTEXT) {
      this.context = EGL14.eglCreateContext(this.display, this.config, EGL14.EGL_NO_CONTEXT, new int[] {
        EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
        0x30FB /* EGL_CONTEXT_MINOR_VERSION */, 2,
        EGL14.EGL_NONE,
      }, 0);
    }
    if(this.context == EGL14.EGL_NO_CONTEXT) {
      this.context = EGL14.eglCreateContext(this.display, this.config, EGL14.EGL_NO_CONTEXT, new int[] {
        EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
        EGL14.EGL_NONE,
      }, 0);
    }
    if(this.context == EGL14.EGL_NO_CONTEXT) {
      throw new RuntimeException("eglCreateContext failed: " + EGL14.eglGetError());
    }

    // Stand-in surface so the context stays valid while no window exists
    this.pbuffer = EGL14.eglCreatePbufferSurface(this.display, this.config, new int[] {
      EGL14.EGL_WIDTH, 1,
      EGL14.EGL_HEIGHT, 1,
      EGL14.EGL_NONE,
    }, 0);
    if(this.pbuffer == EGL14.EGL_NO_SURFACE) {
      throw new RuntimeException("eglCreatePbufferSurface failed: " + EGL14.eglGetError());
    }

    this.createWindowSurface(this.surface, AndroidEnv.surfaceWidth(), AndroidEnv.surfaceHeight());

    if(!EGL14.eglMakeCurrent(this.display, this.eglSurface, this.eglSurface, this.context)) {
      throw new RuntimeException("eglMakeCurrent failed: " + EGL14.eglGetError());
    }

    // ES feature level (e.g. Mali Midgard tops out at ES 3.1 - no geometry
    // shaders); GlesCompat drives shader variants and mesh adaptation.
    legend.game.android.GlesCompat.detect(android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VERSION));

    EGL14.eglSwapInterval(this.display, 0);
  }

  /** (Re)create the window EGL surface for the given Android surface. */
  private void createWindowSurface(final Surface surface, final int w, final int h) {
    // Unbind before destroying: destroying a still-current EGLSurface defers
    // the destroy, leaving the native window connected so a re-create on the
    // same Surface fails with EGL_BAD_ALLOC.
    this.releaseWindowSurface();

    this.surface = surface;
    this.eglSurface = EGL14.eglCreateWindowSurface(this.display, this.config, surface, new int[] {EGL14.EGL_NONE}, 0);
    if(this.eglSurface == EGL14.EGL_NO_SURFACE) {
      // Leave the context current on the pbuffer so GL calls stay valid
      EGL14.eglMakeCurrent(this.display, this.pbuffer, this.pbuffer, this.context);
      throw new RuntimeException("eglCreateWindowSurface failed: " + EGL14.eglGetError());
    }

    if(!EGL14.eglMakeCurrent(this.display, this.eglSurface, this.eglSurface, this.context)) {
      throw new RuntimeException("eglMakeCurrent failed: " + EGL14.eglGetError());
    }

    if(w != this.width || h != this.height) {
      this.width = w;
      this.height = h;
      this.events().onResize(w, h);
    }
  }

  /** Destroy the window EGL surface, keeping the context alive on the pbuffer. */
  private void releaseWindowSurface() {
    if(this.eglSurface != EGL14.EGL_NO_SURFACE) {
      EGL14.eglMakeCurrent(this.display, this.pbuffer, this.pbuffer, this.context);
      EGL14.eglDestroySurface(this.display, this.eglSurface);
      this.eglSurface = EGL14.EGL_NO_SURFACE;
      this.boundGeneration = -1;
      Log.i(TAG, "Released window EGL surface; context parked on pbuffer");
    }
  }

  @Override
  public RenderApi getRenderApi() {
    return this.renderApi;
  }

  @Override
  protected void destroy() {
    this.manager.removeAction(this.render);
    this.events().onClose();

    this.teardownEgl();
  }

  private void teardownEgl() {
    if(this.display != EGL14.EGL_NO_DISPLAY) {
      EGL14.eglMakeCurrent(this.display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
      if(this.eglSurface != EGL14.EGL_NO_SURFACE) {
        EGL14.eglDestroySurface(this.display, this.eglSurface);
        this.eglSurface = EGL14.EGL_NO_SURFACE;
      }
      if(this.pbuffer != EGL14.EGL_NO_SURFACE) {
        EGL14.eglDestroySurface(this.display, this.pbuffer);
        this.pbuffer = EGL14.EGL_NO_SURFACE;
      }
      if(this.context != EGL14.EGL_NO_CONTEXT) {
        EGL14.eglDestroyContext(this.display, this.context);
        this.context = EGL14.EGL_NO_CONTEXT;
      }
      EGL14.eglTerminate(this.display);
      this.display = EGL14.EGL_NO_DISPLAY;
    }
  }

  @Override
  protected boolean shouldClose() {
    return this.shouldClose;
  }

  @Override
  public void show() {
    this.events().onResize(AndroidEnv.surfaceWidth(), AndroidEnv.surfaceHeight());
  }

  @Override
  public void close() {
    this.shouldClose = true;
  }

  @Override
  public void updateMonitor() {
  }

  @Override
  public void makeFullscreen() {
    // Android is always fullscreen
  }

  @Override
  public void makeWindowed() {
  }

  @Override
  public void centerWindow() {
  }

  @Override
  public void setTitle(final String title) {
  }

  @Override
  public int getWidth() {
    return AndroidEnv.surfaceWidth();
  }

  @Override
  public int getHeight() {
    return AndroidEnv.surfaceHeight();
  }

  @Override
  public boolean hasFocus() {
    return this.hasFocus;
  }

  @Override
  public InputClass getInputClass() {
    return this.currentInputClass;
  }

  @Override
  public void startTextInput() {
    AndroidEnv.showIme();
  }

  @Override
  public void stopTextInput() {
    AndroidEnv.hideIme();
  }

  @Override
  public void disableCursor() {
  }

  @Override
  public void showCursor() {
  }

  @Override
  public void hideCursor() {
  }

  @Override
  public void useNormalCursor() {
  }

  @Override
  public void usePointerCursor() {
  }

  @Override
  public void setWindowIcon(final Path path) {
  }

  @Override
  public void setFpsLimit(final int limit) {
    this.render.setExpectedFps(limit);
  }

  @Override
  public int getFpsLimit() {
    return this.render.getExpectedFps();
  }

  /**
   * Upstream frame contract: swap, then fire the draw event.
   * Also owns the surface-loss handshake: a destroyed surface is released
   * (the context parks on the pbuffer) and a newly-published surface is
   * bound. With no window surface the frame is skipped entirely, which
   * effectively pauses the game while backgrounded.
   */
  private void tick() {
    if(AndroidEnv.isReleaseRequested()) {
      this.releaseWindowSurface();
      AndroidEnv.ackRelease();
    }

    final AndroidEnv.Pending pending = AndroidEnv.peekPendingSurface();
    if(pending != null && pending.generation != this.boundGeneration && pending.surface.isValid()) {
      try {
        this.createWindowSurface(pending.surface, pending.width, pending.height);
        this.boundGeneration = pending.generation;
        Log.i(TAG, "Bound window surface gen " + pending.generation + " " + pending.width + "x" + pending.height);
      } catch(final RuntimeException e) {
        Log.w(TAG, "Pending surface unusable", e);
      }
    }

    if(this.eglSurface == EGL14.EGL_NO_SURFACE) {
      return;
    }

    EGL14.eglSwapBuffers(this.display, this.eglSurface);
    this.events().onDraw();
    this.manager.clearPressed();
  }
}
