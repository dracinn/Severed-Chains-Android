package lod.lloydmod.spelldeffs;

import legend.game.combat.Battle;
import legend.game.combat.deff.DeffPackage;
import legend.game.unpacker.Loader;
import lod.lloydmod.LloydMod;
import lod.lloydmod.menu.LloydHotPrizeMenu;

import java.io.IOException;
import java.nio.file.LinkOption;

import static legend.game.EngineStates.currentEngineState_8004dd04;

public class DraconicImpact extends DeffPackage {

  public DraconicImpact() {
  }

  @Override
  public void load() {
    try {
      if(LloydMod.hotPrizeEnabled) {
        LloydMod.hotPrizeMenu = new LloydHotPrizeMenu(10, 440, 300);
        LloydMod.menuStack.pushScreen(LloydMod.hotPrizeMenu);
        LloydMod.hotPrizeActivated = true;
      }

      ((Battle)currentEngineState_8004dd04).loadDeff(
        Loader.resolve("..").normalize().toRealPath(LinkOption.NOFOLLOW_LINKS).resolve("mods/" + LloydMod.MOD_ID + "/scripts/battle/5001"),
        Loader.resolve("..").normalize().toRealPath(LinkOption.NOFOLLOW_LINKS).resolve("mods/" + LloydMod.MOD_ID + "/scripts/battle/5002")
      );
    } catch(final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
