package legend.expshare;

import legend.game.characters.CharacterData2c;
import legend.game.modding.events.battle.BattleEndedEvent;
import legend.game.saves.ConfigRegistryEvent;
import org.legendofdragoon.modloader.Mod;
import org.legendofdragoon.modloader.events.EventListener;

import static legend.core.GameEngine.CONFIG;
import static legend.core.GameEngine.EVENTS;
import static legend.game.Scus94491BpeSegment_800b.gameState_800babc8;
import static legend.game.Scus94491BpeSegment_800b.livingChars_800bc968;
import static legend.game.Scus94491BpeSegment_800b.spGained_800bc950;
import static legend.game.characters.CharacterData2c.IN_PARTY;
import static legend.lodmod.LodConfig.MAX_DRAGOON_LEVEL;

@Mod(id = DragoonExpShareMod.MOD_ID, version = "^3.0.0")
public class DragoonExpShareMod {
  public static final String MOD_ID = "dragoon_exp_share";

  public DragoonExpShareMod() {
    EVENTS.register(this);
  }

  @EventListener
  public void onRegisterConfig(final ConfigRegistryEvent event) {
    DragoonExpShareConfigs.register(event);
  }

  @EventListener
  public void onBattleEnded(final BattleEndedEvent event) {
    final float multiplier = CONFIG.getConfig(DragoonExpShareConfigs.SECONDARY_DRAGOON_XP_MULTIPLIER.get());
    if(multiplier <= 0.0f || spGained_800bc950.isEmpty() || livingChars_800bc968.isEmpty()) {
      return;
    }

    int totalSp = 0;
    for(final int sp : spGained_800bc950.values()) {
      totalSp += sp;
    }

    final int share = Math.round((float)totalSp / livingChars_800bc968.size() * multiplier);
    if(share <= 0) {
      return;
    }

    for(int slot = 0; slot < gameState_800babc8.charData_32c.size(); slot++) {
      final CharacterData2c character = gameState_800babc8.charData_32c.get(slot);
      if((character.partyFlags_04 & IN_PARTY) != 0
        && !gameState_800babc8.charIds_88.contains(slot)
        && character.hasDragoon()
        && !spGained_800bc950.containsKey(character)
        && character.dlevel_13 < CONFIG.getConfig(MAX_DRAGOON_LEVEL.get())) {
        spGained_800bc950.mergeInt(character, share, Integer::sum);
      }
    }
  }
}
