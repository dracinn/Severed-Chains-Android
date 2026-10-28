package lod.iconturnorder;

import legend.core.GameEngine;
import legend.core.gpu.Bpp;
import legend.core.gte.MV;
import legend.core.renderer.Obj;
import legend.core.renderer.QuadBuilder;
import legend.core.renderer.QueuedModelStandard;
import legend.core.renderer.Texture;
import legend.core.renderer.TextureDataFormat;
import legend.core.renderer.TextureDataType;
import legend.core.renderer.TextureInternalFormat;
import legend.core.renderer.Translucency;
import legend.game.combat.Battle;
import legend.game.combat.bent.BattleEntity27c;
import legend.game.combat.bent.MonsterBattleEntity;
import legend.game.combat.bent.PlayerBattleEntity;
import legend.game.combat.ui.BattleMenuStruct58;
import legend.game.modding.events.RenderEvent;
import legend.game.modding.events.battle.BattleEndedEvent;
import legend.game.modding.events.battle.BattleStartedEvent;
import legend.game.saves.ConfigRegistryEvent;
import legend.game.scripting.ScriptState;
import legend.lodmod.LodMod;
import legend.turnorder.TurnOrder;
import org.legendofdragoon.modloader.Mod;
import org.legendofdragoon.modloader.events.EventListener;
import org.legendofdragoon.modloader.registries.RegistryId;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static legend.core.GameEngine.CONFIG;
import static legend.core.GameEngine.RENDERER;
import static legend.core.IoHelper.pathToByteBuffer;
import static legend.game.EngineStates.currentEngineState_8004dd04;
import static legend.game.SItem.UI_WHITE_SMALL;
import static legend.game.Scus94491BpeSegment.simpleRand;
import static legend.game.Scus94491BpeSegment_8004.simpleRandSeed_8004dd44;
import static legend.game.Scus94491BpeSegment_8006.battleState_8006e398;
import static legend.game.Text.renderText;
import static legend.game.Text.textZ_800bdf00;
import static legend.game.combat.bent.BattleEntity27c.FLAG_400;
import static legend.game.combat.bent.BattleEntity27c.FLAG_CURRENT_TURN;
import static lod.iconturnorder.IconTurnOrderConfigs.SHOW_ICON_TURN_ORDER;
import static org.lwjgl.opengl.GL11C.GL_RGBA;
import static org.lwjgl.opengl.GL12.GL_UNSIGNED_INT_8_8_8_8_REV;
import static org.lwjgl.stb.STBImage.stbi_failure_reason;
import static org.lwjgl.stb.STBImage.stbi_load_from_memory;
import static org.lwjgl.stb.STBImage.stbi_set_flip_vertically_on_load;
import static org.lwjgl.system.MemoryStack.stackPush;

@Mod(id = IconTurnOrder.MOD_ID, version = "^3.0.0")
public class IconTurnOrder {
  public static final String MOD_ID = "icon_turn_order";

  public static RegistryId id(final String entryId) {
    return new RegistryId(MOD_ID, entryId);
  }

  public boolean render = false;

  private final List<BattleEntity27c> sortedBents = new ArrayList<>();
  private final List<TurnOrder> turns = new ArrayList<>();
  private Obj turnOrderOverlay;
  private Texture turnOrderTexture;
  private final MV turnOrderTransforms = new MV();
  private final MV selectedTurnOrderTransforms = new MV();

  public IconTurnOrder() {
    GameEngine.EVENTS.register(this);
  }

  @EventListener
  public void onRegisterConfig(final ConfigRegistryEvent event) {
    IconTurnOrderConfigs.register(event);
  }

  @EventListener
  public void battleStarted(final BattleStartedEvent event) {
    this.render = true;
  }

