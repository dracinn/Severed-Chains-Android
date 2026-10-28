package lod.lloydmod.menu;


import legend.core.renderer.QueuedModelStandard;
import legend.game.characters.VitalsStat;
import legend.game.combat.bent.PlayerBattleEntity;
import legend.game.combat.types.DragoonSpells09;
import legend.game.combat.ui.BattleHud;
import legend.game.combat.ui.ListMenu;
import legend.game.combat.ui.ListPosition;
import legend.game.i18n.I18n;
import legend.game.inventory.SpellStats0c;
import legend.game.inventory.screens.FontOptions;
import legend.game.inventory.screens.HorizontalAlign;
import legend.game.inventory.screens.TextColour;
import legend.game.modding.events.battle.SpellStatsEvent;
import legend.game.scripting.RunningScript;
import legend.game.scripting.ScriptState;
import legend.game.ui.UiBox;
import legend.lodmod.LodMod;
import lod.lloydmod.LloydDeffs;
import lod.lloydmod.LloydMod;
import lod.lloydmod.LloydSpells;
import org.legendofdragoon.modloader.registries.RegistryId;

import java.util.ArrayList;
import java.util.List;

import static legend.core.GameEngine.EVENTS;
import static legend.core.GameEngine.REGISTRIES;
import static legend.core.GameEngine.RENDERER;
import static legend.game.Scus94491BpeSegment_800b.gameState_800babc8;
import static legend.game.Text.renderText;

public class LloydCombatAttackMenu extends ListMenu {
  private final FontOptions fontOptions = new FontOptions().colour(TextColour.WHITE);
  private UiBox description;
  private final DragoonSpells09 spells;


  public LloydCombatAttackMenu(final BattleHud hud, final PlayerBattleEntity activePlayer, final ListPosition lastPosition, final Runnable onClose) {
    super(hud, activePlayer, 186, modifyLastPosition(lastPosition), onClose);

    final List<SpellStats0c> currentSpells = new ArrayList<>();

    final List<RegistryId> spells = gameState_800babc8.charData_32c.get(activePlayer.charId_272).getUnlockedSpells();
    for(final RegistryId id : spells) {
      if(REGISTRIES.spells.getEntry(id).get().targetType_00 != 0) {
        if(activePlayer.isDragoon()) {
          if(!"running_slash".equals(REGISTRIES.spells.getEntry(id).getId().entryId()) && !"spectral_sword".equals(REGISTRIES.spells.getEntry(id).getId().entryId())) {
            currentSpells.add(REGISTRIES.spells.getEntry(id).get());
          }
        } else {
          if("running_slash".equals(REGISTRIES.spells.getEntry(id).getId().entryId()) || "spectral_sword".equals(REGISTRIES.spells.getEntry(id).getId().entryId())) {
            currentSpells.add(REGISTRIES.spells.getEntry(id).get());
          }
        }
      }
    }

    this.spells = new DragoonSpells09(activePlayer.character);
    this.spells.spells_01.addAll(currentSpells);
  }

  private static ListPosition modifyLastPosition(final ListPosition lastPosition) {
    lastPosition.lastListIndex_26 = 0;
    lastPosition.lastListScroll_28 = 0;
    return lastPosition;
  }

  @Override
  protected int getListCount() {
    return this.spells.spells_01.size();
  }

  @Override
  protected void drawListEntry(final int index, final int x, final int y, final int trim) {
    final TextColour textColour;
    this.setActiveSpell(index);
    if(!this.canUse()) {
      textColour = TextColour.GREY;
    } else {
      textColour = TextColour.WHITE;
    }

    final SpellStats0c spell = this.spells.spells_01.get(index);

    this.fontOptions.trim(trim);
    this.fontOptions.horizontalAlign(HorizontalAlign.LEFT);
    this.fontOptions.colour(textColour);
    renderText(I18n.translate(spell), x, y, this.fontOptions);
    this.fontOptions.horizontalAlign(HorizontalAlign.RIGHT);
    this.fontOptions.colour(TextColour.WHITE);
    renderText(String.valueOf(this.player_08.spell_94.mp_06), x + 152, y, this.fontOptions);

    this.transforms.scaling(0.75f);
    this.transforms.transfer.set(x + 152, y, 124.0f);
    RENDERER.queueOrthoModel(this.menuObj, this.transforms, QueuedModelStandard.class)
      .vertices(this.mpObjOffset, 4);
  }

  @Override
  protected void onSelection(final int index) {
    this.setActiveSpell(index);
  }

