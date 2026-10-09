package legend.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Android variant: checks this repo's GitHub releases on a background thread
 * via HttpURLConnection (java.net.http doesn't exist before API 33). Public
 * surface matches upstream Updater; {@link Release#getPlatformDownloadUrl()}
 * always returns null so the title screen falls back to opening the release
 * page in a browser.
 */
public class Updater {
  private static final Logger LOGGER = LogManager.getFormatterLogger(Updater.class);

  private static final String UPDATE_URL = "https://api.github.com/repos/dracinn/Severed-Chains-Android/releases";

  private Thread activeCheck;

  public void delete() {
  }

  public void check(final Consumer<Release> onComplete) {
    synchronized(this) {
      if(this.activeCheck != null) {
        return;
      }

      //noinspection ConstantValue
      if(Version.TIMESTAMP == null) {
        // If the build timestamp is null, we're running in the IDE or using a custom build that was compiled manually
        LOGGER.info("Custom build, skipping update check");
        onComplete.accept(null);
        return;
      }

      LOGGER.info("Checking for updates...");
      this.activeCheck = new Thread(() -> this.runCheck(onComplete), "Updater");
      this.activeCheck.setDaemon(true);
      this.activeCheck.start();
    }
  }

  private void runCheck(final Consumer<Release> onComplete) {
    Release release = null;

    try {
      final HttpURLConnection conn = (HttpURLConnection)URI.create(UPDATE_URL).toURL().openConnection();
      conn.setConnectTimeout(10_000);
      conn.setReadTimeout(15_000);
      conn.setRequestProperty("Accept", "application/vnd.github+json");
      conn.setRequestProperty("User-Agent", "severed-chains-android");

      if(conn.getResponseCode() / 100 != 2) {
        LOGGER.warn("Update check failed: %d", conn.getResponseCode());
      } else {
        // API 26 lacks InputStream.readAllBytes; read in chunks.
        final StringBuilder body = new StringBuilder();
        final byte[] chunk = new byte[8192];
        try(final InputStream in = conn.getInputStream()) {
          int read;
          while((read = in.read(chunk)) != -1) {
            body.append(new String(chunk, 0, read, StandardCharsets.UTF_8));
          }
        }

        release = this.parseReleases(new JSONArray(body.toString()))
          .stream()
          .filter(r -> r.tag.startsWith("v") && r.timestamp.isAfter(Version.TIMESTAMP))
          .sorted()
          .findFirst()
          .orElse(null);
      }

      conn.disconnect();
    } catch(final IOException | org.json.JSONException | RuntimeException e) {
      LOGGER.warn("Failed to check for updates", e);
    }

    final Release result = release;
    synchronized(this) {
      this.activeCheck = null;
    }

    if(result != null) {
      LOGGER.info("Found new release %s", result);
    } else {
      LOGGER.info("No updates found");
    }

    onComplete.accept(result);
  }

  private List<Release> parseReleases(final JSONArray releasesJson) throws org.json.JSONException {
    final List<Release> releases = new ArrayList<>();

    for(int releaseIndex = 0; releaseIndex < releasesJson.length(); releaseIndex++) {
      final JSONObject releaseJson = releasesJson.getJSONObject(releaseIndex);

      // get asset download URLs from release
      final Map<String, String> assetUrls = new HashMap<>();
      if(releaseJson.has("assets")) {
        final JSONArray assets = releaseJson.getJSONArray("assets");
        for(int assetIndex = 0; assetIndex < assets.length(); assetIndex++) {
          final JSONObject asset = assets.getJSONObject(assetIndex);
          assetUrls.put(asset.getString("name"), asset.getString("browser_download_url"));
        }
      }

      releases.add(new Release(releaseJson.getString("tag_name"), releaseJson.getString("html_url"), ZonedDateTime.parse(releaseJson.getString("updated_at")), releaseJson.getBoolean("prerelease"), assetUrls));
    }

    return releases;
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

    /**
     * Android assets aren't platform downloads; returns null so the caller
     * opens the release page in a browser instead.
     */
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
