package lod.lloydmod;

import legend.game.inventory.SpellRegistryEvent;
import legend.game.inventory.SpellStats0c;
import legend.lodmod.LodMod;
import legend.lodmod.spells.RetailSpell;
import org.legendofdragoon.modloader.registries.Registrar;
import org.legendofdragoon.modloader.registries.RegistryDelegate;

import static legend.core.GameEngine.REGISTRIES;

public class LloydSpells {
  public static final Registrar<SpellStats0c, SpellRegistryEvent> SPELL_REGISTRAR = new Registrar<>(REGISTRIES.spells, LloydMod.MOD_ID);

  public static final RegistryDelegate<RetailSpell> RUNNING_SLASH = SPELL_REGISTRAR.register("running_slash", () -> new RetailSpell(192, 0, 0, 100, 0, 100, 0, 0, LodMod.NO_ELEMENT, 0, 0, 0, 0));
  public static final RegistryDelegate<RetailSpell> SPECTRAL_SWORD = SPELL_REGISTRAR.register("spectral_sword", () -> new RetailSpell(192, 0, 0, 300, 0, 100, 30, 0, LodMod.NO_ELEMENT, 0, 0, 0, 0));
  public static final RegistryDelegate<RetailSpell> SWORD_RUSH = SPELL_REGISTRAR.register("sword_rush", () -> new RetailSpell(192, 0, 0, 200, 0, 100, 0, 0, LodMod.NO_ELEMENT, 0, 0, 0, 0));
  public static final RegistryDelegate<RetailSpell> AERIAL_CRASH = SPELL_REGISTRAR.register("aerial_crash", () -> new RetailSpell(192, 0, 0, 500, 0, 100, 30, 0, LodMod.NO_ELEMENT, 0, 0, 0, 0));
  public static final RegistryDelegate<RetailSpell> BALL_BARRAGE = SPELL_REGISTRAR.register("ball_barrage", () -> new RetailSpell(192, 0, 0, 300, 0, 100, 50, 0, LodMod.NO_ELEMENT, 0, 0, 0, 0));
  public static final RegistryDelegate<RetailSpell> SIGIL_OF_EXPLOSION = SPELL_REGISTRAR.register("sigil_of_explosion", () -> new RetailSpell(200, 0, 0, 700, 0, 100, 70, 0, LodMod.NO_ELEMENT, 0, 0, 0, 0));
  public static final RegistryDelegate<RetailSpell> DRACONIC_IMPALE = SPELL_REGISTRAR.register("draconic_impale", () -> new RetailSpell(192, 0, 0, 1500, 0, 100, 90, 0, LodMod.NO_ELEMENT, 0, 0, 0, 0));

  public static final RegistryDelegate<RetailSpell> TRANS_LIGHT = SPELL_REGISTRAR.register("trans_light", () -> new RetailSpell(0, 0, 0, 0, 0, 0, 0, 0, LodMod.NO_ELEMENT, 0, 0, 0, 0));
  public static final RegistryDelegate<RetailSpell> DANCING_RAY = SPELL_REGISTRAR.register("dancing_ray", () -> new RetailSpell(0, 0, 0, 0, 0, 0, 0, 0, LodMod.NO_ELEMENT, 0, 0, 0, 0));
  public static final RegistryDelegate<RetailSpell> SPINNING_GALE = SPELL_REGISTRAR.register("spinning_gale", () -> new RetailSpell(0, 0, 0, 0, 0, 0, 0, 0, LodMod.NO_ELEMENT, 0, 0, 0, 0));
  public static final RegistryDelegate<RetailSpell> RAVE_TWISTER = SPELL_REGISTRAR.register("rave_twister", () -> new RetailSpell(0, 0, 0, 0, 0, 0, 0, 0, LodMod.NO_ELEMENT, 0, 0, 0, 0));
  public static final RegistryDelegate<RetailSpell> FATAL_BLIZZARD = SPELL_REGISTRAR.register("fatal_blizzard", () -> new RetailSpell(0, 0, 0, 0, 0, 0, 0, 0, LodMod.NO_ELEMENT, 0, 0, 0, 0));

  static void register(final SpellRegistryEvent event) {
    SPELL_REGISTRAR.registryEvent(event);
  }
}
