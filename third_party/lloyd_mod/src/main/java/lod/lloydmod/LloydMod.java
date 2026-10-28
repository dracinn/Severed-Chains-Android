package lod.lloydmod;

import legend.core.GameEngine;
import legend.core.lang.I18nText;
import legend.core.lang.RawText;
import legend.core.platform.input.InputCodepoints;
import legend.game.characters.CharacterData2c;
import legend.game.characters.CharacterTemplate;
import legend.game.characters.RegisterCharacterTemplatesEvent;
import legend.game.characters.VitalsStat;
import legend.game.combat.AdditionMode;
import legend.game.combat.Battle;
import legend.game.combat.bent.AttackEvent;
import legend.game.combat.bent.BattleEntity27c;
import legend.game.combat.bent.MonsterBattleEntity;
import legend.game.combat.bent.PlayerBattleEntity;
import legend.game.combat.deff.RegisterDeffsEvent;
import legend.game.combat.types.CombatantAsset0c;
import legend.game.combat.ui.BattleAction;
import legend.game.combat.ui.GatherBattleActionsEvent;
import legend.game.combat.ui.RegisterBattleActionsEvent;
import legend.game.inventory.EquipmentTypes;
import legend.game.inventory.GatherCharacterEquipmentTypesEvent;
import legend.game.inventory.Good;
import legend.game.inventory.GoodsRegistryEvent;
import legend.game.inventory.GoodsSource;
import legend.game.inventory.ItemIcon;
import legend.game.inventory.SpellRegistryEvent;
import legend.game.inventory.screens.MenuStack;
import legend.game.modding.coremod.CoreMod;
import legend.game.modding.events.RenderEvent;
import legend.game.modding.events.battle.BattleEndedEvent;
import legend.game.modding.events.battle.BattleEntityTurnEvent;
import legend.game.modding.events.battle.BattleStartedEvent;
import legend.game.modding.events.battle.CombatantModelLoadedEvent;
import legend.game.modding.events.config.ConfigUpdatedEvent;
import legend.game.modding.events.gamestate.NewGameEvent;
import legend.game.modding.events.input.InputPressedEvent;
import legend.game.modding.events.inventory.GiveGoodsEvent;
import legend.game.modding.events.submap.SubmapWarpEvent;
import legend.game.scripting.ScriptFile;
import legend.game.scripting.ScriptState;
import legend.game.submap.SMap;
import legend.game.textures.Image;
import legend.game.textures.RegisterAtlasTexturesEvent;
import legend.game.types.EquipmentSlot;
import legend.game.ui.GameOverlay;
import legend.game.unpacker.Loader;
import legend.lodmod.LodBattleActions;
import legend.lodmod.LodGoods;
import legend.lodmod.LodMod;
import legend.lodmod.LodSpells;
import legend.lodmod.equipment.UltimateWargodEquipment;
import legend.lodmod.spells.RetailSpell;
import lod.lloydmod.events.LloydAllowNewGameAddEvent;
import lod.lloydmod.events.LloydJoinedPartyEvent;
import lod.lloydmod.events.LloydVersionEvent;
import lod.lloydmod.helper.LodTextbox;
import lod.lloydmod.menu.LloydBattleAction;
import lod.lloydmod.menu.LloydDragoonSpellAction;
import lod.lloydmod.menu.LloydHotPrizeMenu;
import lod.lloydmod.stats.LloydTemplate;
import org.legendofdragoon.modloader.Mod;
import org.legendofdragoon.modloader.events.EventListener;
import org.legendofdragoon.modloader.events.Priority;
import org.legendofdragoon.modloader.registries.Registrar;
import org.legendofdragoon.modloader.registries.RegistryDelegate;
import org.legendofdragoon.modloader.registries.RegistryId;

import java.nio.file.Path;

import static legend.core.GameEngine.CONFIG;
import static legend.core.GameEngine.EVENTS;
import static legend.core.GameEngine.REGISTRIES;
import static legend.core.GameEngine.SCRIPTS;
import static legend.game.EngineStates.currentEngineState_8004dd04;
import static legend.game.Scus94491BpeSegment_8005.submapCut_80052c30;
import static legend.game.Scus94491BpeSegment_8006.battleState_8006e398;
import static legend.game.Scus94491BpeSegment_800b.gameState_800babc8;
import static legend.game.Scus94491BpeSegment_800b.spGained_800bc950;
import static legend.game.characters.CharacterData2c.CAN_BE_IN_PARTY;
import static legend.game.characters.CharacterData2c.IN_PARTY;
import static legend.lodmod.LodMod.HP_STAT;
import static legend.lodmod.LodMod.INPUT_ACTION_WMAP_QUEEN_FURY_COOLON;
import static legend.lodmod.LodMod.MP_STAT;

