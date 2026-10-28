package lod.lloydmod.menu;

import legend.game.combat.Battle;
import legend.game.combat.bent.PlayerBattleEntity;
import legend.game.combat.ui.BattleActionTickFlowControl;
import legend.game.combat.ui.BattleActionUseFlowControl;
import legend.game.combat.ui.ListPosition;
import legend.game.scripting.ScriptFile;
import legend.game.scripting.ScriptStackFrame;
import legend.game.unpacker.Loader;
import legend.lodmod.battleactions.RetailBattleAction;
import lod.lloydmod.LloydMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static legend.core.GameEngine.SCRIPTS;

public class LloydDragoonSpellAction extends RetailBattleAction {
  private Battle battle;
  private PlayerBattleEntity player;

  public LloydDragoonSpellAction() { super(3); }

  @Override
  public BattleActionUseFlowControl use(final Battle battle, final PlayerBattleEntity player) {
    this.battle = battle;
    this.player = player;
    final ListPosition lastPosition = new ListPosition();
    lastPosition.lastListIndex_26 = 0;
    lastPosition.lastListScroll_28 = 0;

    battle.hud.listMenu_800c6b60 = new LloydDragoonSpellMenu(battle.hud, player, lastPosition, this::onListClose);

    final ScriptFile file = new ScriptFile("lloyd_combat_selection", Loader.loadFileSync(Path.of("mods", LloydMod.MOD_ID, "scripts", "battle", "spell_selection")).getBytes());
    player.getState().pushFrame(new ScriptStackFrame(file, file.getEntry(0)));
    player.getState().context.commandOffset_0c = player.getState().frame().offset;
    player.getState().setStor(40, 0);
    player.getState().setStor(41, 1);

    return BattleActionUseFlowControl.CONTINUE_SCRIPT;
  }

  @Override
  public BattleActionTickFlowControl tick(final Battle battle, final PlayerBattleEntity player) {
    if(this.battle.hud.listMenu_800c6b60 != null) {
      return BattleActionTickFlowControl.PAUSE_SCRIPT;
    }

    if(player.getState().getStor(41) == 1) {
      return BattleActionTickFlowControl.REPEAT_TURN;
    } else {
      return BattleActionTickFlowControl.CONTINUE_SCRIPT;
    }
  }

  private void onListClose() {
    if(this.battle != null) {
      this.battle.hud.listMenu_800c6b60 = null;
      this.player.getState().setStor(40, 2);
    }
  }

}
