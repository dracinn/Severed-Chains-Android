package legend.stardust;

import legend.game.modding.events.RenderEvent;
import legend.game.modding.events.submap.SubmapLoadEvent;
import legend.game.saves.ConfigRegistryEvent;
import legend.game.scripting.ScriptFile;
import legend.game.scripting.ScriptState;
import legend.game.submap.SMap;
import legend.game.submap.SubmapObject;
import legend.game.submap.SubmapObject210;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.legendofdragoon.modloader.Mod;
import org.legendofdragoon.modloader.events.EventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static legend.core.GameEngine.CONFIG;
import static legend.core.GameEngine.EVENTS;
import static legend.game.EngineStates.currentEngineState_8004dd04;
import static legend.game.Scus94491BpeSegment_800b.gameState_800babc8;

/**
 * Places triangle indicators over stardust pickups that haven't been collected.
 *
 * <p>Instead of hardcoding stardust positions, the mod inspects each submap
 * object's script on map load: a stardust pickup is any object whose script
 * file writes to game var 19 (the stardust counter). The indicator is anchored
 * to that object's model transform, so it works on any map without per-map
 * data. An object that deallocates itself (the usual "already collected"
 * behaviour) or that sets a script flag found in its own file loses its marker.
 */
@Mod(id = StardustIndicatorMod.MOD_ID, version = "^3.0.0")
public class StardustIndicatorMod {
  private static final Logger LOGGER = LogManager.getFormatterLogger(StardustIndicatorMod.class);

  public static final String MOD_ID = "stardust_indicator";

  /** Game var holding the collected stardust count ({@code gameState.stardust_9c}) */
  private static final int STARDUST_VAR = 19;
  /** {@code gameVar[127][i]} resolves to {@code gameState.chestFlags_1c4[i]} */
  private static final int CHEST_FLAGS_VAR = 127;
  /** Triangle indicator palette slot (CLUT x index 208 in SMap) */
  private static final int INDICATOR_TYPE = 3;
  /** Script function-table indices for the global flag calls (Scus94491BpeSegment_8004) */
  private static final int FUNC_SET_FLAG_1 = 2;
  private static final int FUNC_SET_FLAG_2 = 4;
  /** Max instructions to walk per file before giving up */
  private static final int MAX_OPS = 0x4000;

  /** Ops whose first param is a destination (and so a var-19 write) */
  private static final Set<Integer> WRITE_OPS = Set.of(
    8, 10, 12, // mov, memcpy, mov
    16, 17, 18, 19, 20, 21, 22, // and, or, xor, andor, not, shl, shr
    24, 25, 26, 27, 28, 29, 30, // add, sub, sub_rev, incr, decr, neg, abs
    32, 33, 34, 35, 40, 41, 42, 43, 44, // mul/div/mod family
    48, 49, 50, 51, 52 // sqrt, rand, sin, cos, atan2
  );

  static final class Marker {
    int sobjIndex;
    /** Packed flag index set when this stardust is collected; -1 = unknown */
    int flagIndex = -1;
    /** 1 = scriptFlags1, 2 = scriptFlags2, 3 = chestFlags */
    int flagArray;
    int indicatorIndex = -1;
  }

  private final List<Marker> markers = new ArrayList<>();
  private SMap smap;
  private int lastStardustCount = -1;

  public StardustIndicatorMod() {
    EVENTS.register(this);
  }

  @EventListener
  public void onRegisterConfig(final ConfigRegistryEvent event) {
    StardustIndicatorConfigs.register(event);
  }

  @EventListener
  public void onSubmapLoad(final SubmapLoadEvent event) {
    this.smap = event.getEngineState();
    this.markers.clear();
    this.lastStardustCount = gameState_800babc8.stardust_9c;

    if(!enabled()) {
      this.smap = null;
      return;
    }

    int scanned = 0;
    for(int i = 0; i < event.submapObjects.size(); i++) {
      final SubmapObject obj = event.submapObjects.get(i);
      if(obj.script == null) {
        continue;
      }

      scanned++;
      final Marker marker = new Marker();
      marker.sobjIndex = i;

      if(this.scanForStardust(obj.script, marker)) {
        LOGGER.info("Stardust granter: sobj %d (%s) flag=%d/%d", i, obj.script.name, marker.flagIndex, marker.flagArray);
        this.markers.add(marker);
      }
    }

    LOGGER.info("Stardust scan: %d/%d objects grant stardust", this.markers.size(), scanned);
  }

