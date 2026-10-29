package lod.stardustindicator;

import legend.core.gte.MV;
import legend.core.lang.RawText;
import legend.core.platform.input.InputAction;
import legend.game.inventory.ItemIcon;
import legend.game.inventory.InventoryEntry;
import legend.game.inventory.screens.InputPropagation;
import legend.game.inventory.screens.MenuScreen;
import legend.game.inventory.screens.controls.Background;
import legend.game.inventory.screens.controls.Glyph;
import legend.game.inventory.screens.controls.ItemList;
import legend.game.inventory.screens.controls.Label;
import legend.game.inventory.screens.controls.ListBox;
import legend.game.textures.TextureAtlasIcon;
import legend.game.types.MenuEntryStruct04;
import org.legendofdragoon.modloader.registries.RegistryId;

import javax.annotation.Nullable;

import static legend.core.GameEngine.getTextureAtlas;
import static legend.game.FullScreenEffects.startFadeEffect;
import static legend.game.Menus.deallocateRenderables;
import static legend.game.Scus94491BpeSegment_800b.gameState_800babc8;
import static legend.game.modding.coremod.CoreMod.INPUT_ACTION_MENU_BACK;
import static legend.game.modding.coremod.CoreMod.INPUT_ACTION_MENU_LEFT;
import static legend.game.modding.coremod.CoreMod.INPUT_ACTION_MENU_RIGHT;
import static legend.game.sound.Audio.playMenuSound;

/** Scrollable checklist of the 50 stardust, in the Goods screen's two-column list style. */
public class StardustTrackerScreen extends MenuScreen {
  // Canonical stardust order: "Location|Location detail" pairs.
  private static final String[] ENTRIES = {
    "Seles|Headstone in the\ncemetery",
    "Bale|Well in Slambert\nPlaza",
    "Bale|Spears in the\nweapon shop",
    "Bale|Castle basement\nfurnace",
    "Bale|Castle 3F, upper-\nleft room",
    "Bale|Underground wine\nstash",
    "Bale|Lavitz's house\nkitchen",
    "Hoax|Fireplace, upper-\nleft house",
    "Hoax|Kaiser's HQ,\nlower-left",
    "Marshland|Stronghold\nfireplace",
    "Lohan|Bookcase secret\nroom",
    "Lohan|Armor in Dabas'\nshop",
    "Lohan|Basket by the\nbig tent",
    "Lohan|Jars near the\ntown exit",
    "Kazas|Barrels, first\nhouse left",
    "Kazas|Up the ladder,\nfirst right",
    "Kazas|Counter, third\nhouse right",
    "Kazas|Slums house\nbookcase",
    "Kazas|Slums shop, front\nbarrels",
    "Kazas|Slums shop, back\nbarrels",
    "Fletz|Wine barrels in\nthe bar",
    "Fletz|Roof table by\nthe church",
    "Fletz|Weapons in the\nweapon shop",
    "Fletz|Blue stone in\njewelry shop",
    "Fletz|Bottles in the\nitem shop",
    "Donau|Well in the\nupper town",
    "Donau|Mayor's house,\ntop-left",
    "Twin Castle|Front hall\narmor",
    "Twin Castle|Altar room",
    "Gigantos|Torch by the\ntreasure room",
    "Queen Fury|Lamp by\nKongol",
    "Queen Fury|Pipe in the\ncabin",
    "Lidiera|Top of the\nlargest house",
    "Fueno|Painting in the\nhospital",
    "Fueno|Barrels under\ninn stairs",
    "Furni|Barrel in the\nright house",
    "Furni|Teo's crystal\nball",
    "Neet|Lamp at the\nentrance",
    "Wingly Forest|Guaraha's\nhouse",
    "Wingly Forest|Resting\nroom pillar",
    "Deningrad|Weapon shop\ncloth",
    "Deningrad|Item shop\ncloth",
    "Deningrad|Wheels in\ninn 2F",
    "Deningrad|Shana's room",
    "Deningrad|Seal room\nentrance",
    "Vellweb|Dragoon tower\naltar",
    "Ulara|Flowers by the\nsave point",
    "Ulara|Vase in the\nweapon shop",
    "Ulara|Dead plant, red\nflowers",
    "Rouge|Jar in Martel's\nroom",
  };

  private static final int FLAG_BASE = 0x1c3;

  private final Runnable unload;
  private final ItemList<StardustEntry> leftList;
  private final ItemList<StardustEntry> rightList;
  private final Label description = new Label(RawText.BLANK);

  private final MV mv = new MV();
  private TextureAtlasIcon sparkle;

  private static final class StardustEntry implements InventoryEntry<StardustEntry> {
    private final int flag;
    private final String name;
    private final String hint;
    private final boolean found;

    private StardustEntry(final int flag, final String name, final String hint, final boolean found) {
      this.flag = flag;
      this.name = name;
      this.hint = hint;
      this.found = found;
    }

    private String name() {
      return this.name;
    }

    private String hint() {
      return this.hint;
    }

