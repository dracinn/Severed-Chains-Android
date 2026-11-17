package legend.game.macos;

import legend.game.inventory.screens.FontOptions;
import legend.game.modding.coremod.config.PerfOverlayMode;

import java.util.Locale;

import static legend.core.GameEngine.CONFIG;
import static legend.core.GameEngine.DEFAULT_FONT;
import static legend.core.GameEngine.RENDERER;
import static legend.game.SItem.UI_WHITE_SHADOWED_SMALL;
import static legend.game.Text.renderText;
import static legend.game.Text.textZ_800bdf00;
import static legend.game.modding.coremod.CoreMod.PERF_OVERLAY_CONFIG;

/** MangoHud-style performance HUD, driven by the perf_overlay config the
 *  Android build registers. Drawn in-engine via renderText (there is no UI
 *  layer on macOS) — top-left like MangoHud. FPS mode shows the frame rate;
 *  FULL adds frame-time stats and render vs native resolution. */
public final class PerfOverlay {
  private static final FontOptions FONT = UI_WHITE_SHADOWED_SMALL;
  private static final long[] TIMES = new long[120];

  private PerfOverlay() { }

  public static void draw() {
    if(!PERF_OVERLAY_CONFIG.isValid()) {
      return;
    }

    final PerfOverlayMode mode = CONFIG.getConfig(PERF_OVERLAY_CONFIG.get());
    if(mode == PerfOverlayMode.OFF) {
      return;
    }

    final int oldZ = textZ_800bdf00;
    textZ_800bdf00 = 1;

    final float x = 4.0f - RENDERER.getWidescreenOrthoOffsetX();
    float y = 4.0f;

    final String fps = String.format(Locale.US, "%.1f FPS", RENDERER.getCurrentFps());
    renderText(fps, x, y, FONT, (model, shadow) -> model.worldScissor().set(0, 0, RENDERER.getRenderWidth(), RENDERER.getRenderHeight()));
    y += DEFAULT_FONT.textHeight(fps) * FONT.getSize();

    if(mode == PerfOverlayMode.FULL) {
      final int count = RENDERER.copyFrameTimes(TIMES);
      long sum = 0;
      long max = 0;
      for(int i = 0; i < count; i++) {
        sum += TIMES[i];
        if(TIMES[i] > max) {
          max = TIMES[i];
        }
      }

      final String frame = String.format(Locale.US, "%.1f ms avg, %.0f ms max", count == 0 ? 0.0 : sum / (double)count / 1e6, max / 1e6);
      renderText(frame, x, y, FONT, (model, shadow) -> model.worldScissor().set(0, 0, RENDERER.getRenderWidth(), RENDERER.getRenderHeight()));
      y += DEFAULT_FONT.textHeight(frame) * FONT.getSize();

      final String res = String.format(Locale.US, "%dx%d -> %dx%d, limit %d",
        RENDERER.getNativeWidth(), RENDERER.getNativeHeight(),
        RENDERER.getRenderWidth(), RENDERER.getRenderHeight(),
        RENDERER.window().getFpsLimit());
      renderText(res, x, y, FONT, (model, shadow) -> model.worldScissor().set(0, 0, RENDERER.getRenderWidth(), RENDERER.getRenderHeight()));
    }

    textZ_800bdf00 = oldZ;
  }
}
