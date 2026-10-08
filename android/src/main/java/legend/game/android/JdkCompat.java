package legend.game.android;

import android.graphics.Bitmap;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Replacements for JDK APIs that exist on desktop Java but not on Android.
 * Upstream call sites are rewritten to this class when staging shared sources
 * (see stageSharedSources/patchStagedSources in android/build.gradle), keeping
 * the upstream files themselves untouched.
 */
public final class JdkCompat {
  private JdkCompat() { }

  /**
   * The process working directory is changed to the app files dir at startup
   * ({@link NativeOs#chdir} in {@link MainActivity}), so upstream's relative
   * {@code Path.of(...)}/{@code Paths.get(...)} calls resolve there directly.
   * {@code user.dir} still reports {@code /}, so {@code Path.toAbsolutePath()}
   * is wrong on Android — staged sources rewrite it to {@link #absolute}.
   */
  private static Path base = Paths.get("/");

  public static void setBaseDir(final Path dir) {
    base = dir.toAbsolutePath().normalize();
  }

  public static Path basePath() {
    return base;
  }

  /** {@code path.toAbsolutePath()} resolving relative paths against the app dir, not user.dir. */
  public static Path absolute(final Path path) {
    return path.isAbsolute() ? path : base.resolve(path).normalize();
  }

  /** Java 25 {@code Math.TAU}. */
  public static final double TAU = Math.PI * 2;

  /** Java 21 {@code Math.clamp(long, int, int)}. */
  public static int clamp(final long value, final int min, final int max) {
    if(min > max) {
      throw new IllegalArgumentException(min + " > " + max);
    }

    return (int)Math.min(max, Math.max(value, min));
  }

  /** Java 21 {@code Math.clamp(long, long, long)}. */
  public static long clamp(final long value, final long min, final long max) {
    if(min > max) {
      throw new IllegalArgumentException(min + " > " + max);
    }

    return Math.min(max, Math.max(value, min));
  }

  /** Java 21 {@code Math.clamp(float, float, float)}. */
  public static float clamp(final float value, final float min, final float max) {
    if(min > max) {
      throw new IllegalArgumentException(min + " > " + max);
    }

    if(Float.isNaN(value)) {
      return Float.NaN;
    }

    return Math.min(Math.max(value, min), max);
  }

  /** Java 21 {@code Math.clamp(double, double, double)}. */
  public static double clamp(final double value, final double min, final double max) {
    if(min > max) {
      throw new IllegalArgumentException(min + " > " + max);
    }

    if(Double.isNaN(value)) {
      return Double.NaN;
    }

    return Math.min(Math.max(value, min), max);
  }

  /** Java 17 {@code RandomGenerator.nextFloat(bound)}. */
  public static float nextFloat(final Random random, final float bound) {
    if(bound <= 0.0f) {
      throw new IllegalArgumentException("bound must be positive");
    }

    return random.nextFloat() * bound;
  }

  /** Java 17 {@code RandomGenerator.nextFloat(origin, bound)}. */
  public static float nextFloat(final Random random, final float origin, final float bound) {
    if(origin >= bound) {
      throw new IllegalArgumentException("bound must be greater than origin");
    }

    return origin + random.nextFloat() * (bound - origin);
  }

  /** Java 13 {@code ByteBuffer.get(int, byte[])}. */
  public static ByteBuffer get(final ByteBuffer buffer, final int index, final byte[] dst) {
    return get(buffer, index, dst, 0, dst.length);
  }

  /** Java 13 {@code ByteBuffer.get(int, byte[], int, int)}. */
  public static ByteBuffer get(final ByteBuffer buffer, final int index, final byte[] dst, final int offset, final int length) {
    final ByteBuffer dup = buffer.duplicate();
    dup.position(index);
    return dup.get(dst, offset, length);
  }

  /** Java 16 {@code ByteBuffer.put(int, byte[], int, int)}. */
  public static ByteBuffer put(final ByteBuffer buffer, final int index, final byte[] src, final int offset, final int length) {
    final ByteBuffer dup = buffer.duplicate();
    dup.position(index);
    return dup.put(src, offset, length);
  }

  /** Java 16 {@code IntBuffer.put(int, int[])}. */
  public static IntBuffer put(final IntBuffer buffer, final int index, final int[] src) {
    return put(buffer, index, src, 0, src.length);
  }

  /** Java 16 {@code IntBuffer.put(int, int[], int, int)}. */
  public static IntBuffer put(final IntBuffer buffer, final int index, final int[] src, final int offset, final int length) {
    final IntBuffer dup = buffer.duplicate();
    dup.position(index);
    return dup.put(src, offset, length);
  }

  /**
   * Java 21 {@code Executors.newVirtualThreadPerTaskExecutor()}. Android has no
   * virtual threads; a bounded pool is the functional equivalent. An unbounded
   * cached pool spawns a platform thread (~1MB stack) per task and exhausts
   * native memory when the unpacker submits thousands of tasks.
   */
  public static CloseableExecutor newVirtualThreadPerTaskExecutor() {
    return new CloseableExecutor();
  }

  /** ExecutorService that is also AutoCloseable so upstream try-with-resources works. */
  public static final class CloseableExecutor extends ThreadPoolExecutor {
    private static final ThreadFactory DAEMON = runnable -> {
      final Thread thread = new Thread(runnable);
      thread.setDaemon(true);
      return thread;
    };

    CloseableExecutor() {
      super(Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors())),
        Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), DAEMON);
    }

    /** Mirrors {@code ExecutorService.close()}: shutdown then await termination. */
    @Override
    public void close() {
      this.shutdown();

      boolean terminated = false;
      while(!terminated) {
        try {
          terminated = this.awaitTermination(1L, TimeUnit.DAYS);
        } catch(final InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      }
    }
  }

  /** Replaces awt {@code ImageIO.write(BufferedImage)}: ARGB int[] -> PNG. */
  public static void writePng(final int[] argbPixels, final int width, final int height, final File output) throws IOException {
    final Bitmap bitmap = Bitmap.createBitmap(argbPixels, width, height, Bitmap.Config.ARGB_8888);
    try(final OutputStream out = new FileOutputStream(output)) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    } finally {
      bitmap.recycle();
    }
  }

  /** org.json {@code JSONObject.keySet()} is missing on Android; wrap keys(). */
  public static Iterable<String> keySet(final JSONObject json) {
    return new Iterable<>() {
      @Override
      public Iterator<String> iterator() {
        return json.keys();
      }
    };
  }
}
