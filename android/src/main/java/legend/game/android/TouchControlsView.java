package legend.game.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import legend.core.platform.AndroidInput;
import legend.core.platform.input.InputAxis;
import legend.core.platform.input.InputButton;
import legend.core.platform.input.TouchFaceButtonStyle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * On-screen gamepad overlay. Draws a floating analog stick (left) and a
 * PlayStation face-button diamond (right) plus shoulder/select/start, kept
 * inside the pillarboxed margins of the 4:3 game image. The stick is not
 * fixed: a touch anywhere in the left zone anchors it under the thumb and
 * drags emit LEFT_X/LEFT_Y axis values. The face-button cluster can be
 * moved by dragging the empty middle of the diamond and resized by
 * pinching there; the result is persisted via AndroidInput.reportFaceLayout.
 * Emits gamepad button/axis events
 * (deviceId -1) into the AndroidInput queue; touches that hit nothing are
 * left for the touch-to-mouse path. Hides itself when a physical gamepad
 * reports input, reappears on the next touch.
 */
public class TouchControlsView extends View {
  /** Fraction of stick radius below which a deflection reads as centred. */
  private static final float STICK_DEADZONE = 0.06f;
  private static final float MIN_FACE_SCALE = 0.5f;
  private static final float MAX_FACE_SCALE = 2.0f;
  /** Face-cluster unit offsets, multiplied by the (scaled) diamond offset. */
  private static final float FACE_OFF_DP = 48f;
  private static final float FACE_R_DP = 26f;

  private static final int GOLD = Color.rgb(212, 175, 55);
  private static final int CREAM = Color.rgb(235, 225, 200);
  private static final int DARK = Color.rgb(20, 20, 28);
  private static final int SYMBOL_TRIANGLE = Color.rgb(140, 230, 210);
  private static final int SYMBOL_CROSS = Color.rgb(150, 180, 255);
  private static final int SYMBOL_SQUARE = Color.rgb(255, 160, 200);
  private static final int SYMBOL_CIRCLE = Color.rgb(255, 140, 140);
  private static final int LETTER_RED = Color.rgb(255, 90, 80);
  private static final int LETTER_YELLOW = Color.rgb(255, 210, 60);
  private static final int LETTER_GREEN = Color.rgb(120, 220, 120);
  private static final int LETTER_BLUE = Color.rgb(110, 170, 255);

  /** A control zone for round buttons, pill buttons, and trigger axes. */
  private static final class Ctrl {
    final String label;
    float x;
    float y;
    float r;
    /** Pill dimensions; when pw > 0 the control is a rounded rect, not a circle. */
    float pw;
    float ph;
    InputButton button;
    InputAxis axis;
    /** Face-cluster member: unit diamond offsets (-1..1) around the centre. */
    float ox;
    float oy;

    Ctrl(final String label) {
      this.label = label;
    }

    static Ctrl button(final String label, final InputButton button) {
      final Ctrl c = new Ctrl(label);
      c.button = button;
      return c;
    }

    static Ctrl axis(final String label, final InputAxis axis) {
      final Ctrl c = new Ctrl(label);
      c.axis = axis;
      return c;
    }
  }

  private final List<Ctrl> controls = new ArrayList<>();
  private final Paint discPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint symbolPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path triPath = new Path();
  private final RectF tmpRect = new RectF();

  /** Face-button glyph style pushed from the Controls config (game thread -> UI). */
  private volatile TouchFaceButtonStyle faceButtonStyle = TouchFaceButtonStyle.PLAYSTATION;
  /** Opacity multiplier for the face buttons and joystick, from Controls config. */
  private volatile float opacity = 1.0f;
  /** Face-cluster position (fraction of view) and scale, pushed from config. */
  private volatile float faceCxN = 0.91f;
  private volatile float faceCyN = 0.70f;
  private volatile float faceScaleN = 1.0f;

  /** Face-cluster state in view pixels, refreshed by layoutFaceCluster(). */
  private final List<Ctrl> faceCtrls = new ArrayList<>();
  private float faceCx;
  private float faceCy;
  private float faceScale = 1.0f;
  private float faceBoundR;

