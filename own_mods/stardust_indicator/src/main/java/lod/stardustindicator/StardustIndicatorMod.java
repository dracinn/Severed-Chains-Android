package lod.stardustindicator;

import legend.core.gte.MV;
import legend.game.inventory.Good;
import legend.game.inventory.GoodsRegistryEvent;
import legend.game.inventory.ItemIcon;
import legend.game.inventory.screens.MenuScreen;
import legend.game.inventory.screens.MenuStack;
import legend.game.modding.events.RenderEvent;
import legend.game.modding.events.gamestate.GameLoadedEvent;
import legend.game.modding.events.inventory.ScriptFlags2ChangedEvent;
import legend.game.modding.events.submap.SubmapObjectTextureEvent;
import legend.game.saves.ConfigEntry;
import legend.game.saves.ConfigRegistryEvent;
import legend.game.submap.SMap;
import legend.game.types.MenuEntryStruct04;
import org.joml.Vector3f;
import org.legendofdragoon.modloader.Mod;
import org.legendofdragoon.modloader.events.EventListener;
import org.legendofdragoon.modloader.registries.Registrar;
import org.legendofdragoon.modloader.registries.RegistryDelegate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static legend.core.GameEngine.EVENTS;
import static legend.core.GameEngine.REGISTRIES;
import static legend.core.GameEngine.SCRIPTS;
import static legend.game.EngineStates.currentEngineState_8004dd04;
import static legend.game.FullScreenEffects.startFadeEffect;
import static legend.game.Graphics.worldToScreenMatrix_800c3548;
import static legend.game.Scus94491BpeSegment_800b.gameState_800babc8;
import static legend.game.sound.Audio.playMenuSound;

/** Renders a pulsing sparkle over every uncollected stardust on the submap. */
@Mod(id = StardustIndicatorMod.MOD_ID, version = "^3.0.0")
public class StardustIndicatorMod {
  public static final String MOD_ID = "stardustindicator";

  /** Pseudo-good injected into the goods list; selecting it opens the tracker. */
  public static final Registrar<Good, GoodsRegistryEvent> GOODS_REGISTRAR = new Registrar<>(REGISTRIES.goods, MOD_ID);
  public static final RegistryDelegate<Good> TRACKER_GOOD = GOODS_REGISTRAR.register("stardust_tracker", () -> new Good(900, ItemIcon.STONE));

  public static final Registrar<ConfigEntry<?>, ConfigRegistryEvent> CONFIG_REGISTRAR = new Registrar<>(REGISTRIES.config, MOD_ID);
  public static final RegistryDelegate<StardustTrackerConfig> TRACKER_CONFIG = CONFIG_REGISTRAR.register("stardust_tracker", StardustTrackerConfig::new);

  /** Called from the transformed GoodsScreen when any goods entry is confirmed. */
  public static void onGoodSelected(final MenuEntryStruct04<Good> entry, final MenuScreen screen) {
    if(entry.item_00 != TRACKER_GOOD.get()) {
      return;
    }

    playMenuSound(1);
    final MenuStack stack = screen.getStack();
    stack.pushScreen(new StardustTrackerScreen(() -> {
      startFadeEffect(2, 10);
      stack.popScreen();
    }));
  }

  private static final int FRAME_COUNT = 8;
  private static final float SPARKLE_SCALE = 40.0f;
  private static final Vector3f SCALE = new Vector3f(SPARKLE_SCALE, SPARKLE_SCALE, SPARKLE_SCALE);

  private final List<Vector3f> positions = new ArrayList<>();
  private final List<Integer> flags = new ArrayList<>();
  private final MV billboard = new MV();
  private final MV mv = new MV();
  private SparkleSprite sparkle;
  private int frame;

  public StardustIndicatorMod() {
    EVENTS.register(this);
  }

  @EventListener
  public void onRegisterGoods(final GoodsRegistryEvent event) {
    GOODS_REGISTRAR.registryEvent(event);
  }

  @EventListener
  public void onRegisterConfig(final ConfigRegistryEvent event) {
    CONFIG_REGISTRAR.registryEvent(event);
  }

  @EventListener
  public void onSubmapLoaded(final SubmapObjectTextureEvent event) {
    this.positions.clear();
    this.flags.clear();

    final StardustLocation[] locations = STARDUST.get(event.submapCut);
    if(locations == null) {
      return;
    }

    for(final StardustLocation location : locations) {
      if(!gameState_800babc8.scriptFlags2_bc.get(location.flag())) {
        this.positions.add(new Vector3f(location.x(), location.y(), location.z()));
        this.flags.add(location.flag());
      }
    }

  }

  @EventListener
  public void onGameLoaded(final GameLoadedEvent event) {
    if(this.sparkle != null) {
      this.sparkle.unload();
    }

    this.sparkle = new SparkleSprite();
    this.sparkle.load();
  }

