package legend.core.platform.input;

import java.util.List;

import static legend.core.GameEngine.PLATFORM;
import static legend.core.GameEngine.RENDERER;

/** Uses the first Unicode private area */
public final class InputCodepoints {
  private InputCodepoints() { }

  public static final char DPAD_UP = 0xe000;
  public static final char DPAD_DOWN = 0xe001;
  public static final char DPAD_LEFT = 0xe002;
  public static final char DPAD_RIGHT = 0xe003;
  public static final char SELECT = 0xe004;
  public static final char START = 0xe005;
  public static final char LEFT_BUMPER = 0xe006;
  public static final char LEFT_TRIGGER = 0xe007;
  public static final char LEFT_STICK = 0xe008;
  public static final char RIGHT_BUMPER = 0xe009;
  public static final char RIGHT_TRIGGER = 0xe00a;
  public static final char RIGHT_STICK = 0xe00b;
  public static final char A = 0xe00c;
  public static final char B = 0xe00d;
  public static final char X = 0xe00e;
  public static final char Y = 0xe00f;
  public static final char LEFT_AXIS_X = 0xe010;
  public static final char LEFT_AXIS_Y = 0xe011;
  public static final char RIGHT_AXIS_X = 0xe012;
  public static final char RIGHT_AXIS_Y = 0xe013;
  public static final char GUIDE = 0xe014;

  public static final char XBOX_BUTTON_BACK = 0xe100;
  /** Three lines */
  public static final char XBOX_BUTTON_MENU = 0xe101;
  /** Two squares */
  public static final char XBOX_BUTTON_VIEW = 0xe102;
  public static final char XBOX_BUTTON_GUIDE = 0xe103;

  public static final char PS_BUTTON_CROSS = 0xe200;
  public static final char PS_BUTTON_CIRCLE = 0xe201;
  public static final char PS_BUTTON_SQUARE = 0xe202;
  public static final char PS_BUTTON_TRIANGLE = 0xe203;
  public static final char PS_BUTTON_TOUCHPAD = 0xe204;
  public static final char PS_BUTTON_GUIDE = 0xe205;
  public static final char PS_BUTTON_SHARE = 0xe206;
  public static final char PS_BUTTON_OPTIONS = 0xe207;
  public static final char PS_BUTTON_L1 = 0xe208;
  public static final char PS_BUTTON_R1 = 0xe209;
  public static final char PS_BUTTON_L2 = 0xe20a;
  public static final char PS_BUTTON_R2 = 0xe20b;
  public static final char PS_BUTTON_L3 = 0xe20c;
  public static final char PS_BUTTON_R3 = 0xe20d;
  public static final char PS_DPAD_UP = 0xe20e;
  public static final char PS_DPAD_DOWN = 0xe20f;
  public static final char PS_DPAD_LEFT = 0xe210;
  public static final char PS_DPAD_RIGHT = 0xe211;
  public static final char PS_LEFT_AXIS_X = 0xe212;
  public static final char PS_LEFT_AXIS_Y = 0xe213;
  public static final char PS_RIGHT_AXIS_X = 0xe214;
  public static final char PS_RIGHT_AXIS_Y = 0xe215;

  public static final char SWITCH_BUTTON_A = 0xe400;
  public static final char SWITCH_BUTTON_B = 0xe401;
  public static final char SWITCH_BUTTON_X = 0xe402;
  public static final char SWITCH_BUTTON_Y = 0xe403;
  public static final char SWITCH_BUTTON_MINUS = 0xe404;
  public static final char SWITCH_BUTTON_PLUS = 0xe405;
  public static final char SWITCH_BUTTON_HOME = 0xe406;
  public static final char SWITCH_BUTTON_L = 0xe407;
  public static final char SWITCH_BUTTON_ZL = 0xe408;
  public static final char SWITCH_BUTTON_R = 0xe409;
  public static final char SWITCH_BUTTON_ZR = 0xe40a;
  public static final char SWITCH_DPAD_UP = 0xe40b;
  public static final char SWITCH_DPAD_DOWN = 0xe40c;
  public static final char SWITCH_DPAD_LEFT = 0xe40d;
  public static final char SWITCH_DPAD_RIGHT = 0xe40e;
  public static final char SWITCH_LEFT_STICK = 0xe40f;
  public static final char SWITCH_RIGHT_STICK = 0xe410;
  public static final char SWITCH_LEFT_AXIS_X = 0xe411;
  public static final char SWITCH_LEFT_AXIS_Y = 0xe412;
  public static final char SWITCH_RIGHT_AXIS_X = 0xe413;
  public static final char SWITCH_RIGHT_AXIS_Y = 0xe414;

  public static final char GENERIC_LEFT_PADDLE1 = 0xe300;
  public static final char GENERIC_RIGHT_PADDLE1 = 0xe301;
  public static final char GENERIC_LEFT_PADDLE2 = 0xe302;
  public static final char GENERIC_RIGHT_PADDLE2 = 0xe303;
  public static final char GENERIC_MISC1 = 0xe304;
  public static final char GENERIC_MISC2 = 0xe305;
  public static final char GENERIC_MISC3 = 0xe306;
  public static final char GENERIC_MISC4 = 0xe307;
  public static final char GENERIC_MISC5 = 0xe308;
  public static final char GENERIC_MISC6 = 0xe309;

  public static final char TEXTBOX_INPUT_ACTION = 0xef00;

  /** Adjusts codepoints for the given controller (e.g. converts A->cross for PS controllers) */
  public static char getCodepoint(final InputGamepadType type, final InputButton button) {
    return getCodepoint(type, button.codepoint);
  }