  /** Move/resize gesture on the face cluster: -1 when not owned. */
  private int faceMovePointerId = -1;
  private int faceScalePointerId = -1;
  private float faceMoveX;
  private float faceMoveY;
  private float faceScaleX;
  private float faceScaleY;
  private float grabDX;
  private float grabDY;
  private float pinchStartDist;
  private float pinchStartScale;

  /** pointerId -> x,y for fingers that landed on a control or the stick */
  private final SparseArray<float[]> active = new SparseArray<>();
  /** Tokens (InputButton / InputAxis) currently held by active pointers */
  private final Set<Object> heldTokens = new HashSet<>();
  private boolean gestureConsumed;

  /** Floating stick: -1 when no finger owns it. */
  private int stickPointerId = -1;
  private float stickAnchorX;
  private float stickAnchorY;
  /** Knob deflection in -1..1 */
  private float stickDeflX;
  private float stickDeflY;
  private float lastStickAxisX;
  private float lastStickAxisY;

  /** Rest position and travel radius of the stick base. */
  private float stickHomeX;
  private float stickHomeY;
  private float stickR;
  /** Left-side region in which a touchdown claims the stick. */
  private float stickZoneTop;
  private float stickZoneRight;
  private float stickZoneBottom;

  private float dp;

  public TouchControlsView(final Context context) {
    super(context);
    this.discPaint.setStyle(Paint.Style.FILL);
    this.rimPaint.setStyle(Paint.Style.STROKE);
    this.rimPaint.setColor(GOLD);
    this.sheenPaint.setStyle(Paint.Style.STROKE);
    this.sheenPaint.setColor(Color.argb(140, 255, 240, 190));
    this.sheenPaint.setStrokeCap(Paint.Cap.ROUND);
    this.labelPaint.setColor(CREAM);
    this.labelPaint.setTextAlign(Paint.Align.CENTER);
    this.labelPaint.setFakeBoldText(true);
    this.symbolPaint.setStyle(Paint.Style.STROKE);
    this.symbolPaint.setStrokeCap(Paint.Cap.ROUND);
    this.letterPaint.setTextAlign(Paint.Align.CENTER);
    this.letterPaint.setFakeBoldText(true);
    AndroidInput.overlay = this;
  }

  /** Called on the UI thread by AndroidPlatformManager when the Controls config changes. */
  public void setFaceButtonStyle(final TouchFaceButtonStyle style) {
    this.faceButtonStyle = style;
    this.invalidate();
  }

  /** Called on the UI thread by AndroidPlatformManager when the Controls config changes. */
  public void setOpacity(final float opacity) {
    this.opacity = opacity;
    this.invalidate();
  }

  /** Called on the UI thread by AndroidPlatformManager when the persisted face-cluster layout changes. */
  public void setFaceLayout(final float normalizedX, final float normalizedY, final float scale) {
    this.faceCxN = normalizedX;
    this.faceCyN = normalizedY;
    this.faceScaleN = scale;
    if(this.dp != 0) {
      this.faceCx = normalizedX * this.getWidth();
      this.faceCy = normalizedY * this.getHeight();
      this.faceScale = Math.max(MIN_FACE_SCALE, Math.min(MAX_FACE_SCALE, scale));
      this.layoutFaceCluster();
      this.invalidate();
    }
  }

