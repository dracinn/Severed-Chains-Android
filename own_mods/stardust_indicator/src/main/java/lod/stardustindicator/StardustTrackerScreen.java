package lod.stardustindicator;

import legend.core.lang.RawText;
import legend.core.platform.input.InputAction;
import legend.game.inventory.screens.InputPropagation;
import legend.game.inventory.screens.VerticalLayoutScreen;
import legend.game.inventory.screens.controls.Background;
import legend.game.inventory.screens.controls.Label;
import legend.game.modding.coremod.CoreMod;

import static legend.game.FullScreenEffects.startFadeEffect;
import static legend.game.Menus.deallocateRenderables;
import static legend.game.Scus94491BpeSegment_800b.battleLoaded_800bc94c;
import static legend.game.Scus94491BpeSegment_800b.gameState_800babc8;
import static legend.game.sound.Audio.playMenuSound;

/** Scrollable checklist of all 50 stardust, read live from scriptFlags2. */
public class StardustTrackerScreen extends VerticalLayoutScreen {
  static final int FLAG_BASE = 0x1c3;

  /** Canonical stardust list in flag order; index = flag - FLAG_BASE. */
  private static final String[] NAMES = {
    "Seles - headstone in the cemetery",
    "Bale - well in Slambert Plaza",
    "Bale - spears in the weapon shop",
    "Bale - castle basement furnace",
    "Bale - castle 3F, upper-left room",
    "Bale - underground wine stash",
    "Bale - Lavitz's house, kitchen",
    "Hoax - house fireplace, upper-left",
    "Hoax - Kaiser's HQ, lower-left",
    "Marshland - stronghold fireplace",
    "Lohan - bookcase secret room",
    "Lohan - armor in Dabas' shop",
    "Lohan - basket by the big tent",
    "Lohan - jars near the town exit",
    "Kazas - barrels, first house left",
    "Kazas - up the ladder, first right",
    "Kazas - counter, third house right",
    "Kazas - slums house bookcase",
    "Kazas - slums shop, front barrels",
    "Kazas - slums shop, back barrels",
    "Fletz - wine barrels in the bar",
    "Fletz - roof table by the church",
    "Fletz - weapons in weapon shop",
    "Fletz - blue stone, jewelry shop",
    "Fletz - bottles in item shop",
    "Donau - well in upper town",
    "Donau - Mayor's house, top-left",
    "Twin Castle - front hall armor",
    "Twin Castle - altar room",
    "Gigantos - torch by treasure room",
    "Queen Fury - lamp by Kongol",
    "Queen Fury - pipe in the cabin",
    "Lidiera - top of largest house",
    "Fueno - painting in hospital",
    "Fueno - barrels under inn stairs",
    "Furni - barrel in right house",
    "Furni - Teo's crystal ball",
    "Neet - lamp at the entrance",
    "Wingly Forest - Guaraha's house",
    "Wingly Forest - resting room pillar",
    "Deningrad - weapon shop cloth",
    "Deningrad - item shop cloth",
    "Deningrad - wheels, inn 2F",
    "Deningrad - Shana's room",
    "Deningrad - seal room entrance",
    "Vellweb - dragoon tower altar",
    "Ulara - flowers by save point",
    "Ulara - vase in weapon shop",
    "Ulara - dead plant, red flowers",
    "Rouge - jar in Martel's room",
  };

  private final Runnable unload;

  public StardustTrackerScreen(final Runnable unload) {
    this.unload = unload;
    this.addControl(new Background());

    deallocateRenderables(0xff);
    startFadeEffect(2, 10);

    int collected = 0;
    for(int i = 0; i < NAMES.length; i++) {
      if(gameState_800babc8.scriptFlags2_bc.get(FLAG_BASE + i)) {
        collected++;
      }
    }

    this.addRow(new RawText("Stardust " + collected + " / " + NAMES.length), null);

    for(int i = 0; i < NAMES.length; i++) {
      final boolean found = gameState_800babc8.scriptFlags2_bc.get(FLAG_BASE + i);
      this.addRow(new RawText((i + 1) + ". " + NAMES[i]), new Label(new RawText(found ? "Found" : "Missing")));
    }
  }

  @Override
  protected InputPropagation inputActionPressed(final InputAction action, final boolean repeat) {
    if(super.inputActionPressed(action, repeat) == InputPropagation.HANDLED) {
      return InputPropagation.HANDLED;
    }

    if(action == CoreMod.INPUT_ACTION_MENU_BACK.get()) {
      playMenuSound(3);
      this.unload.run();
      return InputPropagation.HANDLED;
    }

    return InputPropagation.PROPAGATE;
  }

  @Override
  protected float getSizeScale() {
    if(battleLoaded_800bc94c) {
      return this.getWidth() / 320.0f;
    } else {
      return super.getWidth() / 368.0f;
    }
  }

  @Override
  public int getWidth() {
    if(battleLoaded_800bc94c) {
      return 320;
    } else {
      return super.getWidth();
    }
  }
}
