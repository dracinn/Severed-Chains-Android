package legend.game.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
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
 * On-screen gamepad overlay. Draws a translucent d-pad (left) and PlayStation
 * face-button diamond (right) plus shoulder/select/start, kept inside the
 * pillarboxed margins of the 4:3 game image. Emits gamepad button/axis events
 * (deviceId -1) into the AndroidInput queue; touches that hit nothing are
 * left for the touch-to-mouse path. Hides itself when a physical gamepad
 * reports input, reappears on the next touch.
 */
public class TouchControlsView extends View {
  private static final int ALPHA = 90; // ~35%

  /** A control zone. D-pad produces up to two direction buttons from a single touch. */
  private static final class Ctrl {
    final String label;
    float x;
    float y;
    float r;
    InputButton button;
    InputAxis axis;
    boolean dpad;

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

    static Ctrl dpad() {
      final Ctrl c = new Ctrl("");
      c.dpad = true;
      return c;
    }
  }

  private final List<Ctrl> controls = new ArrayList<>();
  private final Ctrl dpad = Ctrl.dpad();
  private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  /** pointerId -> x,y for fingers that landed on a control */
  private final SparseArray<float[]> active = new SparseArray<>();
  /** Tokens (InputButton / InputAxis) currently held by active pointers */
  private final Set<Object> heldTokens = new HashSet<>();
  private boolean gestureConsumed;

  private float dp;

  public TouchControlsView(final Context context) {
    super(context);
    this.fillPaint.setARGB(ALPHA, 40, 40, 40);
    this.fillPaint.setStyle(Paint.Style.FILL);
    this.strokePaint.setARGB(ALPHA, 255, 255, 255);
    this.strokePaint.setStyle(Paint.Style.STROKE);
    this.strokePaint.setStrokeWidth(2f);
    this.textPaint.setARGB(ALPHA + 80, 255, 255, 255);
    this.textPaint.setTextAlign(Paint.Align.CENTER);
    AndroidInput.overlay = this;
  }

  private void layout() {
    final float w = this.getWidth();
    final float h = this.getHeight();
    this.dp = this.getResources().getDisplayMetrics().density;
    this.textPaint.setTextSize(16 * this.dp);

    this.controls.clear();

    // D-pad in the left pillarbox margin
    this.dpad.x = w * 0.09f;
    this.dpad.y = h * 0.70f;
    this.dpad.r = 60 * this.dp;
    this.controls.add(this.dpad);

    // Face-button diamond in the right pillarbox margin
    final float fx = w * 0.91f;
    final float fy = h * 0.70f;
    final float off = 48 * this.dp;
    final float br = 24 * this.dp;
    this.add(this.ctrl("✕", InputButton.A, fx, fy + off, br));              // bottom
    this.add(this.ctrl("○", InputButton.B, fx + off, fy, br));              // right
    this.add(this.ctrl("□", InputButton.X, fx - off, fy, br));              // left
    this.add(this.ctrl("△", InputButton.Y, fx, fy - off, br));              // top

    // Shoulder buttons
    final Ctrl l2 = Ctrl.axis("L2", InputAxis.LEFT_TRIGGER);
    l2.x = w * 0.05f; l2.y = h * 0.07f; l2.r = 26 * this.dp;
    this.controls.add(l2);
    final Ctrl l1 = Ctrl.button("L1", InputButton.LEFT_BUMPER);
    l1.x = w * 0.05f; l1.y = h * 0.21f; l1.r = 26 * this.dp;
    this.controls.add(l1);
    final Ctrl r2 = Ctrl.axis("R2", InputAxis.RIGHT_TRIGGER);
    r2.x = w * 0.95f; r2.y = h * 0.07f; r2.r = 26 * this.dp;
    this.controls.add(r2);
    final Ctrl r1 = Ctrl.button("R1", InputButton.RIGHT_BUMPER);
    r1.x = w * 0.95f; r1.y = h * 0.21f; r1.r = 26 * this.dp;
    this.controls.add(r1);

    // Select / Start
    final Ctrl select = Ctrl.button("SEL", InputButton.SELECT);
    select.x = w * 0.10f; select.y = h * 0.94f; select.r = 18 * this.dp;
    this.controls.add(select);
    final Ctrl start = Ctrl.button("STA", InputButton.START);
    start.x = w * 0.90f; start.y = h * 0.94f; start.r = 18 * this.dp;
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
        if(this.hit(ev.getX(idx), ev.getY(idx)) != null) {
          this.gestureConsumed = true;
          this.active.put(id, new float[] {ev.getX(idx), ev.getY(idx)});
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
        this.active.remove(ev.getPointerId(idx));
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

  private void updateHeldTokens() {
    final Set<Object> tokens = new HashSet<>();

    for(int i = 0; i < this.active.size(); i++) {
      final float[] pos = this.active.valueAt(i);
      final Ctrl c = this.hit(pos[0], pos[1]);
      if(c == null) {
        continue;
      }

      if(c.dpad) {
        final float dx = (pos[0] - c.x) / c.r;
        final float dy = (pos[1] - c.y) / c.r;
        if(dx < -0.35f) {
          tokens.add(InputButton.DPAD_LEFT);
        } else if(dx > 0.35f) {
          tokens.add(InputButton.DPAD_RIGHT);
        }
        if(dy < -0.35f) {
          tokens.add(InputButton.DPAD_UP);
        } else if(dy > 0.35f) {
          tokens.add(InputButton.DPAD_DOWN);
        }
      } else if(c.axis != null) {
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

  private Ctrl hit(final float x, final float y) {
    for(final Ctrl c : this.controls) {
      final float dx = x - c.x;
      final float dy = y - c.y;
      if(dx * dx + dy * dy <= c.r * c.r) {
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

    for(final Ctrl c : this.controls) {
      if(c.dpad) {
        canvas.drawCircle(c.x, c.y, c.r, this.fillPaint);
        canvas.drawCircle(c.x, c.y, c.r, this.strokePaint);
        final float o = c.r * 0.55f;
        this.drawLabel(canvas, "▲", c.x, c.y - o);
        this.drawLabel(canvas, "▼", c.x, c.y + o);
        this.drawLabel(canvas, "◀", c.x - o, c.y);
        this.drawLabel(canvas, "▶", c.x + o, c.y);
      } else {
        final boolean held = this.heldTokens.contains(c.button != null ? c.button : c.axis);
        if(held) {
          this.fillPaint.setARGB(ALPHA + 60, 80, 80, 80);
        }
        canvas.drawCircle(c.x, c.y, c.r, this.fillPaint);
        if(held) {
          this.fillPaint.setARGB(ALPHA, 40, 40, 40);
        }
        canvas.drawCircle(c.x, c.y, c.r, this.strokePaint);
        this.drawLabel(canvas, c.label, c.x, c.y);
      }
    }
  }

  private void drawLabel(final Canvas canvas, final String label, final float x, final float y) {
    canvas.drawText(label, x, y - (this.textPaint.descent() + this.textPaint.ascent()) / 2, this.textPaint);
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    if(AndroidInput.overlay == this) {
      AndroidInput.overlay = null;
    }
  }
}