  private void layout() {
    final float w = this.getWidth();
    final float h = this.getHeight();
    this.dp = this.getResources().getDisplayMetrics().density;
    this.labelPaint.setTextSize(14 * this.dp);
    this.rimPaint.setStrokeWidth(2.2f * this.dp);
    this.sheenPaint.setStrokeWidth(2.5f * this.dp);
    this.symbolPaint.setStrokeWidth(2.5f * this.dp);

    this.controls.clear();
    this.faceCtrls.clear();

    // Stick rests in the left pillarbox margin and can be anchored anywhere
    // in the left zone; buttons keep priority so the zone may overlap them.
    this.stickHomeX = w * 0.09f;
    this.stickHomeY = h * 0.70f;
    this.stickR = 60 * this.dp;
    this.stickZoneRight = w * 0.30f;
    this.stickZoneTop = h * 0.25f;
    this.stickZoneBottom = h * 0.92f;

    // Face-button diamond; position/scale come from config (drag the centre
    // gap to move, pinch to resize)
    this.faceScale = Math.max(MIN_FACE_SCALE, Math.min(MAX_FACE_SCALE, this.faceScaleN));
    this.faceCx = this.faceCxN * w;
    this.faceCy = this.faceCyN * h;
    this.faceCtrls.add(this.faceCtrl("\u2715", InputButton.A, 0, 1));    // bottom
    this.faceCtrls.add(this.faceCtrl("\u25cb", InputButton.B, 1, 0));    // right
    this.faceCtrls.add(this.faceCtrl("\u25a1", InputButton.X, -1, 0));   // left
    this.faceCtrls.add(this.faceCtrl("\u25b3", InputButton.Y, 0, -1));   // top
    this.layoutFaceCluster();
    this.controls.addAll(this.faceCtrls);

    // Shoulder pills along the top edge
    final float sw = 72 * this.dp;
    final float sh = 34 * this.dp;
    final float sy = sh / 2 + h * 0.02f;
    final Ctrl l2 = Ctrl.axis("L2", InputAxis.LEFT_TRIGGER);
    l2.x = w * 0.04f + sw / 2; l2.y = sy; l2.pw = sw; l2.ph = sh;
    this.controls.add(l2);
    final Ctrl l1 = Ctrl.button("L1", InputButton.LEFT_BUMPER);
    l1.x = l2.x + sw + 14 * this.dp; l1.y = sy; l1.pw = sw; l1.ph = sh;
    this.controls.add(l1);
    final Ctrl r2 = Ctrl.axis("R2", InputAxis.RIGHT_TRIGGER);
    r2.x = w * 0.96f - sw / 2; r2.y = sy; r2.pw = sw; r2.ph = sh;
    this.controls.add(r2);
    final Ctrl r1 = Ctrl.button("R1", InputButton.RIGHT_BUMPER);
    r1.x = r2.x - sw - 14 * this.dp; r1.y = sy; r1.pw = sw; r1.ph = sh;
    this.controls.add(r1);

    // Select / Start pills centred at the bottom edge
    final float mw = 58 * this.dp;
    final float mh = 26 * this.dp;
    final float my = h * 0.94f;
    final Ctrl select = Ctrl.button("SEL", InputButton.SELECT);
    select.x = w * 0.5f - mw / 2 - 8 * this.dp; select.y = my; select.pw = mw; select.ph = mh;
    this.controls.add(select);
    final Ctrl start = Ctrl.button("STA", InputButton.START);
    start.x = w * 0.5f + mw / 2 + 8 * this.dp; start.y = my; start.pw = mw; start.ph = mh;
    this.controls.add(start);
  }

  private void add(final Ctrl c) {
    this.controls.add(c);
  }

  private Ctrl ctrl(final String label, final InputButton button, final float x, final float y, final float r) {
    final Ctrl c = Ctrl.button(label, button);
    c.x = x; c.y = y; c.r = r;
    return c;
  }

  private Ctrl faceCtrl(final String label, final InputButton button, final float ox, final float oy) {
    final Ctrl c = Ctrl.button(label, button);
    c.ox = ox;
    c.oy = oy;
    return c;
  }

  /** Positions the face-button discs around the current centre/scale. */
  private void layoutFaceCluster() {
    final float w = this.getWidth();
    final float h = this.getHeight();
    final float off = FACE_OFF_DP * this.dp * this.faceScale;
    final float br = FACE_R_DP * this.dp * this.faceScale;
    this.faceBoundR = off + br + 14 * this.dp;

    // Keep the cluster centre where at least part of it stays reachable
    this.faceCx = Math.max(this.faceBoundR * 0.4f, Math.min(w - this.faceBoundR * 0.4f, this.faceCx));
    this.faceCy = Math.max(this.faceBoundR * 0.4f, Math.min(h - this.faceBoundR * 0.4f, this.faceCy));

    for(final Ctrl c : this.faceCtrls) {
      c.x = this.faceCx + c.ox * off;
      c.y = this.faceCy + c.oy * off;
      c.r = br;
    }
  }

  private boolean inFaceCluster(final float x, final float y) {
    final float dx = x - this.faceCx;
    final float dy = y - this.faceCy;
    return dx * dx + dy * dy <= this.faceBoundR * this.faceBoundR;
  }

