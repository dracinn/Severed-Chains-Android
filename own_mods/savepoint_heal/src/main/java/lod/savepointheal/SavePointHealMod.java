package lod.savepointheal;

import legend.game.characters.CharacterData2c;
import legend.game.inventory.screens.SaveGameScreen;
import legend.game.modding.events.RenderEvent;
import legend.game.submap.SMap;
import org.joml.Vector3f;
import org.legendofdragoon.modloader.Mod;
import org.legendofdragoon.modloader.events.EventListener;

import static legend.core.GameEngine.EVENTS;
import static legend.game.EngineStates.currentEngineState_8004dd04;
import static legend.game.SItem.menuStack;
import static legend.game.Scus94491BpeSegment_800b.gameState_800babc8;
import static legend.game.characters.CharacterData2c.IN_PARTY;
import static legend.lodmod.LodMod.HP_STAT;
import static legend.lodmod.LodMod.MP_STAT;

/** Restores the active party's HP and MP at save points. */
@Mod(id = SavePointHealMod.MOD_ID, version = "^3.0.0")
public class SavePointHealMod {
  public static final String MOD_ID = "savepointheal";
  private static final float RADIUS = 200.0f;

  private boolean wasOpen;
  private boolean near;

  public SavePointHealMod() {
    EVENTS.register(this);
  }

  @EventListener
  public void render(final RenderEvent event) {
    // Heal on opening the save screen
    final boolean open = menuStack.peek() instanceof SaveGameScreen;
    if(open && !this.wasOpen) {
      this.heal();
    }
    this.wasOpen = open;

    // Heal on walking into a save point's vicinity
    if(currentEngineState_8004dd04 instanceof final SMap smap && smap.hasSavePoint() && gameState_800babc8 != null) {
      final Vector3f player = smap.playerPos();
      final Vector3f spot = smap.savePointPos();
      final boolean inside = player.distanceSquared(spot) <= RADIUS * RADIUS;
      if(inside && !this.near) {
        this.heal();
      }
      this.near = inside;
    } else {
      this.near = false;
    }
  }

  private void heal() {
    for(int i = 0; i < gameState_800babc8.charData_32c.size(); i++) {
      final CharacterData2c chr = gameState_800babc8.charData_32c.get(i);
      if((chr.partyFlags_04 & IN_PARTY) != 0) {
        chr.stats.getStat(HP_STAT.get()).restore();
        chr.stats.getStat(MP_STAT.get()).restore();
      }
    }
  }
}