  @EventListener
  public void onRender(final RenderEvent event) {
    if(this.markers.isEmpty()) {
      return;
    }

    if(currentEngineState_8004dd04 != this.smap) {
      this.releaseAll();
      this.markers.clear();
      this.smap = null;
      return;
    }

    final int stardustCount = gameState_800babc8.stardust_9c;
    if(stardustCount != this.lastStardustCount) {
      this.lastStardustCount = stardustCount;
      // A stardust was just picked up: drop the marker closest to the player in
      // case the pickup object doesn't deallocate or set a flag we know about
      this.dropNearestToPlayer();
    }

    for(int i = this.markers.size() - 1; i >= 0; i--) {
      final Marker marker = this.markers.get(i);
      final ScriptState<?> state = marker.sobjIndex < this.smap.sobjs_800c6880.length ? this.smap.sobjs_800c6880[marker.sobjIndex] : null;
      final boolean collected = marker.flagIndex >= 0 && this.isFlagSet(marker.flagArray, marker.flagIndex);

      if(state == null || collected) {
        LOGGER.info("Marker removed: sobj %d (state=%s flagSet=%b)", marker.sobjIndex, state == null ? "null" : "live", collected);
        this.releaseIndicator(marker);
        this.markers.remove(i);
      } else if(gameState_800babc8.indicatorsDisabled_4e3) {
        this.releaseIndicator(marker);
      } else {
        final SubmapObject210 sobj = (SubmapObject210)state.innerStruct_00;
        if(marker.indicatorIndex == -1) {
          marker.indicatorIndex = this.smap.addIndicator3d(INDICATOR_TYPE, sobj.model_00.coord2_14, 0.0f, -4.0f);
          LOGGER.info("Indicator claimed: sobj %d slot %d", marker.sobjIndex, marker.indicatorIndex);
        } else {
          this.smap.setIndicator3d(marker.indicatorIndex, INDICATOR_TYPE, sobj.model_00.coord2_14, 0.0f, -4.0f);
        }
      }
    }
  }

  private boolean isFlagSet(final int flagArray, final int packedIndex) {
    return switch(flagArray) {
      case 1 -> gameState_800babc8.scriptFlags1_13c.get(packedIndex >>> 5 & 0x7, packedIndex & 0x1f);
      case 2 -> gameState_800babc8.scriptFlags2_bc.get(packedIndex);
      case 3 -> packedIndex < gameState_800babc8.chestFlags_1c4.length && gameState_800babc8.chestFlags_1c4[packedIndex] != 0;
      default -> false;
    };
  }

  private void dropNearestToPlayer() {
    final ScriptState<?> player = this.smap.sobjs_800c6880.length > 0 ? this.smap.sobjs_800c6880[0] : null;
    if(player == null) {
      return;
    }

    final var playerPos = ((SubmapObject210)player.innerStruct_00).model_00.coord2_14.coord.transfer;

    Marker closest = null;
    float closestDist = Float.MAX_VALUE;
    for(final Marker marker : this.markers) {
      final ScriptState<?> state = marker.sobjIndex < this.smap.sobjs_800c6880.length ? this.smap.sobjs_800c6880[marker.sobjIndex] : null;
      if(state == null) {
        continue;
      }

      final var pos = ((SubmapObject210)state.innerStruct_00).model_00.coord2_14.coord.transfer;
      final float dist = pos.distanceSquared(playerPos);
      if(dist < closestDist) {
        closestDist = dist;
        closest = marker;
      }
    }

    if(closest != null) {
      this.releaseIndicator(closest);
      this.markers.remove(closest);
    }
  }

  /**
   * Frees the marker's indicator slot and shifts any markers allocated above
   * it down by one so the used slots stay contiguous - the renderer stops at
   * the first empty slot.
   */
  private void releaseIndicator(final Marker marker) {
    if(marker.indicatorIndex == -1 || this.smap == null) {
      return;
    }

    final int freed = marker.indicatorIndex;
    int top = freed;

    for(final Marker m : this.markers) {
      if(m.indicatorIndex > top) {
        top = m.indicatorIndex;
      }
      if(m.indicatorIndex > freed) {
        m.indicatorIndex--;
      }
    }

    this.smap.removeIndicator(top);
    marker.indicatorIndex = -1;
  }

