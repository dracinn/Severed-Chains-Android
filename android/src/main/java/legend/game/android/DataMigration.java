package legend.game.android;

import android.util.Log;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Moves the extracted files/ tree between storage roots (internal ↔ SD
 * app dir). Copy-then-verify-then-delete: the source tree is only removed
 * after every file lands and the byte totals match, so a mid-copy failure
 * leaves a usable install at the original location.
 */
public final class DataMigration {
  private static final String TAG = "SC-Migrate";

  public interface Progress {
    void update(long copiedBytes, long totalBytes);
  }

  private DataMigration() { }

  /**
   * Copy {@code src} to {@code dst}, verify file count and byte total, then
   * delete {@code src}. Throws on any copy or verification failure — the
   * partial destination is removed and {@code src} is untouched.
   */
  public static void moveTree(final Path src, final Path dst, final Progress progress) throws IOException {
    if(src.toAbsolutePath().normalize().equals(dst.toAbsolutePath().normalize())) {
      throw new IOException("Refusing to move " + src + " onto itself");
    }
    final long[] totalBytes = {0};
    final long[] totalFiles = {0};
    Files.walkFileTree(src, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
        totalFiles[0]++;
        totalBytes[0] += attrs.size();
        return FileVisitResult.CONTINUE;
      }
    });
    Log.i(TAG, "Moving " + totalFiles[0] + " files (" + totalBytes[0] + " bytes) from " + src + " to " + dst);

    final AtomicLong copied = new AtomicLong();
    final long[] copiedFiles = {0};
    try {
      Files.walkFileTree(src, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
          Files.createDirectories(dst.resolve(src.relativize(dir).toString()));
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
          final Path dest = dst.resolve(src.relativize(file).toString());
          Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
          copiedFiles[0]++;
          final long done = copied.addAndGet(attrs.size());
          if(progress != null) {
            progress.update(done, totalBytes[0]);
          }
          return FileVisitResult.CONTINUE;
        }
      });
    } catch(final IOException | RuntimeException e) {
      deleteTreeQuietly(dst);
      throw e instanceof IOException ? (IOException)e : new IOException(e);
    }

    // Verify the copy before the source is touched: file count and byte
    // total must match the pre-scan.
    final long[] dstBytes = {0};
    final long[] dstFiles = {0};
    Files.walkFileTree(dst, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
        dstFiles[0]++;
        dstBytes[0] += attrs.size();
        return FileVisitResult.CONTINUE;
      }
    });
    if(dstFiles[0] != totalFiles[0] || dstBytes[0] != totalBytes[0]) {
      deleteTreeQuietly(dst);
      throw new IOException("Verification failed: copied " + dstFiles[0] + "/" + totalFiles[0]
        + " files, " + dstBytes[0] + "/" + totalBytes[0] + " bytes");
    }

    deleteTree(src);
    Log.i(TAG, "Migration complete: " + dstFiles[0] + " files verified, source removed");
  }

  private static void deleteTree(final Path root) throws IOException {
    if(!Files.exists(root)) {
      return;
    }
    Files.walkFileTree(root, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  private static void deleteTreeQuietly(final Path root) {
    try {
      deleteTree(root);
    } catch(final IOException e) {
      Log.w(TAG, "Failed to clean up " + root, e);
    }
  }
}
