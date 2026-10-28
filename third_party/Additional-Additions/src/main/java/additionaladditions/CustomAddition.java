package additionaladditions;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import legend.game.additions.AdditionHitProperties10;
import legend.game.additions.SimpleAddition;
import legend.game.characters.CharacterAdditionInfo;
import legend.game.characters.CharacterData2c;
import legend.game.unpacker.FileData;
import legend.game.unpacker.Loader;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static legend.game.DrgnFiles.loadDrgnDir;

public class CustomAddition extends SimpleAddition {
  private final int baseAnimationPackage;
  public final String id;
  private final String name;
  private final String[] animationFiles;

  public CustomAddition(final JsonObject obj, final int baseAnimationPackage, final String id) {
    this.baseAnimationPackage = baseAnimationPackage;

    this.name = obj.getAsJsonPrimitive("name").getAsString();
    final int overlayFrame = obj.getAsJsonPrimitive("overlay_frame").getAsInt();

    final JsonArray levelMultipliersObj = obj.getAsJsonArray("level_multipliers");
    final SimpleAddition.LevelMultipliers[] levelMultipliers = new LevelMultipliers[levelMultipliersObj.size()];

    for(int i = 0; i < levelMultipliersObj.size(); i++) {
      final JsonObject multipliersObj = levelMultipliersObj.get(i).getAsJsonObject();
      levelMultipliers[i] = new SimpleAddition.LevelMultipliers(multipliersObj.getAsJsonPrimitive("damage").getAsFloat(), multipliersObj.getAsJsonPrimitive("sp").getAsFloat());
    }

    final JsonArray hitsObj = obj.getAsJsonArray("hits");
    final AdditionHitProperties10[] hits = new AdditionHitProperties10[hitsObj.size()];
    final String[] animationFiles = new String[hitsObj.size()];

    for(int i = 0; i < hitsObj.size(); i++) {
      final JsonObject hitObj = hitsObj.get(i).getAsJsonObject();
      final String animation = hitObj.getAsJsonPrimitive("animation").getAsString();

      final int interpolationScale;
      if(hitObj.has("animation_scale")) {
        interpolationScale = hitObj.getAsJsonPrimitive("animation_scale").getAsInt();
      } else {
        interpolationScale = 100;
      }

      final int flags = hitObj.getAsJsonPrimitive("flags").getAsInt();
      final int audioFile = hitObj.getAsJsonPrimitive("audio_file").getAsInt();
      final int failAnimation = hitObj.getAsJsonPrimitive("fail_animation").getAsInt();
      final int totalFrames = hitObj.getAsJsonPrimitive("total_frames").getAsInt();
      final int moveFrames = hitObj.getAsJsonPrimitive("move_frames").getAsInt();
      final int successFrames = hitObj.getAsJsonPrimitive("success_frames").getAsInt();
      final int hitFrame = hitObj.getAsJsonPrimitive("hit_frame").getAsInt();
      final int distanceFromTarget = hitObj.getAsJsonPrimitive("distance_from_target").getAsInt();
      final int cameraMovementX = hitObj.getAsJsonPrimitive("camera_movement_x").getAsInt();
      final int cameraMovementZ = hitObj.getAsJsonPrimitive("camera_movement_z").getAsInt();
      final int cameraMovementTicks = hitObj.getAsJsonPrimitive("camera_movement_ticks").getAsInt();
      final int damageMultiplier = hitObj.getAsJsonPrimitive("damage_multiplier").getAsInt();
      final int sp = hitObj.getAsJsonPrimitive("sp").getAsInt();

      hits[i] = new AdditionHitProperties10(flags, totalFrames, hitFrame, successFrames, damageMultiplier, sp, audioFile, i == hitsObj.size() - 1 ? 4 : 0, cameraMovementX, cameraMovementZ, cameraMovementTicks, distanceFromTarget, moveFrames, 0, failAnimation, overlayFrame);
      hits[i].animationScale = interpolationScale;

      animationFiles[i] = animation;
    }

    super(false, levelMultipliers, hits);
    this.id = id;
    this.animationFiles = animationFiles;
  }

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public CompletableFuture<List<FileData>> loadAnimations(final CharacterData2c charData, final CharacterAdditionInfo additionInfo) {
    final CompletableFuture<List<FileData>> baseAnimationsFuture = loadDrgnDir(0, this.baseAnimationPackage);
    final CompletableFuture<List<FileData>> additionAnimationsFuture = Loader.loadFiles(this.animationFiles);

    return CompletableFuture
      .allOf(baseAnimationsFuture, additionAnimationsFuture)
      .thenApply(v -> {
        final List<FileData> baseAnimations = baseAnimationsFuture.join();
        final List<FileData> additionAnimations = additionAnimationsFuture.join();

        for(int i = 0; i < additionAnimations.size(); i++) {
          baseAnimations.set(16 + i, additionAnimations.get(i));
        }

        return baseAnimations;
      });
  }
}
