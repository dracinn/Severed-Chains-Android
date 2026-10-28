package lod.iconturnorder;

import legend.game.saves.ConfigEntry;
import legend.game.saves.ConfigRegistryEvent;
import org.legendofdragoon.modloader.registries.Registrar;
import org.legendofdragoon.modloader.registries.RegistryDelegate;

import static legend.core.GameEngine.REGISTRIES;

public final class IconTurnOrderConfigs {
  private IconTurnOrderConfigs() { }

  private static final Registrar<ConfigEntry<?>, ConfigRegistryEvent> REGISTRAR = new Registrar<>(REGISTRIES.config, IconTurnOrder.MOD_ID);

  public static final RegistryDelegate<ShowIconTurnOrderConfig> SHOW_ICON_TURN_ORDER = REGISTRAR.register("show_icon_turn_order", ShowIconTurnOrderConfig::new);

  static void register(final ConfigRegistryEvent event) {
    REGISTRAR.registryEvent(event);
  }

}
