package legend.game.macos;

import legend.core.gte.MV;
import legend.core.renderer.QueuedModelStandard;
import legend.core.renderer.Translucency;
import legend.game.inventory.screens.FontOptions;
import legend.game.inventory.screens.TextColour;
import legend.game.modding.coremod.config.PerfOverlayMode;

import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.util.Locale;

import static legend.core.GameEngine.CONFIG;
import static legend.core.GameEngine.DEFAULT_FONT;
import static legend.core.GameEngine.RENDERER;
import static legend.game.Text.renderText;
import static legend.game.Text.textZ_800bdf00;
import static legend.game.modding.coremod.CoreMod.PERF_OVERLAY_CONFIG;

/** MangoHud-style performance HUD, driven by the shared perf_overlay config
 *  (mirroring Android's PerfOverlayView, extended to a fuller MangoHud
 *  feature set). Drawn in the native ortho overlay pass — top-left like
 *  MangoHud. Coordinates are the game's logical ortho units (~368x240).
 *
 *  <p>FPS mode: big colour-coded FPS + frame-time sparkline with a 16.7ms
 *  guide. FULL adds frame ms, 1%/0.1% lows, CPU/RAM, render → display
 *  resolution, fps limit and backend name. */
public final class PerfOverlay {
  private static final int HISTORY = 600;
  private static final int SPARK = 120;      // sparkline bars
  private static final float BUDGET_MS = 1000.0f / 60.0f;

  private static final float MARGIN = 6.0f;
  private static final float PAD = 4.0f;
  private static final float PANEL_W = 150.0f;
  private static final float SPARK_H = 16.0f;

  private static final FontOptions FONT_FPS = new FontOptions().colour(TextColour.WHITE).shadowColour(TextColour.BLACK).size(1.33f);
  private static final FontOptions FONT_STAT = new FontOptions().colour(TextColour.WHITE).shadowColour(TextColour.BLACK).size(0.6f);
  private static final FontOptions FONT_FPS_LIME = new FontOptions().colour(TextColour.LIME).shadowColour(TextColour.BLACK).size(1.33f);
  private static final FontOptions FONT_FPS_YELLOW = new FontOptions().colour(TextColour.YELLOW).shadowColour(TextColour.BLACK).size(1.33f);
  private static final FontOptions FONT_FPS_RED = new FontOptions().colour(TextColour.RED).shadowColour(TextColour.BLACK).size(1.33f);

  private static final long[] times = new long[HISTORY];
  private static int timesIndex;
  private static int timesCount;
  private static long lastNanos;

  /** Reflective handle to com.sun.management's getProcessCpuLoad — the
   *  jpackage runtime may not include jdk.management, so degrade to no
   *  CPU stat instead of failing class-init. */
  private static java.lang.invoke.MethodHandle cpuLoadHandle;
  private static boolean cpuLoadChecked;

  private PerfOverlay() { }

