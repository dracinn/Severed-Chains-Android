package lod.lloydmod.spelldeffs;

import legend.game.combat.Battle;
import legend.game.combat.deff.DeffPackage;
import legend.game.unpacker.Loader;
import lod.lloydmod.LloydMod;
import lod.lloydmod.menu.LloydHotPrizeMenu;

import java.io.IOException;
import java.nio.file.LinkOption;

import static legend.game.EngineStates.currentEngineState_8004dd04;

public class SigilofExplosion extends DeffPackage {

  public SigilofExplosion() {
  }

  @Override
  public void load() {
    try {
      if(LloydMod.hotPrizeEnabled) {
        LloydMod.hotPrizeMenu = new LloydHotPrizeMenu(8, 360, 1250);
        LloydMod.menuStack.pushScreen(LloydMod.hotPrizeMenu);
        LloydMod.hotPrizeActivated = true;
      }

      ((Battle)currentEngineState_8004dd04).loadDeff(
        Loader.resolve("..").normalize().toRealPath(LinkOption.NOFOLLOW_LINKS).resolve("mods/" + LloydMod.MOD_ID + "/scripts/battle/5005"),
        Loader.resolve("..").normalize().toRealPath(LinkOption.NOFOLLOW_LINKS).resolve("mods/" + LloydMod.MOD_ID + "/scripts/battle/5006")
      );
    } catch(final IOException e) {
      throw new RuntimeException(e);
    }
  }
}