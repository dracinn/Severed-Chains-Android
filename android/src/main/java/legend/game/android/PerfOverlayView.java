package legend.game.android;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.view.View;

import java.util.Locale;

import legend.game.modding.coremod.config.PerfOverlayMode;

import static legend.core.GameEngine.CONFIG;
import static legend.core.GameEngine.RENDERER;
import static legend.game.modding.coremod.CoreMod.PERF_OVERLAY_CONFIG;

/**
 * MangoHud-style performance HUD drawn on the UI layer at native screen
 * resolution, independent of the game's render resolution. Polls the
 * {@code perf_overlay} config and renderer frame stats on a slow ticker.
 */
public class PerfOverlayView extends View {
  private static final int GOLD = Color.rgb(212, 175, 55);
  private static final int CREAM = Color.rgb(235, 225, 200);
  private static final int GOOD = Color.rgb(120, 220, 120);
  private static final int WARN = Color.rgb(255, 210, 60);
  private static final int BAD = Color.rgb(255, 90, 80);

  private static final long TICK_MS = 250;

  private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint fpsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint statPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint graphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path graphPath = new Path();
  private final RectF panel = new RectF();

  private final long[] frameTimes = new long[120];
  private PerfOverlayMode mode = PerfOverlayMode.OFF;

  private final Runnable ticker = new Runnable() {
    @Override
    public void run() {
      PerfOverlayView.this.tick();
      PerfOverlayView.this.postDelayed(this, TICK_MS);
    }
  };

  public PerfOverlayView(final Context context) {
    super(context);

    this.bgPaint.setStyle(Paint.Style.FILL);
    this.bgPaint.setColor(Color.argb(170, 14, 14, 22));

    this.rimPaint.setStyle(Paint.Style.STROKE);
    this.rimPaint.setColor(GOLD);
    this.rimPaint.setAlpha(120);
    this.rimPaint.setStrokeWidth(1.5f);

    this.fpsPaint.setColor(GOLD);
    this.fpsPaint.setTypeface(Typeface.MONOSPACE);
    this.fpsPaint.setTextAlign(Paint.Align.LEFT);

    this.labelPaint.setColor(CREAM);
    this.labelPaint.setAlpha(180);
    this.labelPaint.setTypeface(Typeface.MONOSPACE);

    this.statPaint.setColor(CREAM);
    this.statPaint.setAlpha(200);
    this.statPaint.setTypeface(Typeface.MONOSPACE);

    this.graphPaint.setStyle(Paint.Style.STROKE);
    this.graphPaint.setStrokeWidth(2f);
    this.graphPaint.setStrokeCap(Paint.Cap.ROUND);

    this.gridPaint.setStyle(Paint.Style.STROKE);
    this.gridPaint.setColor(CREAM);
    this.gridPaint.setAlpha(50);
    this.gridPaint.setStrokeWidth(1f);

    this.setVisibility(GONE);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    this.post(this.ticker);
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    this.removeCallbacks(this.ticker);
  }

  private void tick() {
    PerfOverlayMode mode = PerfOverlayMode.OFF;
    try {
      if(PERF_OVERLAY_CONFIG.isValid()) {
        mode = CONFIG.getConfig(PERF_OVERLAY_CONFIG.get());
      }
    } catch(final RuntimeException ignored) {
      // Engine not up yet
    }

    if(mode == null) {
      mode = PerfOverlayMode.OFF;
    }

    if(mode != this.mode) {
      this.mode = mode;
      this.setVisibility(mode == PerfOverlayMode.OFF ? GONE : VISIBLE);
    }

    if(mode != PerfOverlayMode.OFF) {
      this.invalidate();
    }
  }

  private float batteryTempC() {
    final Intent battery = this.getContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    if(battery == null) {
      return Float.NaN;
    }
    return battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE) / 10.0f;
  }

  @Override
  protected void onDraw(final Canvas canvas) {
    super.onDraw(canvas);
    if(this.mode == PerfOverlayMode.OFF || RENDERER == null) {
      return;
    }

    final float density = this.getResources().getDisplayMetrics().density;
    final float pad = 10 * density;
    final float pillH = (this.mode == PerfOverlayMode.FULL ? 96 : 64) * density;
    final float pillW = 210 * density;
    final float left = (this.getWidth() - pillW) / 2.0f;
    final float top = 6 * density;
    this.panel.set(left, top, left + pillW, top + pillH);

    final float radius = 14 * density;
    canvas.drawRoundRect(this.panel, radius, radius, this.bgPaint);
    canvas.drawRoundRect(this.panel, radius, radius, this.rimPaint);

    final boolean engineUp = RENDERER != null && RENDERER.window() != null;

    // FPS number + label
    this.fpsPaint.setTextSize(26 * density);
    this.labelPaint.setTextSize(11 * density);
    final float textX = this.panel.left + pad;
    final float fpsBaseline = this.panel.top + pad + 20 * density;
    canvas.drawText(engineUp ? String.format(Locale.US, "%.1f", RENDERER.getCurrentFps()) : "--", textX, fpsBaseline, this.fpsPaint);
    canvas.drawText("FPS", textX + 78 * density, fpsBaseline, this.labelPaint);

    // Frame-time sparkline
    final float graphLeft = this.panel.left + pad;
    final float graphTop = fpsBaseline + 6 * density;
    final float graphW = pillW - pad * 2;
    final float graphH = 18 * density;
    final float graphBottom = graphTop + graphH;

    // 16.7ms (60fps) guide line
    final float targetY = graphBottom - Math.min(16.7f / 50.0f, 1.0f) * graphH;
    canvas.drawLine(graphLeft, targetY, graphLeft + graphW, targetY, this.gridPaint);

    final int count = engineUp ? RENDERER.copyFrameTimes(this.frameTimes) : 0;
    if(count > 1) {
      final float stepX = graphW / (this.frameTimes.length - 1);
      final float startX = graphLeft + graphW - count * stepX;

      for(int i = 0; i < count - 1; i++) {
        final float ms0 = this.frameTimes[i] / 1_000_000.0f;
        final float ms1 = this.frameTimes[i + 1] / 1_000_000.0f;
        final float y0 = graphBottom - Math.min(ms0 / 50.0f, 1.0f) * graphH;
        final float y1 = graphBottom - Math.min(ms1 / 50.0f, 1.0f) * graphH;
        final float worst = Math.max(ms0, ms1);
        this.graphPaint.setColor(worst <= 20.0f ? GOOD : worst <= 33.4f ? WARN : BAD);
        canvas.drawLine(startX + i * stepX, y0, startX + (i + 1) * stepX, y1, this.graphPaint);
      }
    }

    if(this.mode == PerfOverlayMode.FULL) {
      this.statPaint.setTextSize(10.5f * density);
      final float statX = this.panel.left + pad;
      float statY = graphBottom + 14 * density;
      canvas.drawText(
        engineUp
          ? String.format(Locale.US, "Render %dx%d → %dx%d",
            RENDERER.getRenderWidth(), RENDERER.getRenderHeight(),
            RENDERER.window().getWidth(), RENDERER.window().getHeight())
          : "Render --",
        statX, statY, this.statPaint
      );

      statY += 13 * density;
      final float tempC = this.batteryTempC();
      final String stats = Float.isNaN(tempC)
        ? "Limit -- • Batt --"
        : String.format(Locale.US, "Limit %s • Batt %.0f°C", engineUp ? String.valueOf(RENDERER.window().getFpsLimit()) : "--", tempC);
      canvas.drawText(stats, statX, statY, this.statPaint);
    }
  }
}