  @Override
  protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
    this.layout();
  }

  /** Called from AndroidInput when a physical gamepad reports input. */
  public void onPhysicalGamepadInput() {
    this.post(() -> this.setVisibility(GONE));
  }

  /**
   * Feed a touch event through the overlay. Returns true if this gesture
   * owns a control (caller should not route it to the mouse path).
   */
  public boolean handleTouch(final MotionEvent ev) {
    if(this.getVisibility() != VISIBLE) {
      this.setVisibility(VISIBLE);
    }

    switch(ev.getActionMasked()) {
      case MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
        final int idx = ev.getActionIndex();
        final int id = ev.getPointerId(idx);
        final float x = ev.getX(idx);
        final float y = ev.getY(idx);
        if(this.faceMovePointerId != -1 && this.faceScalePointerId == -1 && this.inFaceCluster(x, y)) {
          // Second finger while dragging the cluster: pinch to resize
          this.faceScalePointerId = id;
          this.faceScaleX = x;
          this.faceScaleY = y;
          this.pinchStartDist = Math.max(1.0f, (float)Math.hypot(x - this.faceMoveX, y - this.faceMoveY));
          this.pinchStartScale = this.faceScale;
        } else if(this.hit(x, y) != null) {
          this.gestureConsumed = true;
          this.active.put(id, new float[] {x, y});
        } else if(this.stickPointerId == -1 && this.inStickZone(x, y)) {
          this.stickPointerId = id;
          this.stickAnchorX = x;
          this.stickAnchorY = y;
          this.gestureConsumed = true;
          this.active.put(id, new float[] {x, y});
          this.invalidate();
        } else if(this.faceMovePointerId == -1 && this.inFaceCluster(x, y)) {
          // Finger in the empty middle of the diamond: drag to move the cluster
          this.faceMovePointerId = id;
          this.faceMoveX = x;
          this.faceMoveY = y;
          this.grabDX = x - this.faceCx;
          this.grabDY = y - this.faceCy;
          this.gestureConsumed = true;
        }
      }

      case MotionEvent.ACTION_MOVE -> {
        for(int i = 0; i < ev.getPointerCount(); i++) {
          final int pid = ev.getPointerId(i);
          final float[] pos = this.active.get(pid);
          if(pos != null) {
            pos[0] = ev.getX(i);
            pos[1] = ev.getY(i);
          } else if(pid == this.faceMovePointerId) {
            this.faceMoveX = ev.getX(i);
            this.faceMoveY = ev.getY(i);
          } else if(pid == this.faceScalePointerId) {
            this.faceScaleX = ev.getX(i);
            this.faceScaleY = ev.getY(i);
          }
        }
        this.updateFaceGesture();
      }

      case MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
        final int idx = ev.getActionIndex();
        final int id = ev.getPointerId(idx);
        this.active.remove(id);
        if(id == this.stickPointerId) {
          this.releaseStick();
        }
        if(id == this.faceScalePointerId) {
          this.faceScalePointerId = -1;
        }
        if(id == this.faceMovePointerId) {
          this.faceMovePointerId = -1;
          this.faceScalePointerId = -1;
          this.persistFaceLayout();
        }
        if(ev.getActionMasked() == MotionEvent.ACTION_CANCEL) {
          if(this.faceMovePointerId != -1) {
            this.faceMovePointerId = -1;
            this.faceScalePointerId = -1;
            this.persistFaceLayout();
          }
        }
      }
    }

    if(!this.gestureConsumed) {
      return false;
    }

    this.updateHeldTokens();

    if(this.active.size() == 0 && this.faceMovePointerId == -1 || ev.getActionMasked() == MotionEvent.ACTION_CANCEL) {
      this.gestureConsumed = false;
    }

    return true;
  }

  /** Apply the latest move/scale pointer positions to the cluster. */
  private void updateFaceGesture() {
    if(this.faceMovePointerId == -1) {
      return;
    }

    this.faceCx = this.faceMoveX - this.grabDX;
    this.faceCy = this.faceMoveY - this.grabDY;

    if(this.faceScalePointerId != -1) {
      final float dist = Math.max(1.0f, (float)Math.hypot(this.faceScaleX - this.faceMoveX, this.faceScaleY - this.faceMoveY));
      this.faceScale = Math.max(MIN_FACE_SCALE, Math.min(MAX_FACE_SCALE, this.pinchStartScale * dist / this.pinchStartDist));
    }

    this.layoutFaceCluster();
    this.invalidate();
  }

  /** Hand the normalized cluster position/scale to the game thread for config persistence. */
  private void persistFaceLayout() {
    final float w = this.getWidth();
    final float h = this.getHeight();
    if(w > 0 && h > 0) {
      AndroidInput.reportFaceLayout(this.faceCx / w, this.faceCy / h, this.faceScale);
    }
  }

  private void releaseStick() {
    this.stickPointerId = -1;
    this.stickDeflX = 0;
    this.stickDeflY = 0;
    if(this.lastStickAxisX != 0) {
      this.lastStickAxisX = 0;
      AndroidInput.gamepadAxis(AndroidInput.TOUCH_DEVICE_ID, InputAxis.LEFT_X, 0);
    }
    if(this.lastStickAxisY != 0) {
      this.lastStickAxisY = 0;
      AndroidInput.gamepadAxis(AndroidInput.TOUCH_DEVICE_ID, InputAxis.LEFT_Y, 0);
    }
    this.invalidate();
  }

  /** Recompute deflection for the owning finger and emit changed axes. */
  private void updateStick(final float x, final float y) {
    float dx = (x - this.stickAnchorX) / this.stickR;
    float dy = (y - this.stickAnchorY) / this.stickR;
    final float len = (float)Math.sqrt(dx * dx + dy * dy);
    if(len > 1.0f) {
      dx /= len;
      dy /= len;
    }
    if(Math.abs(dx) < STICK_DEADZONE) {
      dx = 0;
    }
    if(Math.abs(dy) < STICK_DEADZONE) {
      dy = 0;
    }

    this.stickDeflX = dx;
    this.stickDeflY = dy;

    if(dx != this.lastStickAxisX) {
      this.lastStickAxisX = dx;
      AndroidInput.gamepadAxis(AndroidInput.TOUCH_DEVICE_ID, InputAxis.LEFT_X, dx);
    }
    if(dy != this.lastStickAxisY) {
      this.lastStickAxisY = dy;
      AndroidInput.gamepadAxis(AndroidInput.TOUCH_DEVICE_ID, InputAxis.LEFT_Y, dy);
    }

    this.invalidate();
  }

  private void updateHeldTokens() {
    final Set<Object> tokens = new HashSet<>();

    for(int i = 0; i < this.active.size(); i++) {
      if(this.active.keyAt(i) == this.stickPointerId) {
        final float[] pos = this.active.valueAt(i);
        this.updateStick(pos[0], pos[1]);
        continue;
      }

      final float[] pos = this.active.valueAt(i);
      final Ctrl c = this.hit(pos[0], pos[1]);
      if(c == null) {
        continue;
      }

      if(c.axis != null) {
        tokens.add(c.axis);
      } else if(c.button != null) {
        tokens.add(c.button);
      }
    }

    for(final Object token : tokens) {
      if(this.heldTokens.add(token)) {
        this.emit(token, true);
      }
    }

    final Set<Object> released = new HashSet<>(this.heldTokens);
    released.removeAll(tokens);
    for(final Object token : released) {
      this.heldTokens.remove(token);
      this.emit(token, false);
    }

    if(!released.isEmpty() || !tokens.isEmpty()) {
      this.invalidate();
    }
  }

  private void emit(final Object token, final boolean down) {
    if(token instanceof final InputButton button) {
      AndroidInput.gamepadButton(AndroidInput.TOUCH_DEVICE_ID, button, down);
    } else if(token instanceof final InputAxis axis) {
      AndroidInput.gamepadAxis(AndroidInput.TOUCH_DEVICE_ID, axis, down ? 1.0f : 0.0f);
    }
  }

  private boolean inStickZone(final float x, final float y) {
    return x < this.stickZoneRight && y > this.stickZoneTop && y < this.stickZoneBottom;
  }

  private Ctrl hit(final float x, final float y) {
    for(final Ctrl c : this.controls) {
      final float dx = x - c.x;
      final float dy = y - c.y;
      if(c.pw > 0) {
        if(Math.abs(dx) <= c.pw / 2 && Math.abs(dy) <= c.ph / 2) {
          return c;
        }
      } else if(dx * dx + dy * dy <= c.r * c.r) {
        return c;
      }
    }
    return null;
  }

  @Override
  protected void onDraw(final Canvas canvas) {
    if(this.dp == 0) {
      return;
    }

    // Stick base sits at the anchor while owned, home position otherwise
    final float baseX = this.stickPointerId != -1 ? this.stickAnchorX : this.stickHomeX;
    final float baseY = this.stickPointerId != -1 ? this.stickAnchorY : this.stickHomeY;
    this.halo(canvas, baseX, baseY, this.stickR * 1.35f, GOLD, this.a(24));
    this.discPaint.setColor(Color.argb(this.a(120), Color.red(DARK), Color.green(DARK), Color.blue(DARK)));
    canvas.drawCircle(baseX, baseY, this.stickR, this.discPaint);
    this.rimPaint.setColor(Color.argb(this.a(45), 255, 255, 255));
    canvas.drawCircle(baseX, baseY, this.stickR, this.rimPaint);
    this.rimPaint.setColor(GOLD);

    // Thumb orb: dark disc, gold rim, top sheen
    final float nx = baseX + this.stickDeflX * this.stickR;
    final float ny = baseY + this.stickDeflY * this.stickR;
    final float nr = this.stickR * 0.45f;
    this.discPaint.setColor(Color.argb(this.a(235), Color.red(DARK), Color.green(DARK), Color.blue(DARK)));
    canvas.drawCircle(nx, ny, nr, this.discPaint);
    this.rimPaint.setAlpha(this.a(255));
    canvas.drawCircle(nx, ny, nr, this.rimPaint);
    this.rimPaint.setAlpha(255);
    this.tmpRect.set(nx - nr + 4 * this.dp, ny - nr + 4 * this.dp, nx + nr - 4 * this.dp, ny + nr - 4 * this.dp);
    this.sheenPaint.setAlpha(this.a(140));
    canvas.drawArc(this.tmpRect, 200f, 140f, false, this.sheenPaint);
    this.sheenPaint.setAlpha(140);

    for(final Ctrl c : this.controls) {
      final boolean held = this.heldTokens.contains(c.button != null ? c.button : c.axis);
      if(c.pw > 0) {
        this.drawPill(canvas, c, held);
      } else {
        this.drawDiscButton(canvas, c, held);
      }
    }

    // Faint ring in the diamond's empty middle: the move/resize grip
    this.rimPaint.setAlpha(this.a(45));
    canvas.drawCircle(this.faceCx, this.faceCy, 7 * this.dp * this.faceScale, this.rimPaint);
    this.rimPaint.setAlpha(255);
  }

  /** Soft radial glow behind a control. */
  private void halo(final Canvas canvas, final float x, final float y, final float r, final int color, final int alpha) {
    this.haloPaint.setShader(new RadialGradient(x, y, r,
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)), Color.TRANSPARENT,
        Shader.TileMode.CLAMP));
    canvas.drawCircle(x, y, r, this.haloPaint);
  }

  /** Rounded-rect control (shoulders, select/start). */
  private void drawPill(final Canvas canvas, final Ctrl c, final boolean held) {
    this.discPaint.setColor(Color.argb(held ? 235 : 190, Color.red(DARK), Color.green(DARK), Color.blue(DARK)));
    this.tmpRect.set(c.x - c.pw / 2, c.y - c.ph / 2, c.x + c.pw / 2, c.y + c.ph / 2);
    canvas.drawRoundRect(this.tmpRect, c.ph / 2, c.ph / 2, this.discPaint);
    this.rimPaint.setAlpha(held ? 255 : 180);
    canvas.drawRoundRect(this.tmpRect, c.ph / 2, c.ph / 2, this.rimPaint);
    this.rimPaint.setAlpha(255);
    this.labelPaint.setAlpha(held ? 255 : 240);
    canvas.drawText(c.label, c.x, c.y - (this.labelPaint.descent() + this.labelPaint.ascent()) / 2, this.labelPaint);
  }

  /** Base alpha scaled by the configured touch-controls opacity. */
  private int a(final int base) {
    return Math.round(base * this.opacity);
  }

  /** Round control (face buttons): dark disc, gold rim, sheen, PS symbol. */
  private void drawDiscButton(final Canvas canvas, final Ctrl c, final boolean held) {
    this.halo(canvas, c.x, c.y, c.r * 1.6f, GOLD, this.a(26));
    this.discPaint.setColor(Color.argb(this.a(held ? 240 : 200), Color.red(DARK), Color.green(DARK), Color.blue(DARK)));
    canvas.drawCircle(c.x, c.y, c.r, this.discPaint);
    this.rimPaint.setAlpha(this.a(held ? 255 : 190));
    canvas.drawCircle(c.x, c.y, c.r, this.rimPaint);
    this.rimPaint.setAlpha(255);
    this.tmpRect.set(c.x - c.r + 3 * this.dp, c.y - c.r + 3 * this.dp, c.x + c.r - 3 * this.dp, c.y + c.r - 3 * this.dp);
    this.sheenPaint.setAlpha(this.a(140));
    canvas.drawArc(this.tmpRect, 200f, 130f, false, this.sheenPaint);
    this.sheenPaint.setAlpha(140);
    this.drawFaceSymbol(canvas, c);
  }

  /** Face-button symbol in the configured style, drawn procedurally. */
  private void drawFaceSymbol(final Canvas canvas, final Ctrl c) {
    if(this.faceButtonStyle == TouchFaceButtonStyle.XBOX) {
      // Xbox layout: Y top, B right, A bottom, X left
      final String letter = switch(c.button) {
        case InputButton.Y -> "Y";
        case InputButton.B -> "B";
        case InputButton.A -> "A";
        default -> "X";
      };
      this.drawLetter(canvas, c, letter, switch(c.button) {
        case InputButton.Y -> LETTER_YELLOW;
        case InputButton.B -> LETTER_RED;
        case InputButton.A -> LETTER_GREEN;
        default -> LETTER_BLUE;
      });
      return;
    }

    if(this.faceButtonStyle == TouchFaceButtonStyle.SWITCH) {
      // Nintendo layout: X top, A right, B bottom, Y left
      final String letter = switch(c.button) {
        case InputButton.Y -> "X";
        case InputButton.B -> "A";
        case InputButton.A -> "B";
        default -> "Y";
      };
      this.drawLetter(canvas, c, letter, switch(c.button) {
        case InputButton.Y -> LETTER_BLUE;
        case InputButton.B -> LETTER_RED;
        case InputButton.A -> LETTER_YELLOW;
        default -> LETTER_GREEN;
      });
      return;
    }

    // PlayStation symbols
    final float s = c.r * 0.32f;
    if(c.button == InputButton.Y) {
      this.symbolPaint.setColor(SYMBOL_TRIANGLE);
      this.symbolPaint.setAlpha(this.a(255));
      this.triPath.reset();
      this.triPath.moveTo(c.x, c.y - s);
      this.triPath.lineTo(c.x + s * 0.9f, c.y + s * 0.7f);
      this.triPath.lineTo(c.x - s * 0.9f, c.y + s * 0.7f);
      this.triPath.close();
      canvas.drawPath(this.triPath, this.symbolPaint);
    } else if(c.button == InputButton.B) {
      this.symbolPaint.setColor(SYMBOL_CIRCLE);
      this.symbolPaint.setAlpha(this.a(255));
      canvas.drawCircle(c.x, c.y, s * 0.85f, this.symbolPaint);
    } else if(c.button == InputButton.A) {
      this.symbolPaint.setColor(SYMBOL_CROSS);
      this.symbolPaint.setAlpha(this.a(255));
      canvas.drawLine(c.x - s, c.y - s, c.x + s, c.y + s, this.symbolPaint);
      canvas.drawLine(c.x + s, c.y - s, c.x - s, c.y + s, this.symbolPaint);
    } else if(c.button == InputButton.X) {
      this.symbolPaint.setColor(SYMBOL_SQUARE);
      this.symbolPaint.setAlpha(this.a(255));
      this.tmpRect.set(c.x - s * 0.85f, c.y - s * 0.85f, c.x + s * 0.85f, c.y + s * 0.85f);
      canvas.drawRect(this.tmpRect, this.symbolPaint);
    }
  }

  private void drawLetter(final Canvas canvas, final Ctrl c, final String letter, final int color) {
    this.letterPaint.setColor(color);
    this.letterPaint.setAlpha(this.a(255));
    this.letterPaint.setTextSize(c.r * 0.95f);
    canvas.drawText(letter, c.x, c.y - (this.letterPaint.descent() + this.letterPaint.ascent()) / 2, this.letterPaint);
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    if(AndroidInput.overlay == this) {
      AndroidInput.overlay = null;
    }
  }
}
