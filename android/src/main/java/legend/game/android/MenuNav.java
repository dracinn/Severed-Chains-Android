package legend.game.android;

import legend.core.GameEngine;
import legend.core.platform.AndroidInput;
import legend.core.platform.AndroidPlatformManager;
import legend.core.platform.input.InputAction;
import legend.core.platform.input.InputButton;
import legend.game.EngineStates;
import legend.game.SItem;
import legend.game.inventory.screens.Control;
import legend.game.inventory.screens.ControlHost;
import legend.game.inventory.screens.MenuScreen;
import legend.game.inventory.screens.controls.Button;
import legend.game.inventory.screens.controls.Dropdown;
import legend.game.inventory.screens.controls.Label;
import legend.game.title.Ttle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static legend.game.modding.coremod.CoreMod.INPUT_ACTION_MENU_BACK;
import static legend.game.modding.coremod.CoreMod.INPUT_ACTION_MENU_BOTTOM;
import static legend.game.modding.coremod.CoreMod.INPUT_ACTION_MENU_CONFIRM;
import static legend.game.modding.coremod.CoreMod.INPUT_ACTION_MENU_DOWN;
import static legend.game.modding.coremod.CoreMod.INPUT_ACTION_MENU_LEFT;
import static legend.game.modding.coremod.CoreMod.INPUT_ACTION_MENU_PAGE_DOWN;
import static legend.game.modding.coremod.CoreMod.INPUT_ACTION_MENU_PAGE_UP;
import static legend.game.modding.coremod.CoreMod.INPUT_ACTION_MENU_RIGHT;
import static legend.game.modding.coremod.CoreMod.INPUT_ACTION_MENU_TOP;
import static legend.game.modding.coremod.CoreMod.INPUT_ACTION_MENU_UP;
import static legend.lodmod.LodMod.INPUT_ACTION_GENERAL_MOVE_DOWN;
import static legend.lodmod.LodMod.INPUT_ACTION_GENERAL_MOVE_LEFT;
import static legend.lodmod.LodMod.INPUT_ACTION_GENERAL_MOVE_RIGHT;
import static legend.lodmod.LodMod.INPUT_ACTION_GENERAL_MOVE_UP;

/**
 * Programmatic model of the game's menu tree for location reporting and
 * direct navigation — lets automation know where it is and where to go
 * without pixel-level input or screenshots.
 *
 * Title menu (Ttle):
 *   0 = New Campaign, 1 = Continue (hidden and skipped when the game has
 *   no campaigns), 2 = Options, 3 = About, 4 = Quit.
 *   Initial selection is {@code hasCampaigns ? 1 : 0} — a fresh install
 *   lands on New Campaign, an install with saves lands on Continue.
 *   Input is accepted only while {@link Ttle#isMenuInputReady()}.
 *
 * Menu stack (SItem.menuStack):
 *   Title "Options" pushes OptionsPresetsScreen (preset list + Edit row);
 *   Edit pushes OptionsCategoryScreen (one row per ConfigCategory); a
 *   category row pushes OptionsScreen (config entries sorted by label);
 *   confirming a dropdown row pushes its option-list screen; CONFIRM on a
 *   selected option pops it.
 *
 * Drive it by writing commands (one per line) to
 * {@code <public root>/nav_command.txt}; progress and the current location
 * are written back to {@code nav_state.txt} and logcat. Commands:
 *   where                          dump the location map (screens + rows)
 *   press up|down|left|right|confirm|back|top|bottom|pageup|pagedown
 *   press pad_<btn>                raw gamepad-button tap through the touch
 *                                  device path (pad_a, pad_b, pad_x, pad_y,
 *                                  pad_start, pad_select, pad_l1, pad_r1,
 *                                  pad_up/down/left/right) — reaches in-game
 *                                  handlers that poll buttons rather than
 *                                  menu actions (e.g. Y opens the menu)
 *   hold pad_<btn> / release pad_<btn>
 *                                  hold/release a raw button for movement
 *   focus <text>                   move selection to the row/entry whose
 *                                  label contains <text> (case-insensitive),
 *                                  then confirm
 *   pick <text>                    inside an open dropdown list, hover the
 *                                  option containing <text> and confirm it
 *   nav <text>;<text>;...          chained focus+confirm steps
 */
public final class MenuNav {
  private static final Logger LOGGER = LogManager.getFormatterLogger(MenuNav.class);

