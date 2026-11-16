package legend.game.android;

/**
 * User frame-rate cap (Options &gt; Graphics &gt; Frame rate cap). Mirrored
 * into prefs so it applies before the config registries exist — the same
 * reason the renderer backend is a pref and not just a config entry.
 *
 * Two mechanisms, both preserving game pacing (no slow-motion):
 *
 * <ul>
 * <li><b>30/20/15</b> are integer vsync divisors, so staged transforms clamp
 * every {@code vsyncMode_8007a3b8} write to {@link #minVsync()}. The engine's
 * own frame-skip machinery then runs the cap exactly like a native 30fps
 * scene — tick rate, the {@code timestamp_a0 += vsyncMode} advance and all
 * {@code 60 / vsyncMode} duration math stay self-consistent.</li>
 * <li><b>45</b> can't be expressed as a vsync divisor, so it keeps the 60Hz
 * game tick and drops GL submission every 4th tick through RenderEngine's
 * frameSkip path — the sim runs at full rate while 45fps is presented.</li>
 * </ul>
 */
public final class FrameCap {
  private FrameCap() { }

  private static final String PREF = "fps_cap";

  /** Configured cap in fps; 0 = engine-native pacing (default). */
  public static int cap() {
    try {
      return AndroidEnv.prefs().getInt(PREF, 0);
    } catch(final RuntimeException e) {
      // Graphics' static init can run before AndroidEnv has a Context —
      // fall through to native pacing until the first clamped write.
      return 0;
    }
  }

  public static void setCap(final int fps) {
    AndroidEnv.prefs().edit().putInt(PREF, fps).apply();
  }

  /**
   * Floor for the engine's vsync divisor. Staged transforms wrap every
   * {@code vsyncMode_8007a3b8 = N} write as {@code Math.max(N, minVsync())}
   * so the field — and everything derived from it — sees the clamped rate.
   */
  public static int minVsync() {
    return switch(cap()) {
      case 15 -> 4;
      case 20 -> 3;
      case 30 -> 2;
      default -> 1;
    };
  }

  /** True when the 45fps render-skip should drive RenderEngine's frameSkip. */
  public static boolean renderSkipActive(final int fpsLimit) {
    return cap() == 45 && fpsLimit >= 60;
  }

  private static int skipPhase;

  /**
   * Yields 0,0,0,1 repeating — RenderEngine renders only on ==0 ticks, so
   * three of every four 60Hz ticks present (45fps) while the sim never slows.
   */
  public static int skipIndex() {
    skipPhase = (skipPhase + 1) % 4;
    return skipPhase == 0 ? 1 : 0;
  }
}
