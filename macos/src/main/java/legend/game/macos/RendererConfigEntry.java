package legend.game.macos;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import legend.game.saves.ConfigCategory;
import legend.game.saves.ConfigCollection;
import legend.game.saves.ConfigStorageLocation;
import legend.game.saves.EnumConfigEntry;

/**
 * Graphics &gt; Renderer backend picker. The choice is mirrored into
 * config.conf ({@code renderer_backend}) so {@link AngleContext} can apply
 * it when the window is created on the next launch — the {@code .help}
 * string tells the user a restart is required. ANGLE-Vulkan is
 * experimental, so it is opt-in only; AUTO keeps the system-GL default.
 */
public class RendererConfigEntry extends EnumConfigEntry<RendererConfigEntry.Backend> {
  private static final Logger LOGGER = LogManager.getFormatterLogger(RendererConfigEntry.class);

  public enum Backend {
    AUTO,
    SYSTEM_GL,
    ANGLE_VULKAN,
  }

  public RendererConfigEntry() {
    super(Backend.class, Backend.AUTO, ConfigStorageLocation.GLOBAL, ConfigCategory.GRAPHICS);
  }

  @Override
  public boolean hasHelp() {
    return true;
  }

  @Override
  public void onChange(final ConfigCollection configCollection, final Backend oldValue, final Backend newValue) {
    super.onChange(configCollection, oldValue, newValue);
    legend.core.Config.setRendererBackend(newValue.name());
    try {
      legend.core.Config.save();
    } catch(final IOException e) {
      LOGGER.error("Failed to persist renderer backend", e);
    }
  }
}
