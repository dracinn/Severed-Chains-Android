package lod.stardustindicator;

import legend.core.lang.RawText;
import legend.game.SItem;
import legend.game.inventory.screens.controls.Button;
import legend.game.saves.ConfigCategory;
import legend.game.saves.ConfigEntry;
import legend.game.saves.ConfigStorageLocation;
import legend.game.saves.StringConfigEntry;
import org.legendofdragoon.modloader.registries.RegistryDelegate;

import java.util.List;
import java.util.Map;

import static legend.game.FullScreenEffects.startFadeEffect;

public class StardustTrackerConfig extends ConfigEntry<Map<RegistryDelegate<StringConfigEntry>, List<String>>> {
  public StardustTrackerConfig() {
    super(Map.of(), ConfigStorageLocation.CAMPAIGN, ConfigCategory.GAMEPLAY, StardustTrackerConfig::serializer, StardustTrackerConfig::deserializer);

    this.setEditControl((current, configCollection) -> {
      final Button button = new Button(new RawText("Stardust Tracker"));
      button.onPressed(() -> button.getScreen().getStack().pushScreen(new StardustTrackerScreen(() -> {
        startFadeEffect(2, 10);
        SItem.menuStack.popScreen();
      })));

      return button;
    });
  }

  private static byte[] serializer(final Map<RegistryDelegate<StringConfigEntry>, List<String>> registryDelegateListMap) {
    return new byte[0];
  }

  private static Map<RegistryDelegate<StringConfigEntry>, List<String>> deserializer(final byte[] data) {
    return Map.of();
  }
}
