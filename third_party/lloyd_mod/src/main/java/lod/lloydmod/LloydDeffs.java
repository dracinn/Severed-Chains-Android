package lod.lloydmod;

import legend.game.combat.deff.DeffPackage;
import legend.game.combat.deff.RegisterDeffsEvent;
import lod.lloydmod.spelldeffs.AerialCrash;
import lod.lloydmod.spelldeffs.BallBarrage;
import lod.lloydmod.spelldeffs.DraconicImpact;
import lod.lloydmod.spelldeffs.RunningSlash;
import lod.lloydmod.spelldeffs.SigilofExplosion;
import lod.lloydmod.spelldeffs.SpectralSword;
import lod.lloydmod.spelldeffs.SwordRush;
import org.legendofdragoon.modloader.registries.Registrar;
import org.legendofdragoon.modloader.registries.RegistryDelegate;

import static legend.core.GameEngine.REGISTRIES;

public  class LloydDeffs {
  private static final Registrar<DeffPackage, RegisterDeffsEvent> DEFF_REGISTRAR = new Registrar<>(REGISTRIES.deff, LloydMod.MOD_ID);

  public static final RegistryDelegate<DeffPackage> RUNNING_SLASH_DEFF = DEFF_REGISTRAR.register("running_slash_deff", RunningSlash::new);
  public static final RegistryDelegate<DeffPackage> SPECTRAL_SWORD_DEFF = DEFF_REGISTRAR.register("spectral_sword_deff", SpectralSword::new);
  public static final RegistryDelegate<DeffPackage> SWORD_RUSH_DEFF = DEFF_REGISTRAR.register("sword_rush_deff", SwordRush::new);
  public static final RegistryDelegate<DeffPackage> AERIAL_CRASH_DEFF = DEFF_REGISTRAR.register("aerial_crash_deff", AerialCrash::new);
  public static final RegistryDelegate<DeffPackage> BALL_BARRAGE_DEFF = DEFF_REGISTRAR.register("ball_barrage_deff", BallBarrage::new);
  public static final RegistryDelegate<DeffPackage> SIGIL_OF_EXPLOSION_DEFF = DEFF_REGISTRAR.register("sigil_of_explosion_deff", SigilofExplosion::new);
  public static final RegistryDelegate<DeffPackage> DRACONIC_IMPACT_DEFF = DEFF_REGISTRAR.register("draconic_impact_deff", DraconicImpact::new);

  static void register(final RegisterDeffsEvent event) {
    DEFF_REGISTRAR.registryEvent(event);
  }
}
