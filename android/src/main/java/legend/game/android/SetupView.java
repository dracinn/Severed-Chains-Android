package legend.game.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.StatFs;
import android.provider.OpenableColumns;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * First-run setup screen: lets the user pick their four LoD disc images via
 * the system file picker, copies them into isos/, and reports which discs are
 * detected (using the same PVD serial check as the upstream unpacker). The
 * upstream unpacker itself still runs after Start — including its own
 * "waiting for disc" behaviour — this view only covers file staging.
 */
public final class SetupView extends ScrollView {
  private static final String TAG = "SC-Setup";
  static final int PICK_ISOS = 41;

  private static final int COLOUR_TEXT = 0xffe8eaed;
  private static final int COLOUR_DIM = 0xff9aa0a6;
  private static final int COLOUR_OK = 0xff81c784;
  private static final int COLOUR_ERR = 0xffe57373;

  private final Activity activity;
  private final File isosDir;
  private final Runnable onStart;

  private final TextView[] discRows = new TextView[4];
  private final TextView unrecognized;
  private final Button startButton;
  private final LinearLayout importPanel;
  private final ProgressBar progressBar;
  private final TextView progressText;

  private volatile boolean cancelImport;
  private Thread importThread;

  /** Re-scan isos/ periodically so files dropped in by other means show up. */
  private final Runnable rescanTick = new Runnable() {
    @Override
    public void run() {
      SetupView.this.rescan();
      SetupView.this.postDelayed(this, 2000);
    }
  };

