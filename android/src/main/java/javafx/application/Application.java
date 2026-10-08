package javafx.application;

/**
 * Compile-time stand-in for JavaFX Application. The debugger (the only
 * Application subclass) is a no-op on Android, so launch() never runs.
 */
public abstract class Application {
  public static void launch(final Class<? extends Application> applicationClass) {
  }
}