    private boolean found() {
      return this.found;
    }

    @Override
    public RegistryId getRegistryId() {
      return StardustIndicatorMod.SPARKLE_ICON_ID;
    }

    @Override
    public ItemIcon getIcon() {
      return null;
    }

    @Override
    public String getNameTranslationKey() {
      return "stardustindicator.stardust_tracker.name";
    }

    @Override
    public String getDescriptionTranslationKey() {
      return "stardustindicator.stardust_tracker.description";
    }

    @Override
    public int getSize() {
      return 1;
    }

    @Override
    public int getMaxSize() {
      return 1;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }
  }

  public StardustTrackerScreen(final Runnable unload) {
    deallocateRenderables(0xff);
    startFadeEffect(2, 10);

    this.unload = unload;

    final ListBox.Highlight<MenuEntryStruct04<StardustEntry>> description = item -> this.description.setText(item == null ? RawText.BLANK : new RawText(item.item_00.hint()));

    this.leftList = new ItemList<>(entry -> entry.item_00.name(), null, this::renderIcon, null, null, null);
    this.leftList.setPos(8, 15);
    this.leftList.setTitle(new RawText("Stardust Tracker"));

    this.rightList = new ItemList<>(entry -> entry.item_00.name(), null, this::renderIcon, null, null, null);
    this.rightList.setPos(188, 15);

    this.leftList.onHoverIn(() -> this.setFocus(this.leftList));
    this.leftList.onGotFocus(() -> {
      this.leftList.showHighlight();
      this.rightList.hideHighlight();
      this.description.show();
      description.highlight(this.leftList.getSelectedItem());
    });
    this.leftList.onInputActionPressed((action, repeat) -> {
      if(action == INPUT_ACTION_MENU_RIGHT.get()) {
        playMenuSound(1);
        this.setFocus(this.rightList);
        this.rightList.select(this.leftList.getSelectedIndex());
        return InputPropagation.HANDLED;
      }

      return InputPropagation.PROPAGATE;
    });
    this.leftList.onHighlight(description);

    this.rightList.onHoverIn(() -> this.setFocus(this.rightList));
    this.rightList.onGotFocus(() -> {
      this.leftList.hideHighlight();
      this.rightList.showHighlight();
      description.highlight(this.rightList.getSelectedItem());
    });
    this.rightList.onInputActionPressed((action, repeat) -> {
      if(action == INPUT_ACTION_MENU_LEFT.get()) {
        playMenuSound(1);
        this.setFocus(this.leftList);
        this.leftList.select(this.rightList.getSelectedIndex());
        return InputPropagation.HANDLED;
      }

      return InputPropagation.PROPAGATE;
    });
    this.rightList.onHighlight(description);

    this.addControl(new Background());
    this.addControl(Glyph.glyph(91)).setPos(194, 173); // Description pane

    this.description.setPos(194, 178);

    addControl(this.leftList);
    addControl(this.rightList);
    addControl(this.description);

    this.setFocus(this.leftList);

    int count = 0;
    for(int i = 0; i < ENTRIES.length; i++) {
      final String[] parts = ENTRIES[i].split("\\|", 2);
      final boolean found = gameState_800babc8.scriptFlags2_bc.get(FLAG_BASE + i);
      if(found) {
        count++;
      }

      final MenuEntryStruct04<StardustEntry> item = new MenuEntryStruct04<>(new StardustEntry(FLAG_BASE + i, (i + 1) + ". " + parts[0], parts[1], found));

      if(i % 2 == 0) {
        this.leftList.add(item);
      } else {
        this.rightList.add(item);
      }
    }

    this.rightList.setTitle(new RawText(count + " / " + ENTRIES.length));

    description.highlight(this.leftList.getSelectedItem());
  }

  /** Sparkle icon in the row's icon slot for found stardust; empty for missing. */
  private void renderIcon(final MenuEntryStruct04<StardustEntry> entry, final int x, final int y, final int flags) {
    if(!entry.item_00.found()) {
      return;
    }

    if(this.sparkle == null) {
      this.sparkle = getTextureAtlas().getIcon(StardustIndicatorMod.SPARKLE_ICON_ID);
    }

    if(this.sparkle == null) {
      return;
    }

    this.mv.transfer.set(x - 6.0f, y - 1.0f, 35.0f);
    this.mv.scaling(12.0f, 12.0f, 1.0f);
    this.sparkle.render(this.mv);
  }

  private void menuEscape() {
    playMenuSound(3);
    this.unload.run();
  }

  @Override
  public InputPropagation inputActionPressed(final InputAction action, final boolean repeat) {
    if(super.inputActionPressed(action, repeat) == InputPropagation.HANDLED) {
      return InputPropagation.HANDLED;
    }

    if(action == INPUT_ACTION_MENU_BACK.get() && !repeat) {
      this.menuEscape();
      return InputPropagation.HANDLED;
    }

    return InputPropagation.PROPAGATE;
  }

  @Override
  protected void render() {

  }
}