  @Override
  protected boolean canUse() {
    final int currentMP = this.player_08.stats.getStat(LodMod.MP_STAT.get()).getCurrent();
    final int spellMPCost = this.player_08.spell_94.mp_06;
    return currentMP >= spellMPCost;
  }

  private void setActiveSpell(final int index) {
    this.player_08.spell_94 = this.spells.spells_01.get(index);
  }

  @Override
  protected void onUse(final int index) {
    final VitalsStat mp = this.player_08.stats.getStat(LodMod.MP_STAT.get());
    mp.setCurrent(mp.getCurrent() - this.player_08.spell_94.mp_06);

    final ScriptState<PlayerBattleEntity> state = this.player_08.getState();
    state.setStor(40, 1);

    state.setStor(8, this.selectionState_a0);
    state.setStor(28, this.hud.battleMenu_800c6c34.target_48);
    state.setStor(30, this.player_08.getState().index);

    if(this.player_08.isDragoon()) {
      if(this.listScroll_1e + this.listIndex_24 == 0) {
        LloydMod.lloydUsedSpell = LloydSpells.SWORD_RUSH.get();
        state.registryIds[1] = LloydDeffs.SWORD_RUSH_DEFF.getId();
        state.setStor(41, 2);
      } else if(this.listScroll_1e + this.listIndex_24 == 1) {
        LloydMod.lloydUsedSpell = LloydSpells.AERIAL_CRASH.get();
        state.registryIds[1] = LloydDeffs.AERIAL_CRASH_DEFF.getId();
        state.setStor(41, 0);
      } else if(this.listScroll_1e + this.listIndex_24 == 2) {
        LloydMod.lloydUsedSpell = LloydSpells.BALL_BARRAGE.get();
        state.registryIds[1] = LloydDeffs.BALL_BARRAGE_DEFF.getId();
        state.setStor(41, 0);
      } else if(this.listScroll_1e + this.listIndex_24 == 3) {
        LloydMod.lloydUsedSpell = LloydSpells.SIGIL_OF_EXPLOSION.get();
        state.registryIds[1] = LloydDeffs.SIGIL_OF_EXPLOSION_DEFF.getId();
        state.setStor(41, 0);
      } else if(this.listScroll_1e + this.listIndex_24 == 4) {
        LloydMod.lloydUsedSpell = LloydSpells.DRACONIC_IMPALE.get();
        state.registryIds[1] = LloydDeffs.DRACONIC_IMPACT_DEFF.getId();
        state.setStor(41, 0);
      }
    } else {
      if(this.listScroll_1e + this.listIndex_24 == 0) {
        LloydMod.lloydUsedSpell = LloydSpells.RUNNING_SLASH.get();
        state.registryIds[1] = LloydDeffs.RUNNING_SLASH_DEFF.getId();
        state.setStor(41, 0);
      } else if(this.listScroll_1e + this.listIndex_24 == 1) {
        LloydMod.lloydUsedSpell = LloydSpells.SPECTRAL_SWORD.get();
        state.registryIds[1] = LloydDeffs.SPECTRAL_SWORD_DEFF.getId();
        state.setStor(41, 1);
      }
    }

    this.hud.setBattleHudVisibility(false);
  }

  @Override
  protected void onClose() {

  }

  @Override
  protected int handleTargeting() {
    final int itemTargetType = this.player_08.spell_94.targetType_00;
    final int targetType = (itemTargetType & 0x40) > 0 ? 1 : 0;
    final boolean targetAll = (itemTargetType & 0x8) != 0;
    return this.hud.handleTargeting(targetType, targetAll);
  }

  @Override
  public void getTargetingInfo(final RunningScript<?> script) {
    script.params_20[0].set(this.selectionState_a0);
    script.params_20[1].set(this.hud.battleMenu_800c6c34.target_48);
    script.params_20[2].set(this.player_08.spell_94.getRegistryId());
  }

  @Override
  public void draw() {
    super.draw();

    if(this.menuState_00 != 0 && (this.flags_02 & 0x1) != 0) {
      //LAB_800f5f50
      if((this.flags_02 & 0x40) != 0) {
        final SpellStats0c spell = EVENTS.postEvent(new SpellStatsEvent(this.spells.character_00, this.spells.spells_01.get(this.listScroll_1e + this.listIndex_24))).spell;

        //Selected item description
        if(this.description == null) {
          this.description = new UiBox(44, 156, 232, 14);
        }

        this.description.render();

        this.fontOptions.trim(0);
        this.fontOptions.horizontalAlign(HorizontalAlign.CENTRE);
        renderText(I18n.translate(spell.getTranslationKey("description")), 160, 157, this.fontOptions);
      }
    }
  }
}
