package lod.lloydmod.menu;

import legend.core.Random;
import legend.core.gte.MV;
import legend.core.lang.I18nText;
import legend.core.lang.RawText;
import legend.core.lang.TextComponent;
import legend.core.platform.input.InputAction;
import legend.core.platform.input.InputClass;
import legend.core.platform.input.InputCodepoints;
import legend.core.renderer.QueuedModelStandard;
import legend.core.renderer.Translucency;
import legend.game.inventory.screens.Control;
import legend.game.inventory.screens.FontOptions;
import legend.game.inventory.screens.HorizontalAlign;
import legend.game.inventory.screens.InputPropagation;
import legend.game.inventory.screens.MenuScreen;
import legend.game.inventory.screens.TextColour;
import org.legendofdragoon.modloader.registries.RegistryDelegate;

import java.util.ArrayList;
import java.util.List;

import static legend.core.GameEngine.RENDERER;
import static legend.game.SItem.UI_WHITE_SMALL;
import static legend.game.Scus94491BpeSegment_800b.gameState_800babc8;
import static legend.game.Text.renderText;
import static legend.game.modding.coremod.CoreMod.INPUT_ACTION_MENU_BACK;
import static legend.game.modding.coremod.CoreMod.INPUT_ACTION_MENU_CONFIRM;
import static legend.game.modding.coremod.CoreMod.INPUT_ACTION_MENU_DELETE;
import static legend.game.modding.coremod.CoreMod.INPUT_ACTION_MENU_SORT;

public class LloydHotPrizeMenu extends MenuScreen {
  private final List<InputAction> actions = new ArrayList<>();
  public final int maxPrizes;
  public final int timeStarted;
  public final int delayStart;
  public int timeToComplete;
  public int completedPrizes = 0;

  private final MV transforms = new MV();
  private float r;
  private float g;
  private float b;
  private final FontOptions SMALL = new FontOptions().colour(TextColour.WHITE).size(0.67f).horizontalAlign(HorizontalAlign.CENTRE);

  public LloydHotPrizeMenu(final int numberOfButtons, final int ticks, final int delayStart) {
    this.maxPrizes = numberOfButtons;
    this.delayStart = delayStart;
    this.timeStarted = gameState_800babc8.timestamp_a0 + this.delayStart;
    this.timeToComplete = ticks;

    for(int i = 0; i < numberOfButtons; i++) {
      final int button = new Random().nextInt(4) + 1;
      if(button == 1) {
        this.actions.add(INPUT_ACTION_MENU_CONFIRM.get());
        this.addHotkey(new RawText(""), INPUT_ACTION_MENU_CONFIRM, () -> {});
      } else if(button == 2) {
        this.actions.add(INPUT_ACTION_MENU_DELETE.get());
        this.addHotkey(new RawText(""), INPUT_ACTION_MENU_DELETE, () -> {});
      } else if(button == 3) {
        this.actions.add(INPUT_ACTION_MENU_SORT.get());
        this.addHotkey(new RawText(""), INPUT_ACTION_MENU_SORT, () -> {});
      } else if(button == 4) {
        this.actions.add(INPUT_ACTION_MENU_BACK.get());
        this.addHotkey(new RawText(""), INPUT_ACTION_MENU_BACK, () -> {});
      }
    }

    this.updateButtons();
  }

  @Override
  protected InputPropagation inputActionPressed(final InputAction action, final boolean repeat) {
    if(!this.getControls().isEmpty() && !repeat) {
      if(gameState_800babc8.timestamp_a0 - this.timeStarted <= this.timeToComplete && this.actions.getFirst() == action) {
        this.getControls().removeFirst();
        this.actions.removeFirst();
        this.updateButtons();
        this.completedPrizes++;
      } else {
        this.timeToComplete -= 2;
      }
    }

    if(super.inputActionPressed(action, repeat) == InputPropagation.HANDLED) {
      return InputPropagation.HANDLED;
    }

    return InputPropagation.PROPAGATE;
  }

  @Override
  protected void render() {
    this.transforms.transfer.set(150, 118, 999.0f);
    this.transforms.scaling(20, 12, 999.0f);

    renderText(String.valueOf(this.completedPrizes), 160, 121, this.SMALL);

    RENDERER
      .queueOrthoModel(RENDERER.opaqueQuad, this.transforms, QueuedModelStandard.class)
      .monochrome(0.0f)
      .translucency(Translucency.HALF_B_PLUS_HALF_F);

    final float completed = gameState_800babc8.timestamp_a0 - this.timeStarted >= this.timeToComplete ? 1 : (float)(gameState_800babc8.timestamp_a0 - this.timeStarted) / (float)this.timeToComplete;

    this.transforms.transfer.set(150, 118, 999.0f);
    this.transforms.scaling(20 * completed, 12, 999.0f);

    if(completed >= 0.75f) {
      this.r = 0.85f;
      this.g = 0.0f;
      this.b = 0.0f;
    } else if(completed >= 0.5f) {
      this.r = 0.85f;
      this.g = 0.85f;
      this.b = 0.0f;
    } else {
      this.r = 0.0f;
      this.g = 0.0f;
      this.b = 0.85f;
    }

    RENDERER
      .queueOrthoModel(RENDERER.opaqueQuad, this.transforms, QueuedModelStandard.class)
      .colour(this.r, this.g, this.b)
      .translucency(Translucency.B_PLUS_QUARTER_F);
  }

  @Override
  public void addHotkey(final TextComponent label, final RegistryDelegate<InputAction> action, final Runnable handler) {
    final ModButton button = this.addControl(new ModButton(new I18nText("lod_core.ui.hotkey", new RawText(""), InputCodepoints.getActionName(action.get()))));
    button.setScale(1f);
    button.setSize((int)(button.getFont().textWidth(button.getText().get()) * button.getFontOptions().getSize() + 15), 15);
    button.setPos(315, this.getControls().size() * 15);
    button.onPressed(handler::run);
  }

  @Override
  protected InputPropagation inputClassChanged(final InputClass type) {
    this.updateButtons();

    if(super.inputClassChanged(type) == InputPropagation.HANDLED) {
      return InputPropagation.HANDLED;
    }

    for(int i = 0; i < this.getControls().size(); i++) {
      if(super.inputClassChanged(type) == InputPropagation.HANDLED) {
        return InputPropagation.HANDLED;
      }
    }

    return InputPropagation.PROPAGATE;
  }

  private void updateButtons() {
    for(int i = 0; i < this.getControls().size(); i++) {
      final Control control = this.getControls().get(i);
      if(control instanceof final ModButton button) {
        button.setPos(155, 105);
        if(i == 0) {
          button.setSize((int)(button.getFont().textWidth(button.getText().get()) * button.getFontOptions().getSize() + 15), 15);
          button.setScale(1.0f);
        } else {
          button.setScale(0);
        }
        button.setText(new I18nText("lod_core.ui.hotkey", new RawText(""), InputCodepoints.getActionName(this.actions.get(i))));
      }
    }
  }
}