@Mod(id = LloydMod.MOD_ID, version = "^3.0.0")

public class LloydMod {
  public static final String MOD_ID = "lloyd_mod";
  public static boolean DRAMOD_VERSION = false;
  public static boolean ALLOW_LLOYD_NEW_GAME = true;

  public static RegistryId id(final String entryId) {
    return new RegistryId(MOD_ID, entryId);
  }

  private static final Registrar<CharacterTemplate, RegisterCharacterTemplatesEvent> CHARACTER_REGISTRAR = new Registrar<>(GameEngine.REGISTRIES.characterTemplates, LloydMod.MOD_ID);
  private static final Registrar<BattleAction, RegisterBattleActionsEvent> ACTION_REGISTRAR = new Registrar<>(REGISTRIES.battleActions, LloydMod.MOD_ID);
  private static final Registrar<Good, GoodsRegistryEvent> GOODS_REGISTRAR = new Registrar<>(REGISTRIES.goods, LloydMod.MOD_ID);

  public static final RegistryDelegate<LloydTemplate> LLOYD = CHARACTER_REGISTRAR.register("lloyd", LloydTemplate::new);
  public static final RegistryDelegate<BattleAction> LLOYD_ATTACK = ACTION_REGISTRAR.register("lloyd_attack", LloydBattleAction::new);
  public static final RegistryDelegate<BattleAction> LLOYD_DRAGOON_SPELLS = ACTION_REGISTRAR.register("lloyd_dragoon_spells", LloydDragoonSpellAction::new);
  public static final RegistryDelegate<Good> CHAOS_DIVINE_DRAGOON_SPIRIT = GOODS_REGISTRAR.register("chaos_divine_dragoon_spirit", () -> new Good(80, ItemIcon.DIVINE_DRAGOON_SPIRIT));

  public static RetailSpell lloydUsedSpell;
  private boolean gainedSp = false;
  private boolean gainedMp = false;
  private final CombatantAsset0c[] lloydAssets = new CombatantAsset0c[32];
  private boolean loadedAnimations = false;

  private boolean firstUseAerialCrash = false;
  private boolean firstUseBallBarrage = false;
  private boolean firstUseSigilofExplosion = false;
  private boolean firstUseDraconicImpact = false;

  public static final MenuStack menuStack = new MenuStack();
  public static LloydHotPrizeMenu hotPrizeMenu;
  public static boolean hotPrizeEnabled = true;
  public static boolean hotPrizeActivated = true;

  public int addLloydEarly = 0;

  public LloydMod() {
    GameEngine.EVENTS.register(this);
  }

  @EventListener
  public void onRegisterCharacter(final RegisterCharacterTemplatesEvent event) {
    final LloydVersionEvent versionEvent = new LloydVersionEvent();
    EVENTS.postEvent(versionEvent);
    DRAMOD_VERSION = versionEvent.draModVersion;
    event.register(LLOYD.getId(), new LloydTemplate());

    final LloydAllowNewGameAddEvent newGameEvent = new LloydAllowNewGameAddEvent();
    EVENTS.postEvent(newGameEvent);
    ALLOW_LLOYD_NEW_GAME = newGameEvent.allowNewGameAdd;
  }

  @EventListener
  public void onRegisterGood(final GoodsRegistryEvent event) {
    GOODS_REGISTRAR.registryEvent(event);
  }

  @EventListener
  public void onRegisterBattleAction(final RegisterBattleActionsEvent event) {
    final LloydVersionEvent versionEvent = new LloydVersionEvent();
    EVENTS.postEvent(versionEvent);
    DRAMOD_VERSION = versionEvent.draModVersion;

    event.register(LLOYD_ATTACK.getId(), new LloydBattleAction());
    event.register(LLOYD_DRAGOON_SPELLS.getId(), new LloydDragoonSpellAction());

    if(DRAMOD_VERSION) {
      GameEngine.loadLangOverrides(Path.of("mods", "lloyd_mod", "dramod_lang"));
    } else {
      GameEngine.loadLangOverrides(Path.of("mods", "lloyd_mod", "lang"));
    }
  }