  public void drawTurnOrder() {
    final int textureCount = battleState_8006e398.allBents_e0c.size() + 2;
    if(this.turnOrderOverlay == null) {
      this.turnOrderTexture = Texture.create("turn_order_texture", builder -> {
        builder.data(BufferUtils.createIntBuffer(240 * (textureCount * 120)), 240, (textureCount * 120));
        builder.internalFormat(TextureInternalFormat.RGBA_8);
        builder.dataFormat(TextureDataFormat.RGBA);
        builder.dataType(TextureDataType.UBYTE);
      });

      for(int bentIndex = 0; bentIndex < battleState_8006e398.allBents_e0c.size(); bentIndex++) {
        if(battleState_8006e398.allBents_e0c.get(bentIndex) != null) {
          if(battleState_8006e398.allBents_e0c.get(bentIndex).innerStruct_00 instanceof PlayerBattleEntity) {
            this.buildTurnOrderTexture(bentIndex, Path.of("mods", "iconturnorder", "player", String.valueOf(((PlayerBattleEntity)battleState_8006e398.allBents_e0c.get(bentIndex).innerStruct_00).character.template.getRegistryId().toString().replace(':', '_')) + ".png"), true);
          } else {
            this.buildTurnOrderTexture(bentIndex, Path.of("mods", "iconturnorder", "monster", String.valueOf(((BattleEntity27c)battleState_8006e398.allBents_e0c.get(bentIndex).innerStruct_00).charId_272) + ".png"), false);
          }
        }
      }

      this.buildTurnOrderTexture(battleState_8006e398.allBents_e0c.size(), Path.of("mods", "iconturnorder", "monster", "SelectedOutline.png"), false);
      this.buildTurnOrderTexture(battleState_8006e398.allBents_e0c.size() + 1, Path.of("mods", "iconturnorder", "player", "SelectedOutline.png"), false);

      this.turnOrderOverlay = new QuadBuilder("Turn Order Overlay")
        .bpp(Bpp.BITS_24)
        .posSize(32.0f, 16.0f)
        .uvSize(1.0f, 120.0f / (textureCount * 120.0f))
        .build();
    }

    final int oldSeed = simpleRandSeed_8004dd44;
    this.sortedBents.clear();
    this.turns.clear();
    int processedBents = 0;

    for(int bentIndex = 0; bentIndex < battleState_8006e398.getAliveBentCount(); bentIndex++) {
      this.turns.add(new TurnOrder(battleState_8006e398.aliveBents_e78.get(bentIndex).innerStruct_00));
    }

    while(processedBents < 7) {
      int highestTurnValue = 0;
      int highestIndex = 0;
      for(int i = 0; i < this.turns.size(); i++) {
        final TurnOrder turnOrder = this.turns.get(i);
        final int turnValue = turnOrder.turnValue;

        if(highestTurnValue <= turnValue) {
          highestTurnValue = turnValue;
          highestIndex = i;
        }
      }

      if(highestTurnValue > 0xd9) {
        final TurnOrder turnOrder = this.turns.get(highestIndex);
        turnOrder.turnValue -= 0xd9;
        this.sortedBents.add(turnOrder.bent);
        processedBents++;
      }

      for(int i = 0; i < this.turns.size(); i++) {
        final TurnOrder turnOrder = this.turns.get(i);
        turnOrder.turnValue += Math.round(turnOrder.bent.stats.getStat(LodMod.SPEED_STAT.get()).get() * (simpleRand() / (float)0xffff * 0.2f + 0.9f));
      }
    }

    simpleRandSeed_8004dd44 = oldSeed;

    for(int bentIndex = 0; bentIndex < battleState_8006e398.getAliveBentCount(); bentIndex++) {
      final ScriptState<? extends BattleEntity27c> state = battleState_8006e398.aliveBents_e78.get(bentIndex);

      if(state.hasAnyFlag(FLAG_400 | FLAG_CURRENT_TURN)) {
        this.sortedBents.addFirst(new TurnOrder(state.innerStruct_00).bent);
      }
    }


    final int oldZ = textZ_800bdf00;

    for(int bentIndex = 0; bentIndex < this.sortedBents.size(); bentIndex++) {
      for(int searchIndex = 0; searchIndex < battleState_8006e398.allBents_e0c.size(); searchIndex++) {
        if(battleState_8006e398.allBents_e0c.get(searchIndex) != null) {
          if(this.sortedBents.get(bentIndex) == battleState_8006e398.allBents_e0c.get(searchIndex).innerStruct_00) {
            this.turnOrderTransforms.transfer.set((float)((RENDERER.getRenderWidth() / (RENDERER.getRenderHeight() / RENDERER.getNativeHeight())) - RENDERER.getNativeWidth()) / -2.0f + 4, 4 + bentIndex * 20, 1);

            if(battleState_8006e398.getForcedTurnBent() != null && this.sortedBents.get(bentIndex) == battleState_8006e398.getForcedTurnBent().innerStruct_00) {
              textZ_800bdf00 = 0;
              renderText("!", (float)((RENDERER.getRenderWidth() / (RENDERER.getRenderHeight() / RENDERER.getNativeHeight())) - RENDERER.getNativeWidth()) / -2.0f + 34, 8 + bentIndex * 20, UI_WHITE_SMALL);
            }

            RENDERER
              .queueOrthoModel(this.turnOrderOverlay, this.turnOrderTransforms, QueuedModelStandard.class)
              .translucency(Translucency.HALF_B_PLUS_HALF_F)
              .alpha(1.0f)
              .useTextureAlpha()
              .uvOffset(0, (float)searchIndex / (float)textureCount)
              .texture(this.turnOrderTexture);

            final BattleMenuStruct58 menu = ((Battle)currentEngineState_8004dd04).hud.battleMenu_800c6c34;
            if(menu.displayTargetArrowAndName_4c) {
              final int targetCombatant = menu.combatantIndex_54;
              this.selectedTurnOrderTransforms.scaling(1.0f, 16.0f, 1.0f);
              this.selectedTurnOrderTransforms.transfer.set((float)((RENDERER.getRenderWidth() / (RENDERER.getRenderHeight() / RENDERER.getNativeHeight())) - RENDERER.getNativeWidth()) / -2.0f + 2, 4 + bentIndex * 20, 1);

              if(targetCombatant == -1) {
                if(menu.targetType_50 == 1) {
                  if(this.sortedBents.get(bentIndex) instanceof MonsterBattleEntity) {
                    RENDERER
                      .queueOrthoModel(RENDERER.opaqueQuad, this.selectedTurnOrderTransforms, QueuedModelStandard.class);
                    RENDERER
                      .queueOrthoModel(this.turnOrderOverlay, this.turnOrderTransforms, QueuedModelStandard.class)
                      .translucency(Translucency.HALF_B_PLUS_HALF_F)
                      .alpha(1.0f)
                      .useTextureAlpha()
                      .uvOffset(0, (textureCount - 1) / (float)textureCount)
                      .texture(this.turnOrderTexture);
                  }
                } else {
                  if(this.sortedBents.get(bentIndex) instanceof PlayerBattleEntity) {
                    RENDERER
                      .queueOrthoModel(RENDERER.opaqueQuad, this.selectedTurnOrderTransforms, QueuedModelStandard.class);

                    RENDERER
                      .queueOrthoModel(this.turnOrderOverlay, this.turnOrderTransforms, QueuedModelStandard.class)
                      .translucency(Translucency.HALF_B_PLUS_HALF_F)
                      .alpha(1.0f)
                      .useTextureAlpha()
                      .uvOffset(0, (textureCount - 1) / (float)textureCount)
                      .texture(this.turnOrderTexture);
                  }
                }
              } else {
                if(menu.targetType_50 == 1) {
                  if(battleState_8006e398.aliveMonsterBents_ebc.get(targetCombatant).innerStruct_00 == this.sortedBents.get(bentIndex)) {
                    RENDERER
                      .queueOrthoModel(RENDERER.opaqueQuad, this.selectedTurnOrderTransforms, QueuedModelStandard.class);
                    RENDERER
                      .queueOrthoModel(this.turnOrderOverlay, this.turnOrderTransforms, QueuedModelStandard.class)
                      .translucency(Translucency.HALF_B_PLUS_HALF_F)
                      .alpha(1.0f)
                      .useTextureAlpha()
                      .uvOffset(0, (textureCount - 1) / (float)textureCount)
                      .texture(this.turnOrderTexture);
                  }
                } else if(menu.targetType_50 == 0) {
                  if(battleState_8006e398.playerBents_e40.get(targetCombatant).innerStruct_00 == this.sortedBents.get(bentIndex)) {
                    RENDERER
                      .queueOrthoModel(RENDERER.opaqueQuad, this.selectedTurnOrderTransforms, QueuedModelStandard.class);
                    RENDERER
                      .queueOrthoModel(this.turnOrderOverlay, this.turnOrderTransforms, QueuedModelStandard.class)
                      .translucency(Translucency.HALF_B_PLUS_HALF_F)
                      .alpha(1.0f)
                      .useTextureAlpha()
                      .uvOffset(0, (textureCount - 1) / (float)textureCount)
                      .texture(this.turnOrderTexture);
                  }
                }
              }
            }
          }
        }

        textZ_800bdf00 = oldZ;
      }
    }
  }