  /** Adjusts codepoints for the given controller (e.g. converts A->cross for PS controllers) */
  public static char getCodepoint(final InputGamepadType type, final char codepoint) {
    switch(type) {
      case XBOX_360 -> {
        switch(codepoint) {
          case SELECT -> {
            return XBOX_BUTTON_BACK;
          }

          case GUIDE -> {
            return XBOX_BUTTON_GUIDE;
          }
        }
      }

      case XBOX_ONE -> {
        switch(codepoint) {
          case START -> {
            return XBOX_BUTTON_MENU;
          }

          case SELECT -> {
            return XBOX_BUTTON_VIEW;
          }

          case GUIDE -> {
            return XBOX_BUTTON_GUIDE;
          }
        }
      }

      case PLAYSTATION -> {
        switch(codepoint) {
          case A -> {
            return PS_BUTTON_CROSS;
          }

          case B -> {
            return PS_BUTTON_CIRCLE;
          }

          case X -> {
            return PS_BUTTON_SQUARE;
          }

          case Y -> {
            return PS_BUTTON_TRIANGLE;
          }

          case SELECT -> {
            return PS_BUTTON_SHARE;
          }

          case START -> {
            return PS_BUTTON_OPTIONS;
          }

          case LEFT_BUMPER -> {
            return PS_BUTTON_L1;
          }

          case RIGHT_BUMPER -> {
            return PS_BUTTON_R1;
          }

          case LEFT_TRIGGER -> {
            return PS_BUTTON_L2;
          }

          case RIGHT_TRIGGER -> {
            return PS_BUTTON_R2;
          }

          case LEFT_STICK -> {
            return PS_BUTTON_L3;
          }

          case RIGHT_STICK -> {
            return PS_BUTTON_R3;
          }

          case DPAD_UP -> {
            return PS_DPAD_UP;
          }

          case DPAD_DOWN -> {
            return PS_DPAD_DOWN;
          }

          case DPAD_LEFT -> {
            return PS_DPAD_LEFT;
          }

          case DPAD_RIGHT -> {
            return PS_DPAD_RIGHT;
          }

          case LEFT_AXIS_X -> {
            return PS_LEFT_AXIS_X;
          }

          case LEFT_AXIS_Y -> {
            return PS_LEFT_AXIS_Y;
          }

          case RIGHT_AXIS_X -> {
            return PS_RIGHT_AXIS_X;
          }

          case RIGHT_AXIS_Y -> {
            return PS_RIGHT_AXIS_Y;
          }

          case GUIDE -> {
            return PS_BUTTON_GUIDE;
          }
        }
      }

      case SWITCH -> {
        switch(codepoint) {
          // Switch face buttons are lettered opposite to Xbox at the same
          // positions: bottom=B, right=A, left=Y, top=X
          case A -> {
            return SWITCH_BUTTON_B;
          }

          case B -> {
            return SWITCH_BUTTON_A;
          }

          case X -> {
            return SWITCH_BUTTON_Y;
          }

          case Y -> {
            return SWITCH_BUTTON_X;
          }

          case SELECT -> {
            return SWITCH_BUTTON_MINUS;
          }

          case START -> {
            return SWITCH_BUTTON_PLUS;
          }

          case LEFT_BUMPER -> {
            return SWITCH_BUTTON_L;
          }

          case LEFT_TRIGGER -> {
            return SWITCH_BUTTON_ZL;
          }

          case RIGHT_BUMPER -> {
            return SWITCH_BUTTON_R;
          }

          case RIGHT_TRIGGER -> {
            return SWITCH_BUTTON_ZR;
          }

          case DPAD_UP -> {
            return SWITCH_DPAD_UP;
          }

          case DPAD_DOWN -> {
            return SWITCH_DPAD_DOWN;
          }

          case DPAD_LEFT -> {
            return SWITCH_DPAD_LEFT;
          }

          case DPAD_RIGHT -> {
            return SWITCH_DPAD_RIGHT;
          }

          case LEFT_STICK -> {
            return SWITCH_LEFT_STICK;
          }

          case RIGHT_STICK -> {
            return SWITCH_RIGHT_STICK;
          }

          case LEFT_AXIS_X -> {
            return SWITCH_LEFT_AXIS_X;
          }

          case LEFT_AXIS_Y -> {
            return SWITCH_LEFT_AXIS_Y;
          }

          case RIGHT_AXIS_X -> {
            return SWITCH_RIGHT_AXIS_X;
          }

          case RIGHT_AXIS_Y -> {
            return SWITCH_RIGHT_AXIS_Y;
          }

          case GUIDE -> {
            return SWITCH_BUTTON_HOME;
          }
        }
      }
    }

    return codepoint;
  }

  public static String getActionName(final InputAction action) {
    final InputClass type = RENDERER.window().getInputClass();
    final List<InputActivation> activations = InputBindings.getActivationsForAction(action);

    for(int i = 0; i < activations.size(); i++) {
      final InputActivation activation = activations.get(i);

      if(type == InputClass.GAMEPAD) {
        if(activation instanceof final ButtonInputActivation button) {
          return String.valueOf(button.button.codepoint);
        }

        if(activation instanceof final AxisInputActivation axis) {
          return String.valueOf(axis.axis.codepoint);
        }
      }

      if(type == InputClass.KEYBOARD) {
        if(activation instanceof final KeyInputActivation key) {
          return PLATFORM.getKeyName(key.key);
        }

        if(activation instanceof final ScancodeInputActivation scancode) {
          return PLATFORM.getKeyName(scancode.key);
        }
      }
    }

    return "<unbound>";
  }
}
