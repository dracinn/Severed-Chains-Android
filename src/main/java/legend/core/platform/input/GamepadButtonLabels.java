package legend.core.platform.input;

/** Which letters/symbols button prompts use for a physical controller (Android only). */
public enum GamepadButtonLabels {
  /** Detect from the controller's vendor, falling back to Xbox letters */
  AUTO,
  XBOX,
  PLAYSTATION,
  /** Nintendo layout: A right, B bottom, X top, Y left */
  SWITCH,
}
