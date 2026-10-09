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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * On-screen gamepad overlay. Draws a floating analog stick (left) and a
 * PlayStation face-button diamond (right) plus shoulder/select/start, kept
 * inside the pillarboxed margins of the 4:3 game image. The stick is not
 * fixed: a touch anywhere in the left zone anchors it under the thumb and
 * drags emit LEFT_X/LEFT_Y axis values. Emits gamepad button/axis events
 * (deviceId -1) into the AndroidInput queue; touches that hit nothing are
 * left for the touch-to-mouse path. Hides itself when a physical gamepad
 * reports input, reappears on the next touch.
 */
public class TouchControlsView extends View {
  /** Fraction of stick radius below which a deflection reads as centred. */
  private static final float STICK_DEADZONE = 0.06f;

  private static final int GOLD = Color.rgb(212, 175, 55);
  private static final int CREAM = Color.rgb(235, 225, 200);
  private static final int DARK = Color.rgb(20, 20, 28);
  private static final int SYMBOL_TRIANGLE = Color.rgb(140, 230, 210);
  private static final int SYMBOL_CROSS = Color.rgb(150, 180, 255);
  private static final int SYMBOL_SQUARE = Color.rgb(255, 160, 200);
  private static final int SYMBOL_CIRCLE = Color.rgb(255, 140, 140);

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
  private final Path triPath = new Path();
  private final RectF tmpRect = new RectF();

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
    AndroidInput.overlay = this;
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

    // Stick rests in the left pillarbox margin and can be anchored anywhere
    // in the left zone; buttons keep priority so the zone may overlap them.
    this.stickHomeX = w * 0.09f;
    this.stickHomeY = h * 0.70f;
    this.stickR = 60 * this.dp;
    this.stickZoneRight = w * 0.30f;
    this.stickZoneTop = h * 0.25f;
    this.stickZoneBottom = h * 0.92f;