  private static final String[] TITLE_OPTIONS = {"New Campaign", "Continue", "Options", "About", "Quit"};
  /** Continue (index 1) is hidden and skipped by up/down when there are no campaigns. */
  private static final int TITLE_CONTINUE_INDEX = 1;

  /** Ticks between injected presses — lets a frame process input first. */
  private static final int ACTION_COOLDOWN = 4;
  /** Ticks to let a confirm land its transition. */
  private static final int CONFIRM_COOLDOWN = 10;
  /** Ticks before a nav step is abandoned. */
  private static final int STEP_TIMEOUT = 900;
  /** Ticks between nav_state.txt updates. */
  private static final int STATUS_PERIOD = 15;

  private enum Phase { IDLE, FOCUS, AWAIT_TRANSITION, PICK }

  private static Phase phase = Phase.IDLE;
  private static String[] navSteps;
  private static int navStepIndex;
  private static String focusTarget;
  private static String pickTarget;
  /** The dropdown under the row that was focused before confirm opened its list. */
  private static Dropdown<?> activeDropdown;
  /** Marker for AWAIT_TRANSITION: the screen (or null while on the title) that was top when we confirmed. */
  private static MenuScreen transitionFrom;
  private static boolean transitionFromTitle;
  private static int actionCooldown;
  private static int stepTicks;
  private static String status = "idle";
  private static String lastStatusOut = "";
  private static int statusTicks;
  /** Raw buttons tapped last tick, released on the next so the press spans a poll. */
  private static final List<InputButton> pendingReleases = new ArrayList<>();

  private static File commandFile() {
    return new File(AndroidEnv.PUBLIC_SAVES_DIR.getParentFile(), "nav_command.txt");
  }

  private static File stateFile() {
    return new File(AndroidEnv.PUBLIC_SAVES_DIR.getParentFile(), "nav_state.txt");
  }

  /** Per-tick pump — reads pending commands and advances the stepper. */
  public static void tick() {
    final File cmd = commandFile();
    if(cmd.isFile() && cmd.length() > 0) {
      try {
        final List<String> lines = Files.readAllLines(cmd.toPath(), StandardCharsets.UTF_8);
        // /sdcard is FUSE — a new file can be visible before its content is,
        // so an empty read just retries next tick instead of losing it
        if(!lines.isEmpty()) {
          cmd.delete();
          for(final String line : lines) {
            final String trimmed = line.trim();
            if(!trimmed.isEmpty()) {
              command(trimmed);
            }
          }
        }
      } catch(final IOException e) {
        LOGGER.warn("MenuNav failed to read command file: %s", e.getMessage());
      }
    }

    if(!pendingReleases.isEmpty()) {
      for(final InputButton button : pendingReleases) {
        AndroidInput.gamepadButton(AndroidInput.TOUCH_DEVICE_ID, button, false);
      }
      pendingReleases.clear();
    }

    pump();
    writeStatus();
  }

  /** Current location + focusable rows + locked configs, e.g. {@code TITLE[sel=1=Continue] | rows: ...}. */
  public static String describe() {
    return describeLocation() + describeLocks();
  }

  private static String describeLocation() {
    // MenuStack screens render over the title state — they take precedence
    final List<MenuScreen> screens = SItem.menuStack.getScreens();
    if(!screens.isEmpty()) {
      final StringBuilder sb = new StringBuilder("MENU[");
      for(int i = 0; i < screens.size(); i++) {
        if(i > 0) {
          sb.append('>');
        }
        sb.append(screens.get(i).getClass().getSimpleName());
      }
      sb.append(']');

      if(inDropdownScreen() && activeDropdown != null) {
        sb.append(" hover=").append(activeDropdown.getHoverIndex()).append(" | options:");
        for(int i = 0; i < activeDropdown.getOptionCount(); i++) {
          if(i == activeDropdown.getHoverIndex()) {
            sb.append(" *");
          } else {
            sb.append(' ');
          }
          sb.append(activeDropdown.getOptionText(i).get());
        }
        return sb.toString();
      }

      final Control focus = screens.get(0).getFocus();
      sb.append(" focus=").append(describeControl(focus));
      sb.append(" | rows: ").append(describeRows(screens.get(0), focus));
      return sb.toString();
    }

    if(EngineStates.currentEngineState_8004dd04 instanceof final Ttle ttle) {
      final StringBuilder sb = new StringBuilder("TITLE");
      if(!ttle.isMenuInputReady()) {
        sb.append("[loading]");
      } else {
        sb.append("[sel=").append(ttle.getSelectedMenuOption()).append('=')
          .append(TITLE_OPTIONS[ttle.getSelectedMenuOption()]).append(']');
      }
      sb.append(" campaigns=").append(ttle.hasCampaigns());
      sb.append(" | rows: ").append(titleRows(ttle));
      return sb.toString();
    }

    final var state = EngineStates.currentEngineState_8004dd04;
    return "STATE[" + (state == null ? "none" : state.getClass().getSimpleName()) + ']';
  }

