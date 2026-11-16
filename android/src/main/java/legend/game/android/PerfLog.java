package legend.game.android;

import android.util.Log;

import java.util.Arrays;

/**
 * Frame-time stats for hunting in-game hitches. Called once per rendered
 * frame with the frame-to-frame delta the engine already measures. Logs a
 * warning on slow frames and a p50/p95/max summary every ~10s of wall time.
 */
public final class PerfLog {
  private static final String TAG = "PerfLog";
  private static final int RING = 240;
  private static final long WINDOW_NS = 10_000_000_000L;

  private static final long[] ring = new long[RING];
  private static int ringIndex;
  private static int ringCount;
  private static long frames;
  private static long sumNs;
  private static long maxNs;
  private static int over33;
  private static int over50;
  private static long windowStart;

  // Phase split: how much of the frame is eglSwapBuffers (queue/vsync
  // backpressure — GPU-bound signal) vs the engine's draw+submit work.
  private static long phaseSwapNs;
  private static long phaseDrawNs;
  private static long phaseMaxSwapNs;

  // Pacer sleep accuracy: requested nanos vs actual — surfaces sleep
  // overshoot, which is invisible in per-frame work times.
  private static long sleepReqNs;
  private static long sleepActNs;
  private static long sleepSamples;

  /** Pacer sleep sample from JdkCompat.sleepNanos (game thread). */
  public static void sleepSample(final long requestedNs, final long actualNs) {
    sleepReqNs += requestedNs;
    sleepActNs += actualNs;
    sleepSamples++;
  }

  // Loop-level accounting: iterations vs render frames tells whether the
  // render Action fires every loop pass or skips (phase-locked pacer).
  public static long loopIterStart;
  private static long loopIters;
  private static long loopBusyNs;

  /** Called once per PlatformManager.run iteration with busy time inside action ticks. */
  public static void loopSample(final long busyNs) {
    loopIters++;
    loopBusyNs += busyNs;
  }

  private PerfLog() { }

  /** Per-frame phase costs measured in AndroidWindow.tick. */
  public static void phase(final long swapNs, final long drawNs) {
    phaseSwapNs += swapNs;
    phaseDrawNs += drawNs;
    if(swapNs > phaseMaxSwapNs) {
      phaseMaxSwapNs = swapNs;
    }
  }

  public static void frame(final long frameNs) {
    ring[ringIndex] = frameNs;
    ringIndex = (ringIndex + 1) % RING;
    if(ringCount < RING) {
      ringCount++;
    }

    frames++;
    sumNs += frameNs;
    if(frameNs > maxNs) {
      maxNs = frameNs;
    }
    if(frameNs > 50_000_000L) {
      over50++;
    } else if(frameNs > 33_000_000L) {
      over33++;
    }
    if(frameNs > 66_000_000L) {
      Log.w(TAG, "Slow frame: " + frameNs / 1_000_000L + "ms");
    }

    final long now = System.nanoTime();
    if(windowStart == 0) {
      windowStart = now;
      return;
    }
    if(now - windowStart < WINDOW_NS || frames == 0) {
      return;
    }

    final long[] copy = Arrays.copyOf(ring, ringCount);
    Arrays.sort(copy);
    final long avg = sumNs / frames;
    Log.i(TAG, "frames=" + frames
      + " avg=" + avg / 1_000_000L + "ms"
      + " p50=" + copy[copy.length / 2] / 1_000_000L + "ms"
      + " p95=" + copy[(int)(copy.length * 0.95f)] / 1_000_000L + "ms"
      + " max=" + maxNs / 1_000_000L + "ms"
      + " over33ms=" + over33
      + " over50ms=" + over50
      + " swap=" + (frames > 0 ? phaseSwapNs / frames : 0) / 1_000_000L + "ms"
      + " draw=" + (frames > 0 ? phaseDrawNs / frames : 0) / 1_000_000L + "ms"
      + " maxSwap=" + phaseMaxSwapNs / 1_000_000L + "ms"
      + " sleep=" + (sleepSamples > 0 ? sleepReqNs / sleepSamples : 0) / 1_000_000L + "ms"
      + " slept=" + (sleepSamples > 0 ? sleepActNs / sleepSamples : 0) / 1_000_000L + "ms"
      + " iters=" + loopIters
      + " busy=" + (loopIters > 0 ? loopBusyNs / loopIters : 0) / 1_000_000L + "ms");

    phaseSwapNs = 0;
    phaseDrawNs = 0;
    phaseMaxSwapNs = 0;
    sleepReqNs = 0;
    sleepActNs = 0;
    sleepSamples = 0;
    loopIters = 0;
    loopBusyNs = 0;

    frames = 0;
    sumNs = 0;
    maxNs = 0;
    over33 = 0;
    over50 = 0;
    ringCount = 0;
    ringIndex = 0;
    windowStart = now;
  }
}
