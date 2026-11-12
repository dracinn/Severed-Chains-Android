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

  private PerfLog() { }

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
      + " over50ms=" + over50);

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