  /** Locked config entries, e.g. {@code " | locked: lod_core:allow_widescreen=[Android]"}. */
  private static String describeLocks() {
    final StringBuilder sb = new StringBuilder();
    for(final var id : GameEngine.REGISTRIES.config) {
      final var entry = GameEngine.REGISTRIES.config.getEntry(id).get();
      final var locked = GameEngine.CONFIG.getLocked(entry);
      if(!locked.isEmpty()) {
        sb.append(sb.length() == 0 ? " | locked:" : ",").append(' ').append(id).append('=').append(locked);
      }
    }
    return sb.toString();
  }

  /** Execute a single command string. */
  public static void command(final String cmd) {
    LOGGER.info("MenuNav command: %s", cmd);
    final String lower = cmd.toLowerCase();

    if(lower.equals("where")) {
      status = describe();
      LOGGER.info("MenuNav: %s", status);
      return;
    }

    if(lower.startsWith("press ")) {
      final String name = lower.substring(6).trim();
      final InputButton button = parsePadButton(name);
      if(button != null) {
        AndroidInput.gamepadButton(AndroidInput.TOUCH_DEVICE_ID, button, true);
        pendingReleases.add(button);
        status = "pressed " + name;
        return;
      }
      final InputAction action = parseAction(name);
      if(action == null) {
        status = "error: unknown action in " + cmd;
        return;
      }
      inject(action);
      status = "pressed " + name;
      return;
    }

    if(lower.startsWith("hold ") || lower.startsWith("release ")) {
      final boolean down = lower.startsWith("hold ");
      final String name = lower.substring(down ? 5 : 8).trim();
      final InputButton button = parsePadButton(name);
      if(button != null) {
        pendingReleases.remove(button);
        AndroidInput.gamepadButton(AndroidInput.TOUCH_DEVICE_ID, button, down);
        status = (down ? "holding " : "released ") + name;
        return;
      }
      final InputAction action = parseMoveAction(name);
      if(action != null) {
        injectHeld(action, down);
        status = (down ? "holding " : "released ") + name;
        return;
      }
      status = "error: unknown button in " + cmd;
      return;
    }

    if(lower.startsWith("focus ")) {
      startFocusStep(cmd.substring(6).trim());
      return;
    }

    if(lower.startsWith("pick ")) {
      pickTarget = cmd.substring(5).trim();
      stepTicks = 0;
      phase = Phase.PICK;
      status = "picking " + pickTarget;
      return;
    }

    if(lower.startsWith("nav ")) {
      navSteps = cmd.substring(4).split(";");
      for(int i = 0; i < navSteps.length; i++) {
        navSteps[i] = navSteps[i].trim();
      }
      navStepIndex = 0;
      status = "nav step 1/" + navSteps.length + ": " + navSteps[0];
      startStep(navSteps[0]);
      return;
    }

    status = "error: unknown command " + cmd;
  }

  // ------------------------------------------------------------------
  // Navigation stepper

  /** Start one chained step — {@code pick <text>} selects inside an open
   *  dropdown list, anything else focuses a row and confirms it. */
  private static void startStep(final String step) {
    if(step.toLowerCase().startsWith("pick ")) {
      pickTarget = step.substring(5).trim();
      stepTicks = 0;
      phase = Phase.PICK;
      return;
    }
    startFocusStep(step);
  }

  private static void startFocusStep(final String target) {
    focusTarget = target;
    activeDropdown = null;
    stepTicks = 0;
    phase = Phase.FOCUS;
  }

  private static void pump() {
    if(actionCooldown > 0) {
      actionCooldown--;
      return;
    }

    switch(phase) {
      case FOCUS -> pumpFocus();
      case PICK -> pumpPick();
      case AWAIT_TRANSITION -> pumpAwaitTransition();
      default -> advanceNav();
    }
  }

