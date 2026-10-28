package lod.lloydmod.spelldeffs;

import legend.game.combat.Battle;
import legend.game.combat.deff.DeffPackage;
import legend.game.unpacker.Loader;
import lod.lloydmod.LloydMod;
import lod.lloydmod.menu.LloydHotPrizeMenu;

import java.io.IOException;
import java.nio.file.LinkOption;

import static legend.game.EngineStates.currentEngineState_8004dd04;

public class AerialCrash extends DeffPackage {

  public AerialCrash() {

  }

  @Override
  public void load() {
    try {
      if(LloydMod.hotPrizeEnabled) {
        LloydMod.hotPrizeMenu = new LloydHotPrizeMenu(4, 160, 140);
        LloydMod.menuStack.pushScreen(LloydMod.hotPrizeMenu);
        LloydMod.hotPrizeActivated = true;
      }

      ((Battle)currentEngineState_8004dd04).loadDeff(
        Loader.resolve("..").normalize().toRealPath(LinkOption.NOFOLLOW_LINKS).resolve("mods/" + LloydMod.MOD_ID + "/scripts/battle/4999"),
        Loader.resolve("..").normalize().toRealPath(LinkOption.NOFOLLOW_LINKS).resolve("mods/" + LloydMod.MOD_ID + "/scripts/battle/5000")
      );
    } catch(final IOException e) {
      throw new RuntimeException(e);
    }
  }

}