  @EventListener(priority = Priority.LOW)
  public void onNewGame(final NewGameEvent event) {
    final LloydVersionEvent versionEvent = new LloydVersionEvent();
    EVENTS.postEvent(versionEvent);
    DRAMOD_VERSION = versionEvent.draModVersion;

    final CharacterData2c lloyd = event.gameState.addCharacter(LLOYD.get().make(event.gameState));
    this.levelUp(lloyd, 27);

    if(DRAMOD_VERSION) {
      lloyd.equip(EquipmentSlot.WEAPON, REGISTRIES.equipment.getEntry("dragoon_modifier", "broad_sword").get());
      lloyd.equip(EquipmentSlot.HELMET, REGISTRIES.equipment.getEntry("dragoon_modifier", "bandana").get());
      lloyd.equip(EquipmentSlot.ARMOUR, REGISTRIES.equipment.getEntry("dragoon_modifier", "leather_armor").get());
      lloyd.equip(EquipmentSlot.BOOTS, REGISTRIES.equipment.getEntry("dragoon_modifier", "leather_boots").get());
      lloyd.equip(EquipmentSlot.ACCESSORY, REGISTRIES.equipment.getEntry("dragoon_modifier", "bracelet").get());
    } else {
      lloyd.equip(EquipmentSlot.WEAPON, REGISTRIES.equipment.getEntry("lod", "broad_sword").get());
      lloyd.equip(EquipmentSlot.HELMET, REGISTRIES.equipment.getEntry("lod", "bandana").get());
      lloyd.equip(EquipmentSlot.ARMOUR, REGISTRIES.equipment.getEntry("lod", "leather_armor").get());
      lloyd.equip(EquipmentSlot.BOOTS, REGISTRIES.equipment.getEntry("lod", "leather_boots").get());
      lloyd.equip(EquipmentSlot.ACCESSORY, REGISTRIES.equipment.getEntry("lod", "bracelet").get());
    }
  }

  private void addLloyd() {
    boolean lloydFound = false;
    for(int i = 0; i < gameState_800babc8.charData_32c.size(); i++) {
      if(gameState_800babc8.charData_32c.get(i).template instanceof LloydTemplate) {
        lloydFound = true;
        gameState_800babc8.charData_32c.get(i).partyFlags_04 = IN_PARTY | CAN_BE_IN_PARTY;
      }
    }

    if(!lloydFound) {
      final CharacterData2c lloyd = LLOYD.get().make(gameState_800babc8);
      lloyd.partyFlags_04 = IN_PARTY | CAN_BE_IN_PARTY;
      gameState_800babc8.addCharacter(lloyd);
      this.levelUp(lloyd, 27);

      if(DRAMOD_VERSION) {
        lloyd.equip(EquipmentSlot.WEAPON, REGISTRIES.equipment.getEntry("dragoon_modifier", "broad_sword").get());
        lloyd.equip(EquipmentSlot.HELMET, REGISTRIES.equipment.getEntry("dragoon_modifier", "bandana").get());
        lloyd.equip(EquipmentSlot.ARMOUR, REGISTRIES.equipment.getEntry("dragoon_modifier", "leather_armor").get());
        lloyd.equip(EquipmentSlot.BOOTS, REGISTRIES.equipment.getEntry("dragoon_modifier", "leather_boots").get());
        lloyd.equip(EquipmentSlot.ACCESSORY, REGISTRIES.equipment.getEntry("dragoon_modifier", "bracelet").get());
      } else {
        lloyd.equip(EquipmentSlot.WEAPON, REGISTRIES.equipment.getEntry("lod", "broad_sword").get());
        lloyd.equip(EquipmentSlot.HELMET, REGISTRIES.equipment.getEntry("lod", "bandana").get());
        lloyd.equip(EquipmentSlot.ARMOUR, REGISTRIES.equipment.getEntry("lod", "leather_armor").get());
        lloyd.equip(EquipmentSlot.BOOTS, REGISTRIES.equipment.getEntry("lod", "leather_boots").get());
        lloyd.equip(EquipmentSlot.ACCESSORY, REGISTRIES.equipment.getEntry("lod", "bracelet").get());
      }
    }

    if(!gameState_800babc8.goods_19c.has(CHAOS_DIVINE_DRAGOON_SPIRIT.get())) {
      gameState_800babc8.goods_19c.give(CHAOS_DIVINE_DRAGOON_SPIRIT.get(), GoodsSource.GAMEPLAY);
    }
  }

