package legend.game.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.StatFs;
import android.provider.Settings;
import android.provider.OpenableColumns;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
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
 *
 * Lays out as two columns on wide screens (info/storage left, discs/actions
 * right) and a single scrolling column on narrow ones. All framework views —
 * the app targets API 26 so no appcompat/material widgets.
 */
public final class SetupView extends ScrollView {
  private static final String TAG = "SC-Setup";
  static final int PICK_ISOS = 41;

  private static final int COLOUR_BG = 0xff0e1116;
  private static final int COLOUR_SURFACE = 0xff1a2027;
  private static final int COLOUR_SURFACE_ALT = 0xff141a21;
  private static final int COLOUR_STROKE = 0xff2c3640;
  private static final int COLOUR_TEXT = 0xffe8eaed;
  private static final int COLOUR_DIM = 0xff9aa0a6;
  private static final int COLOUR_ACCENT = 0xff8ab4f8;
  private static final int COLOUR_ON_ACCENT = 0xff0b0e14;
  private static final int COLOUR_OK = 0xff7ee0a3;
  private static final int COLOUR_ERR = 0xffff8a80;
  private static final int COLOUR_RIPPLE = 0x22ffffff;

  private final Activity activity;
  private final File isosDir;
  private final Runnable onStart;

  private final DiscCard[] discCards = new DiscCard[4];
  private final TextView unrecognized;
  private final Button startButton;
  private final LinearLayout importPanel;
  private final ProgressBar progressBar;
  private final TextView progressText;

  private final LinearLayout storageCard;
  private final TextView storageTitle;
  private final TextView storageBody;
  private final Button storageButton;
  private boolean storageGranted = true;

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

    this.setBackgroundColor(COLOUR_BG);
    this.setFillViewport(true);

    final int pad = this.dp(20);
    final int gap = this.dp(16);
    final boolean wide = this.getResources().getConfiguration().screenWidthDp >= 600;

    // --- Left column: header, storage access card, notes --------------------

    final LinearLayout left = new LinearLayout(activity);
    left.setOrientation(LinearLayout.VERTICAL);

    final TextView title = this.text("Severed Chains", COLOUR_TEXT, 26, true);
    left.addView(title);

    final TextView subtitle = this.text("Legend of Dragoon on Android", COLOUR_DIM, 14, false);
    subtitle.setPadding(0, this.dp(2), 0, this.dp(10));
    left.addView(subtitle);

    // Shared-storage access card — the always-visible fallback for the
    // one-time prompt, kept up to date by rescan().
    this.storageCard = new LinearLayout(activity);
    this.storageCard.setOrientation(LinearLayout.VERTICAL);
    this.storageCard.setBackground(this.rounded(COLOUR_SURFACE, 14, COLOUR_STROKE, 1));
    this.storageCard.setPadding(pad, pad, pad, pad);
    left.addView(this.storageCard);

    this.storageTitle = this.text("Shared storage", COLOUR_TEXT, 15, true);
    this.storageCard.addView(this.storageTitle);

    this.storageBody = this.text("", COLOUR_DIM, 13, false);
    this.storageBody.setPadding(0, this.dp(6), 0, 0);
    this.storageCard.addView(this.storageBody);

    this.storageButton = this.secondaryButton("Enable access");
    final LinearLayout.LayoutParams storageButtonLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    storageButtonLp.topMargin = this.dp(12);
    this.storageCard.addView(this.storageButton, storageButtonLp);
    this.storageButton.setOnClickListener(v -> this.requestStorageAccess());
    this.storageGranted = AndroidEnv.hasSharedStorageAccess();
    this.updateStorageCard();

    // Compatibility-renderer toggle: forces the GLES 3.1 path for GPUs
    // whose drivers mishandle geometry shaders (e.g. older Adreno).
    final LinearLayout compatCard = new LinearLayout(activity);
    compatCard.setOrientation(LinearLayout.HORIZONTAL);
    compatCard.setGravity(Gravity.CENTER_VERTICAL);
    compatCard.setBackground(this.rounded(COLOUR_SURFACE, 14, COLOUR_STROKE, 1));
    compatCard.setPadding(pad, this.dp(10), pad, this.dp(10));
    final LinearLayout.LayoutParams compatLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    compatLp.topMargin = this.dp(12);
    left.addView(compatCard, compatLp);

