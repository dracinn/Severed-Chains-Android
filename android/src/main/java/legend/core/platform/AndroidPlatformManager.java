package legend.core.platform;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.CombinedVibration;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.InputDevice;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2FloatMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import legend.core.platform.input.AxisInputActivation;
import legend.core.platform.input.ButtonInputActivation;
import legend.core.platform.input.InputAction;
import legend.core.platform.input.InputActionState;
import legend.core.platform.input.InputAxis;
import legend.core.platform.input.InputAxisDirection;
import legend.core.platform.input.InputBinding;
import legend.core.platform.input.InputBindings;
import legend.core.platform.input.InputButton;
import legend.core.platform.input.InputClass;
import legend.core.platform.input.InputGamepadType;
import legend.core.platform.input.InputKey;
import legend.core.platform.input.KeyInputActivation;
import legend.core.platform.input.ScancodeInputActivation;
import legend.game.android.AndroidEnv;
import legend.game.modding.events.input.InputPressedEvent;
import legend.game.modding.events.input.InputReleasedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.joml.Math;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static legend.core.GameEngine.CONFIG;
import static legend.core.GameEngine.EVENTS;
import static legend.game.modding.coremod.CoreMod.MENU_INNER_DEADZONE_CONFIG;
import static legend.game.modding.coremod.CoreMod.MENU_OUTER_DEADZONE_CONFIG;
import static legend.game.modding.coremod.CoreMod.MOVEMENT_INNER_DEADZONE_CONFIG;
import static legend.game.modding.coremod.CoreMod.MOVEMENT_OUTER_DEADZONE_CONFIG;
import static legend.game.modding.coremod.CoreMod.RECEIVE_INPUT_ON_INACTIVE_WINDOW_CONFIG;

/**
 * Android PlatformManager. Mirrors upstream SdlPlatformManager's tickInput()
 * as closely as possible; instead of polling SDL events it drains the
 * {@link AndroidInput} queue which the Activity feeds on the UI thread.
 * Binding matching uses enum equality rather than SDL code comparisons.
 * Axis values arrive as -1..1 floats and are converted back to the SDL
 * 0x7fff scale so the deadzone maths is identical.
 */
public class AndroidPlatformManager extends PlatformManager {
  private static final Logger LOGGER = LogManager.getFormatterLogger(AndroidPlatformManager.class);
  private static final Marker INPUT_MARKER = MarkerManager.getMarker("INPUT");
  private static final Marker ACTIONS_MARKER = MarkerManager.getMarker("ACTIONS");

  private static final Object INPUT_LOCK = new Object();

  private final List<AndroidWindow> windows = new ArrayList<>();
  private AndroidWindow lastActiveWindow;

  private final Set<InputAction> pressed = new HashSet<>();
  private final Map<InputAction, InputActionState> actionStates = new HashMap<>();
  private final Map<InputAction, AxisInputState> axisActionStates = new HashMap<>();
  private boolean clearActionStates;

  private final IntSet gamepads = new IntOpenHashSet();
  private int lastGamepad = -1;
  private int lastRumbleGamepad = -1;

  private float rumbleBigCurrentIntensity;
  private float rumbleSmallCurrentIntensity;
  private float rumbleBigStartingIntensity;
  private float rumbleSmallStartingIntensity;
  private float rumbleBigEndingIntensity;
  private float rumbleSmallEndingIntensity;
  private long rumbleLerpStart;
  private long rumbleLerpDuration;

  @Override
  public void init() {
    this.gamepads.clear();
    AndroidInput.scanGamepads();
  }

  @Override
  public boolean isContextCurrent() {
    return android.opengl.EGL14.eglGetCurrentContext() != android.opengl.EGL14.EGL_NO_CONTEXT;
  }

  @Override
  public boolean hasGamepad() {
    return !this.gamepads.isEmpty();
  }

  @Override
  public InputGamepadType getGamepadType() {
    return InputGamepadType.STANDARD;
  }

  @Override
  public int getMouseButton(final int index) {
    return 1 + index;
  }

  @Override
  public String[] listDisplays() {
    return new String[] {"Display"};
  }