  private static void advanceNav() {
    if(navSteps != null && navStepIndex + 1 < navSteps.length) {
      navStepIndex++;
      status = "nav step " + (navStepIndex + 1) + '/' + navSteps.length + ": " + navSteps[navStepIndex];
      startStep(navSteps[navStepIndex]);
    } else if(navSteps != null) {
      navSteps = null;
      status = "nav done: " + describe();
    }
  }

  private static void pumpFocus() {
    if(++stepTicks > STEP_TIMEOUT) {
      status = "error: timeout focusing " + focusTarget + " at " + describe();
      focusTarget = null;
      phase = Phase.IDLE;
      return;
    }

    if(focusMatches(focusTarget)) {
      // Remember the dropdown under this row — confirm may open its list
      // and the next step's target is one of its options.
      final Dropdown<?> rowDropdown = findFocusedDropdown();
      if(rowDropdown != null) {
        activeDropdown = rowDropdown;
      }
      transitionFrom = SItem.menuStack.peek();
      transitionFromTitle = EngineStates.currentEngineState_8004dd04 instanceof Ttle;
      inject(INPUT_ACTION_MENU_CONFIRM.get());
      actionCooldown = CONFIRM_COOLDOWN;
      focusTarget = null;
      stepTicks = 0;
      phase = Phase.AWAIT_TRANSITION;
      return;
    }

    if(inDropdownScreen()) {
      navigateDropdownHover(focusTarget);
    } else {
      inject(INPUT_ACTION_MENU_DOWN.get());
    }
    actionCooldown = ACTION_COOLDOWN;
  }

  private static void pumpAwaitTransition() {
    if(++stepTicks > STEP_TIMEOUT / 3) {
      // Transition never happened (e.g. confirm did nothing) — move on anyway
      phase = Phase.IDLE;
      return;
    }

    final boolean titleLeft = transitionFromTitle && !(EngineStates.currentEngineState_8004dd04 instanceof Ttle);
    final boolean screenChanged = SItem.menuStack.peek() != transitionFrom;
    if(titleLeft || screenChanged) {
      phase = Phase.IDLE;
    }
  }

  private static void pumpPick() {
    if(++stepTicks > STEP_TIMEOUT) {
      status = "error: timeout picking " + pickTarget;
      pickTarget = null;
      phase = Phase.IDLE;
      return;
    }

    if(!inDropdownScreen()) {
      inject(INPUT_ACTION_MENU_CONFIRM.get());
      actionCooldown = CONFIRM_COOLDOWN;
      return;
    }

    if(activeDropdown == null) {
      activeDropdown = findFocusedDropdown();
      if(activeDropdown == null) {
        status = "error: pick couldn't find the open dropdown";
        pickTarget = null;
        phase = Phase.IDLE;
        return;
      }
    }

    final int target = findOptionIndex(activeDropdown, pickTarget);
    if(target == -1) {
      status = "error: no option matching " + pickTarget;
      pickTarget = null;
      phase = Phase.IDLE;
      return;
    }

    if(activeDropdown.getHoverIndex() == target) {
      inject(INPUT_ACTION_MENU_CONFIRM.get());
      actionCooldown = CONFIRM_COOLDOWN;
      status = "picked " + pickTarget;
      pickTarget = null;
      activeDropdown = null;
      phase = Phase.AWAIT_TRANSITION;
      transitionFrom = SItem.menuStack.peek();
      transitionFromTitle = false;
      return;
    }

    inject(activeDropdown.getHoverIndex() < target ? INPUT_ACTION_MENU_DOWN.get() : INPUT_ACTION_MENU_UP.get());
    actionCooldown = ACTION_COOLDOWN;
  }

  private static void navigateDropdownHover(final String target) {
    if(activeDropdown == null) {
      activeDropdown = findFocusedDropdown();
      if(activeDropdown == null) {
        inject(INPUT_ACTION_MENU_DOWN.get());
        return;
      }
    }

    final int idx = findOptionIndex(activeDropdown, target);
    if(idx == -1) {
      inject(INPUT_ACTION_MENU_DOWN.get());
      return;
    }

    inject(activeDropdown.getHoverIndex() < idx ? INPUT_ACTION_MENU_DOWN.get() : INPUT_ACTION_MENU_UP.get());
  }

  // ------------------------------------------------------------------
  // Location queries

  /** True when the top of the menu stack is a Dropdown's option-list screen. */
  private static boolean inDropdownScreen() {
    final MenuScreen top = SItem.menuStack.peek();
    return top != null && top.getClass().isMemberClass() && top.getClass().getEnclosingClass() == Dropdown.class;
  }

