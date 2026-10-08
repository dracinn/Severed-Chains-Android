package javafx.application;

/**
 * Compile-time stand-in for JavaFX Platform. Upstream game code calls these
 * only on debugger/updater paths that are unreachable on Android.
 */
public final class Platform {
  private Platform() { }

  public static void runLater(final Runnable runnable) {
    runnable.run();
  }

  public static void setImplicitExit(final boolean implicitExit) {
  }

  public static void exit() {
  }
}
