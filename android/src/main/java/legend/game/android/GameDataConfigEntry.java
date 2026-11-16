package legend.game.android;

import legend.core.lang.I18nText;
import legend.core.lang.TextComponent;
import legend.game.inventory.screens.controls.Dropdown;
import legend.game.saves.ConfigCategory;
import legend.game.saves.ConfigCollection;
import legend.game.saves.ConfigStorageLocation;
import legend.game.saves.EnumConfigEntry;

/**
 * Options &gt; Game data location picker, visible from both the title-menu
 * and in-game Options screens. Controls where the multi-GB unpacked files/
 * tree lives — internal app storage or the app-private dir on a removable
 * SD card (plain filesystem access, no SAF). Mirrored into the AndroidEnv
 * pref that {@code gameFilesDir()} consults at boot; a change flags a
 * verified move of the tree on next launch. SD is offered only while a
 * usable removable volume is mounted.
 */
public class GameDataConfigEntry extends EnumConfigEntry<GameDataConfigEntry.GameDataLocation> {
  public enum GameDataLocation {
    AUTO,
    INTERNAL,
    SD_CARD,
  }

  public GameDataConfigEntry() {
    super(GameDataLocation.class, GameDataLocation.AUTO, ConfigStorageLocation.GLOBAL, ConfigCategory.OTHER);

    this.setEditControl((current, gameState) -> {
      final GameDataLocation effective = fromPref(AndroidEnv.gameDataLocation());
      final boolean sdAvailable = AndroidEnv.dataRootFor("sd") != null;

      final Dropdown<TextComponent> dropdown = new Dropdown<>((index, e) -> e);
      dropdown.onSelection(index -> gameState.setConfig(this, GameDataLocation.values()[index]));

      for(final GameDataLocation mode : GameDataLocation.values()) {
        if(mode == GameDataLocation.SD_CARD && !sdAvailable) {
          continue; // last enum value — skipping keeps index == ordinal
        }
        dropdown.addOption(new I18nText("lod_core.config.game_data_location." + mode.name()));
        if(mode == effective) {
          dropdown.setSelectedIndex(dropdown.size() - 1);
        }
      }

      return dropdown;
    });
  }

  @Override
  public boolean hasHelp() {
    return true;
  }

  @Override
  public boolean availableInBattle() {
    return false;
  }

  @Override
  public void onChange(final ConfigCollection configCollection, final GameDataLocation oldValue, final GameDataLocation newValue) {
    super.onChange(configCollection, oldValue, newValue);
    AndroidEnv.setGameDataLocation(switch(newValue) {
      case AUTO -> "auto";
      case INTERNAL -> "internal";
      case SD_CARD -> "sd";
    });
  }

  private static GameDataLocation fromPref(final String pref) {
    return switch(pref) {
      case "internal" -> GameDataLocation.INTERNAL;
      case "sd" -> GameDataLocation.SD_CARD;
      default -> GameDataLocation.AUTO;
    };
  }
}