  public static void draw() {
    if(!PERF_OVERLAY_CONFIG.isValid()) {
      return;
    }

    final PerfOverlayMode mode = CONFIG.getConfig(PERF_OVERLAY_CONFIG.get());
    if(mode == PerfOverlayMode.OFF) {
      return;
    }

    // Feed our own frame-time ring — draw() runs once per render tick, so
    // the delta is the frame interval (keeps working through frame-skip).
    final long now = System.nanoTime();
    if(lastNanos != 0) {
      times[timesIndex] = now - lastNanos;
      timesIndex = (timesIndex + 1) % times.length;
      if(timesCount < times.length) {
        timesCount++;
      }
    }
    lastNanos = now;

    final float panelW = Math.min(PANEL_W, RENDERER.getNativeWidth() * 0.42f);
    final float x = MARGIN;
    final float y = MARGIN;

    final float fps = RENDERER.getCurrentFps();
    final float fpsLimit = RENDERER.window().getFpsLimit();
    final boolean full = mode == PerfOverlayMode.FULL;

    // Green/red threshold follows the active fps limit (a 30Hz-paced scene
    // should show calm green at 33ms); the guide line stays a fixed 16.7ms
    // 60fps reference.
    final float budget = fpsLimit > 0 ? 1000.0f / fpsLimit : BUDGET_MS;
    final float scaleMs = budget * 2.0f;

    // Panel height: FPS row + sparkline (+3 stat rows in FULL)
    final float fpsRowH = DEFAULT_FONT.textHeight("0") * FONT_FPS.getSize();
    final float statRowH = DEFAULT_FONT.textHeight("0") * FONT_STAT.getSize();
    final float panelH = PAD * 2 + fpsRowH + 1.5f + SPARK_H + (full ? statRowH * 4.0f + 2.0f : 0);

    final MV mv = new MV();

    // Translucent backing panel — B_MINUS_F subtracts the quad colour from
    // the background, dimming it toward black like MangoHud's dark bg.
    mv.identity().scale(panelW, panelH, 1.0f);
    mv.transfer.x = x;
    mv.transfer.y = y;
    mv.transfer.z = 1.0f;
    RENDERER.queueNativeOrthoModel(RENDERER.opaqueQuad, mv, QueuedModelStandard.class)
      .colour(0.6f, 0.6f, 0.6f)
      .translucency(Translucency.B_MINUS_F)
      .depthOffset(-1.0f);

    // Sparkline — one vertical bar per frame, height = frame time,
    // green on-pace for the fps limit, red over it.
    final float sparkX = x + PAD;
    final float sparkY = y + PAD + fpsRowH + 1.5f;
    final float sparkW = panelW - PAD * 2;
    final float step = sparkW / SPARK;
    final float barW = Math.max(0.6f, step * 0.7f);

    // 16.7ms guide line
    final float guideY = sparkY + SPARK_H * (1.0f - BUDGET_MS / scaleMs);
    mv.identity().scale(sparkW, 0.6f, 1.0f);
    mv.transfer.x = sparkX;
    mv.transfer.y = guideY;
    mv.transfer.z = 1.0f;
    RENDERER.queueNativeOrthoModel(RENDERER.opaqueQuad, mv, QueuedModelStandard.class)
      .colour(0.5f, 0.5f, 0.5f)
      .depthOffset(0.5f);

    final int bars = Math.min(timesCount, SPARK);
    for(int i = 0; i < bars; i++) {
      final int idx = (timesIndex - bars + i + times.length) % times.length;
      final float ms = times[idx] / 1e6f;
      final float h = Math.max(0.8f, Math.min(1.0f, ms / scaleMs) * SPARK_H);
      mv.identity().scale(barW, h, 1.0f);
      mv.transfer.x = sparkX + i * step;
      mv.transfer.y = sparkY + SPARK_H - h;
      mv.transfer.z = 1.0f;
      final var model = RENDERER.queueNativeOrthoModel(RENDERER.opaqueQuad, mv, QueuedModelStandard.class)
        .depthOffset(1.0f);
      if(ms <= budget) {
        model.colour(0.35f, 0.85f, 0.25f);
      } else {
        model.colour(0.95f, 0.25f, 0.15f);
      }
    }

    final int oldZ = textZ_800bdf00;
    textZ_800bdf00 = 1;

    // FPS number — colour-coded against the fps limit like MangoHud
    final FontOptions fpsFont;
    if(fpsLimit > 0 && fps >= fpsLimit * 0.9f) {
      fpsFont = FONT_FPS_LIME;
    } else if(fpsLimit > 0 && fps >= fpsLimit * 0.7f) {
      fpsFont = FONT_FPS_YELLOW;
    } else {
      fpsFont = FONT_FPS_RED;
    }
    final String fpsText = String.format(Locale.US, "%.1f", fps);
    renderText(fpsText, x + PAD, y + PAD, fpsFont,
      (model, shadow) -> model.worldScissor().set(0, 0, RENDERER.getRenderWidth(), RENDERER.getRenderHeight()));
    renderText("FPS", x + PAD + DEFAULT_FONT.textWidth(fpsText) * FONT_FPS.getSize() + 2.0f, y + PAD + fpsRowH - statRowH, FONT_STAT,
      (model, shadow) -> model.worldScissor().set(0, 0, RENDERER.getRenderWidth(), RENDERER.getRenderHeight()));

    if(full) {
      float ty = sparkY + SPARK_H + 2.0f;

      // Frame ms + lows (worst-frame averages like MangoHud's 1%/0.1%)
      if(timesCount > 0) {
        final float avg = avgMs();
        final float low1 = lowPercent(0.01f) / 1e6f;
        final float low01 = lowPercent(0.001f) / 1e6f;
        renderText(String.format(Locale.US, "%.1fms 1%% low %.0f 0.1%% low %.0f", avg, low1, low01), x + PAD, ty, FONT_STAT,
          (model, shadow) -> model.worldScissor().set(0, 0, RENDERER.getRenderWidth(), RENDERER.getRenderHeight()));
        ty += statRowH;
      }

      // CPU% (reflective — jdk.management may be absent) + heap
      final double cpu = cpuLoad();
      final long heap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
      renderText(String.format(Locale.US, "%s  MEM %s",
        cpu < 0 ? "CPU --%" : String.format(Locale.US, "CPU %.0f%%", cpu * 100),
        heap < 1_000_000_000L ? String.format(Locale.US, "%.0fM", heap / 1e6) : String.format(Locale.US, "%.1fG", heap / 1e9)), x + PAD, ty, FONT_STAT,
        (model, shadow) -> model.worldScissor().set(0, 0, RENDERER.getRenderWidth(), RENDERER.getRenderHeight()));
      ty += statRowH;

      // Resolution + limit + backend
      renderText(String.format(Locale.US, "%dx%d > %dx%d  limit %d  %s",
        RENDERER.getNativeWidth(), RENDERER.getNativeHeight(),
        RENDERER.getRenderWidth(), RENDERER.getRenderHeight(),
        (int)fpsLimit, AngleContext.rendererName()), x + PAD, ty, FONT_STAT,
        (model, shadow) -> model.worldScissor().set(0, 0, RENDERER.getRenderWidth(), RENDERER.getRenderHeight()));
    }

    textZ_800bdf00 = oldZ;
  }