  private static boolean focusMatches(final String target) {
    final String t = target.toLowerCase();

    if(inDropdownScreen() && activeDropdown != null) {
      final int idx = findOptionIndex(activeDropdown, target);
      return idx != -1 && activeDropdown.getHoverIndex() == idx;
    }

    final MenuScreen top = SItem.menuStack.peek();
    if(top != null) {
      final Control focus = top.getFocus();
      if(focus == null) {
        return false;
      }

      // VerticalLayoutScreen rows are Labels wrapping the focused control —
      // match the row's label, or the control's own text when the label is blank
      for(final Control row : top) {
        if(row == focus || containsFocus(row, focus)) {
          final String rowText = controlText(row);
          if(rowText != null && rowText.toLowerCase().contains(t)) {
            return true;
          }
          final String own = controlText(focus);
          return own != null && own.toLowerCase().contains(t);
        }
      }
      return false;
    }

    if(EngineStates.currentEngineState_8004dd04 instanceof final Ttle ttle) {
      return ttle.isMenuInputReady() && TITLE_OPTIONS[ttle.getSelectedMenuOption()].toLowerCase().contains(t);
    }

    return false;
  }

  /** The Dropdown nested under the currently focused row, if any. */
  private static Dropdown<?> findFocusedDropdown() {
    final MenuScreen top = SItem.menuStack.peek();
    final Control focus = top == null ? null : top.getFocus();
    if(focus == null) {
      return null;
    }
    if(focus instanceof final Dropdown<?> dropdown) {
      return dropdown;
    }
    final List<Dropdown> dropdowns = new ArrayList<>();
    collectControls(focus, Dropdown.class, dropdowns);
    return dropdowns.isEmpty() ? null : dropdowns.get(0);
  }

  @SuppressWarnings("unchecked")
  private static <T extends Control> void collectControls(final ControlHost host, final Class<T> type, final List<T> out) {
    for(final Control c : host) {
      if(type.isInstance(c)) {
        out.add((T)c);
      }
      collectControls(c, type, out);
    }
  }

  private static int findOptionIndex(final Dropdown<?> dropdown, final String target) {
    final String t = target.toLowerCase();
    for(int i = 0; i < dropdown.getOptionCount(); i++) {
      final String text = dropdown.getOptionText(i).get();
      if(text != null && text.toLowerCase().contains(t)) {
        return i;
      }
      if(String.valueOf(dropdown.getOption(i)).toLowerCase().contains(t)) {
        return i;
      }
    }
    return -1;
  }

  private static String titleRows(final Ttle ttle) {
    final StringBuilder sb = new StringBuilder();
    for(int i = 0; i < TITLE_OPTIONS.length; i++) {
      if(i == TITLE_CONTINUE_INDEX && !ttle.hasCampaigns()) {
        continue;
      }
      if(sb.length() > 0) {
        sb.append(" | ");
      }
      if(i == ttle.getSelectedMenuOption()) {
        sb.append('*');
      }
      sb.append(TITLE_OPTIONS[i]);
    }
    return sb.toString();
  }

  private static String describeRows(final MenuScreen screen, final Control focus) {
    final StringBuilder sb = new StringBuilder();
    for(final Control c : screen) {
      final String label = rowLabel(c);
      if(label == null) {
        continue;
      }
      if(sb.length() > 0) {
        sb.append(" | ");
      }
      if(c == focus || containsFocus(c, focus)) {
        sb.append('*');
      }
      sb.append(label);
    }
    return sb.toString();
  }

  private static boolean containsFocus(final Control row, final Control focus) {
    if(focus == null) {
      return false;
    }
    for(final Control c : row) {
      if(c == focus || containsFocus(c, focus)) {
        return true;
      }
    }
    return false;
  }

  /** A control's own display text, if the control type carries one. */
  private static String controlText(final Control c) {
    if(c instanceof final Label label && label.getText() != null) {
      return label.getText().get();
    }
    if(c instanceof final Button button && button.getText() != null) {
      return button.getText().get();
    }
    return null;
  }

  /** A row's display text: its first Label descendant, else first control text. */
  private static String rowLabel(final Control c) {
    final String own = controlText(c);
    if(own != null && !own.isBlank()) {
      return own;
    }
    final List<Label> labels = new ArrayList<>();
    collectControls(c, Label.class, labels);
    for(final Label label : labels) {
      if(label.getText() != null && !label.getText().get().isBlank()) {
        return label.getText().get();
      }
    }
    final List<Button> buttons = new ArrayList<>();
    collectControls(c, Button.class, buttons);
    for(final Button button : buttons) {
      if(button.getText() != null && !button.getText().get().isBlank()) {
        return button.getText().get();
      }
    }
    return null;
  }