  @Override
  protected Window createWindow(final String title, final int width, final int height) {
    final AndroidWindow window = new AndroidWindow(this);
    this.windows.add(window);
    this.lastActiveWindow = window;
    return window;
  }

  @Override
  protected void removeWindows(final Collection<Window> windows) {
    this.windows.removeAll(windows);
  }

  @Override
  public AndroidWindow getLastWindow() {
    return this.lastActiveWindow;
  }

  @Override
  public void resetActionStates() {
    this.clearActionStates = true;
  }

  private final LongSet axesHeld = new LongOpenHashSet();

  @Override
  protected void tickInput() {
    synchronized(INPUT_LOCK) {
      AndroidInput.Event event;
      while((event = AndroidInput.poll()) != null) {
        switch(event) {
          case AndroidInput.Event.Focus focus -> {
            final AndroidWindow window = this.getWindow();
            if(window != null) {
              if(focus.focused()) {
                this.lastActiveWindow = window;
              }
              window.hasFocus = focus.focused();
              window.events().onFocus(focus.focused());
            }
          }

          case AndroidInput.Event.Key key -> {
            if(LOGGER.isInfoEnabled(INPUT_MARKER)) {
              if(key.down()) {
                LOGGER.info(INPUT_MARKER, "Key down %s mods %s repeat %b", key.key(), key.mods(), key.repeat());
              } else {
                LOGGER.info(INPUT_MARKER, "Key up %s mods %s repeat %b", key.key(), key.mods(), key.repeat());
              }
            }

            final AndroidWindow window = this.getWindow();

            if(window == null) {
              continue;
            }

            this.setWindowInputClass(window, InputClass.KEYBOARD);
            window.mods.clear();
            window.mods.addAll(key.mods());

            final InputKey inputKey = key.key();
            final InputKey inputScan = key.scan();

            if(inputKey != null || inputScan != null) {
              if(key.down()) {
                if(LOGGER.isInfoEnabled(ACTIONS_MARKER)) {
                  LOGGER.info(ACTIONS_MARKER, "Triggering press key %s -> %s", key.key(), inputKey);
                }

                window.events().onKeyPress(inputKey, inputScan, window.mods, key.repeat());
              } else {
                if(LOGGER.isInfoEnabled(ACTIONS_MARKER)) {
                  LOGGER.info(ACTIONS_MARKER, "Triggering release key %s -> %s", key.key(), inputKey);
                }

                window.events().onKeyRelease(inputKey, inputScan, window.mods);
              }
            }

            final List<InputBinding<KeyInputActivation>> keycodeBindings = InputBindings.getBindings(KeyInputActivation.class);

            for(int i = 0; i < keycodeBindings.size(); i++) {
              final InputBinding<KeyInputActivation> binding = keycodeBindings.get(i);

              if(binding.activation.key == inputKey) {
                if(key.down()) {
                  if((binding.activation.mods.isEmpty() || window.mods.containsAll(binding.activation.mods)) && !key.repeat()) {
                    if(LOGGER.isInfoEnabled(ACTIONS_MARKER)) {
                      LOGGER.info(ACTIONS_MARKER, "Triggering press input key %s -> %s -> %s", key.key(), inputKey, binding.action);
                    }

                    this.pressed.add(binding.action);
                    this.triggerBindingPress(binding);
                  }
                } else {
                  if(LOGGER.isInfoEnabled(ACTIONS_MARKER)) {
                    LOGGER.info(ACTIONS_MARKER, "Triggering release input key %s -> %s -> %s", key.key(), inputKey, binding.action);
                  }

                  this.triggerBindingRelease(binding);
                }
              }
            }

            final List<InputBinding<ScancodeInputActivation>> scancodeBindings = InputBindings.getBindings(ScancodeInputActivation.class);

            for(int i = 0; i < scancodeBindings.size(); i++) {
              final InputBinding<ScancodeInputActivation> binding = scancodeBindings.get(i);

              if(binding.activation.key == inputScan) {
                if(key.down()) {
                  if((binding.activation.mods.isEmpty() || window.mods.containsAll(binding.activation.mods)) && !key.repeat()) {
                    if(LOGGER.isInfoEnabled(ACTIONS_MARKER)) {
                      LOGGER.info(ACTIONS_MARKER, "Triggering press input action scancode %s -> %s", key.scan(), binding.action);
                    }

                    this.pressed.add(binding.action);
                    window.events().onInputActionPressed(binding.action, false);
                    this.getInputActionState(binding.action).press();
                    EVENTS.postEvent(new InputPressedEvent(binding.action, false));
                  }
                } else {
                  if(LOGGER.isInfoEnabled(ACTIONS_MARKER)) {
                    LOGGER.info(ACTIONS_MARKER, "Triggering release input action scancode %s -> %s", key.scan(), binding.action);
                  }

                  window.events().onInputActionReleased(binding.action);
                  this.getInputActionState(binding.action).release();
                  EVENTS.postEvent(new InputReleasedEvent(binding.action));
                }
              }
            }

            if(key.down() && key.codepoint() != 0) {
              window.events().onChar(key.codepoint());
            }
          }

          case AndroidInput.Event.Char c -> {
            final AndroidWindow window = this.getWindow();
            if(window != null) {
              window.events().onChar(c.codepoint());
            }
          }

          case AndroidInput.Event.MouseMove mouse -> {
            final AndroidWindow window = this.getWindow();

            if(window != null) {
              this.setWindowInputClass(window, InputClass.KEYBOARD);
              window.events().onMouseMove(mouse.x(), mouse.y());
            }
          }

          case AndroidInput.Event.MouseButton mouse -> {
            final AndroidWindow window = this.getWindow();

            if(window != null) {
              this.setWindowInputClass(window, InputClass.KEYBOARD);

              if(mouse.down()) {
                window.events().onMousePress(mouse.button(), window.mods);
              } else {
                window.events().onMouseRelease(mouse.button(), window.mods);
              }
            }
          }

          case AndroidInput.Event.GamepadAdded added -> {
            this.gamepads.add(added.deviceId());
            this.lastGamepad = added.deviceId();
            LOGGER.info("Gamepad %d connected", added.deviceId());

            final AndroidWindow window = this.getWindow();
            if(window != null) {
              window.events().onControllerConnected(added.deviceId());
            }
          }

          case AndroidInput.Event.GamepadRemoved removed -> {
            if(this.gamepads.remove(removed.deviceId())) {
              LOGGER.info("Gamepad %d disconnected", removed.deviceId());

              if(this.lastGamepad == removed.deviceId()) {
                this.lastGamepad = -1;
              }

              AndroidInput.forgetDevice(removed.deviceId());

              final AndroidWindow window = this.getWindow();
              if(window != null) {
                window.events().onControllerDisconnected(removed.deviceId());
              }
            }

            this.removeGamepadAxisInputs(removed.deviceId());
          }

          case AndroidInput.Event.Axis axis -> {
            final int rawValue = Math.round(axis.value() * 0x7fff);

            if(LOGGER.isInfoEnabled(INPUT_MARKER) && Math.abs(rawValue) > 1000) {
              LOGGER.info(INPUT_MARKER, "Axis gamepad %d axis %s value %d", axis.deviceId(), axis.axis(), rawValue);
            }

            if(this.lastActiveWindow != null && (this.lastActiveWindow.hasFocus || CONFIG.getConfig(RECEIVE_INPUT_ON_INACTIVE_WINDOW_CONFIG.get()))) {
              final float menuInnerDeadzone = CONFIG.getConfig(MENU_INNER_DEADZONE_CONFIG.get());
              final float menuOuterDeadzone = CONFIG.getConfig(MENU_OUTER_DEADZONE_CONFIG.get());
              final float movementInnerDeadzone = CONFIG.getConfig(MOVEMENT_INNER_DEADZONE_CONFIG.get());
              final float movementOuterDeadzone = CONFIG.getConfig(MOVEMENT_OUTER_DEADZONE_CONFIG.get());
              final float minInner = Math.min(menuInnerDeadzone, movementInnerDeadzone);
              final float maxOuter = Math.min(menuOuterDeadzone, movementOuterDeadzone);
              final long gamepadAxis = gamepadAxisId(axis.deviceId(), axis.axis().ordinal());

              if(Math.abs(rawValue) >= minInner * 0x7fff) {
                this.setWindowInputClass(this.lastActiveWindow, InputClass.GAMEPAD);
                this.lastGamepad = axis.deviceId();
                this.axesHeld.add(gamepadAxis);
                final float menuValue = Math.min(1.0f, (Math.abs(rawValue) / (float)0x7fff - menuInnerDeadzone) / (Math.abs(menuOuterDeadzone - menuInnerDeadzone)));
                final float movementValue = Math.min(1.0f, (Math.abs(rawValue) / (float)0x7fff - movementInnerDeadzone) / (Math.abs(movementOuterDeadzone - movementInnerDeadzone)));
                this.lastActiveWindow.events().onAxis(axis.axis(), InputAxisDirection.getDirection(rawValue), menuValue, movementValue);
              } else {
                this.axesHeld.remove(gamepadAxis);
              }

              final List<InputBinding<AxisInputActivation>> axisBindings = InputBindings.getBindings(AxisInputActivation.class);

              for(int i = 0; i < axisBindings.size(); i++) {
                final InputBinding<AxisInputActivation> binding = axisBindings.get(i);

                if(binding.activation.axis == axis.axis()) {
                  final InputAxisDirection direction = InputAxisDirection.getDirection(rawValue);
                  float value = 0.0f;

                  if(binding.activation.direction == direction) {
                    final float inner;
                    final float outer;
                    if(binding.action.useMovementDeadzone) {
                      inner = movementInnerDeadzone;
                      outer = movementOuterDeadzone;
                    } else {
                      inner = menuInnerDeadzone;
                      outer = menuOuterDeadzone;
                    }

                    final float deadzoneValue = Math.min(1.0f, (Math.abs(rawValue) / (float)0x7fff - inner) / (Math.abs(outer - inner)));
                    if(deadzoneValue > 0.0f) {
                      value = deadzoneValue * Math.signum(rawValue);
                    }
                  }

                  final long axisInput = axisInputId(axis.deviceId(), axis.axis().ordinal(), binding.activation.direction);
                  this.updateAxisAction(binding.action, axisInput, value, axis.deviceId(), axis.axis().ordinal(), rawValue);
                }
              }
            }
          }

          case AndroidInput.Event.Button button -> {
            if(button.down()) {
              LOGGER.info(INPUT_MARKER, "Button down gamepad %d button %s", button.deviceId(), button.button());

              if(this.lastActiveWindow != null && (this.lastActiveWindow.hasFocus || CONFIG.getConfig(RECEIVE_INPUT_ON_INACTIVE_WINDOW_CONFIG.get()))) {
                this.setWindowInputClass(this.lastActiveWindow, InputClass.GAMEPAD);
                this.lastGamepad = button.deviceId();
                this.lastActiveWindow.events().onButtonPress(button.button(), false);

                final List<InputBinding<ButtonInputActivation>> bindings = InputBindings.getBindings(ButtonInputActivation.class);

                for(int i = 0; i < bindings.size(); i++) {
                  final InputBinding<ButtonInputActivation> binding = bindings.get(i);

                  if(binding.activation.button == button.button()) {
                    LOGGER.info(ACTIONS_MARKER, "Triggering press input action button gamepad %d button %s -> %s", button.deviceId(), button.button(), binding.action);
                    this.triggerBindingPress(binding);
                  }
                }
              }
            } else {
              LOGGER.info(INPUT_MARKER, "Button up gamepad %d button %s", button.deviceId(), button.button());

              if(this.lastActiveWindow != null && (this.lastActiveWindow.hasFocus || CONFIG.getConfig(RECEIVE_INPUT_ON_INACTIVE_WINDOW_CONFIG.get()))) {
                this.setWindowInputClass(this.lastActiveWindow, InputClass.GAMEPAD);
                this.lastGamepad = button.deviceId();
                this.lastActiveWindow.events().onButtonRelease(button.button());

                final List<InputBinding<ButtonInputActivation>> bindings = InputBindings.getBindings(ButtonInputActivation.class);

                for(int i = 0; i < bindings.size(); i++) {
                  final InputBinding<ButtonInputActivation> binding = bindings.get(i);

                  if(binding.activation.button == button.button()) {
                    LOGGER.info(ACTIONS_MARKER, "Triggering release input action button gamepad %d button %s -> %s", button.deviceId(), button.button(), binding.action);
                    this.triggerBindingRelease(binding);
                  }
                }
              }
            }
          }
        }
      }
    }

    if(this.lastActiveWindow != null) {
      if(this.lastActiveWindow.hasFocus || CONFIG.getConfig(RECEIVE_INPUT_ON_INACTIVE_WINDOW_CONFIG.get())) {
        for(final var entry : this.actionStates.entrySet()) {
          final InputAction action = entry.getKey();
          final InputActionState state = entry.getValue();

          if(state.repeat()) {
            LOGGER.info(ACTIONS_MARKER, "Triggering repeat input action %s", action);
            this.lastActiveWindow.events().onInputActionPressed(action, true);
            EVENTS.postEvent(new InputPressedEvent(action, true));
          }
        }
      } else {
        this.pressed.clear();
        this.axesHeld.clear();

        for(final var entry : this.actionStates.entrySet()) {
          final InputAction action = entry.getKey();
          final InputActionState state = entry.getValue();

          if(state.isHeld()) {
            LOGGER.info(ACTIONS_MARKER, "Triggering release input action %s", action);
            this.lastActiveWindow.events().onInputActionReleased(action);
            state.release();
            EVENTS.postEvent(new InputReleasedEvent(action));
          }
        }

        this.axisActionStates.clear();
      }
    }

    if(this.rumbleLerpStart != 0) {
      final long time = System.nanoTime() - this.rumbleLerpStart;
      final float ratio = Math.min(Math.max(time / (float)this.rumbleLerpDuration, 0.0f), 1.0f);
      final float big = Math.lerp(this.rumbleBigStartingIntensity, this.rumbleBigEndingIntensity, ratio);
      final float small = Math.lerp(this.rumbleSmallStartingIntensity, this.rumbleSmallEndingIntensity, ratio);
      this.rumble(big, small, 0);

      if(time >= this.rumbleLerpDuration) {
        this.rumbleLerpStart = 0;
      }
    }

    if(this.clearActionStates) {
      this.clearActionStates = false;
      this.actionStates.clear();
      this.axisActionStates.clear();
    }
  }