  private static float avgMs() {
    long sum = 0;
    for(int i = 0; i < timesCount; i++) {
      sum += times[i];
    }
    return sum / (float)Math.max(1, timesCount) / 1e6f;
  }

  /** Average of the worst {@code fraction} of frame times (the "lows"). */
  private static long lowPercent(final float fraction) {
    final int n = Math.max(1, (int)(timesCount * fraction));
    final long[] worst = new long[n];
    for(int i = 0; i < timesCount; i++) {
      final long t = times[i];
      for(int j = 0; j < n; j++) {
        if(t > worst[j]) {
          System.arraycopy(worst, j, worst, j + 1, n - j - 1);
          worst[j] = t;
          break;
        }
      }
    }
    long sum = 0;
    for(final long t : worst) {
      sum += t;
    }
    return sum / n;
  }

  private static double cpuLoad() {
    if(!cpuLoadChecked) {
      cpuLoadChecked = true;
      try {
        final Class<?> cls = Class.forName("com.sun.management.OperatingSystemMXBean");
        final Object bean = ManagementFactory.getOperatingSystemMXBean();
        if(cls.isInstance(bean)) {
          cpuLoadHandle = MethodHandles.publicLookup().unreflect(cls.getMethod("getProcessCpuLoad"))
            .bindTo(bean);
        }
      } catch(final Throwable ignored) { }
    }
    if(cpuLoadHandle == null) {
      return -1.0;
    }
    try {
      return (double)cpuLoadHandle.invoke();
    } catch(final Throwable e) {
      return -1.0;
    }
  }
}
