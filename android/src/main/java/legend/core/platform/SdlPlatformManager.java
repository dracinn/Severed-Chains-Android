package legend.core.platform;

/**
 * Android variant: keeps the SdlPlatformManager FQN that GameEngine
 * instantiates, delegating to the Android implementation. This file replaces
 * the SDL version only in the Android source set; the upstream file is
 * untouched for desktop builds.
 */
public class SdlPlatformManager extends AndroidPlatformManager {
  public SdlPlatformManager() {
    // GameEngine.<clinit> constructs this inside Main.main, after the Main
    // static block has bound log4j2.xml — a safe point to hook the
    // debug-build verbose log appender (no-op in release builds).
    legend.game.android.GameLog.attachVerboseAppender();
  }
}
