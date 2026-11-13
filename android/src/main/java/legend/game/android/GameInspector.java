package legend.game.android;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import legend.core.platform.AndroidInput;
import legend.game.EngineStates;
import legend.game.Graphics;
import legend.game.types.LodString;
import legend.game.types.TextboxText84;

/**
 * Read-only mirror of what the engine drew on the current frame. Two staged
 * transforms feed it: {@code Text.renderText} (menus/HUD) and
 * {@code Text.renderTextboxText} (dialogue) call {@link #text}/{@link #textbox},
 * and {@code RenderEngine.renderFrame} calls {@link #beginFrame}. The captured
 * elements serve two consumers:
 *
 * <ul>
 *   <li>A virtual-node {@link AccessibilityNodeProvider} installed on the game
 *       SurfaceView, so screen readers (and uiautomator) see every on-screen
 *       text element with its real bounds. Activating a node taps it through
 *       the normal mouse path.</li>
 *   <li>A loopback-only HTTP endpoint (debuggable builds) for live screen
 *       reads: {@code adb forward tcp:15234 tcp:15234 && curl localhost:15234/screen}</li>
 * </ul>
 *
 * Nothing here affects rendering — it only observes.
 */
public final class GameInspector {
  private GameInspector() { }

  /** One text element queued this frame, in game coordinates (0..displayW/H). */
  public static final class El {
    public final String text;
    public final float x;
    public final float y;

    El(final String text, final float x, final float y) {
      this.text = text;
      this.x = x;
      this.y = y;
    }
  }

  /** Elements queued by the frame currently in progress (game thread only). */
  private static List<El> pending = new ArrayList<>();
  /** Elements queued by the last completed frame; immutable once published. */
  private static volatile List<El> current = List.of();
  private static volatile long frame;

  /** Called at the top of RenderEngine.renderFrame (game thread). Publishes
   *  the previous frame's elements and starts a fresh list. */
  public static void beginFrame() {
    current = pending;
    pending = new ArrayList<>();
    frame++;
  }

  /** Called from Text.renderText (game thread). */
  public static void text(final String text, final float x, final float y) {
    if(text != null && !text.isBlank()) {
      pending.add(new El(text, x, y));
    }
  }

  /** Textbox text is stable per box — decode the LodString once per instance. */
  private static final Map<LodString, String> decoded = new IdentityHashMap<>();

  /** Called from Text.renderTextboxText (game thread). Records the whole box
   *  at its origin; includes chars not yet typed out, which is fine for a
   *  "what is being said" read. */
  public static void textbox(final TextboxText84 box) {
    if(box == null || box.str_24 == null) {
      return;
    }

    String str = decoded.get(box.str_24);
    if(str == null) {
      str = box.str_24.get();
      if(decoded.size() > 64) {
        decoded.clear();
      }
      decoded.put(box.str_24, str);
    }

    if(!str.isBlank()) {
      pending.add(new El(str, box._18 - Graphics.centreScreenX_1f8003dc, box._1a - Graphics.centreScreenY_1f8003de));
    }
  }

  /** Elements on the last completed frame, in draw order. */
  public static List<El> snapshot() {
    return current;
  }

  public static long frameCount() {
    return frame;
  }

  // ------------------------------------------------------------------
  // Accessibility: virtual nodes on the game SurfaceView
  // ------------------------------------------------------------------

  /** Installs the virtual-node provider on the game surface. {@code injector}
   *  receives view-local px and must feed a tap into the input queue. */
  public static void attach(final View surface, final TapInjector injector) {
    surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    surface.setContentDescription("Game screen");
    surface.setAccessibilityDelegate(new View.AccessibilityDelegate() {
      @Override
      public AccessibilityNodeProvider getAccessibilityNodeProvider(final View host) {
        return provider(surface, injector);
      }
    });
  }

  /** Maps game coords to view px. The engine always centres the 4:3 image in
   *  the surface, so map into the centered 4:3 region regardless of layout. */
  private static Rect boundsFor(final View surface, final El el) {
    final int vw = surface.getWidth();
    final int vh = surface.getHeight();
    final int dw = Math.max(1, Graphics.displayWidth_1f8003e0);
    final int dh = Math.max(1, Graphics.displayHeight_1f8003e4);
    final float imgW = Math.min(vw, vh * 4.0f / 3.0f);
    final float imgH = imgW * 0.75f;
    final float offX = (vw - imgW) / 2.0f;
    final float offY = (vh - imgH) / 2.0f;
    // Line height ~12px in game space; clamp bounds inside the view
    final int l = (int)(offX + el.x * imgW / dw);
    final int t = (int)(offY + el.y * imgH / dh);
    return new Rect(l, t, Math.min(vw, l + 40), Math.min(vh, t + (int)(12 * imgH / dh) + 2));
  }

