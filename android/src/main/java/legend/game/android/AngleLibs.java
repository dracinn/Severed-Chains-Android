package legend.game.android;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Extracts the bundled ANGLE libraries (GLES-over-Vulkan) from
 * assets/angle/&lt;abi&gt;/ into the app-private files dir so they can be
 * dlopen'd by absolute path. Assets are used rather than jniLibs so the
 * canonical libEGL.so/libGLESv2.so names can't shadow the system libraries
 * during soname resolution.
 */
public final class AngleLibs {
  private static final String TAG = "AngleLibs";
  private static final String[] LIBS = {"libEGL.so", "libGLESv2.so"};

  private AngleLibs() { }

  /**
   * Extracted ANGLE dir, or null when the APK was built without ANGLE
   * (e.g. a build machine with no angleLibsDir). Re-extracts whenever the
   * app version changes or a lib goes missing.
   */
  public static File dir() {
    final Context context = AndroidEnv.context();
    final String abi = abi();
    if(abi == null) {
      return null;
    }

    final File dir = new File(context.getFilesDir(), "angle/" + abi);
    final File stamp = new File(dir, ".version");
    final long version = versionCode(context);
    if(stamp.isFile() && readStamp(stamp) == version) {
      return dir;
    }

    boolean ok = true;
    for(final String lib : LIBS) {
      final File out = new File(dir, lib);
      try(final InputStream in = context.getAssets().open("angle/" + abi + "/" + lib)) {
        if(!dir.isDirectory() && !dir.mkdirs()) {
          return null;
        }
        try(final FileOutputStream fos = new FileOutputStream(out)) {
          in.transferTo(fos);
        }
        //noinspection ResultOfMethodCallIgnored
        out.setExecutable(true, true);
      } catch(final IOException e) {
        if(out.exists()) {
          //noinspection ResultOfMethodCallIgnored
          out.delete();
        }
        ok = false;
        break;
      }
    }
    if(!ok) {
      Log.i(TAG, "No bundled ANGLE libs for " + abi + " — Vulkan backend unavailable");
      return null;
    }

    try(final FileOutputStream fos = new FileOutputStream(stamp)) {
      fos.write(Long.toString(version).getBytes());
    } catch(final IOException e) {
      return null;
    }
    Log.i(TAG, "ANGLE libs ready in " + dir);
    return dir;
  }

  private static String abi() {
    for(final String abi : Build.SUPPORTED_ABIS) {
      if("arm64-v8a".equals(abi) || "x86_64".equals(abi)) {
        return abi;
      }
    }
    return null;
  }

  private static long versionCode(final Context context) {
    try {
      return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
    } catch(final PackageManager.NameNotFoundException e) {
      return 0;
    }
  }

  private static long readStamp(final File stamp) {
    try {
      return Long.parseLong(new String(java.nio.file.Files.readAllBytes(stamp.toPath())).trim());
    } catch(final IOException | NumberFormatException e) {
      return -1;
    }
  }
}
