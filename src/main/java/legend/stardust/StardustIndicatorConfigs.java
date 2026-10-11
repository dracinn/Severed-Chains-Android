package legend.stardust;

import legend.game.saves.ConfigEntry;
import legend.game.saves.ConfigRegistryEvent;
import org.legendofdragoon.modloader.registries.Registrar;
import org.legendofdragoon.modloader.registries.RegistryDelegate;

import static legend.core.GameEngine.REGISTRIES;

public final class StardustIndicatorConfigs {
  private StardustIndicatorConfigs() { }

  private static final Registrar<ConfigEntry<?>, ConfigRegistryEvent> REGISTRAR = new Registrar<>(REGISTRIES.config, StardustIndicatorMod.MOD_ID);

  public static final RegistryDelegate<StardustIndicatorConfigEntry> STARDUST_INDICATORS = REGISTRAR.register("stardust_indicators", StardustIndicatorConfigEntry::new);

  static void register(final ConfigRegistryEvent event) {
    REGISTRAR.registryEvent(event);
  }
}