  private void levelUp(final CharacterData2c character, final int level) {
    while(character.level_12 < level - 1) {
      character.applyLevelUp(null);
    }

    character.xp_00 = character.getXpToNextLevel();
    character.applyLevelUp(null);

    character.stats.getStat(HP_STAT.get()).restore();
    character.stats.getStat(MP_STAT.get()).restore();
  }

  @EventListener
  public void onSubmapWarpEvent(final SubmapWarpEvent event) {
    if(event.submapCut == 745 && gameState_800babc8.scriptFlags2_bc.get(8, 28) && !gameState_800babc8.scriptFlags2_bc.get(9, 16)) {
      GameOverlay.addNotification(5, new RawText("Somehow, Lloyd returned."));
    }

    if(event.submapCut == 561) {
      new Thread(() -> {
        try {
          Thread.sleep(1000);
          ((SMap)currentEngineState_8004dd04).mapTransition(736, 0);
        } catch (final Exception ignored) {
        }
      }).start();
    }

    if(event.submapCut == 441 && ALLOW_LLOYD_NEW_GAME && gameState_800babc8.scriptFlags2_bc.get(8, 28)) {
      this.addLloyd();
    }
  }

  @EventListener
  public void giveGoodsEvent(final GiveGoodsEvent event) {
    if(event.givenGoods.contains(LodGoods.MOON_MIRROR.get())) {
      if(!gameState_800babc8.goods_19c.has(CHAOS_DIVINE_DRAGOON_SPIRIT.get())) {
        final LloydJoinedPartyEvent joinEvent = EVENTS.postEvent(new LloydJoinedPartyEvent());

        if(!joinEvent.override) {
          gameState_800babc8.goods_19c.give(CHAOS_DIVINE_DRAGOON_SPIRIT.get(), GoodsSource.GAMEPLAY);

          final int textboxIndex = LodTextbox.getFreeTextboxIndex();
          LodTextbox.initTextbox(textboxIndex, 200, 145, 28, 2);
          LodTextbox.setTextboxContents(textboxIndex, "Lloyd has joined the party!");

          SCRIPTS.getState(0).setStor(40, 0);
          final ScriptFile file = new ScriptFile("lloyd_party", Loader.loadFileSync(Path.of("mods", LloydMod.MOD_ID, "scripts", "submap", "lloyd_party")).getBytes());
          final int index = SCRIPTS.findFreeScriptState();
          final ScriptState<?> joinScriptState = SCRIPTS.allocateScriptState(index, "lloyd_party", null);
          joinScriptState.fork();
          joinScriptState.loadScriptFile(file);

          this.addLloyd();
        }
      }
    }
  }

  @EventListener
  public void onRegisterSpell(final SpellRegistryEvent event) {
    LloydSpells.register(event);
  }

  @EventListener
  public void onRegisterDeff(final RegisterDeffsEvent event) {
    LloydDeffs.register(event);
  }

  @EventListener(priority = Priority.LOWEST)
  public void gatherBattleActions(final GatherBattleActionsEvent event) {
    if(event.player.character.template instanceof LloydTemplate) {
      if(event.player.isDragoon()) {
        event.actions.remove(LodBattleActions.D_ATTACK.get());
        event.actions.remove(LodBattleActions.SPELLS.get());
        event.actions.put(LLOYD_ATTACK.get(), 0);
        if(DRAMOD_VERSION) {
          event.actions.put(LLOYD_DRAGOON_SPELLS.get(), 100);
        }
      } else {
        event.actions.remove(LodBattleActions.ATTACK.get());
        event.actions.put(LLOYD_ATTACK.get(), 0);
      }
    }
  }

  @EventListener
  public void onRegisterAtlas(final RegisterAtlasTexturesEvent event) {
    for(int i = 0; i < 3; i++) {
      event.add(id("transform_" + i), Image.load(Path.of("mods", LloydMod.MOD_ID, "transform_" + i + ".png")));
    }
  }

