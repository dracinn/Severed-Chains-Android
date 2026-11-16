package legend.game.android;

import legend.core.renderer.Resolution;

/**
 * Dynamic resolution scaling. Adjusts the engine's internal render
 * resolution between discrete rungs (the upstream {@link Resolution}
 * ladder, capped at the user's configured maximum) based on measured
 * frame pacing — swap-interval backpressure makes a late tick a real
 * GPU-bound signal, not just CPU jitter.
 *
 * Policy: evaluated every ~2 s of frames — &gt;25 % missed frames steps
 * down one rung; three consecutive comfortable windows steps up one rung.
 * A 3 s cooldown follows every change because a step reallocates all
 * render buffers (a visible hitch — hysteresis keeps it rare).
 */
public final class DynamicResolution {
  private static final String PREF = "dynamic_resolution";

  /** Frame interval that counts as a miss against a 60 Hz budget. */
  private static final long MISS_NANOS = 17_500_000L;
  /** Interval that counts as comfortable headroom. */
  private static final long HEADROOM_NANOS = 14_500_000L;
  /** Frames per evaluation window (~2 s at 60 Hz). */
  private static final int EVAL_FRAMES = 120;
  /** Miss ratio that triggers a step down. */
  private static final double MISS_STEP_DOWN = 0.25;
  /** Consecutive comfortable windows before stepping up. */
  private static final int UP_WINDOWS = 3;
  /** Minimum time between rung changes — realloc hitches must stay rare. */
  private static final long COOLDOWN_NANOS = 3_000_000_000L;
  /** Frames ignored after a ladder rebuild — boot/loading is not signal. */
  private static final int WARMUP_FRAMES = 90;

  private static Resolution[] rungs;
  private static Resolution cap;
  private static int capHeight;
  private static int warmup;
  private static int index;
  private static long lastFrameNanos;
  private static long lastStepNanos;
  private static int frames;
  private static int misses;
  private static int headroomFrames;
  private static int cleanWindows;
  private static double lastMissRatio;
  private static boolean lastStepWasDown;
  private static boolean inelastic;

  private DynamicResolution() { }

  public static boolean enabled() {
    return AndroidEnv.prefs().getBoolean(PREF, false);
  }

  public static void setEnabled(final boolean enabled) {
    AndroidEnv.prefs().edit().putBoolean(PREF, enabled).apply();
  }

  /**
   * Resolution the engine should actually allocate for — the configured
   * value when disabled, otherwise the current dynamic rung. Called by the
   * staged transform on {@code RenderEngine.onResize}; {@code targetHeight}
   * is the window height so NATIVE can be placed at its real ladder
   * position rather than the top of the enum.
   */
  public static Resolution effective(final Resolution configured, final int targetHeight) {
    rebuildIfCapChanged(configured, targetHeight);
    if(!enabled() || rungs.length < 2) {
      return configured;
    }
    return rungs[index];
  }

  /**
   * Called once per rendered frame from {@code AndroidWindow.tick}. Returns
   * true when the rung just changed — the caller must then re-fire the
   * window-resize event so the engine re-allocates at the new rung.
   */
  public static boolean pollFrame() {
    final long now = System.nanoTime();
    if(lastFrameNanos == 0) {
      lastFrameNanos = now;
      return false;
    }
    final long interval = now - lastFrameNanos;
    lastFrameNanos = now;

    if(!enabled() || rungs == null || rungs.length < 2 || now - lastStepNanos < COOLDOWN_NANOS) {
      return false;
    }

    if(warmup > 0) {
      warmup--;
      return false;
    }

    // Ignore pathological gaps (backgrounding, loading stalls) — they're
    // not render-load signal.
    if(interval < 250_000_000L) {
      frames++;
      if(interval > MISS_NANOS) {
        misses++;
      }
      if(interval < HEADROOM_NANOS) {
        headroomFrames++;
      }
    }

    if(frames < EVAL_FRAMES) {
      return false;
    }

    final double missRatio = (double)misses / frames;
    final double headroomRatio = (double)headroomFrames / frames;
    frames = misses = headroomFrames = 0;

    // Elasticity check: if the last step down didn't materially improve the
    // miss ratio, the bottleneck isn't resolution (CPU/driver-bound) —
    // restore the previous rung and stop stepping down until real headroom
    // appears, rather than needlessly rendering at 240p for 0 fps gained.
    if(lastStepWasDown && missRatio >= lastMissRatio - 0.05) {
      inelastic = true;
      final int prev = index;
      index = Math.min(index + 1, rungs.length - 1);
      lastStepWasDown = false;
      lastStepNanos = now;
      if(index != prev) {
        android.util.Log.i("SC-DynRes", "Step down ineffective — restoring " + rungs[index]);
        return true;
      }
      return false;
    }

    if(missRatio > MISS_STEP_DOWN && index > 0 && !inelastic) {
      cleanWindows = 0;
      lastMissRatio = missRatio;
      lastStepWasDown = true;
      return stepTo(index - 1, now);
    }
    if(headroomRatio > 0.95) {
      inelastic = false; // real headroom — resolution matters again
      if(++cleanWindows >= UP_WINDOWS && index < rungs.length - 1) {
        cleanWindows = 0;
        lastStepWasDown = false;
        return stepTo(index + 1, now);
      }
    } else {
      cleanWindows = 0;
    }
    return false;
  }

  private static boolean stepTo(final int newIndex, final long now) {
    index = newIndex;
    lastStepNanos = now;
    android.util.Log.i("SC-DynRes", "Resolution rung -> " + rungs[index]);
    return true;
  }

  /**
   * Build the rung ladder. For a fixed cap it's every rung at or below
   * the cap. For NATIVE it's every rung at or below the window height,
   * with NATIVE on top — rungs above native resolution only ever cost
   * fill-rate, so they're never offered as a step. A single-rung ladder
   * disables scaling implicitly.
   */
  private static void rebuildIfCapChanged(final Resolution configured, final int targetHeight) {
    if(rungs != null && cap == configured && capHeight == targetHeight) {
      return;
    }
    cap = configured;
    capHeight = targetHeight;
    final java.util.List<Resolution> list = new java.util.ArrayList<>();
    if(configured == Resolution.NATIVE) {
      for(final Resolution r : Resolution.values()) {
        if(r != Resolution.NATIVE && r.verticalResolution <= targetHeight) {
          list.add(r);
        }
      }
      list.add(Resolution.NATIVE);
    } else {
      for(final Resolution r : Resolution.values()) {
        if(r != Resolution.NATIVE && r.verticalResolution <= configured.verticalResolution) {
          list.add(r);
        }
      }
    }
    rungs = list.toArray(new Resolution[0]);
    index = rungs.length - 1;
    frames = misses = headroomFrames = cleanWindows = 0;
    warmup = WARMUP_FRAMES;
    lastFrameNanos = lastStepNanos = 0;
    lastMissRatio = 0;
    lastStepWasDown = inelastic = false;
  }
}
