package legend.core;

import legend.core.lang.TextComponent;

import java.util.function.Consumer;

/**
 * No-op Android variant: there is no external updater JAR on Android. The
 * update path is only reachable when a download URL is configured, which the
 * Android {@link Updater} never produces.
 */
public final class SelfUpdater {
  private SelfUpdater() { }

  public enum UpdateState {
    LAUNCHING_UPDATER,
    DONE,
    FAILED
  }

  /**
   * Progress info passed to the UI callback.
   */
  public record UpdateProgress(UpdateState state, TextComponent message) { }

  public static boolean launchUpdater(final String downloadUrl, final Consumer<UpdateProgress> progressCallback) {
    return false;
  }
}