  @EventListener
  public void gatherCharacterEquipmentTypes(final GatherCharacterEquipmentTypesEvent event) {
    event.add(LLOYD.get(), EquipmentTypes.DART, EquipmentTypes.LONGSWORD, EquipmentTypes.SHORTSWORD, EquipmentTypes.NEUTRAL, EquipmentTypes.MALE, EquipmentTypes.ADDITIONS, EquipmentTypes.HEAVY, EquipmentTypes.ARMOR_OF_YORE);
  }

  public void hotPrizeCheck() {
    hotPrizeEnabled = true;

    for(int i = 0; i < battleState_8006e398.getAllBentCount(); i++) {
      final ScriptState<? extends BattleEntity27c> state = battleState_8006e398.allBents_e0c.get(i);
      final BattleEntity27c bent = state.innerStruct_00;
      if(bent instanceof final PlayerBattleEntity player && player.character.template instanceof LloydTemplate) {
        if(player.equipment_11e.get(EquipmentSlot.ACCESSORY) instanceof UltimateWargodEquipment) {
          hotPrizeEnabled = false;
        }
      }
    }

    if(CONFIG.getConfig(CoreMod.ADDITION_MODE_CONFIG.get()) == AdditionMode.AUTOMATIC) {
      hotPrizeEnabled = false;
    }
  }

  @EventListener
  public void onBattleStarted(final BattleStartedEvent event) {
    this.hotPrizeCheck();
    for(int i = 0; i < battleState_8006e398.getAllBentCount(); i++) {
      final ScriptState<? extends BattleEntity27c> state = battleState_8006e398.allBents_e0c.get(i);
      final BattleEntity27c bent = state.innerStruct_00;
      if(bent instanceof final PlayerBattleEntity player) {
        if(player.character.template instanceof LloydTemplate) {
          if(DRAMOD_VERSION) {
            final VitalsStat mp = player.stats.getStat(LodMod.MP_STAT.get());
            mp.setCurrent(0);
            this.firstUseAerialCrash = false;
            this.firstUseBallBarrage = false;
            this.firstUseSigilofExplosion = false;
            this.firstUseDraconicImpact = false;
          }
        }
      }
    }

    hotPrizeMenu = new LloydHotPrizeMenu(0, 0, 0);
  }

  @EventListener
  public void onConfigChanges(final ConfigUpdatedEvent event) {
    if(event.config == CoreMod.ADDITION_MODE_CONFIG.get()) {
      if(currentEngineState_8004dd04 instanceof final Battle battle) {
        this.hotPrizeCheck();
      }
    }
  }

  @EventListener
  public void onBattleEntityTurnStart(final BattleEntityTurnEvent event) {
    if(event.bent instanceof final PlayerBattleEntity player && player.character.template instanceof LloydTemplate) {
      lloydUsedSpell = null;
      hotPrizeActivated = false;
      this.gainedSp = false;
      this.gainedMp = false;

      if(!player.isDragoon()) {
        this.firstUseAerialCrash = false;
        this.firstUseBallBarrage = false;
        this.firstUseSigilofExplosion = false;
        this.firstUseDraconicImpact = false;
      }
    }
  }

  @EventListener
  public void onBattleEnded(final BattleEndedEvent event) {
    this.loadedAnimations = false;
    menuStack.reset();
  }

