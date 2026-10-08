package legend.game.debugger;

import javafx.application.Application;

/** Android variant: the JavaFX debugger does not exist on Android. */
public class Debugger extends Application {
  public static boolean isRunning() {
    return false;
  }

  public static void show() {
  }

  public static Object getStage() {
    return null;
  }
}
