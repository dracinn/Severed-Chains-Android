package lod.lloydmod.stats;

import legend.core.Latch;
import legend.core.gte.MV;
import legend.game.additions.AdditionHitProperties10;
import legend.game.additions.AdditionHits80;
import legend.game.additions.AdditionSound;
import legend.game.characters.CharacterData2c;
import legend.game.characters.CharacterSpellInfo;
import legend.game.characters.Element;
import legend.game.characters.SpellDragoonLevelUnlockCriterion;
import legend.game.characters.SpellDragoonSpiritUnlockCriterion;
import legend.game.combat.bent.PlayerBattleEntity;
import legend.game.inventory.CanEquip;
import legend.game.inventory.Equipment;
import legend.game.inventory.Good;
import legend.game.textures.Image;
import legend.game.textures.TextureAtlasIcon;
import legend.game.types.EquipmentSlot;
import legend.game.types.GameState52c;
import legend.game.unpacker.FileData;
import legend.game.unpacker.Loader;
import legend.lodmod.characters.RetailCharacterTemplate;
import lod.lloydmod.LloydMod;
import lod.lloydmod.LloydSpells;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static legend.core.GameEngine.getTextureAtlas;
import static legend.game.DrgnFiles.loadDrgnDir;
import static legend.lodmod.LodGoods.DIVINE_DRAGOON_SPIRIT;
import static legend.lodmod.LodMod.DIVINE_ELEMENT;
import static lod.lloydmod.LloydMod.CHAOS_DIVINE_DRAGOON_SPIRIT;

public class LloydTemplate extends RetailCharacterTemplate {
  private static final int[] XP = {20, 43, 104, 203, 351, 558, 833, 1186, 1627, 2165, 2811, 3574, 4465, 5491, 6665, 7994, 9489, 11160, 13017, 15069, 17326, 19798, 22494, 25425, 28599, 32028, 35720, 39685, 43934, 48475, 53320, 58476, 63955, 69766, 75918, 82422, 89287, 96523, 104140, 112148, 120555, 129373, 138611, 148278, 158385, 168940, 179955, 191438, 203400, 218962, 235146, 251965, 269431, 287556, 306352, 325832, 346007, 366890, 388494};
  private static final int[] HP = {27,26,27,27,26,27,26,27,27,26,27,74,73,74,74,74,73,74,74,73,74,100,99,99,100,99,100,99,100,99,100,105,106,106,105,106,106,105,106,106,105,230,230,230,230,230,230,230,230,230,230,142,142,143,142,142,142,142,142,142};
  private static final int[] ATTACK = {2,1,2,1,3,1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,3,1,3,2,3,2};
  private static final int[] DEFENSE = {3,1,2,3,1,2,2,2,2,2,2,2,2,2,3,1,3,2,2,2,2,2,3,1,3,2,2,2,2,2,3,1,3,2,2,2,2,2,3,1,3,2,1,3,2,2,2,2,2,3,1,3,2,2,3,2,2,2,3,3};
  private static final int[] MAGIC_ATTACK = {3,2,2,2,2,2,2,2,2,2,3,2,3,2,3,2,3,2,3,2,3,2,3,2,3,2,3,2,3,2,3,2,3,2,3,2,3,2,3,2,3,2,3,2,3,2,3,2,3,2,3,2,3,3,3,3,3,3,3,3};
  private static final int[] MAGIC_DEFENSE = {5,1,2,2,3,2,2,3,2,2,4,2,3,4,2,4,3,2,4,3,4,2,2,3,3,2,3,3,2,3,3,2,4,3,3,3,4,2,3,4,3,2,3,2,4,2,2,4,2,2,4,2,3,4,3,4,3,4,3,4};


