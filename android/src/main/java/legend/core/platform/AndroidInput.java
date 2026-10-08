package legend.core.platform;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import legend.core.platform.input.InputAxis;
import legend.core.platform.input.InputButton;
import legend.core.platform.input.InputKey;
import legend.core.platform.input.InputMod;
import legend.game.android.TouchControlsView;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Translates Android key/motion events into the upstream input model and
 * queues them for consumption by {@link AndroidPlatformManager#tickInput()}
 * on the game thread. All enqueue methods are called from the UI thread;
 * tickInput drains {@link #queue} under its lock.
 */
public final class AndroidInput {
  private AndroidInput() { }

  /** Device id used for the on-screen touch gamepad. */
  public static final int TOUCH_DEVICE_ID = -1;

  /** Events consumed by AndroidPlatformManager.tickInput(). */
  public sealed interface Event {
    record Key(InputKey key, InputKey scan, Set<InputMod> mods, boolean repeat, boolean down, int codepoint) implements Event { }
    record Button(int deviceId, InputButton button, boolean down) implements Event { }
    record Axis(int deviceId, InputAxis axis, float value) implements Event { }
    record MouseMove(double x, double y) implements Event { }
    record MouseButton(int button, boolean down) implements Event { }
    record Focus(boolean focused) implements Event { }
    record Char(int codepoint) implements Event { }
    record GamepadAdded(int deviceId) implements Event { }
    record GamepadRemoved(int deviceId) implements Event { }
  }

  private static final ConcurrentLinkedQueue<Event> queue = new ConcurrentLinkedQueue<>();

  static Event poll() {
    return queue.poll();
  }

  // ------------------------------------------------------------------
  // Overlay show/hide hooks (set by TouchControlsView)
  // ------------------------------------------------------------------

  @Nullable
  public static TouchControlsView overlay;

  private static void notifyPhysicalGamepadInput() {
    final TouchControlsView view = overlay;
    if(view != null) {
      view.onPhysicalGamepadInput();
    }
  }

  // ------------------------------------------------------------------
  // Producers (UI thread)
  // ------------------------------------------------------------------

  public static void focus(final boolean focused) {
    queue.offer(new Event.Focus(focused));
  }

  public static void mouseMove(final double x, final double y) {
    queue.offer(new Event.MouseMove(x, y));
  }

  public static void mouseButton(final int button, final boolean down) {
    queue.offer(new Event.MouseButton(button, down));
  }

  /** Enqueue a typed code point (from the IME InputConnection). */
  public static void charInput(final int codepoint) {
    queue.offer(new Event.Char(codepoint));
  }

  /** Enqueue a synthetic key press+release for a KEYCODE_* (IME editing keys). */
  public static void keyPressRelease(final int keyCode) {
    final InputKey key = KEY_MAP.get(keyCode);
    if(key == null) {
      return;
    }

    final Set<InputMod> mods = Set.of();
    queue.offer(new Event.Key(key, key, mods, false, true, 0));
    queue.offer(new Event.Key(key, key, mods, false, false, 0));
  }

  public static void gamepadButton(final int deviceId, final InputButton button, final boolean down) {
    if(deviceId != TOUCH_DEVICE_ID) {
      notifyPhysicalGamepadInput();
    }
    queue.offer(new Event.Button(deviceId, button, down));
  }

  public static void gamepadAxis(final int deviceId, final InputAxis axis, final float value) {
    if(deviceId != TOUCH_DEVICE_ID) {
      notifyPhysicalGamepadInput();
    }
    queue.offer(new Event.Axis(deviceId, axis, value));
  }

  public static void gamepadAdded(final int deviceId) {
    queue.offer(new Event.GamepadAdded(deviceId));
  }

  public static void gamepadRemoved(final int deviceId) {
    queue.offer(new Event.GamepadRemoved(deviceId));
  }

  /** Rescan all connected devices; enqueue added events for gamepads. */
  public static void scanGamepads() {
    for(final int id : InputDevice.getDeviceIds()) {
      final InputDevice device = InputDevice.getDevice(id);
      if(isGamepad(device)) {
        gamepadAdded(id);
      }
    }
  }

  public static boolean isGamepad(@Nullable final InputDevice device) {
    if(device == null || device.isVirtual()) {
      return false;
    }

    final int sources = device.getSources();
    return (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
      || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
  }

  private static boolean isGamepadSource(final int source) {
    return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
      || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
  }

  // ------------------------------------------------------------------
  // KeyEvent handling
  // ------------------------------------------------------------------

  /** @return true if the event was consumed (queue an input event). */
  public static boolean handleKeyEvent(final KeyEvent event) {
    final int action = event.getAction();
    if(action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) {
      return false;
    }

    final boolean down = action == KeyEvent.ACTION_DOWN;
    final int keyCode = event.getKeyCode();

    if(isGamepadSource(event.getSource()) || isGamepad(event.getDevice())) {
      return handleGamepadKey(event, keyCode, down);
    }

    // Keyboard-class source
    InputKey key = KEY_MAP.get(keyCode);
    if(key == null && keyCode == KeyEvent.KEYCODE_BACK) {
      // Menu-back is ESCAPE upstream; consume so the Activity doesn't finish
      key = InputKey.ESCAPE;
    }

    if(key == null) {
      return false;
    }

    final Set<InputMod> mods = modsFromEvent(event);
    final boolean repeat = down && event.getRepeatCount() > 0;
    final int codepoint = down ? event.getUnicodeChar() : 0;
    queue.offer(new Event.Key(key, key, mods, repeat, down, codepoint));
    return true;
  }

  private static boolean handleGamepadKey(final KeyEvent event, final int keyCode, final boolean down) {
    final InputButton button = GAMEPAD_BUTTON_MAP.get(keyCode);
    if(button != null) {
      gamepadButton(event.getDeviceId(), button, down);
      return true;
    }

    // Trigger key events are only needed for devices without trigger axes
    if(keyCode == KeyEvent.KEYCODE_BUTTON_L2 || keyCode == KeyEvent.KEYCODE_BUTTON_R2) {
      final InputDevice device = event.getDevice();
      final InputAxis axis = keyCode == KeyEvent.KEYCODE_BUTTON_L2 ? InputAxis.LEFT_TRIGGER : InputAxis.RIGHT_TRIGGER;
      final boolean hasAxis;
      if(device == null) {
        hasAxis = false;
      } else if(axis == InputAxis.LEFT_TRIGGER) {
        hasAxis = device.getMotionRange(MotionEvent.AXIS_LTRIGGER) != null || device.getMotionRange(MotionEvent.AXIS_BRAKE) != null;
      } else {
        hasAxis = device.getMotionRange(MotionEvent.AXIS_RTRIGGER) != null || device.getMotionRange(MotionEvent.AXIS_GAS) != null;
      }

      if(!hasAxis) {
        gamepadAxis(event.getDeviceId(), axis, down ? 1.0f : 0.0f);
      }
      return true;
    }

    if(keyCode == KeyEvent.KEYCODE_BACK) {
      // Most controllers map the system back button to B
      gamepadButton(event.getDeviceId(), InputButton.B, down);
      return true;
    }

    return false;
  }

  private static Set<InputMod> modsFromEvent(final KeyEvent event) {
    final Set<InputMod> mods = EnumSet.noneOf(InputMod.class);
    if(event.isShiftPressed()) {
      mods.add(InputMod.SHIFT);
    }
    if(event.isCtrlPressed()) {
      mods.add(InputMod.CTRL);
    }
    if(event.isAltPressed()) {
      mods.add(InputMod.ALT);
    }
    return mods;
  }

  // ------------------------------------------------------------------
  // MotionEvent (gamepad axes) handling
  // ------------------------------------------------------------------

  /** deviceId -> last seen value per axis */
  private static final Map<Integer, Map<InputAxis, Float>> lastAxisValues = new HashMap<>();
  /** deviceId -> hat-driven dpad buttons currently held */
  private static final Map<Integer, Set<InputButton>> hatButtons = new HashMap<>();

  /** @return true if this is a gamepad/joystick motion event (consumed). */
  public static boolean handleGenericMotionEvent(final MotionEvent event) {
    if(event.getAction() != MotionEvent.ACTION_MOVE || !isGamepadSource(event.getSource())) {
      return false;
    }

    final int deviceId = event.getDeviceId();

    axisIfChanged(event, deviceId, MotionEvent.AXIS_X, InputAxis.LEFT_X);
    axisIfChanged(event, deviceId, MotionEvent.AXIS_Y, InputAxis.LEFT_Y);
    axisIfChanged(event, deviceId, MotionEvent.AXIS_Z, InputAxis.RIGHT_X);
    axisIfChanged(event, deviceId, MotionEvent.AXIS_RZ, InputAxis.RIGHT_Y);

    // Some controllers report triggers as brake/gas instead of l/rtrigger
    axisIfChanged(event, deviceId, InputAxis.LEFT_TRIGGER,
      Math.max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)));
    axisIfChanged(event, deviceId, InputAxis.RIGHT_TRIGGER,
      Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)));

    // Many controllers report the d-pad as hat axes
    final float hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
    final float hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
    final Set<InputButton> held = hatButtons.computeIfAbsent(deviceId, id -> new HashSet<>());
    updateHatButton(deviceId, held, InputButton.DPAD_LEFT, hatX < -0.5f);
    updateHatButton(deviceId, held, InputButton.DPAD_RIGHT, hatX > 0.5f);
    updateHatButton(deviceId, held, InputButton.DPAD_UP, hatY < -0.5f);
    updateHatButton(deviceId, held, InputButton.DPAD_DOWN, hatY > 0.5f);

    return true;
  }

  private static void axisIfChanged(final MotionEvent event, final int deviceId, final int axisCode, final InputAxis axis) {
    axisIfChanged(event, deviceId, axis, event.getAxisValue(axisCode));
  }

  private static void axisIfChanged(final MotionEvent event, final int deviceId, final InputAxis axis, final float value) {
    final Map<InputAxis, Float> last = lastAxisValues.computeIfAbsent(deviceId, id -> new EnumMap<>(InputAxis.class));
    final Float prev = last.get(axis);
    if(prev == null || prev != value) {
      last.put(axis, value);
      gamepadAxis(deviceId, axis, value);
    }
  }

  private static void updateHatButton(final int deviceId, final Set<InputButton> held, final InputButton button, final boolean pressed) {
    if(pressed == held.contains(button)) {
      return;
    }

    if(pressed) {
      held.add(button);
    } else {
      held.remove(button);
    }
    gamepadButton(deviceId, button, pressed);
  }

  /** Drop per-device state when a gamepad goes away. */
  public static void forgetDevice(final int deviceId) {
    lastAxisValues.remove(deviceId);
    hatButtons.remove(deviceId);
  }

  // ------------------------------------------------------------------
  // Key/button lookup tables
  // ------------------------------------------------------------------

  private static final Map<Integer, InputKey> KEY_MAP = new HashMap<>();
  private static final Map<Integer, InputButton> GAMEPAD_BUTTON_MAP = new HashMap<>();

  private static void map(final int keyCode, final InputKey key) {
    KEY_MAP.put(keyCode, key);
  }

  private static void mapPad(final int keyCode, final InputButton button) {
    GAMEPAD_BUTTON_MAP.put(keyCode, button);
  }

  static {
    // Letters
    for(int i = 0; i < 26; i++) {
      map(KeyEvent.KEYCODE_A + i, InputKey.values()[InputKey.A.ordinal() + i]);
    }

    // Top-row digits
    for(int i = 0; i < 10; i++) {
      map(KeyEvent.KEYCODE_0 + i, InputKey.values()[InputKey.NUM_0.ordinal() + i]);
    }

    // Function keys
    for(int i = 0; i < 12; i++) {
      map(KeyEvent.KEYCODE_F1 + i, InputKey.values()[InputKey.F1.ordinal() + i]);
    }

    // Numpad digits (KP_0 is declared after KP_9 in InputKey)
    for(int i = 0; i < 10; i++) {
      map(KeyEvent.KEYCODE_NUMPAD_0 + i, i == 0 ? InputKey.KP_0 : InputKey.values()[InputKey.KP_1.ordinal() + i - 1]);
    }

    map(KeyEvent.KEYCODE_ENTER, InputKey.RETURN);
    map(KeyEvent.KEYCODE_NUMPAD_ENTER, InputKey.KP_ENTER);
    map(KeyEvent.KEYCODE_ESCAPE, InputKey.ESCAPE);
    map(KeyEvent.KEYCODE_DEL, InputKey.BACKSPACE);
    map(KeyEvent.KEYCODE_FORWARD_DEL, InputKey.DELETE);
    map(KeyEvent.KEYCODE_TAB, InputKey.TAB);
    map(KeyEvent.KEYCODE_SPACE, InputKey.SPACE);
    map(KeyEvent.KEYCODE_INSERT, InputKey.INSERT);
    map(KeyEvent.KEYCODE_MOVE_HOME, InputKey.HOME);
    map(KeyEvent.KEYCODE_MOVE_END, InputKey.END);
    map(KeyEvent.KEYCODE_PAGE_UP, InputKey.PAGE_UP);
    map(KeyEvent.KEYCODE_PAGE_DOWN, InputKey.PAGE_DOWN);
    map(KeyEvent.KEYCODE_SYSRQ, InputKey.PRINT_SCREEN);
    map(KeyEvent.KEYCODE_SCROLL_LOCK, InputKey.SCROLL_LOCK);
    map(KeyEvent.KEYCODE_BREAK, InputKey.PAUSE);

    // Keyboard-source d-pad behaves as arrow keys
    map(KeyEvent.KEYCODE_DPAD_UP, InputKey.UP);
    map(KeyEvent.KEYCODE_DPAD_DOWN, InputKey.DOWN);
    map(KeyEvent.KEYCODE_DPAD_LEFT, InputKey.LEFT);
    map(KeyEvent.KEYCODE_DPAD_RIGHT, InputKey.RIGHT);
    map(KeyEvent.KEYCODE_DPAD_CENTER, InputKey.RETURN);

    // Punctuation
    map(KeyEvent.KEYCODE_COMMA, InputKey.COMMA);
    map(KeyEvent.KEYCODE_PERIOD, InputKey.PERIOD);
    map(KeyEvent.KEYCODE_SLASH, InputKey.SLASH);
    map(KeyEvent.KEYCODE_BACKSLASH, InputKey.BACKSLASH);
    map(KeyEvent.KEYCODE_SEMICOLON, InputKey.SEMICOLON);
    map(KeyEvent.KEYCODE_APOSTROPHE, InputKey.APOSTROPHE);
    map(KeyEvent.KEYCODE_LEFT_BRACKET, InputKey.LEFT_BRACKET);
    map(KeyEvent.KEYCODE_RIGHT_BRACKET, InputKey.RIGHT_BRACKET);
    map(KeyEvent.KEYCODE_GRAVE, InputKey.GRAVE);
    map(KeyEvent.KEYCODE_MINUS, InputKey.MINUS);
    map(KeyEvent.KEYCODE_EQUALS, InputKey.EQUALS);
    map(KeyEvent.KEYCODE_PLUS, InputKey.PLUS);
    map(KeyEvent.KEYCODE_STAR, InputKey.ASTERISK);
    map(KeyEvent.KEYCODE_POUND, InputKey.HASH);
    map(KeyEvent.KEYCODE_AT, InputKey.AT);

    // Numpad operators
    map(KeyEvent.KEYCODE_NUMPAD_DIVIDE, InputKey.KP_DIVIDE);
    map(KeyEvent.KEYCODE_NUMPAD_MULTIPLY, InputKey.KP_MULTIPLY);
    map(KeyEvent.KEYCODE_NUMPAD_SUBTRACT, InputKey.KP_MINUS);
    map(KeyEvent.KEYCODE_NUMPAD_ADD, InputKey.KP_PLUS);
    map(KeyEvent.KEYCODE_NUMPAD_DOT, InputKey.KP_PERIOD);
    map(KeyEvent.KEYCODE_NUMPAD_EQUALS, InputKey.KP_EQUALS);

    // Modifiers
    map(KeyEvent.KEYCODE_SHIFT_LEFT, InputKey.LEFT_SHIFT);
    map(KeyEvent.KEYCODE_SHIFT_RIGHT, InputKey.RIGHT_SHIFT);
    map(KeyEvent.KEYCODE_CTRL_LEFT, InputKey.LEFT_CTRL);
    map(KeyEvent.KEYCODE_CTRL_RIGHT, InputKey.RIGHT_CTRL);
    map(KeyEvent.KEYCODE_ALT_LEFT, InputKey.LEFT_ALT);
    map(KeyEvent.KEYCODE_ALT_RIGHT, InputKey.RIGHT_ALT);
    map(KeyEvent.KEYCODE_META_LEFT, InputKey.LEFT_GUI);
    map(KeyEvent.KEYCODE_META_RIGHT, InputKey.RIGHT_GUI);

    // Gamepad buttons (gamepad/joystick source only)
    mapPad(KeyEvent.KEYCODE_BUTTON_A, InputButton.A);
    mapPad(KeyEvent.KEYCODE_BUTTON_B, InputButton.B);
    mapPad(KeyEvent.KEYCODE_BUTTON_X, InputButton.X);
    mapPad(KeyEvent.KEYCODE_BUTTON_Y, InputButton.Y);
    mapPad(KeyEvent.KEYCODE_BUTTON_START, InputButton.START);
    mapPad(KeyEvent.KEYCODE_BUTTON_SELECT, InputButton.SELECT);
    mapPad(KeyEvent.KEYCODE_BUTTON_L1, InputButton.LEFT_BUMPER);
    mapPad(KeyEvent.KEYCODE_BUTTON_R1, InputButton.RIGHT_BUMPER);
    mapPad(KeyEvent.KEYCODE_BUTTON_THUMBL, InputButton.LEFT_STICK);
    mapPad(KeyEvent.KEYCODE_BUTTON_THUMBR, InputButton.RIGHT_STICK);
    mapPad(KeyEvent.KEYCODE_BUTTON_MODE, InputButton.GUIDE);
    mapPad(KeyEvent.KEYCODE_DPAD_UP, InputButton.DPAD_UP);
    mapPad(KeyEvent.KEYCODE_DPAD_DOWN, InputButton.DPAD_DOWN);
    mapPad(KeyEvent.KEYCODE_DPAD_LEFT, InputButton.DPAD_LEFT);
    mapPad(KeyEvent.KEYCODE_DPAD_RIGHT, InputButton.DPAD_RIGHT);
  }

  /** Human-readable label for a key (settings screens). */
  public static String keyName(final InputKey key) {
    return key.name().replace('_', ' ');
  }
}
