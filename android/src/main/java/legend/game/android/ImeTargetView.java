package legend.game.android;

import android.content.Context;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import legend.core.platform.AndroidInput;

/**
 * Invisible 1px view that acts as the soft-keyboard target. The game has no
 * real editable widget; this connection translates IME operations into the
 * char/key events the upstream Textbox understands.
 */
public final class ImeTargetView extends View {
  public ImeTargetView(final Context context) {
    super(context);
    // Not focusable by default: view-system focus traversal (or a stray DPAD
    // event) must not focus this view, or the IME re-shows spontaneously.
    // AndroidEnv.showIme makes it focusable before requesting focus.
    this.setFocusable(false);
  }

  @Override
  public boolean onCheckIsTextEditor() {
    return true;
  }

  @Override
  public InputConnection onCreateInputConnection(final EditorInfo outAttrs) {
    outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
    outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_DONE;
    return new ImeConnection(this);
  }

  private static final class ImeConnection extends BaseInputConnection {
    private CharSequence composing = "";

    ImeConnection(final View target) {
      super(target, false);
    }

    private static void commitChars(final CharSequence text) {
      for(int i = 0; i < text.length(); ) {
        final int codepoint = Character.codePointAt(text, i);
        AndroidInput.charInput(codepoint);
        i += Character.charCount(codepoint);
      }
    }

    @Override
    public boolean setComposingText(final CharSequence text, final int newCursorPosition) {
      this.composing = text;
      return true;
    }

    @Override
    public boolean finishComposingText() {
      if(this.composing.length() > 0) {
        commitChars(this.composing);
        this.composing = "";
      }
      return true;
    }

    @Override
    public boolean commitText(final CharSequence text, final int newCursorPosition) {
      this.composing = "";
      commitChars(text);
      return true;
    }

    @Override
    public boolean deleteSurroundingText(final int beforeLength, final int afterLength) {
      if(this.composing.length() > 0) {
        // Backspaces inside a composition just shrink it; don't emit game keys yet
        final int remove = Math.min(beforeLength, this.composing.length());
        this.composing = this.composing.subSequence(0, this.composing.length() - remove);
        return true;
      }

      for(int i = 0; i < beforeLength; i++) {
        AndroidInput.keyPressRelease(KeyEvent.KEYCODE_DEL);
      }
      return true;
    }

    @Override
    public boolean sendKeyEvent(final KeyEvent event) {
      return AndroidInput.handleKeyEvent(event);
    }

    @Override
    public boolean performEditorAction(final int actionCode) {
      if(actionCode == EditorInfo.IME_ACTION_DONE || actionCode == EditorInfo.IME_ACTION_UNSPECIFIED) {
        this.finishComposingText();
        AndroidInput.keyPressRelease(KeyEvent.KEYCODE_ENTER);
        AndroidEnv.hideIme();
        return true;
      }
      return false;
    }
  }
}
