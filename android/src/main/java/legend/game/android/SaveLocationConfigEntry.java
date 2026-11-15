package legend.game.android;

import legend.core.lang.I18nText;
import legend.core.lang.TextComponent;
import legend.game.inventory.screens.controls.Dropdown;
import legend.game.saves.ConfigCategory;
import legend.game.saves.ConfigCollection;
import legend.game.saves.ConfigStorageLocation;
import legend.game.saves.EnumConfigEntry;

/**
 * Options &gt; Save location picker, visible from both the title-menu and
 * in-game Options screens. The choice is mirrored into the AndroidEnv
 * shared pref that {@code savesPath()} consults — campaign config loads
 * too late in boot to be the authority — and the dropdown's initial
 * selection also reflects the pref, so the setup screen and the options
 * screen always agree. SD is offered only while a writable removable
 * volume exists.
 */
public class SaveLocationConfigEntry extends EnumConfigEntry<SaveLocationConfigEntry.SaveLocation> {
  public enum SaveLocation {
    AUTO,
    INTERNAL,
    SHARED,
    SD_CARD,
  }

  public SaveLocationConfigEntry() {
    super(SaveLocation.class, SaveLocation.AUTO, ConfigStorageLocation.GLOBAL, ConfigCategory.OTHER);

    this.setEditControl((current, gameState) -> {
      // The stored dcnf value can drift from the pref (setup screen, legacy
      // saves_on_sd) — present the effective location, not the stored one.
      final SaveLocation effective = fromPref(AndroidEnv.saveLocation());
      final boolean sdAvailable = AndroidEnv.sdSavesTarget() != null;

      final Dropdown<TextComponent> dropdown = new Dropdown<>((index, e) -> e);
      dropdown.onSelection(index -> gameState.setConfig(this, SaveLocation.values()[index]));

      for(final SaveLocation mode : SaveLocation.values()) {
        if(mode == SaveLocation.SD_CARD && !sdAvailable) {
          continue; // last enum value — skipping keeps index == ordinal
        }
        dropdown.addOption(new I18nText("lod_core.config.save_location." + mode.name()));
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
  public void onChange(final ConfigCollection configCollection, final SaveLocation oldValue, final SaveLocation newValue) {
    super.onChange(configCollection, oldValue, newValue);
    AndroidEnv.setSaveLocation(switch(newValue) {
      case AUTO -> "auto";
      case INTERNAL -> "internal";
      case SHARED -> "documents";
      case SD_CARD -> "sd";
    });
  }

  private static SaveLocation fromPref(final String pref) {
    return switch(pref) {
      case "internal" -> SaveLocation.INTERNAL;
      case "documents" -> SaveLocation.SHARED;
      case "sd" -> SaveLocation.SD_CARD;
      default -> SaveLocation.AUTO;
    };
  }
}