    // Face-button diamond in the right pillarbox margin
    final float fx = w * 0.91f;
    final float fy = h * 0.70f;
    final float off = 48 * this.dp;
    final float br = 26 * this.dp;
    this.add(this.ctrl("\u2715", InputButton.A, fx, fy + off, br));              // bottom
    this.add(this.ctrl("\u25cb", InputButton.B, fx + off, fy, br));              // right
    this.add(this.ctrl("\u25a1", InputButton.X, fx - off, fy, br));              // left
    this.add(this.ctrl("\u25b3", InputButton.Y, fx, fy - off, br));              // top

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
        if(this.hit(x, y) != null) {
          this.gestureConsumed = true;
          this.active.put(id, new float[] {x, y});
        } else if(this.stickPointerId == -1 && this.inStickZone(x, y)) {
          this.stickPointerId = id;
          this.stickAnchorX = x;
          this.stickAnchorY = y;
          this.gestureConsumed = true;
          this.active.put(id, new float[] {x, y});
          this.invalidate();
        }
      }

      case MotionEvent.ACTION_MOVE -> {
        for(int i = 0; i < ev.getPointerCount(); i++) {
          final float[] pos = this.active.get(ev.getPointerId(i));
          if(pos != null) {
            pos[0] = ev.getX(i);
            pos[1] = ev.getY(i);
          }
        }
      }

      case MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
        final int idx = ev.getActionIndex();
        final int id = ev.getPointerId(idx);
        this.active.remove(id);
        if(id == this.stickPointerId) {
          this.releaseStick();
        }
      }
    }

    if(!this.gestureConsumed) {
      return false;
    }

    this.updateHeldTokens();

    if(this.active.size() == 0 || ev.getActionMasked() == MotionEvent.ACTION_CANCEL) {
      this.gestureConsumed = false;
    }

    return true;
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
    this.halo(canvas, baseX, baseY, this.stickR * 1.35f, GOLD, 24);
    this.discPaint.setColor(Color.argb(120, Color.red(DARK), Color.green(DARK), Color.blue(DARK)));
    canvas.drawCircle(baseX, baseY, this.stickR, this.discPaint);
    this.rimPaint.setColor(Color.argb(45, 255, 255, 255));
    canvas.drawCircle(baseX, baseY, this.stickR, this.rimPaint);
    this.rimPaint.setColor(GOLD);

    // Thumb orb: dark disc, gold rim, top sheen
    final float nx = baseX + this.stickDeflX * this.stickR;
    final float ny = baseY + this.stickDeflY * this.stickR;
    final float nr = this.stickR * 0.45f;
    this.discPaint.setColor(Color.argb(235, Color.red(DARK), Color.green(DARK), Color.blue(DARK)));
    canvas.drawCircle(nx, ny, nr, this.discPaint);
    canvas.drawCircle(nx, ny, nr, this.rimPaint);
    this.tmpRect.set(nx - nr + 4 * this.dp, ny - nr + 4 * this.dp, nx + nr - 4 * this.dp, ny + nr - 4 * this.dp);
    canvas.drawArc(this.tmpRect, 200f, 140f, false, this.sheenPaint);

    for(final Ctrl c : this.controls) {
      final boolean held = this.heldTokens.contains(c.button != null ? c.button : c.axis);
      if(c.pw > 0) {
        this.drawPill(canvas, c, held);
      } else {
        this.drawDiscButton(canvas, c, held);
      }
    }
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

  /** Round control (face buttons): dark disc, gold rim, sheen, PS symbol. */
  private void drawDiscButton(final Canvas canvas, final Ctrl c, final boolean held) {
    this.halo(canvas, c.x, c.y, c.r * 1.6f, GOLD, 26);
    this.discPaint.setColor(Color.argb(held ? 240 : 200, Color.red(DARK), Color.green(DARK), Color.blue(DARK)));
    canvas.drawCircle(c.x, c.y, c.r, this.discPaint);
    this.rimPaint.setAlpha(held ? 255 : 190);
    canvas.drawCircle(c.x, c.y, c.r, this.rimPaint);
    this.rimPaint.setAlpha(255);
    this.tmpRect.set(c.x - c.r + 3 * this.dp, c.y - c.r + 3 * this.dp, c.x + c.r - 3 * this.dp, c.y + c.r - 3 * this.dp);
    canvas.drawArc(this.tmpRect, 200f, 130f, false, this.sheenPaint);
    this.drawFaceSymbol(canvas, c);
  }

  /** PlayStation symbol inside a face button, drawn procedurally. */
  private void drawFaceSymbol(final Canvas canvas, final Ctrl c) {
    final float s = c.r * 0.32f;
    if(c.button == InputButton.Y) {
      this.symbolPaint.setColor(SYMBOL_TRIANGLE);
      this.triPath.reset();
      this.triPath.moveTo(c.x, c.y - s);
      this.triPath.lineTo(c.x + s * 0.9f, c.y + s * 0.7f);
      this.triPath.lineTo(c.x - s * 0.9f, c.y + s * 0.7f);
      this.triPath.close();
      canvas.drawPath(this.triPath, this.symbolPaint);
    } else if(c.button == InputButton.B) {
      this.symbolPaint.setColor(SYMBOL_CIRCLE);
      canvas.drawCircle(c.x, c.y, s * 0.85f, this.symbolPaint);
    } else if(c.button == InputButton.A) {
      this.symbolPaint.setColor(SYMBOL_CROSS);
      canvas.drawLine(c.x - s, c.y - s, c.x + s, c.y + s, this.symbolPaint);
      canvas.drawLine(c.x + s, c.y - s, c.x - s, c.y + s, this.symbolPaint);
    } else if(c.button == InputButton.X) {
      this.symbolPaint.setColor(SYMBOL_SQUARE);
      this.tmpRect.set(c.x - s * 0.85f, c.y - s * 0.85f, c.x + s * 0.85f, c.y + s * 0.85f);
      canvas.drawRect(this.tmpRect, this.symbolPaint);
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    if(AndroidInput.overlay == this) {
      AndroidInput.overlay = null;
    }
  }
}
