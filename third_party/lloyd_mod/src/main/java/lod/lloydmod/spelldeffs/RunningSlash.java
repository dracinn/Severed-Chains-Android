package lod.lloydmod.spelldeffs;

import legend.core.lang.I18nText;
import legend.core.lang.RawText;
import legend.game.combat.Battle;
import legend.game.combat.deff.DeffPackage;
import legend.game.inventory.screens.controls.Button;
import legend.game.unpacker.Loader;
import lod.lloydmod.LloydMod;
import lod.lloydmod.menu.LloydHotPrizeMenu;

import java.io.IOException;
import java.nio.file.LinkOption;

import static legend.game.EngineStates.currentEngineState_8004dd04;
import static legend.game.modding.coremod.CoreMod.INPUT_ACTION_MENU_SORT;

public class RunningSlash extends DeffPackage {

  public RunningSlash() {

  }

  @Override
  public void load() {
    try {
      if(LloydMod.hotPrizeEnabled) {
        LloydMod.hotPrizeMenu = new LloydHotPrizeMenu(2, 80, 0);
        LloydMod.menuStack.pushScreen(LloydMod.hotPrizeMenu);
        LloydMod.hotPrizeActivated = true;
      }

      ((Battle)currentEngineState_8004dd04).loadDeff(
        Loader.resolve("..").normalize().toRealPath(LinkOption.NOFOLLOW_LINKS).resolve("mods/" + LloydMod.MOD_ID + "/scripts/battle/4997"),
        Loader.resolve("..").normalize().toRealPath(LinkOption.NOFOLLOW_LINKS).resolve("mods/" + LloydMod.MOD_ID + "/scripts/battle/4998")
      );
    } catch(final IOException e) {
      throw new RuntimeException(e);
    }
  }

}
