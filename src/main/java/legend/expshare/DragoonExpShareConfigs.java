package legend.expshare;

import legend.game.modding.coremod.config.SecondaryCharacterXpMultiplierConfigEntry;
import legend.game.saves.ConfigEntry;
import legend.game.saves.ConfigRegistryEvent;
import org.legendofdragoon.modloader.registries.Registrar;
import org.legendofdragoon.modloader.registries.RegistryDelegate;

import static legend.core.GameEngine.REGISTRIES;

public final class DragoonExpShareConfigs {
  private DragoonExpShareConfigs() { }

  private static final Registrar<ConfigEntry<?>, ConfigRegistryEvent> REGISTRAR = new Registrar<>(REGISTRIES.config, DragoonExpShareMod.MOD_ID);

  public static final RegistryDelegate<SecondaryCharacterXpMultiplierConfigEntry> SECONDARY_DRAGOON_XP_MULTIPLIER = REGISTRAR.register("secondary_dragoon_xp_multiplier", SecondaryCharacterXpMultiplierConfigEntry::new);

  static void register(final ConfigRegistryEvent event) {
    REGISTRAR.registryEvent(event);
  }
}