  private AndroidWindow getWindow() {
    if(this.lastActiveWindow != null) {
      return this.lastActiveWindow;
    }

    return this.windows.isEmpty() ? null : this.windows.get(0);
  }

  public void triggerBindingPress(final InputBinding<?> binding) {
    this.pressed.add(binding.action);
    this.lastActiveWindow.events().onInputActionPressed(binding.action, false);
    this.getInputActionState(binding.action).press();
    EVENTS.postEvent(new InputPressedEvent(binding.action, false));
  }

  public void triggerBindingRelease(final InputBinding<?> binding) {
    this.lastActiveWindow.events().onInputActionReleased(binding.action);
    this.getInputActionState(binding.action).release();
    EVENTS.postEvent(new InputReleasedEvent(binding.action));
  }

  @Override
  public void clearPressed() {
    synchronized(INPUT_LOCK) {
      this.pressed.clear();
    }
  }

  @Override
  public void rumble(final float intensity, final int ms) {
    this.rumble(intensity, intensity, ms);
  }

  @Override
  public void rumble(final float bigIntensity, final float smallIntensity, final int ms) {
    this.rumbleBigCurrentIntensity = bigIntensity;
    this.rumbleSmallCurrentIntensity = smallIntensity;

    if(this.lastRumbleGamepad != this.lastGamepad) {
      if(this.lastRumbleGamepad != -1) {
        this.cancelRumble(this.lastRumbleGamepad);
      }

      this.lastRumbleGamepad = this.lastGamepad;
    }

    this.vibrate(this.lastGamepad, bigIntensity, smallIntensity, ms);
  }