  private void releaseAll() {
    for(final Marker marker : this.markers) {
      this.releaseIndicator(marker);
    }
  }

  private boolean enabled() {
    return CONFIG.getConfig(StardustIndicatorConfigs.STARDUST_INDICATORS.get());
  }

  /**
   * Walks a script file's instruction stream looking for writes to the
   * stardust counter (var 19). Because submap object files are self-contained,
   * a linear walk covers every function in the file. Also collects flag
   * indices passed to set-flag calls so the marker can detect collection
   * without waiting for the object to deallocate.
   *
   * @return true if the file contains code that writes var 19
   */
  static boolean scanForStardust(final ScriptFile file, final Marker marker) {
    final int words = file.data.length / 4;
    final int codeStart = file.getEntry(0); // first entry offset = end of the entry table
    if(codeStart <= 0 || codeStart >= words) {
      return false;
    }

    boolean grants = false;
    final Set<Integer> flagCandidates = new HashSet<>();
    final Set<Integer> flagArrays = new HashSet<>();

    int offset = codeStart;
    for(int n = 0; n < MAX_OPS && offset < words; n++) {
      final int op = file.getOp(offset++);
      final int opcode = op & 0xff;
      final int paramCount = op >>> 8 & 0xff;
      final int opParam = op >>> 16;

      if(paramCount > 10) {
        break; // almost certainly walked into data
      }

      for(int p = 0; p < paramCount && offset < words; p++) {
        final int paramWord = file.getOp(offset++);
        final int type = paramWord >>> 24;
        final int cmd0 = paramWord & 0xff;
        final int cmd1 = paramWord >>> 8 & 0xff;

        if(p == 0 && WRITE_OPS.contains(opcode) && isStardustWrite(file, offset - 1, type, cmd0, cmd1)) {
          grants = true;
        }

        if(opcode == 56 && (opParam == FUNC_SET_FLAG_1 || opParam == FUNC_SET_FLAG_2) && p == 0 && type == 0x1 && offset < words) {
          flagCandidates.add(file.getOp(offset)); // type 0x1 = inline literal in the next word
          flagArrays.add(opParam == FUNC_SET_FLAG_1 ? 1 : 2);
        }

        if(p == 0 && WRITE_OPS.contains(opcode) && type == 0xf && cmd0 == CHEST_FLAGS_VAR) {
          flagCandidates.add(cmd1);
          flagArrays.add(3);
        }

        offset += inlineWordCount(paramWord, type);
      }
    }

    // Only trust a flag as the collected marker when the file writes a single,
    // statically resolvable flag; multiple candidates mean the flags are used
    // for other purposes and liveness tracking is safer.
    if(grants && flagCandidates.size() == 1 && flagArrays.size() == 1) {
      marker.flagIndex = flagCandidates.iterator().next();
      marker.flagArray = flagArrays.iterator().next();
    }

    return grants;
  }

  /** Resolves whether a param word encodes a write target of game var 19 */
  private static boolean isStardustWrite(final ScriptFile file, final int offset, final int type, final int cmd0, final int cmd1) {
    return switch(type) {
      case 0x5 -> cmd0 == STARDUST_VAR; // gameVar[19]
      case 0xe -> cmd0 + cmd1 == STARDUST_VAR; // gameVar[cmd0 + cmd1]
      case 0x25 -> {
        // var[inl]: the next word holds an offset into an inline table that
        // stores the var index itself (see ScriptState#parseParam)
        if(cmd0 == 2 || offset + 1 >= file.data.length / 4) {
          yield false;
        }

        final int packed = file.getOp(offset + 1);
        final int varIndexOffset = packed & 0xffff;
        if(varIndexOffset >= file.data.length / 4) {
          yield false;
        }

        yield file.getOp(varIndexOffset) == STARDUST_VAR;
      }
      default -> false;
    };
  }

  /** Extra op words consumed by a param (mirrors ScriptState#parseParam) */
  private static int inlineWordCount(final int paramWord, final int type) {
    return switch(type) {
      case 0x1, 0xc, 0x15, 0x16, 0x17, 0x24, 0x25, 0x26 -> 1;
      case 0x21 -> ((paramWord >>> 16 & 0xff) + 3) / 4;
      default -> 0;
    };
  }
}
