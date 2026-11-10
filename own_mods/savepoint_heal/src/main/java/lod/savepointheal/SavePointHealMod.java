package lod.savepointheal;

import legend.game.characters.CharacterData2c;
import legend.game.inventory.WhichMenu;
import legend.game.modding.events.RenderEvent;
import org.legendofdragoon.modloader.Mod;
import org.legendofdragoon.modloader.events.EventListener;

import static legend.core.GameEngine.EVENTS;
import static legend.game.Menus.whichMenu_800bdc38;
import static legend.game.Scus94491BpeSegment_800b.gameState_800babc8;
import static legend.game.characters.CharacterData2c.IN_PARTY;
import static legend.lodmod.LodMod.HP_STAT;
import static legend.lodmod.LodMod.MP_STAT;

/** Restores the active party's HP and MP whenever the save-point menu opens. */
@Mod(id = SavePointHealMod.MOD_ID, version = "^3.0.0")
public class SavePointHealMod {
  public static final String MOD_ID = "savepointheal";

  private boolean wasOpen;

  public SavePointHealMod() {
    EVENTS.register(this);
  }

  @EventListener
  public void render(final RenderEvent event) {
    final boolean open = whichMenu_800bdc38 == WhichMenu.RENDER_SAVE_GAME_MENU_19;
    if(open && !this.wasOpen && gameState_800babc8 != null) {
      for(int i = 0; i < gameState_800babc8.charData_32c.size(); i++) {
        final CharacterData2c chr = gameState_800babc8.charData_32c.get(i);
        if((chr.partyFlags_04 & IN_PARTY) != 0) {
          chr.stats.getStat(HP_STAT.get()).restore();
          chr.stats.getStat(MP_STAT.get()).restore();
        }
      }
    }
    this.wasOpen = open;
  }
}