  @EventListener
  public void onAttack(final AttackEvent event) {
    if(event.damage == 0) {
      return;
    }

    if(event.attacker instanceof final PlayerBattleEntity player && player.character.template instanceof LloydTemplate) {
      if(hotPrizeEnabled && hotPrizeActivated) {
        event.damage = (int)Math.floor(event.damage * (0.50d + (((double)hotPrizeMenu.completedPrizes / (double)hotPrizeMenu.maxPrizes) * 0.5d)));
      }

      if(DRAMOD_VERSION) {
        if(lloydUsedSpell == LloydSpells.RUNNING_SLASH.get()) {
          this.gainMp(player, 10);
          this.gainSp(player, 30);
        } else if(lloydUsedSpell == LloydSpells.SPECTRAL_SWORD.get()) {
          event.damage *= 2;
          this.gainSp(player, 105);
        } else if(lloydUsedSpell == LloydSpells.SWORD_RUSH.get()) {
          event.damage = (int)Math.floor(event.damage * 1.5);
          this.gainSp(player, 20);
          this.gainMp(player, 20);
        } else if(lloydUsedSpell == LloydSpells.AERIAL_CRASH.get()) {
          event.damage *= 2;

          if(!this.firstUseAerialCrash) {
            this.firstUseAerialCrash = true;
            this.gainSp(player, 200);
          }

          if(this.firstUseAerialCrash && this.firstUseBallBarrage && this.firstUseSigilofExplosion && this.firstUseDraconicImpact) {
            this.gainSp(player, 25);
            this.gainMp(player, 40);
          }
        } else if(lloydUsedSpell == LloydSpells.BALL_BARRAGE.get()) {
          if("dragoon_modifier:spell73".equals(event.attacker.spell_94.getRegistryId().toString())) {
            event.damage *= 3;
          }

          if(!this.firstUseBallBarrage) {
            this.firstUseBallBarrage = true;
            this.gainSp(player, 300);
          }
        } else if(lloydUsedSpell == LloydSpells.SIGIL_OF_EXPLOSION.get()) {
          event.damage *= 4;

          if(!this.firstUseSigilofExplosion) {
            this.firstUseSigilofExplosion = true;
            this.gainSp(player, 400);
          }
        } else if(lloydUsedSpell == LloydSpells.DRACONIC_IMPALE.get()) {
          event.damage *= 5;

          if(!this.firstUseDraconicImpact) {
            this.firstUseDraconicImpact = true;
            this.gainSp(player, 500);
          }
        }

        if(player.isDragoon()) {
          if(event.defender instanceof MonsterBattleEntity) {
            if(lloydUsedSpell == null) {
              event.damage /= 2;
            }

            this.gainSp(player, 80);
          }

          final VitalsStat sp = player.stats.getStat(LodMod.SP_STAT.get());
          battleState_8006e398.dragoonTurnsRemaining_294[player.typeBentSlot_276] = (int)Math.floor(sp.getCurrent() / 100d);
        }
      } else {
        if(lloydUsedSpell == LloydSpells.RUNNING_SLASH.get()) {
          event.damage *= 2;
          this.gainMp(player, 10);
          this.gainSp(player, (int)Math.floor(30d * (player.dlevel_06 / 2d) * ((player.spMultiplier_128 + 100d) / 100d)));
        } else if(lloydUsedSpell == LloydSpells.SPECTRAL_SWORD.get()) {
          event.damage *= 6;
          this.gainSp(player, (int)Math.floor(100d * (player.dlevel_06 / 2d) * ((player.spMultiplier_128 + 100d) / 100d)));
        } else if(lloydUsedSpell == LloydSpells.SWORD_RUSH.get()) {
          event.damage *= 2;
          this.gainMp(player, 20);
        } else if(lloydUsedSpell == LloydSpells.AERIAL_CRASH.get()) {
          event.damage *= 3;
          this.gainMp(player, 20);
        } else if(lloydUsedSpell == LloydSpells.BALL_BARRAGE.get()) {
          if(event.attacker.spell_94.getRegistryId().equals(LodSpells.SPELL73.getId())) {
            event.damage *= 5;
            this.gainMp(player, 20);
          }
        } else if(lloydUsedSpell == LloydSpells.SIGIL_OF_EXPLOSION.get()) {
          event.damage *= 4;
          this.gainMp(player, 20);
        } else if(lloydUsedSpell == LloydSpells.DRACONIC_IMPALE.get()) {
          event.damage *= 6;
          this.gainMp(player, 20);
        }
      }
      menuStack.reset();
    }
  }

  public void gainSp(final PlayerBattleEntity player, final int amount) {
    if(!this.gainedSp) {
      final VitalsStat sp = player.stats.getStat(LodMod.SP_STAT.get());
      sp.setCurrent(sp.getCurrent() + amount);
      spGained_800bc950.mergeInt(player.character, amount, Integer::sum);
      this.gainedSp = true;
    }
  }

  public void gainMp(final PlayerBattleEntity player, final int amount) {
    if(!this.gainedMp) {
      final VitalsStat mp = player.stats.getStat(LodMod.MP_STAT.get());
      mp.setCurrent(mp.getCurrent() + amount);
      this.gainedMp = true;
    }
  }