  private static String describeControl(final Control c) {
    if(c == null) {
      return "none";
    }
    final String label = controlText(c) != null && !controlText(c).isBlank() ? controlText(c) : rowLabel(c);
    final StringBuilder sb = new StringBuilder(c.getClass().getSimpleName());
    if(label != null) {
      sb.append("(\"").append(label).append("\")");
    }
    if(c instanceof final Dropdown<?> dropdown) {
      sb.append('[').append(dropdown.getSelectedIndex()).append('/').append(dropdown.getOptionCount()).append(']');
    } else {
      final List<Dropdown> ds = new ArrayList<>();
      collectControls(c, Dropdown.class, ds);
      if(!ds.isEmpty()) {
        final Dropdown<?> d = ds.get(0);
        final int sel = d.getSelectedIndex();
        sb.append(" dropdown=").append(sel >= 0 && sel < d.getOptionCount() ? d.getOptionText(sel).get() : "?");
      }
    }
    return sb.toString();
  }

  // ------------------------------------------------------------------
  // Input injection + status plumbing

  private static InputAction parseAction(final String name) {
    return switch(name) {
      case "up" -> INPUT_ACTION_MENU_UP.get();
      case "down" -> INPUT_ACTION_MENU_DOWN.get();
      case "left" -> INPUT_ACTION_MENU_LEFT.get();
      case "right" -> INPUT_ACTION_MENU_RIGHT.get();
      case "confirm", "enter", "a" -> INPUT_ACTION_MENU_CONFIRM.get();
      case "back", "b", "escape" -> INPUT_ACTION_MENU_BACK.get();
      case "top" -> INPUT_ACTION_MENU_TOP.get();
      case "bottom" -> INPUT_ACTION_MENU_BOTTOM.get();
      case "pageup" -> INPUT_ACTION_MENU_PAGE_UP.get();
      case "pagedown" -> INPUT_ACTION_MENU_PAGE_DOWN.get();
      default -> null;
    };
  }

  /** pad_* names -> raw gamepad buttons; anything else returns null. */
  private static InputButton parsePadButton(final String name) {
    return switch(name) {
      case "pad_a" -> InputButton.A;
      case "pad_b" -> InputButton.B;
      case "pad_x" -> InputButton.X;
      case "pad_y" -> InputButton.Y;
      case "pad_start" -> InputButton.START;
      case "pad_select" -> InputButton.SELECT;
      case "pad_l1" -> InputButton.LEFT_BUMPER;
      case "pad_r1" -> InputButton.RIGHT_BUMPER;
      case "pad_up" -> InputButton.DPAD_UP;
      case "pad_down" -> InputButton.DPAD_DOWN;
      case "pad_left" -> InputButton.DPAD_LEFT;
      case "pad_right" -> InputButton.DPAD_RIGHT;
      default -> null;
    };
  }

  /** move_* names -> held movement actions; anything else returns null. */
  private static InputAction parseMoveAction(final String name) {
    return switch(name) {
      case "move_up" -> INPUT_ACTION_GENERAL_MOVE_UP.get();
      case "move_down" -> INPUT_ACTION_GENERAL_MOVE_DOWN.get();
      case "move_left" -> INPUT_ACTION_GENERAL_MOVE_LEFT.get();
      case "move_right" -> INPUT_ACTION_GENERAL_MOVE_RIGHT.get();
      default -> null;
    };
  }

  private static void inject(final InputAction action) {
    ((AndroidPlatformManager)GameEngine.PLATFORM).injectMenuAction(action);
  }

  private static void injectHeld(final InputAction action, final boolean down) {
    ((AndroidPlatformManager)GameEngine.PLATFORM).injectMenuActionHeld(action, down);
  }

  private static void writeStatus() {
    if(++statusTicks < STATUS_PERIOD) {
      return;
    }
    statusTicks = 0;

    final String out = status + '\n' + describe() + '\n';
    if(!out.equals(lastStatusOut)) {
      lastStatusOut = out;
      try {
        Files.writeString(stateFile().toPath(), out);
      } catch(final IOException e) {
        // best-effort reporting only
      }
    }
  }

  private MenuNav() { }
}