  private static final int[] DRAMOD_XP = {20, 43, 104, 203, 351, 558, 833, 1186, 1627, 2165, 2811, 3574, 4465, 5491, 6665, 7994, 9489, 11160, 13017, 15069, 17326, 19798, 22494, 25425, 28599, 32028, 35720, 39685, 43934, 48475, 53320, 58476, 63955, 69766, 75918, 82422, 89287, 96523, 104140, 112148, 120555, 129373, 138611, 148278, 158385, 168940, 179955, 191438, 203400, 218962, 235146, 251965, 269431, 287556, 306352, 325832, 346007, 366890, 388494};
  private static final int[] DRAMOD_HP = {48,48,48,48,48,48,48,48,48,48,48,133,133,133,133,133,133,133,133,133,133,179,179,179,179,179,179,179,179,179,179,190,190,190,190,190,190,190,190,190,190,414,414,414,414,414,414,414,414,414,414,256,256,256,256,256,256,256,256,256};
  private static final int[] DRAMOD_ATTACK = {4,3,3,3,5,3,3,5,3,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,5,5,3,5,5,5,5};
  private static final int[] DRAMOD_DEFENSE = {2,1,1,2,1,1,1,2,1,1,2,1,2,1,2,1,2,1,2,1,2,1,2,1,2,1,2,1,2,1,2,1,2,1,2,1,2,1,2,1,2,1,1,2,1,2,1,2,1,2,1,2,2,1,2,2,1,2,2,2};
  private static final int[] DRAMOD_MAGIC_ATTACK = {4,3,3,3,5,3,3,5,3,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,3,5,5,5,3,5,5,5,5};
  private static final int[] DRAMOD_MAGIC_DEFENSE = {2,1,1,1,1,1,1,1,1,1,2,1,2,2,1,2,2,1,2,2,2,1,1,1,2,1,1,2,1,1,2,1,2,2,1,2,2,1,2,2,2,1,1,1,2,1,1,2,1,1,2,1,2,2,2,2,2,2,2,2};

  private final Latch<Image> portrait = new Latch<>(() -> Image.load(Path.of("mods",  LloydMod.MOD_ID, "portrait.png")));

  @Override
  public CharacterData2c make(final GameState52c gameState) {
    final CharacterData2c character = super.make(gameState);

    character.addSpell(LloydSpells.RUNNING_SLASH.getId(), new CharacterSpellInfo(List.of(new SpellDragoonSpiritUnlockCriterion()))).unlock(gameState.timestamp_a0);
    character.addSpell(LloydSpells.SPECTRAL_SWORD.getId(), new CharacterSpellInfo(List.of(new SpellDragoonSpiritUnlockCriterion()))).unlock(gameState.timestamp_a0 + 1);
    character.addSpell(LloydSpells.SWORD_RUSH.getId(), new CharacterSpellInfo(List.of(new SpellDragoonSpiritUnlockCriterion()))).unlock(gameState.timestamp_a0 + 2);
    character.addSpell(LloydSpells.AERIAL_CRASH.getId(), new CharacterSpellInfo(List.of(new SpellDragoonSpiritUnlockCriterion(), new SpellDragoonLevelUnlockCriterion(2))));
    character.addSpell(LloydSpells.BALL_BARRAGE.getId(), new CharacterSpellInfo(List.of(new SpellDragoonSpiritUnlockCriterion(), new SpellDragoonLevelUnlockCriterion(3))));
    character.addSpell(LloydSpells.SIGIL_OF_EXPLOSION.getId(), new CharacterSpellInfo(List.of(new SpellDragoonSpiritUnlockCriterion(), new SpellDragoonLevelUnlockCriterion(4))));
    character.addSpell(LloydSpells.DRACONIC_IMPALE.getId(), new CharacterSpellInfo(List.of(new SpellDragoonSpiritUnlockCriterion(), new SpellDragoonLevelUnlockCriterion(5))));

    return character;
  }

  @Override
  public void loadWorldMapModel(final CharacterData2c character, final Consumer<List<FileData>> onLoad) {
    Loader
      .loadFiles("SECT/DRGN22.BIN/980/231", "SECT/DRGN22.BIN/980/textures/7", "SECT/DRGN22.BIN/980/232", "SECT/DRGN22.BIN/980/233", "SECT/DRGN22.BIN/980/234")
      .thenAccept(onLoad)
    ;
  }

  @Override
  public CanEquip canEquip(final CharacterData2c character, final EquipmentSlot slot, final Equipment equipment) {
    return CanEquip.NORMAL;
  }

  @Override
  public boolean canUseSpells(final CharacterData2c character) {
    return true;
  }

  @Override
  public int getXpToNextLevel(final CharacterData2c character) {
    if(LloydMod.DRAMOD_VERSION) {
      if(character.level_12 - 1 < DRAMOD_XP.length) {
        return DRAMOD_XP[character.level_12 - 1];
      }

      return DRAMOD_XP[DRAMOD_XP.length - 1] + 23000 + (character.level_12 - 1 - DRAMOD_XP.length) * 1000;
    } else {
      if(character.level_12 - 1 < XP.length) {
        return XP[character.level_12 - 1];
      }

      return XP[XP.length - 1] + 23000 + (character.level_12 - 1 - XP.length) * 1000;
    }
  }