  @EventListener
  public void onCombatent(final CombatantModelLoadedEvent event) {
    try {
      if(event.combatant.isPlayer() && event.combatant.playerBent.character.template instanceof LloydTemplate) {
        for(int i = 0; i < event.combatant.assets_14.length; i++) {
          if(event.combatant.playerBent.isDragoon()) {
            event.combatant.assets_14[i] = event.combatant.assets_14[0];
          } else {
            if(!this.loadedAnimations) {
              if(!(event.combatant.assets_14[i] instanceof final CombatantAsset0c.AnimType animType)) {
                event.combatant.assets_14[i] = event.combatant.assets_14[0];
              }
              this.lloydAssets[i] = event.combatant.assets_14[i];
            }
          }
        }

        if(!this.loadedAnimations) {
          this.loadedAnimations = true;
        } else {
          if(!event.combatant.playerBent.isDragoon()) {
            System.arraycopy(this.lloydAssets, 0, event.combatant.assets_14, 0, event.combatant.assets_14.length);
          }
        }
      }
    } catch(Exception _) {
    }
  }

  @EventListener
  public void RenderMenu(final RenderEvent event) {
    if(currentEngineState_8004dd04 instanceof final Battle battle && hotPrizeMenu != null) {
      if(gameState_800babc8.timestamp_a0 >= hotPrizeMenu.timeStarted && gameState_800babc8.timestamp_a0 <= (hotPrizeMenu.timeStarted + hotPrizeMenu.timeToComplete)) {
        menuStack.render();
      }
    }
  }

  @EventListener
  public void inputPressed(final InputPressedEvent event) {
    if(gameState_800babc8 != null) {
      if(submapCut_80052c30 == 10 && ALLOW_LLOYD_NEW_GAME) {
        if(event.action == INPUT_ACTION_WMAP_QUEEN_FURY_COOLON.get()) {
          if(this.addLloydEarly < 5) {
            this.addLloydEarly += 1;

            if(this.addLloydEarly == 5) {
              final LloydJoinedPartyEvent joinEvent = EVENTS.postEvent(new LloydJoinedPartyEvent());

              if(!joinEvent.override) {
                gameState_800babc8.goods_19c.give(CHAOS_DIVINE_DRAGOON_SPIRIT.get(), GoodsSource.GAMEPLAY);

                for(int i = 0; i < gameState_800babc8.charData_32c.size(); i++) {
                  if(gameState_800babc8.charData_32c.get(i).template instanceof LloydTemplate) {
                    gameState_800babc8.charData_32c.remove(i);
                  }
                }

                final CharacterData2c lloyd = LLOYD.get().make(gameState_800babc8);
                lloyd.partyFlags_04 = IN_PARTY | CAN_BE_IN_PARTY;
                gameState_800babc8.addCharacter(lloyd);

                if(DRAMOD_VERSION) {
                  lloyd.equip(EquipmentSlot.WEAPON, REGISTRIES.equipment.getEntry("dragoon_modifier", "broad_sword").get());
                  lloyd.equip(EquipmentSlot.HELMET, REGISTRIES.equipment.getEntry("dragoon_modifier", "bandana").get());
                  lloyd.equip(EquipmentSlot.ARMOUR, REGISTRIES.equipment.getEntry("dragoon_modifier", "leather_armor").get());
                  lloyd.equip(EquipmentSlot.BOOTS, REGISTRIES.equipment.getEntry("dragoon_modifier", "leather_boots").get());
                  lloyd.equip(EquipmentSlot.ACCESSORY, REGISTRIES.equipment.getEntry("dragoon_modifier", "bracelet").get());
                } else {
                  lloyd.equip(EquipmentSlot.WEAPON, REGISTRIES.equipment.getEntry("lod", "broad_sword").get());
                  lloyd.equip(EquipmentSlot.HELMET, REGISTRIES.equipment.getEntry("lod", "bandana").get());
                  lloyd.equip(EquipmentSlot.ARMOUR, REGISTRIES.equipment.getEntry("lod", "leather_armor").get());
                  lloyd.equip(EquipmentSlot.BOOTS, REGISTRIES.equipment.getEntry("lod", "leather_boots").get());
                  lloyd.equip(EquipmentSlot.ACCESSORY, REGISTRIES.equipment.getEntry("lod", "bracelet").get());
                }
              }
              GameOverlay.addNotification(3, new RawText("LLOYD ADDED TO PARTY"));
            } else {
              GameOverlay.addNotification(3, new I18nText("lod_core.ui.hotkey", new RawText("PRESS " + (5 - this.addLloydEarly) + " TIME(S) TO UNLOCK LLOYD"), InputCodepoints.getActionName(INPUT_ACTION_WMAP_QUEEN_FURY_COOLON.get())));
            }
          }
        }
      }
    }
  }
}
