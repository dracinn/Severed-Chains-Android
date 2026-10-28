package lod.lloydmod.helper;

import legend.game.types.BackgroundType;
import legend.game.types.LodString;
import legend.game.types.Textbox4c;
import legend.game.types.TextboxChar08;
import legend.game.types.TextboxState;
import legend.game.types.TextboxText84;
import legend.game.types.TextboxTextState;

import java.util.Arrays;

import static legend.game.Text.calculateAppropriateTextboxBounds;
import static legend.game.Text.clearTextbox;
import static legend.game.Text.clearTextboxText;
import static legend.game.Text.textboxText_800bdf38;
import static legend.game.Text.textboxes_800be358;

public class LodTextbox {
  public static int getFreeTextboxIndex() {
    for(int i = 0; i < 8; i++) {
      if(textboxes_800be358[i].state_00 == TextboxState.UNINITIALIZED_0 && textboxText_800bdf38[i].state_00 == TextboxTextState.UNINITIALIZED_0) {
        return i;
      }
    }
    return -1;
  }

  public static void initTextbox(final int index, final int x, final int y, final int width, final int height) {
    clearTextbox(index);

    final Textbox4c struct4c = textboxes_800be358[index];
    struct4c.renderBorder_06 = true;
    struct4c.backgroundType_04 = BackgroundType.fromInt(1);
    struct4c.x_14 = x;
    struct4c.y_16 = y;
    struct4c.chars_18 = width;
    struct4c.lines_1a = height;
  }

  public static void setTextboxContents(final int index, final String text) {
    clearTextboxText(index);

    final TextboxText84 textboxText = textboxText_800bdf38[index];
    textboxText.type_04 = 1;
    textboxText.flags_08 |= TextboxText84.SHOW_ARROW;
    textboxText.str_24 = new LodString(text);
    textboxText.chars_58 = new TextboxChar08[textboxText.chars_1c * (textboxText.lines_1e + 1)];
    Arrays.setAll(textboxText.chars_58, i -> new TextboxChar08());
    calculateAppropriateTextboxBounds(index, textboxText.x_14, textboxText.y_16);
  }
}
