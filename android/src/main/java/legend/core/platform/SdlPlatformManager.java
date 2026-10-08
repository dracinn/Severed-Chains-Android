package legend.core.platform;

/**
 * Android variant: keeps the SdlPlatformManager FQN that GameEngine
 * instantiates, delegating to the Android implementation. This file replaces
 * the SDL version only in the Android source set; the upstream file is
 * untouched for desktop builds.
 */
public class SdlPlatformManager extends AndroidPlatformManager {
}