    final LinearLayout compatText = new LinearLayout(activity);
    compatText.setOrientation(LinearLayout.VERTICAL);
    compatCard.addView(compatText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

    compatText.addView(this.text("Compatibility renderer", COLOUR_TEXT, 15, true));
    final TextView compatSub = this.text("Use if graphics are corrupted or layered wrong. Applies on next launch.", COLOUR_DIM, 12, false);
    compatSub.setPadding(0, this.dp(2), 0, 0);
    compatText.addView(compatSub);

    final android.widget.Switch compatSwitch = new android.widget.Switch(activity);
    compatSwitch.setChecked(GlesCompat.compatMode());
    compatSwitch.setOnCheckedChangeListener((v, on) -> GlesCompat.setCompatMode(on));
    compatCard.addView(compatSwitch);

    final TextView gpuNote = this.text("If the game closes while starting up, open it again — it retries in a compatibility mode automatically. Crash and debug logs live in Documents/Severed Chains/logs.", COLOUR_DIM, 12, false);
    gpuNote.setPadding(0, this.dp(14), 0, 0);
    left.addView(gpuNote);

    final TextView freeSpace = this.text(String.format(Locale.US, "Free space: %.1f GB (about 3 GB needed)", new StatFs(isosDir.getAbsolutePath()).getAvailableBytes() / 1e9), COLOUR_DIM, 12, false);
    freeSpace.setPadding(0, this.dp(8), 0, 0);
    left.addView(freeSpace);

    // --- Right column: disc cards, actions, import progress -----------------

    final LinearLayout right = new LinearLayout(activity);
    right.setOrientation(LinearLayout.VERTICAL);

    final TextView discsLabel = this.text("GAME DISCS", COLOUR_DIM, 12, true);
    discsLabel.setLetterSpacing(0.08f);
    discsLabel.setPadding(0, 0, 0, this.dp(8));
    right.addView(discsLabel);

    for(int i = 0; i < 4; i++) {
      final DiscCard card = this.discCard(i + 1);
      this.discCards[i] = card;
      final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      lp.bottomMargin = this.dp(6);
      right.addView(card.root, lp);
    }

    this.unrecognized = this.text("", COLOUR_ERR, 12, false);
    this.unrecognized.setPadding(0, 0, 0, this.dp(8));
    this.unrecognized.setVisibility(GONE);
    right.addView(this.unrecognized);

    final LinearLayout buttons = new LinearLayout(activity);
    buttons.setOrientation(LinearLayout.HORIZONTAL);
    buttons.setPadding(0, this.dp(6), 0, 0);
    right.addView(buttons);

    final Button pick = this.secondaryButton("Select disc images");
    pick.setOnClickListener(v -> this.launchPicker());
    final LinearLayout.LayoutParams pickLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    pickLp.rightMargin = this.dp(6);
    buttons.addView(pick, pickLp);

    this.startButton = this.primaryButton("Start");
    this.startButton.setOnClickListener(v -> this.startWithNotice());
    final LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    startLp.leftMargin = this.dp(6);
    buttons.addView(this.startButton, startLp);

    this.importPanel = new LinearLayout(activity);
    this.importPanel.setOrientation(LinearLayout.VERTICAL);
    this.importPanel.setPadding(pad, pad, pad, pad);
    this.importPanel.setBackground(this.rounded(COLOUR_SURFACE_ALT, 14, COLOUR_STROKE, 1));
    this.importPanel.setVisibility(GONE);
    final LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    importLp.topMargin = this.dp(12);
    right.addView(this.importPanel, importLp);

    this.progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
    this.progressBar.setMax(1000);
    this.importPanel.addView(this.progressBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

    this.progressText = this.text("", COLOUR_TEXT, 13, false);
    this.progressText.setPadding(0, this.dp(6), 0, 0);
    this.importPanel.addView(this.progressText);

    final Button cancel = this.secondaryButton("Cancel import");
    cancel.setOnClickListener(v -> this.cancelImport = true);
    final LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    cancelLp.topMargin = this.dp(8);
    this.importPanel.addView(cancel, cancelLp);

    // --- Layout -----------------------------------------------------------

    if(wide) {
      final LinearLayout columns = new LinearLayout(activity);
      columns.setOrientation(LinearLayout.HORIZONTAL);
      columns.setPadding(pad, pad, pad, pad);
      columns.setGravity(Gravity.CENTER_HORIZONTAL);
      this.addView(columns);

      left.setPadding(0, 0, gap / 2, 0);
      final LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2);
      columns.addView(left, leftLp);

      right.setPadding(gap / 2, 0, 0, 0);
      final LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3);
      columns.addView(right, rightLp);
    } else {
      final LinearLayout content = new LinearLayout(activity);
      content.setOrientation(LinearLayout.VERTICAL);
      content.setPadding(pad, pad, pad, pad);
      this.addView(content);
      content.addView(left);
      right.setPadding(0, this.dp(20), 0, 0);
      content.addView(right);
    }