  @EventListener
  @SuppressWarnings("deprecation")
  public void onStardustCollected(final ScriptFlags2ChangedEvent event) {
    for(int i = 0; i < this.flags.size(); i++) {
      if(this.flags.get(i) == event.index) {
        this.flags.remove(i);
        this.positions.remove(i);
        break;
      }
    }
  }

  @EventListener
  public void render(final RenderEvent event) {
    if(this.sparkle == null || !(currentEngineState_8004dd04 instanceof SMap) || SCRIPTS.isPaused()) {
      return;
    }

    for(final Vector3f position : this.positions) {
      // Billboard: transposed view rotation makes the quad face the camera,
      // then compose with the view transform to get model->view space. Queued
      // into the world batch so depth sorting puts it under character models.
      worldToScreenMatrix_800c3548.transpose(this.billboard);
      this.billboard.transfer.set(position);
      this.billboard.scale(SCALE);
      this.billboard.compose(worldToScreenMatrix_800c3548, this.mv);

      this.sparkle.render(this.mv, this.frame);
    }

    this.frame = (this.frame + 1) % FRAME_COUNT;
  }

  private record StardustLocation(int flag, int x, int y, int z) { }

  /** Stardust world positions keyed by submap cut; flag = scriptFlags2 index. */
  private static final Map<Integer, StardustLocation[]> STARDUST = Map.ofEntries(
    Map.entry(12, new StardustLocation[]{new StardustLocation(0x1c3, 0xfffffe4c, 0x0, 0x310)}),
    Map.entry(68, new StardustLocation[]{new StardustLocation(0x1c4, 0xfffffffe, 0x6, 0x3c)}),
    Map.entry(75, new StardustLocation[]{new StardustLocation(0x1c5, 0xfffffe6c, 0x1c, 0xa3)}),
    Map.entry(81, new StardustLocation[]{new StardustLocation(0x1c6, 0x48, 0xffffffcd, 0x123)}),
    Map.entry(83, new StardustLocation[]{new StardustLocation(0x1c7, 0xffffffb6, 0x6, 0)}),
    Map.entry(90, new StardustLocation[]{new StardustLocation(0x1c8, 0xa4, 0x0, 0x1bb)}),
    Map.entry(92, new StardustLocation[]{new StardustLocation(0x1c9, 0xfffffea9, 0xffffffa4, 0x133)}),
    Map.entry(98, new StardustLocation[]{new StardustLocation(0x1ca, 0x48, 0xfffffff9, 0x68)}),
    Map.entry(102, new StardustLocation[]{new StardustLocation(0x1cb, 0xfffffeac, 0x12, 0x28)}),
    Map.entry(109, new StardustLocation[]{new StardustLocation(0x1cc, 0xffffff1d, 0xffffffc2, 0x1b)}),
    Map.entry(145, new StardustLocation[]{new StardustLocation(0x1cd, 0xffffffcb, 0xffffffc7, 0xffffffa8)}),
    Map.entry(148, new StardustLocation[]{new StardustLocation(0x1ce, 0xffffff4b, 0xfffffe6a, 0x82)}),
    Map.entry(140, new StardustLocation[]{new StardustLocation(0x1cf, 0xffffff32, 0x9, 0xfffffff8)}),
    Map.entry(646, new StardustLocation[]{new StardustLocation(0x1d0, 0xffffff8b, 0x9, 0xfffffeb7)}),
    Map.entry(172, new StardustLocation[]{new StardustLocation(0x1d1, 0xbd, 0x0, 0x22)}),
    Map.entry(173, new StardustLocation[]{new StardustLocation(0x1d2, 0xffffffce, 0xfffffee2, 0xf)}),
    Map.entry(175, new StardustLocation[]{new StardustLocation(0x1d3, 0xfffffd68, 0xfffffff0, 0x1a5)}),
    Map.entry(179, new StardustLocation[]{new StardustLocation(0x1d4, 0x49, 0xffffffc3, 0xf7)}),
    Map.entry(180, new StardustLocation[]{new StardustLocation(0x1d5, 0xfffffec7, 0x0, 0x1e7), new StardustLocation(0x1d6, 0xffffff4d, 0x0, 0xffffffec)}),
    Map.entry(201, new StardustLocation[]{new StardustLocation(0x1d7, 0xffffff0a, 0xffffff84, 0xffffff32)}),
    Map.entry(214, new StardustLocation[]{new StardustLocation(0x1d8, 0xffffffb3, 0x0, 0x63)}),
    Map.entry(211, new StardustLocation[]{new StardustLocation(0x1d9, 0xffffffa0, 0x0, 0xffffff74)}),
    Map.entry(219, new StardustLocation[]{new StardustLocation(0x1da, 0xd3, 0x0, 0xffffff2f), new StardustLocation(0x1da, 0x87, 0x0, 0xffffff43)}),
    Map.entry(204, new StardustLocation[]{new StardustLocation(0x1db, 0xffffffec, 0x0, 0xffffff14)}),
    Map.entry(221, new StardustLocation[]{new StardustLocation(0x1dc, 0x140, 0xffffffce, 0xfb)}),
    Map.entry(222, new StardustLocation[]{new StardustLocation(0x1dc, 0x140, 0xffffffce, 0xfb)}),
    Map.entry(665, new StardustLocation[]{new StardustLocation(0x1dd, 0x24e, 0x55a, 0xffffffa8), new StardustLocation(0x1dd, 0x253, 0x55a, 0x28)}),
    Map.entry(239, new StardustLocation[]{new StardustLocation(0x1de, 0xffffffb8, 0x0, 0xffffff07)}),
    Map.entry(248, new StardustLocation[]{new StardustLocation(0x1df, 0xffffff25, 0x0, 0x17b)}),
    Map.entry(265, new StardustLocation[]{new StardustLocation(0x1e0, 0x11, 0x2c, 0x22d)}),
    Map.entry(277, new StardustLocation[]{new StardustLocation(0x1e1, 0xf5, 0x0, 0xfffffe87)}),
    Map.entry(284, new StardustLocation[]{new StardustLocation(0x1e2, 0xffffffc4, 0x0, 0xfffffeb6)}),
    Map.entry(298, new StardustLocation[]{new StardustLocation(0x1e3, 0x96, 0xd, 0x50)}),
    Map.entry(316, new StardustLocation[]{new StardustLocation(0x1e4, 0x59, 0x0, 0xa)}),
    Map.entry(319, new StardustLocation[]{new StardustLocation(0x1e5, 0xaa, 0x0, 0x4a)}),
    Map.entry(331, new StardustLocation[]{new StardustLocation(0x1e6, 0xffffffc0, 0xffffff93, 0xed)}),
    Map.entry(337, new StardustLocation[]{new StardustLocation(0x1e7, 0x6b, 0x50, 0xfffffdaf)}),
    Map.entry(349, new StardustLocation[]{new StardustLocation(0x1e8, 0x81, 0x0, 0xd4), new StardustLocation(0x1e9, 0x5b, 0x0, 0x1a3)}),
    Map.entry(356, new StardustLocation[]{new StardustLocation(0x1ea, 0xffffffde, 0x0, 0x6d)}),
    Map.entry(374, new StardustLocation[]{new StardustLocation(0x1eb, 0xffffff16, 0xffffff82, 0x1f4)}),
    Map.entry(375, new StardustLocation[]{new StardustLocation(0x1ec, 0xad, 0x0, 0x118)}),
    Map.entry(379, new StardustLocation[]{new StardustLocation(0x1ed, 0xf, 0x0, 0xb5)}),
    Map.entry(385, new StardustLocation[]{new StardustLocation(0x1ee, 0xdd, 0xffffffec, 0x195)}),
    Map.entry(386, new StardustLocation[]{new StardustLocation(0x1ef, 0x8e, 0x313, 0xffffffd7)}),
    Map.entry(490, new StardustLocation[]{new StardustLocation(0x1f0, 0x0, 0xffffffe6, 0xe6)}),
    Map.entry(518, new StardustLocation[]{new StardustLocation(0x1f2, 0x0, 0x0, 0x0), new StardustLocation(0x1f2, 0x0, 0xffffffe2, 0x0)}),
    Map.entry(515, new StardustLocation[]{new StardustLocation(0x1f3, 0xfffffff7, 0x50, 0xde), new StardustLocation(0x1f3, 0x4f, 0x50, 0x82)}),
    Map.entry(516, new StardustLocation[]{new StardustLocation(0x1f1, 0x0, 0x0, 0x0), new StardustLocation(0x1f1, 0x0, 0xffffffce, 0x0)}),
    Map.entry(522, new StardustLocation[]{new StardustLocation(0x1f1, 0x0, 0x0, 0x0), new StardustLocation(0x1f1, 0x0, 0xffffffce, 0x0)}),
    Map.entry(523, new StardustLocation[]{new StardustLocation(0x1f2, 0x0, 0x0, 0x0), new StardustLocation(0x1f2, 0x0, 0xffffffe2, 0x0)}),
    Map.entry(525, new StardustLocation[]{new StardustLocation(0x1f3, 0xfffffff7, 0x50, 0xde), new StardustLocation(0x1f3, 0x4f, 0x50, 0x82)}),
    Map.entry(566, new StardustLocation[]{new StardustLocation(0x1f4, 0x65, 0x3b, 0x8)})
  );
}