  public SetupView(final Activity activity, final File isosDir, final Runnable onStart) {
    super(activity);
    this.activity = activity;
    this.isosDir = isosDir;
    this.onStart = onStart;

    this.setBackgroundColor(0xff101418);

    final LinearLayout content = new LinearLayout(activity);
    content.setOrientation(LinearLayout.VERTICAL);
    final int pad = this.dp(24);
    content.setPadding(pad, pad, pad, pad);
    this.addView(content);

    final TextView title = new TextView(activity);
    title.setText("Severed Chains — first-time setup");
    title.setTextColor(COLOUR_TEXT);
    title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
    content.addView(title);

    final TextView info = new TextView(activity);
    info.setText("Select your Legend of Dragoon disc images (.bin, US release). " +
      "All four discs are copied into the app and unpacked on first launch — " +
      "that takes a while and needs about 3 GB of free space.");
    info.setTextColor(COLOUR_TEXT);
    info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
    info.setPadding(0, this.dp(12), 0, this.dp(4));
    content.addView(info);

    final TextView freeSpace = new TextView(activity);
    freeSpace.setText(String.format(Locale.US, "Free space: %.1f GB", new StatFs(isosDir.getAbsolutePath()).getAvailableBytes() / 1e9));
    freeSpace.setTextColor(COLOUR_DIM);
    freeSpace.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
    freeSpace.setPadding(0, 0, 0, this.dp(12));
    content.addView(freeSpace);

    for(int i = 0; i < 4; i++) {
      final TextView row = new TextView(activity);
      row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
      row.setPadding(0, this.dp(3), 0, this.dp(3));
      this.discRows[i] = row;
      content.addView(row);
    }

    this.unrecognized = new TextView(activity);
    this.unrecognized.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
    this.unrecognized.setTextColor(COLOUR_DIM);
    this.unrecognized.setPadding(0, this.dp(4), 0, 0);
    this.unrecognized.setVisibility(GONE);
    content.addView(this.unrecognized);

    final LinearLayout buttons = new LinearLayout(activity);
    buttons.setOrientation(LinearLayout.HORIZONTAL);
    buttons.setPadding(0, this.dp(16), 0, 0);
    content.addView(buttons);

    final Button pick = new Button(activity);
    pick.setText("Select disc images");
    pick.setOnClickListener(v -> this.launchPicker());
    buttons.addView(pick, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

    this.startButton = new Button(activity);
    this.startButton.setOnClickListener(v -> this.onStart.run());
    buttons.addView(this.startButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

    this.importPanel = new LinearLayout(activity);
    this.importPanel.setOrientation(LinearLayout.VERTICAL);
    this.importPanel.setPadding(0, this.dp(16), 0, 0);
    this.importPanel.setVisibility(GONE);
    content.addView(this.importPanel);

    this.progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
    this.progressBar.setMax(1000);
    this.importPanel.addView(this.progressBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

    this.progressText = new TextView(activity);
    this.progressText.setTextColor(COLOUR_TEXT);
    this.progressText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
    this.progressText.setPadding(0, this.dp(6), 0, 0);
    this.importPanel.addView(this.progressText);

    final Button cancel = new Button(activity);
    cancel.setText("Cancel");
    cancel.setOnClickListener(v -> this.cancelImport = true);
    this.importPanel.addView(cancel);

    this.rescan();
    this.postDelayed(this.rescanTick, 2000);
  }

  @Override
  protected void onDetachedFromWindow() {
    this.removeCallbacks(this.rescanTick);
    super.onDetachedFromWindow();
  }

  // ------------------------------------------------------------------
  // Picker + import
  // ------------------------------------------------------------------

  private void launchPicker() {
    final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
      .addCategory(Intent.CATEGORY_OPENABLE)
      .setType("*/*")
      .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    this.activity.startActivityForResult(intent, PICK_ISOS);
  }

  /** Called from MainActivity.onActivityResult while this view is showing. */
  public void onPickerResult(final int requestCode, final int resultCode, final Intent data) {
    if(requestCode != PICK_ISOS || resultCode != Activity.RESULT_OK || data == null) {
      return;
    }

    final List<Uri> uris = new ArrayList<>();
    final ClipData clip = data.getClipData();
    if(clip != null) {
      for(int i = 0; i < clip.getItemCount(); i++) {
        uris.add(clip.getItemAt(i).getUri());
      }
    } else if(data.getData() != null) {
      uris.add(data.getData());
    }

    if(!uris.isEmpty()) {
      this.importUris(uris);
    }
  }

  private void importUris(final List<Uri> uris) {
    if(this.importThread != null) {
      return;
    }

    this.cancelImport = false;
    this.showImportUi(true);
    this.importThread = new Thread(() -> {
      for(int i = 0; i < uris.size() && !this.cancelImport; i++) {
        this.copyIso(uris.get(i), i + 1, uris.size());
      }

      this.post(() -> {
        this.importThread = null;
        this.showImportUi(false);
        this.rescan();
      });
    }, "iso-import");
    this.importThread.start();
  }

  private void copyIso(final Uri uri, final int index, final int count) {
    String name = null;
    long size = -1;
    try(final Cursor cursor = this.activity.getContentResolver().query(uri, null, null, null, null)) {
      if(cursor != null && cursor.moveToFirst()) {
        final int nameCol = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        final int sizeCol = cursor.getColumnIndex(OpenableColumns.SIZE);
        if(nameCol >= 0) {
          name = cursor.getString(nameCol);
        }
        if(sizeCol >= 0) {
          size = cursor.getLong(sizeCol);
        }
      }
    } catch(final RuntimeException e) {
      Log.w(TAG, "Failed to query picked file metadata", e);
    }

    if(name == null || name.isEmpty()) {
      name = "disc-" + index + ".bin";
    }
    name = new File(name).getName(); // strip any path components

    final File existing = new File(this.isosDir, name);
    if(size > 0 && existing.isFile() && existing.length() == size) {
      this.updateProgress(index, count, name + " already imported", 1.0f);
      return;
    }

    final File dest = existing.exists() ? this.uniqueName(name) : existing;
    final File part = new File(this.isosDir, dest.getName() + ".part");

    try(final InputStream in = this.activity.getContentResolver().openInputStream(uri);
        final FileOutputStream out = new FileOutputStream(part)) {
      if(in == null) {
        throw new IOException("Could not open picked file");
      }

      final byte[] buffer = new byte[256 * 1024];
      long copied = 0;
      int read;
      while((read = in.read(buffer)) >= 0) {
        if(this.cancelImport) {
          break;
        }
        out.write(buffer, 0, read);
        copied += read;
        if(size > 0) {
          final float frac = copied / (float)size;
          this.updateProgress(index, count, name, frac);
        } else {
          this.updateProgress(index, count, name + " (" + copied / 1_048_576 + " MB)", -1);
        }
      }

      if(this.cancelImport || (size > 0 && copied < size)) {
        part.delete();
        this.updateProgress(index, count, this.cancelImport ? "Import cancelled" : "Import truncated — try again", -1);
        return;
      }

      if(!part.renameTo(dest)) {
        part.delete();
        throw new IOException("Failed to move imported file into place");
      }

      final long total = copied;
      this.updateProgress(index, count, name + " (" + total / 1_048_576 + " MB) done", 1.0f);
      Log.i(TAG, "Imported " + dest);
    } catch(final IOException | RuntimeException e) {
      Log.e(TAG, "ISO import failed", e);
      part.delete();
      final String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      this.updateProgress(index, count, "Import failed: " + msg, -1);
    }
  }

  private File uniqueName(final String name) {
    final int dot = name.lastIndexOf('.');
    final String base = dot > 0 ? name.substring(0, dot) : name;
    final String ext = dot > 0 ? name.substring(dot) : "";

    for(int i = 1; ; i++) {
      final File candidate = new File(this.isosDir, base + " (" + i + ")" + ext);
      if(!candidate.exists()) {
        return candidate;
      }
    }
  }

  private void updateProgress(final int index, final int count, final String text, final float frac) {
    this.post(() -> {
      this.progressText.setText("File " + index + "/" + count + ": " + text);
      if(frac >= 0) {
        this.progressBar.setIndeterminate(false);
        this.progressBar.setProgress((int)(frac * 1000));
      } else {
        this.progressBar.setIndeterminate(true);
      }
    });
  }

  private void showImportUi(final boolean show) {
    this.post(() -> this.importPanel.setVisibility(show ? VISIBLE : GONE));
  }

  // ------------------------------------------------------------------
  // Disc scan
  // ------------------------------------------------------------------

  private void rescan() {
    final File[] files = this.isosDir.listFiles();
    final File[] discs = new File[4];
    final DiscImage.Info[] infos = new DiscImage.Info[4];
    final List<String> other = new ArrayList<>();
    int found = 0;

    if(files != null) {
      for(final File file : files) {
        if(!file.isFile() || file.getName().endsWith(".part")) {
          continue;
        }

        final DiscImage.Info info = DiscImage.detect(file.toPath());
        final int slot = info.disc() - 1;

        if(info.kind() == DiscImage.Kind.US && (discs[slot] == null || infos[slot].kind() == DiscImage.Kind.OTHER_REGION)) {
          // A US disc displaces a wrong-region file claiming its slot
          if(slot >= 0 && discs[slot] != null) {
            other.add(discs[slot].getName());
          }
          discs[slot] = file;
          infos[slot] = info;
        } else if(info.kind() == DiscImage.Kind.OTHER_REGION && slot >= 0 && discs[slot] == null) {
          // Occupies the slot so the user sees which disc it is, but flags the region
          discs[slot] = file;
          infos[slot] = info;
        } else {
          other.add(file.getName());
        }
      }

      for(final DiscImage.Info info : infos) {
        if(info != null && info.kind() == DiscImage.Kind.US) {
          found++;
        }
      }
    }

    for(int i = 0; i < 4; i++) {
      if(discs[i] == null) {
        this.discRows[i].setText("Disc " + (i + 1) + ": not provided");
        this.discRows[i].setTextColor(COLOUR_DIM);
      } else if(infos[i].kind() == DiscImage.Kind.US) {
        this.discRows[i].setText("Disc " + (i + 1) + ": " + discs[i].getName());
        this.discRows[i].setTextColor(COLOUR_OK);
      } else {
        this.discRows[i].setText("Disc " + (i + 1) + ": " + discs[i].getName() + " — wrong region (" + infos[i].region() + "), US release required");
        this.discRows[i].setTextColor(COLOUR_ERR);
      }
    }

    if(other.isEmpty()) {
      this.unrecognized.setVisibility(GONE);
    } else {
      this.unrecognized.setText("Unrecognized files in isos/: " + String.join(", ", other));
      this.unrecognized.setVisibility(VISIBLE);
    }

    this.startButton.setText(found == 4 ? "Start" : "Continue anyway");
  }

  private int dp(final int value) {
    return (int)(value * this.getResources().getDisplayMetrics().density);
  }
}