  private void buildTurnOrderTexture(final int index, Path path, final boolean player) {
    if(!Files.exists(path)) {
      path = Path.of("mods", "iconturnorder", player ? "player" : "monster", "unknown.png");
    }

    final ByteBuffer imageBuffer;
    try {
      imageBuffer = pathToByteBuffer(path);
    } catch(final IOException e) {
      throw new RuntimeException(e);
    }

    try(final MemoryStack stack = stackPush()) {
      final IntBuffer w = stack.mallocInt(1);
      final IntBuffer h = stack.mallocInt(1);
      final IntBuffer comp = stack.mallocInt(1);

      stbi_set_flip_vertically_on_load(false);
      final ByteBuffer data = stbi_load_from_memory(imageBuffer, w, h, comp, 4);
      if(data == null) {
        throw new RuntimeException("Failed to load image: " + stbi_failure_reason());
      }

      this.turnOrderTexture.data(0, 120 * index, 240, 120, TextureDataType.UBYTE, data);
    }
  }

  @EventListener
  public void battleEndedEvent(final BattleEndedEvent event) {
    this.render = false;
    if(this.turnOrderOverlay != null) {
      this.turnOrderOverlay.delete();
      this.turnOrderOverlay = null;
    }

    if(this.turnOrderTexture != null) {
      this.turnOrderTexture.delete();
      this.turnOrderTexture = null;
    }
  }

  @EventListener
  public void onRender(final RenderEvent event) {
    if(currentEngineState_8004dd04 instanceof final Battle battle && battle.countCombatUiFilesLoaded_800c6cf4 == 6 && this.render && !battle.isBattleDisabled() && CONFIG.getConfig(SHOW_ICON_TURN_ORDER.get())) {
      this.drawTurnOrder();
    }
  }

}