  @Override
  public int getDxpToNextLevel(final CharacterData2c character) {
    if(LloydMod.DRAMOD_VERSION) {
      return switch(character.dlevel_13) {
        case 1 -> 4000;
        case 2 -> 16000;
        case 3 -> 32000;
        case 4 -> 48000;
        case 5 -> 72000;
        case 6 -> 108000;
        default -> 108000 + (character.dlevel_13 - 6) * 36000;
      };
    } else {
      return switch(character.dlevel_13) {
        case 1 -> 1000;
        case 2 -> 6000;
        case 3 -> 12000;
        case 4 -> 24000;
        default -> 24000 + (character.dlevel_13 - 4) * 14000;
      };
    }
  }

  @Override
  public Element getElement(final CharacterData2c character) {
    return DIVINE_ELEMENT.get();
  }

  @Override
  protected Good getDragoonSpirit() {
    return CHAOS_DIVINE_DRAGOON_SPIRIT.get();
  }

  @Override
  public AdditionHits80 getDragoonAddition(final CharacterData2c character) {
    return new AdditionHits80(new AdditionHitProperties10(0xc0, 0, 0, 0, 100, 0, 22, 0, 0, 0, 0, 0, 7, 0, 0, 0, new AdditionSound(0, 0), new AdditionSound(0, 0), new AdditionSound(0, 0), new AdditionSound(0, 0), new AdditionSound(0, 0), new AdditionSound(0, 0), new AdditionSound(0, 0), new AdditionSound(0, 0)), new AdditionHitProperties10(0xc0, 0, 0, 0, 10, 0, 0, 0, 0, 0, 0, 0, 6, 0, 0, 0, new AdditionSound(0, 0), new AdditionSound(0, 0), new AdditionSound(0, 0), new AdditionSound(0, 0)), new AdditionHitProperties10(0xc0, 0, 0, 0, 20, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0), new AdditionHitProperties10(0xc0, 0, 0, 0, 30, 0, 0, 0, 0, 0, 0, 0, 7, 0, 0, 0), new AdditionHitProperties10(0xc0, 0, 0, 0, 40, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), new AdditionHitProperties10(0xc0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), new AdditionHitProperties10(0x0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), new AdditionHitProperties10(0x0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
  }

  @Override
  protected int getHpToAdd(final int level) {
    if(LloydMod.DRAMOD_VERSION) {
      if(level < DRAMOD_HP.length) {
        return DRAMOD_HP[level];
      }
    } else {
      if(level < HP.length) {
        return HP[level];
      }
    }

    return 385;
  }

  @Override
  protected int getSpeedToAdd(final int level) {
    if(LloydMod.DRAMOD_VERSION) {
      if(level == 0) {
        return 110;
      }
    } else {
      if(level == 0) {
        return 80;
      }
    }

    return 0;
  }

  @Override
  protected int getAttackToAdd(final int level) {
    if(LloydMod.DRAMOD_VERSION) {
      if(level < DRAMOD_ATTACK.length) {
        return DRAMOD_ATTACK[level];
      }
    } else {
      if(level < ATTACK.length) {
        return ATTACK[level];
      }
    }

    return 2;
  }

  @Override
  protected int getDefenseToAdd(final int level) {
    if(LloydMod.DRAMOD_VERSION) {
      if(level < DRAMOD_DEFENSE.length) {
        return DRAMOD_DEFENSE[level];
      }
    } else {
      if(level < DEFENSE.length) {
        return DEFENSE[level];
      }
    }

    return 3;
  }

  @Override
  protected int getMagicAttackToAdd(final int level) {
    if(LloydMod.DRAMOD_VERSION) {
      if(level < DRAMOD_MAGIC_ATTACK.length) {
        return DRAMOD_MAGIC_ATTACK[level];
      }
    } else {
      if(level < MAGIC_ATTACK.length) {
        return MAGIC_ATTACK[level];
      }
    }

    return 3;
  }

  @Override
  protected int getMagicDefenseToAdd(final int level) {
    if(LloydMod.DRAMOD_VERSION) {
      if(level < DRAMOD_MAGIC_DEFENSE.length) {
        return DRAMOD_MAGIC_DEFENSE[level];
      }
    } else {
      if(level < MAGIC_DEFENSE.length) {
        return MAGIC_DEFENSE[level];
      }
    }

    return 4;
  }

  @Override
  protected int getDragoonAttackToAdd(final int dlevel) {
    if(LloydMod.DRAMOD_VERSION) {
      if(dlevel == 0) {
        return 200;
      }

      return 0;
    } else {
      if(dlevel == 0) {
        return 150;
      }

      return 5;
    }
  }

  @Override
  protected int getDragoonMagicAttackToAdd(final int dlevel) {
    if(LloydMod.DRAMOD_VERSION) {
      if(dlevel == 0) {
        return 200;
      }

      return 0;
    } else {
      if(dlevel == 0) {
        return 150;
      }

      return 5;
    }
  }

  @Override
  protected int getDragoonDefenseToAdd(final int dlevel) {
    if(LloydMod.DRAMOD_VERSION) {
      if(dlevel == 0) {
        return 200;
      }

      return 0;
    } else {
      if(dlevel == 0) {
        return 230;
      }

      return 5;
    }
  }

  @Override
  protected int getDragoonMagicDefenseToAdd(final int dlevel) {
    if(LloydMod.DRAMOD_VERSION) {
      if(dlevel == 0) {
        return 200;
      }

      return 0;
    } else {
      if(dlevel == 0) {
        return 200;
      }

      return 5;
    }
  }

  @Override
  public Path getAttackSoundsPath(final CharacterData2c character, final PlayerBattleEntity bent) {
    return this.getBattleSoundsPath(character, bent);
  }

  @Override
  public Path getDragoonAttackSoundsPath(final CharacterData2c character, final PlayerBattleEntity bent) {
    return this.getBattleSoundsPath(character, bent);
  }

  @Override
  public Path getDragoonTransformSoundsPath(final CharacterData2c character, final PlayerBattleEntity bent) {
    return Loader.resolve(Path.of("SECT/DRGN0.BIN/1324"));
  }

  @Override
  public CompletableFuture<List<FileData>> loadHumanAttackAnimations(final CharacterData2c character, final PlayerBattleEntity bent) {
    return loadDrgnDir(0, 3862);
  }

  @Override
  public CompletableFuture<List<FileData>> loadDragoonAttackAnimations(final CharacterData2c character, final PlayerBattleEntity bent) {
    return loadDrgnDir(0, 3863);
  }

  @Override
  public int getWeaponTrailColour(final CharacterData2c character, final PlayerBattleEntity bent) {
    return 0x90d0f0;
  }

  @Override
  public int getSpellRingColour(final CharacterData2c character, final PlayerBattleEntity bent) {
    return 0x606060;
  }

  @Override
  public int getParticleColour(final CharacterData2c character, final PlayerBattleEntity bent) {
    return 0x969696;
  }

  @Override
  public int getLeftHandModelPart(final CharacterData2c character, final PlayerBattleEntity bent) {
    return 5;
  }

  @Override
  public int getRightHandModelPart(final CharacterData2c character, final PlayerBattleEntity bent) {
    return 6;
  }

  @Override
  public int getFootModelPart(final CharacterData2c character, final PlayerBattleEntity bent) {
    return 8;
  }

  @Override
  public int getWeaponModelPart(final CharacterData2c character, final PlayerBattleEntity bent) {
    return 9;
  }

  @Override
  public int getWeaponTrailVertexComponent(final CharacterData2c character, final PlayerBattleEntity bent) {
    return 2;
  }

  @Override
  public int getShadowSize(final CharacterData2c character, final PlayerBattleEntity bent) {
    return bent.isDragoon() ? 0x1800 : 0x1200;
  }

  @Override
  public int getSpecialTransformStage(final CharacterData2c character, final PlayerBattleEntity bent) {
    return 78;
  }

  @Override
  public int getDragoonTransformDeff(final CharacterData2c character, final PlayerBattleEntity bent) {
    return 33;
  }

  @Override
  public int getDragoonAttackDeff(final CharacterData2c character, final PlayerBattleEntity bent) {
    return 55;
  }

  @Override
  public int getDragoonAttackSounds(final CharacterData2c character, final PlayerBattleEntity bent) {
    return 111;
  }


  @Override
  public Image loadPortrait() {
    return this.portrait.get();
  }

  @Override
  public void renderTransformIcon(final CharacterData2c character, final PlayerBattleEntity bent, final MV transforms, final int frame) {
    final TextureAtlasIcon icon = getTextureAtlas().getIcon(LloydMod.id("transform_" + frame));
    icon.render(transforms);
  }

  @Override
  public Path getBattleModelPath(final CharacterData2c character, final PlayerBattleEntity bent) {
    final String file = bent.isDragoon() ? "3407" : "3406";
    return Path.of("files", "SECT", "DRGN0.BIN", file);
  }

  @Override
  public Path getBattleTexturePath(final CharacterData2c character, final PlayerBattleEntity bent) {
    final String file = bent.isDragoon() ? "270" : "269";
    return Path.of("files", "monsters", file, "textures", "combat");
  }

  @Override
  public Path getBattleSoundsPath(final CharacterData2c character, final PlayerBattleEntity bent) {
    final String file = bent.isDragoon() ? "270" : "269";
    return Path.of("files", "monsters", file, "sounds");
  }

}