  @Override
  public void adjustRumble(final float intensity, final int ms) {
    this.adjustRumble(intensity, intensity, ms);
  }

  @Override
  public void adjustRumble(final float bigIntensity, final float smallIntensity, final int ms) {
    this.rumbleBigStartingIntensity = this.rumbleBigCurrentIntensity;
    this.rumbleSmallStartingIntensity = this.rumbleSmallCurrentIntensity;
    this.rumbleBigEndingIntensity = bigIntensity;
    this.rumbleSmallEndingIntensity = smallIntensity;
    this.rumbleLerpStart = System.nanoTime();
    this.rumbleLerpDuration = ms * 1_000_000L;
  }

  @Override
  public void stopRumble() {
    if(this.lastRumbleGamepad != -1) {
      this.cancelRumble(this.lastRumbleGamepad);
    }
  }

  // ms == 0 means "run until the next rumble()/stopRumble()", so sustained
  // rumbles get a long duration that a later call cancels. InputDevice
  // vibrators don't need the VIBRATE permission.
  private void vibrate(final int deviceId, final float bigIntensity, final float smallIntensity, final int ms) {
    final InputDevice device = InputDevice.getDevice(deviceId);
    if(device == null) {
      return;
    }

    final int duration = ms > 0 ? ms : 30_000;

    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      final VibratorManager manager = device.getVibratorManager();
      final int[] ids = manager.getVibratorIds();
      if(ids.length == 0) {
        return;
      }

      // First motor is the strong/low-frequency one on dual-motor pads;
      // single-motor pads get the stronger of the two channels.
      final CombinedVibration.ParallelCombination combo = CombinedVibration.startParallel();
      boolean any = false;
      for(int i = 0; i < ids.length; i++) {
        final float intensity = ids.length == 1 ? Math.max(bigIntensity, smallIntensity) : i == 0 ? bigIntensity : smallIntensity;
        if(intensity > 0) {
          combo.addVibrator(ids[i], VibrationEffect.createOneShot(duration, amplitude(intensity)));
          any = true;
        }
      }

      if(any) {
        manager.vibrate(combo.combine());
      } else {
        manager.cancel();
      }
    } else {
      this.legacyVibrate(device, Math.max(bigIntensity, smallIntensity), duration);
    }
  }

  @SuppressWarnings("deprecation") // InputDevice.getVibrator is the only option pre-API 31
  private void legacyVibrate(final InputDevice device, final float intensity, final int duration) {
    final Vibrator vibrator = device.getVibrator();
    if(!vibrator.hasVibrator()) {
      return;
    }

    if(intensity > 0) {
      vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude(intensity)));
    } else {
      vibrator.cancel();
    }
  }

  private void cancelRumble(final int deviceId) {
    final InputDevice device = InputDevice.getDevice(deviceId);
    if(device == null) {
      return;
    }

    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      device.getVibratorManager().cancel();
    } else {
      this.legacyCancel(device);
    }
  }

  @SuppressWarnings("deprecation")
  private void legacyCancel(final InputDevice device) {
    device.getVibrator().cancel();
  }

  private static int amplitude(final float intensity) {
    return Math.max(1, Math.min(255, Math.round(intensity * 255)));
  }

  private void updateAxisAction(final InputAction action, final long axisInput, final float value, final int gamepadId, final int axisId, final int rawValue) {
    final AxisInputState axisState = this.axisActionStates.computeIfAbsent(action, key -> new AxisInputState());
    axisState.update(axisInput, value);
    this.applyAxisActionValue(action, axisState.getValue(), gamepadId, axisId, rawValue);

    if(axisState.isEmpty()) {
      this.axisActionStates.remove(action);
    }
  }

  private void removeGamepadAxisInputs(final int gamepadId) {
    final LongIterator axesHeldIterator = this.axesHeld.iterator();
    while(axesHeldIterator.hasNext()) {
      if(axisInputGamepadId(axesHeldIterator.nextLong()) == gamepadId) {
        axesHeldIterator.remove();
      }
    }

    final Iterator<Map.Entry<InputAction, AxisInputState>> actionIterator = this.axisActionStates.entrySet().iterator();
    while(actionIterator.hasNext()) {
      final Map.Entry<InputAction, AxisInputState> entry = actionIterator.next();
      final AxisInputState axisState = entry.getValue();

      if(axisState.removeGamepad(gamepadId)) {
        this.applyAxisActionValue(entry.getKey(), axisState.getValue(), gamepadId, -1, 0);
      }

      if(axisState.isEmpty()) {
        actionIterator.remove();
      }
    }
  }

  private void applyAxisActionValue(final InputAction action, final float value, final int gamepadId, final int axisId, final int rawValue) {
    final InputActionState state = this.getInputActionState(action);

    if(value != 0.0f) {
      if(!state.isHeld()) {
        LOGGER.info(ACTIONS_MARKER, "Triggering press input action axis gamepad %d axis %d value %d -> %s", gamepadId, axisId, rawValue, action);
        this.pressed.add(action);
        if(this.lastActiveWindow != null) {
          this.lastActiveWindow.events().onInputActionPressed(action, false);
        }
        state.press();
        EVENTS.postEvent(new InputPressedEvent(action, false));
      }

      state.axis(value);
    } else if(state.isHeld() && state.getAxis() != 0.0f) {
      LOGGER.info(ACTIONS_MARKER, "Triggering release input action axis gamepad %d axis %d value %d -> %s", gamepadId, axisId, rawValue, action);
      if(this.lastActiveWindow != null) {
        this.lastActiveWindow.events().onInputActionReleased(action);
      }
      state.release();
      EVENTS.postEvent(new InputReleasedEvent(action));
    }
  }

  private static long gamepadAxisId(final int gamepadId, final int axisId) {
    return (long)gamepadId << 32 | Integer.toUnsignedLong(axisId);
  }

  private static long axisInputId(final int gamepadId, final int axisId, final InputAxisDirection direction) {
    return (long)gamepadId << 32 | Integer.toUnsignedLong(axisId) << 1 | direction.ordinal();
  }

  private static int axisInputGamepadId(final long axisInput) {
    return (int)(axisInput >> 32);
  }

  private InputActionState getInputActionState(final InputAction action) {
    return this.actionStates.computeIfAbsent(action, key -> new InputActionState());
  }

  @Override
  public boolean isActionPressed(final InputAction action) {
    return this.pressed.contains(action);
  }

  @Override
  public boolean isActionRepeat(final InputAction action) {
    return this.getInputActionState(action).isRepeat();
  }

  @Override
  public boolean isActionHeld(final InputAction action) {
    return this.getInputActionState(action).isHeld();
  }

  @Override
  public float getAxis(final InputAction action) {
    return this.getInputActionState(action).getAxis();
  }

  private void setWindowInputClass(final AndroidWindow window, final InputClass classification) {
    if(window.currentInputClass != classification) {
      window.currentInputClass = classification;
      window.events().onInputClassChanged(classification);
    }
  }

  @Override
  public String getKeyName(final InputKey key) {
    return AndroidInput.keyName(key);
  }

  @Override
  public String getScancodeName(final InputKey key) {
    return AndroidInput.keyName(key);
  }

  @Override
  public String getButtonName(final InputButton button) {
    return button.name();
  }

  @Override
  public String getAxisName(final InputAxis axis) {
    return axis.name();
  }

  @Override
  public void openUrl(final String url) {
    final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    AndroidEnv.context().startActivity(intent);
  }

  private static final class AxisInputState {
    private final Long2FloatMap values = new Long2FloatOpenHashMap();

    void update(final long axisInput, final float value) {
      if(value == 0.0f) {
        this.values.remove(axisInput);
      } else {
        this.values.put(axisInput, value);
      }
    }

    boolean removeGamepad(final int gamepadId) {
      return this.values.long2FloatEntrySet().removeIf(entry -> axisInputGamepadId(entry.getLongKey()) == gamepadId);
    }

    float getValue() {
      float value = 0.0f;

      for(final Long2FloatMap.Entry entry : this.values.long2FloatEntrySet()) {
        final float candidate = entry.getFloatValue();
        if(Math.abs(candidate) > Math.abs(value)) {
          value = candidate;
        }
      }

      return value;
    }

    boolean isEmpty() {
      return this.values.isEmpty();
    }
  }
}
