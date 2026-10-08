package legend.core;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Android variant: no update checking - immediately reports no update.
 * Public surface matches upstream Updater.
 */
public class Updater {
  public void delete() {
  }

  public void check(final Consumer<Release> onComplete) {
    onComplete.accept(null);
  }

  public static class Release implements Comparable<Release> {
    public final String tag;
    public final String uri;
    public final ZonedDateTime timestamp;
    public final boolean prerelease;
    public final Map<String, String> assetUrls;

    private Release(final String tag, final String uri, final ZonedDateTime timestamp, final boolean prerelease, final Map<String, String> assetUrls) {
      this.tag = tag;
      this.uri = uri;
      this.timestamp = timestamp;
      this.prerelease = prerelease;
      this.assetUrls = assetUrls;
    }

    public String getPlatformDownloadUrl() {
      return null;
    }

    @Override
    public int compareTo(final Updater.Release o) {
      return -this.timestamp.compareTo(o.timestamp);
    }

    @Override
    public String toString() {
      return this.tag + ' ' + this.timestamp + (this.prerelease ? " (prerelease)" : "");
    }
  }
}
