package lod.lloydmod.menu;


import legend.core.memory.Method;
import legend.game.combat.bent.PlayerBattleEntity;
import legend.game.combat.ui.BattleHud;
import legend.game.combat.ui.CombatItem02;
import legend.game.combat.ui.ListMenu;
import legend.game.combat.ui.ListPosition;
import legend.game.i18n.I18n;
import legend.game.inventory.Item;
import legend.game.inventory.ItemStack;
import legend.game.inventory.screens.FontOptions;
import legend.game.inventory.screens.HorizontalAlign;
import legend.game.inventory.screens.TextColour;
import legend.game.scripting.RunningScript;
import legend.game.scripting.ScriptState;
import legend.game.ui.UiBox;
import legend.lodmod.LodItems;
import lod.lloydmod.LloydSpells;
import org.legendofdragoon.modloader.registries.RegistryId;

import java.util.ArrayList;
import java.util.List;

import static legend.core.GameEngine.REGISTRIES;
import static legend.game.Scus94491BpeSegment_800b.gameState_800babc8;
import static legend.game.Text.renderText;

public class LloydDragoonSpellMenu extends ListMenu {
  private final FontOptions fontOptions = new FontOptions().colour(TextColour.WHITE);
  private UiBox description;
  private final List<CombatItem02> combatItems_800c6988 = new ArrayList<>();
  final PlayerBattleEntity player;

  public LloydDragoonSpellMenu(final BattleHud hud, final PlayerBattleEntity activePlayer, final ListPosition lastPosition, final Runnable onClose) {
    super(hud, activePlayer, 186, lastPosition, onClose);
    this.player = activePlayer;
    this.cacheItems(activePlayer);

    this.flags_02 |= 0x20;
  }

  @Override
  protected int getListCount() {
    return this.combatItems_800c6988.size();
  }

  @Override
  protected void drawListEntry(final int index, final int x, final int y, final int trim) {
    this.fontOptions.trim(trim);
    this.fontOptions.horizontalAlign(HorizontalAlign.LEFT);
    renderText(I18n.translate(this.combatItems_800c6988.get(index).getStack().getNameTranslationKey()), x, y, this.fontOptions);
    renderText("×", x + 143, y, this.fontOptions);

    this.fontOptions.horizontalAlign(HorizontalAlign.RIGHT);
    renderText(String.valueOf(this.combatItems_800c6988.get(index).getSize()), x + 168, y, this.fontOptions);
  }

  @Override
  protected void onSelection(final int index) {
    //LAB_800f50b8
    this.player_08.setActiveItem(this.combatItems_800c6988.get(index).getStack());
  }

  @Override
  protected void onUse(final int index) {
    /*final ItemStack original = this.player_08.item_d4;
    this.player_08.item_d4 = this.player_08.item_d4.take(1);
    gameState_800babc8.items_2e9.removeIfEmpty(original);

    final RepeatItemReturnEvent repeatItemReturnEvent = EVENTS.postEvent(new RepeatItemReturnEvent(stack, stack.isRepeat()));

    if(repeatItemReturnEvent.returnItem) {
      this.hud.battle.usedRepeatItems_800c6c3c.add(stack);
    }*/

    final ItemStack stack = this.player_08.item_d4;
    final ScriptState<PlayerBattleEntity> state = this.player_08.getState();
    state.setStor(40, 1);
    state.setStor(41, 0);
    state.setStor(8, this.selectionState_a0);
    state.setStor(28, this.hud.battleMenu_800c6c34.target_48);
    state.setStor(30, this.player_08.getState().index);

    if(stack.getItem().equals(LodItems.SPINNING_GALE.get())) {
      state.registryIds[0] = LodItems.SPINNING_GALE.getId();
    } else if(stack.getItem().equals(LodItems.FATAL_BLIZZARD.get())) {
      state.registryIds[0] = LodItems.FATAL_BLIZZARD.getId();
      state.setStor(28, -1);
    } else if(stack.getItem().equals(LodItems.TRANS_LIGHT.get())) {
      state.registryIds[0] = LodItems.TRANS_LIGHT.getId();
    } else if(stack.getItem().equals(LodItems.RAVE_TWISTER.get())) {
      state.registryIds[0] = LodItems.RAVE_TWISTER.getId();
      state.setStor(28, -1);
    } else if(stack.getItem().equals(LodItems.DANCING_RAY.get())) {
      state.registryIds[0] = LodItems.DANCING_RAY.getId();
      state.setStor(28, -1);
    }

    this.hud.setBattleHudVisibility(false);
  }

