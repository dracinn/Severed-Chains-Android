package legend.game.android;

import android.content.Context;
import android.util.Log;
import android.view.Surface;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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
        legend.game.Main.main(new String[0]);
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

  @Nullable
  public static String readAsset(final String name) {
    try(final InputStream in = context.getAssets().open(name)) {
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      in.transferTo(out);
      return out.toString(StandardCharsets.UTF_8);
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
