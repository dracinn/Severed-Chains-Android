package additionaladditions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.ReflectionAccessFilter;
import legend.core.IoHelper;
import legend.lodmod.LodMod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.legendofdragoon.modloader.registries.RegistryId;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomAdditionLoader {
  private static final Logger LOGGER = LogManager.getFormatterLogger(CustomAdditionLoader.class);

  private static final Gson SERIALIZER = new GsonBuilder().setPrettyPrinting().addReflectionAccessFilter(rawClass -> ReflectionAccessFilter.FilterResult.BLOCK_ALL).create();

  private static final Path ADDITION_DIR = Path.of("mods", AdditionalAdditionsMod.MOD_ID);

  public static final List<RegistryId> CHAR_IDS = List.of(
    LodMod.id("dart"),
    LodMod.id("lavitz"),
    LodMod.id("shana"),
    LodMod.id("rose"),
    LodMod.id("haschel"),
    LodMod.id("albert"),
    LodMod.id("meru"),
    LodMod.id("kongol"),
    LodMod.id("miranda")
  );

  public Map<RegistryId, List<CustomAddition>> loadAllAsAdditions() {
    final Map<RegistryId, List<CustomAddition>> additions = new HashMap<>();

    try {
      Files.createDirectories(ADDITION_DIR);

      try(final DirectoryStream<Path> stream = Files.newDirectoryStream(ADDITION_DIR, "*.json")) {
        for(final Path path : stream) {
          try {
            final String contents = Files.readString(path, StandardCharsets.UTF_8);
            final JsonObject json = SERIALIZER.fromJson(contents, JsonObject.class);
            final RegistryId charRegId = new RegistryId(json.getAsJsonPrimitive("char_id").getAsString());
            final String filename = path.getFileName().toString();
            additions.computeIfAbsent(charRegId, key -> new ArrayList<>()).add(new CustomAddition(json, 4031 + CHAR_IDS.indexOf(charRegId) * 8, IoHelper.slugName(filename.substring(0, filename.length() - 5))));
          } catch(final Throwable t) {
            LOGGER.error("Failed to load addition " + path, t);
          }
        }
      }
    } catch(final Throwable t) {
      LOGGER.error("Failed to load additions", t);
    }

    return additions;
  }
}