  @Override
  protected void onClose() {
  }

  @Override
  protected int handleTargeting() {
    if(this.player_08.item_d4.canTarget(Item.TargetType.INSTANT)) {
      return 2;
    }

    //TODO
    return this.hud.handleTargeting(this.player_08.item_d4.canTarget(Item.TargetType.ALLIES) ? 0 : 1, this.player_08.item_d4.canTarget(Item.TargetType.ALL));
  }

  @Override
  public void getTargetingInfo(final RunningScript<?> script) {
    script.params_20[0].set(this.selectionState_a0);

    if(this.selectionState_a0 < 1) {
      return;
    }

    script.params_20[1].set(this.hud.battleMenu_800c6c34.target_48);
    script.params_20[2].set(this.player_08.item_d4.getItem().getRegistryId());

    // If it's a target all item, -1 the target
    if(this.selectionState_a0 == 1 && this.player_08.item_d4.canTarget(Item.TargetType.ALL)) {
      script.params_20[1].set(-1);
    }
  }

  @Method(0x800f83c8L)
  private void cacheItems(final PlayerBattleEntity activePlayer) {

    this.combatItems_800c6988.clear();
    for(final RegistryId id : gameState_800babc8.charData_32c.get(activePlayer.charId_272).getUnlockedSpells()) {
      if(REGISTRIES.spells.getEntry(id).get().targetType_00 == 0) {
        if(id.equals(LloydSpells.SPINNING_GALE.getId())) {
          this.combatItems_800c6988.add(new CombatItem02(new ItemStack(LodItems.SPINNING_GALE.get())));
        } else if(id.equals(LloydSpells.FATAL_BLIZZARD.getId())) {
          this.combatItems_800c6988.add(new CombatItem02(new ItemStack(LodItems.FATAL_BLIZZARD.get())));
        } else if(id.equals(LloydSpells.TRANS_LIGHT.getId())) {
          this.combatItems_800c6988.add(new CombatItem02(new ItemStack(LodItems.TRANS_LIGHT.get())));
        } else if(id.equals(LloydSpells.RAVE_TWISTER.getId())) {
          this.combatItems_800c6988.add(new CombatItem02(new ItemStack(LodItems.RAVE_TWISTER.get())));
        } else if(id.equals(LloydSpells.DANCING_RAY.getId())) {
          this.combatItems_800c6988.add(new CombatItem02(new ItemStack(LodItems.DANCING_RAY.get())));
        }
      }
    }
  }

  @Override
  public void draw() {
    super.draw();

    if(this.menuState_00 != 0 && (this.flags_02 & 0x1) != 0) {
      //LAB_800f5f50
      if((this.flags_02 & 0x40) != 0) {
        //Selected item description
        if(this.description == null) {
          this.description = new UiBox(44, 156, 232, 14);
        }

        this.description.render();

        this.fontOptions.trim(0);
        this.fontOptions.horizontalAlign(HorizontalAlign.CENTRE);

        final ItemStack selected = this.combatItems_800c6988.get(this.listScroll_1e + this.listIndex_24).getStack();

        if(!selected.isEmpty()) {
          renderText(I18n.translate(selected.getBattleDescriptionTranslationKey()), 160, 157, this.fontOptions);
        }
      }
    }
  }
}
