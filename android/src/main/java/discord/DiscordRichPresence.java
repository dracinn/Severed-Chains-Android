package discord;

import de.jcm.discordgamesdk.activity.Activity;

/**
 * Android variant: Discord rich presence is desktop-only. Provides the same
 * public surface so shared code (GameEngine, EngineStates) is unchanged.
 */
public final class DiscordRichPresence {
  public final Activity activity;

  public DiscordRichPresence() {
    this.activity = new Activity();
  }

  public void tick() {
  }

  public void updateActivity() {
  }

  public void init() {
  }

  public void destroy() {
  }
}