    this.rescan();
    this.postDelayed(this.rescanTick, 2000);
    this.post(this::maybePromptStorage);
  }

  @Override
  protected void onDetachedFromWindow() {
    this.removeCallbacks(this.rescanTick);
    super.onDetachedFromWindow();
  }

  // ------------------------------------------------------------------
  // UI helpers
  // ------------------------------------------------------------------

  private TextView text(final String label, final int colour, final int sp, final boolean bold) {
    final TextView view = new TextView(this.activity);
    view.setText(label);
    view.setTextColor(colour);
    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    if(bold) {
      view.getPaint().setFakeBoldText(true);
    }
    return view;
  }

  private GradientDrawable rounded(final int colour, final int radiusDp, final int strokeColour, final int strokeDp) {
    final GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(colour);
    drawable.setCornerRadius(this.dp(radiusDp));
    if(strokeDp > 0) {
      drawable.setStroke(this.dp(strokeDp), strokeColour);
    }
    return drawable;
  }

  private RippleDrawable ripple(final GradientDrawable content) {
    return new RippleDrawable(ColorStateList.valueOf(COLOUR_RIPPLE), content, null);
  }

  private Button primaryButton(final String label) {
    final Button button = new Button(this.activity);
    button.setText(label);
    button.setAllCaps(false);
    button.setTextColor(COLOUR_ON_ACCENT);
    button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    button.getPaint().setFakeBoldText(true);
    button.setBackground(this.ripple(this.rounded(COLOUR_ACCENT, 24, 0, 0)));
    button.setPadding(this.dp(20), 0, this.dp(20), 0);
    return button;
  }

  private Button secondaryButton(final String label) {
    final Button button = new Button(this.activity);
    button.setText(label);
    button.setAllCaps(false);
    button.setTextColor(COLOUR_ACCENT);
    button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    button.setBackground(this.ripple(this.rounded(0, 24, COLOUR_STROKE, 1)));
    button.setPadding(this.dp(20), 0, this.dp(20), 0);
    return button;
  }

  private static final class DiscCard {
    LinearLayout root;
    TextView badge;
    TextView title;
    TextView sub;
  }

  private DiscCard discCard(final int disc) {
    final DiscCard card = new DiscCard();

    card.root = new LinearLayout(this.activity);
    card.root.setOrientation(LinearLayout.HORIZONTAL);
    card.root.setGravity(Gravity.CENTER_VERTICAL);
    card.root.setBackground(this.rounded(COLOUR_SURFACE, 14, COLOUR_STROKE, 1));
    card.root.setPadding(this.dp(14), this.dp(9), this.dp(14), this.dp(9));
    card.root.setOnClickListener(v -> this.launchPicker());
    final TypedValue selectable = new TypedValue();
    this.activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectable, true);
    card.root.setForeground(this.activity.getDrawable(selectable.resourceId));

    card.badge = new TextView(this.activity);
    card.badge.setText(String.valueOf(disc));
    card.badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    card.badge.setGravity(Gravity.CENTER);
    card.badge.getPaint().setFakeBoldText(true);
    card.badge.setBackground(this.rounded(COLOUR_SURFACE_ALT, 22, COLOUR_STROKE, 1));
    card.root.addView(card.badge, new LinearLayout.LayoutParams(this.dp(40), this.dp(40)));

    final LinearLayout lines = new LinearLayout(this.activity);
    lines.setOrientation(LinearLayout.VERTICAL);
    lines.setPadding(this.dp(14), 0, 0, 0);
    card.root.addView(lines, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

    card.title = this.text("Disc " + disc, COLOUR_TEXT, 14, true);
    lines.addView(card.title);

    card.sub = this.text("", COLOUR_DIM, 12, false);
    card.sub.setSingleLine(true);
    card.sub.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
    lines.addView(card.sub);

    return card;
  }

  private void updateStorageCard() {
    if(this.storageGranted) {
      this.storageTitle.setText("Shared storage enabled");
      this.storageTitle.setTextColor(COLOUR_OK);
      this.storageBody.setText("ISOs, Mods, and Saves live in Documents/Severed Chains — add files with any file manager and they're picked up automatically.");
      this.storageButton.setVisibility(GONE);
    } else {
      this.storageTitle.setText("Enable shared storage");
      this.storageTitle.setTextColor(COLOUR_TEXT);
      this.storageBody.setText("Optional: creates Documents/Severed Chains folders so you can drop in disc images and .jar mods with a file manager, and keeps saves safe if the app is uninstalled.");
      this.storageButton.setVisibility(VISIBLE);
    }
  }

  /**
   * One-time prompt for shared storage access on first run. The card's
   * "Enable access" button remains afterwards as the fallback entry point.
   */
  private void maybePromptStorage() {
    final File shown = new File(this.activity.getFilesDir(), "storage-prompt-shown");
    if(AndroidEnv.hasSharedStorageAccess() || shown.isFile()) {
      return;
    }

    new AlertDialog.Builder(this.activity, android.R.style.Theme_Material_Dialog_Alert)
      .setTitle("Enable shared storage?")
      .setMessage("Severed Chains can use Documents/Severed Chains for disc images, mods, and saves, so you can manage files with any file manager and keep saves if the app is uninstalled.\n\nYou can also do this later from the setup screen.")
      .setPositiveButton("Enable access", (d, w) -> {
        this.markShown(shown);
        this.requestStorageAccess();
      })
      .setNegativeButton("Not now", (d, w) -> this.markShown(shown))
      .show();
  }

  private void markShown(final File flag) {
    try {
      //noinspection ResultOfMethodCallIgnored
      flag.createNewFile();
    } catch(final IOException ignored) { }
  }

  /**
   * First Start press shows a one-time heads-up about the automatic GL
   * fallback and the ANGLE workaround, so a crash at first boot isn't read
   * as "the app is just broken" on unsupported GPUs.
   */
  private void startWithNotice() {
    final File shown = new File(this.activity.getFilesDir(), "gl-notice-shown");
    if(shown.isFile()) {
      this.onStart.run();
      return;
    }

    new AlertDialog.Builder(this.activity, android.R.style.Theme_Material_Dialog_Alert)
      .setTitle("Graphics compatibility")
      .setMessage("If the game closes itself while starting up, open it again — it " +
        "automatically retries in a safer graphics mode.\n\nIf it still won't start, " +
        "try the ANGLE graphics driver in Developer Options → Preferred graphics " +
        "driver, and attach the logs from Documents/Severed Chains/logs when " +
        "reporting problems.")
      .setPositiveButton("Start", (d, w) -> {
        this.markShown(shown);
        this.onStart.run();
      })
      .setCancelable(false)
      .show();
  }

  // ------------------------------------------------------------------
  // Picker + import
  // ------------------------------------------------------------------

  /** All-files access for the public Documents folders (or legacy WRITE on API <=28). */
  private void requestStorageAccess() {
    if(Build.VERSION.SDK_INT >= 30) {
      try {
        this.activity.startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
          Uri.parse("package:" + this.activity.getPackageName())));
      } catch(final ActivityNotFoundException e) {
        this.activity.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
      }
    } else {
      this.activity.requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, 42);
    }
  }

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

    // When shared storage access is granted, stage into Documents/Severed
    // Chains/ISOs so the files stay user-managed; the unpacker scans both.
    final File destDir = AndroidEnv.isoDestDir();
    //noinspection ResultOfMethodCallIgnored
    destDir.mkdirs();

    final File existing = new File(destDir, name);
    if(size > 0 && existing.isFile() && existing.length() == size) {
      this.updateProgress(index, count, name + " already imported", 1.0f);
      return;
    }

    final File dest = existing.exists() ? this.uniqueName(destDir, name) : existing;
    final File part = new File(destDir, dest.getName() + ".part");

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

  private File uniqueName(final File dir, final String name) {
    final int dot = name.lastIndexOf('.');
    final String base = dot > 0 ? name.substring(0, dot) : name;
    final String ext = dot > 0 ? name.substring(dot) : "";

    for(int i = 1; ; i++) {
      final File candidate = new File(dir, base + " (" + i + ")" + ext);
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
    this.refreshStorageCard();

    final File[] discs = new File[4];
    final DiscImage.Info[] infos = new DiscImage.Info[4];
    final List<String> other = new ArrayList<>();
    int found = 0;

    for(final java.nio.file.Path dir : AndroidEnv.isoDirs()) {
      final File[] files = dir.toFile().listFiles();
      if(files == null) {
        continue;
      }

      for(final File file : files) {
        if(!file.isFile() || file.getName().endsWith(".part")) {
          continue;
        }

        final DiscImage.Info info = DiscImage.detect(file.toPath());
        final int slot = info.disc() - 1;

        if(info.kind() == DiscImage.Kind.US && slot >= 0 && (discs[slot] == null || infos[slot].kind() == DiscImage.Kind.OTHER_REGION)) {
          // A US disc displaces a wrong-region file claiming its slot
          if(discs[slot] != null) {
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
    }

    for(final DiscImage.Info info : infos) {
      if(info != null && info.kind() == DiscImage.Kind.US) {
        found++;
      }
    }

    // The same file can be visible in both scanned dirs (internal + public);
    // don't list names already claimed by a disc slot as unrecognized.
    other.removeIf(name -> {
      for(final File disc : discs) {
        if(disc != null && disc.getName().equals(name)) {
          return true;
        }
      }
      return false;
    });

    for(int i = 0; i < 4; i++) {
      final DiscCard card = this.discCards[i];
      if(discs[i] == null) {
        card.badge.setTextColor(COLOUR_DIM);
        card.sub.setText("Not provided — tap to choose");
        card.sub.setTextColor(COLOUR_DIM);
      } else if(infos[i].kind() == DiscImage.Kind.US) {
        card.badge.setTextColor(COLOUR_OK);
        card.sub.setText(discs[i].getName());
        card.sub.setTextColor(COLOUR_DIM);
      } else {
        card.badge.setTextColor(COLOUR_ERR);
        card.sub.setText(discs[i].getName() + " — wrong region (" + infos[i].region() + ")");
        card.sub.setTextColor(COLOUR_ERR);
      }
    }

    if(other.isEmpty()) {
      this.unrecognized.setVisibility(GONE);
    } else {
      this.unrecognized.setText("Unrecognized files: " + String.join(", ", other));
      this.unrecognized.setVisibility(VISIBLE);
    }

    this.startButton.setText(found == 4 ? "Start" : "Continue anyway");
  }

  /** Updates the storage card when the grant state changes (e.g. on return from Settings). */
  private void refreshStorageCard() {
    final boolean granted = AndroidEnv.hasSharedStorageAccess();
    if(granted != this.storageGranted) {
      this.storageGranted = granted;
      this.updateStorageCard();
    }
  }

  private int dp(final int value) {
    return (int)(value * this.getResources().getDisplayMetrics().density);
  }
}