  private static AccessibilityNodeProvider provider(final View surface, final TapInjector injector) {
    return new AccessibilityNodeProvider() {
      @Override
      public AccessibilityNodeInfo createAccessibilityNodeInfo(final int virtualViewId) {
        final List<El> els = GameInspector.snapshot();

        if(virtualViewId == HOST_VIEW_ID) {
          final AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
          surface.onInitializeAccessibilityNodeInfo(info);
          for(int i = 0; i < els.size(); i++) {
            info.addChild(surface, i);
          }
          return info;
        }

        if(virtualViewId < 0 || virtualViewId >= els.size()) {
          return null;
        }

        final AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        info.setSource(surface, virtualViewId);
        info.setParent(surface);
        info.setPackageName(surface.getContext().getPackageName());
        info.setClassName("android.widget.TextView");
        info.setContentDescription(els.get(virtualViewId).text);
        info.setBoundsInParent(GameInspector.boundsFor(surface, els.get(virtualViewId)));
        info.setVisibleToUser(true);
        info.setEnabled(true);
        info.setClickable(true);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        return info;
      }

      @Override
      public boolean performAction(final int virtualViewId, final int action, final Bundle arguments) {
        if(action == AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.getId()) {
          final List<El> els = GameInspector.snapshot();
          if(virtualViewId >= 0 && virtualViewId < els.size()) {
            final Rect bounds = GameInspector.boundsFor(surface, els.get(virtualViewId));
            injector.tap(bounds.centerX(), bounds.centerY());
            return true;
          }
        }
        return false;
      }
    };
  }

  /** Feeds a tap (down+up) at view-local px into the engine's mouse path. */
  public interface TapInjector {
    void tap(float x, float y);
  }

  // ------------------------------------------------------------------
  // Loopback inspector server (debuggable builds only)
  // ------------------------------------------------------------------

  public static final int PORT = 15234;
  private static boolean serverStarted;

  /** Starts the loopback HTTP endpoint on a daemon thread. Safe to call once. */
  public static synchronized void startServer() {
    if(serverStarted) {
      return;
    }
    serverStarted = true;

    final Thread thread = new Thread(GameInspector::serve, "sc-inspector");
    thread.setDaemon(true);
    thread.start();
  }

  private static void serve() {
    try(final ServerSocket server = new ServerSocket()) {
      server.setReuseAddress(true);
      server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT));

      while(true) {
        final Socket socket = server.accept();
        try(socket) {
          socket.setSoTimeout(3000);
          final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
          final String request = in.readLine();
          final BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

          if(request == null) {
            continue;
          }

          final String path = request.split(" ").length > 1 ? request.split(" ")[1] : "/";
          // Drain headers so keep-alive semantics stay simple
          for(String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) { /* drain */ }

          final String body;
          final String type;
          if(path.startsWith("/screen.json")) {
            body = json();
            type = "application/json";
          } else if(path.startsWith("/screen")) {
            body = dump();
            type = "text/plain; charset=utf-8";
          } else {
            body = "Severed Chains inspector\n/screen      last frame's text, sorted by position\n/screen.json same, as JSON\n";
            type = "text/plain; charset=utf-8";
          }

          final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          out.write("HTTP/1.1 200 OK\r\nContent-Type: " + type + "\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n");
          out.flush();
          socket.getOutputStream().write(bytes);
          socket.getOutputStream().flush();
        }
      }
    } catch(final IOException ignored) { }
  }

  /** Last frame's text, top-to-bottom left-to-right, "x,y: text" lines. */
  public static String dump() {
    final List<El> els = new ArrayList<>(snapshot());
    els.sort(Comparator.comparing((El e) -> e.y).thenComparing(e -> e.x));
    final StringBuilder sb = new StringBuilder("frame=").append(frame)
      .append(" engine=").append(EngineStates.currentEngineState_8004dd04)
      .append(" display=").append(Graphics.displayWidth_1f8003e0).append('x').append(Graphics.displayHeight_1f8003e4)
      .append('\n');
    for(final El el : els) {
      sb.append((int)el.x).append(',').append((int)el.y).append(": ").append(el.text.replace('\n', ' ')).append('\n');
    }
    return sb.toString();
  }

  private static String json() {
    final StringBuilder sb = new StringBuilder("{\"frame\":").append(frame)
      .append(",\"engine\":\"").append(EngineStates.currentEngineState_8004dd04)
      .append("\",\"display\":[").append(Graphics.displayWidth_1f8003e0).append(',').append(Graphics.displayHeight_1f8003e4)
      .append("],\"elements\":[");
    final List<El> els = snapshot();
    for(int i = 0; i < els.size(); i++) {
      final El el = els.get(i);
      if(i > 0) {
        sb.append(',');
      }
      sb.append("{\"x\":").append(el.x).append(",\"y\":").append(el.y).append(",\"text\":\"")
        .append(el.text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")).append("\"}");
    }
    return sb.append("]}").toString();
  }

  /** Feeds a tap into the mouse queue — same as a finger on the surface. */
  public static void injectTap(final float x, final float y) {
    AndroidInput.mouseMove(x, y);
    AndroidInput.mouseButton(1, true);
    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
      () -> AndroidInput.mouseButton(1, false), 60);
  }
}
