package additionaladditions;

import legend.game.additions.AdditionRegistryEvent;
import legend.game.characters.CharacterAdditionInfo;
import legend.game.characters.CharacterCreatedEvent;
import org.legendofdragoon.modloader.Mod;
import org.legendofdragoon.modloader.events.EventListener;
import org.legendofdragoon.modloader.events.Priority;
import org.legendofdragoon.modloader.registries.RegistryId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static legend.core.GameEngine.EVENTS;

@Mod(id = AdditionalAdditionsMod.MOD_ID, version = "3.0.0")
public class AdditionalAdditionsMod {
  public static final String MOD_ID = "additional_additions";

  private final CustomAdditionLoader customAdditionLoader = new CustomAdditionLoader();

  private final Map<RegistryId, List<CustomAddition>> additions = new HashMap<>();

  public AdditionalAdditionsMod() {
    EVENTS.register(this);
  }

  public static RegistryId id(final String entryId) {
    return new RegistryId(MOD_ID, entryId);
  }

  @EventListener(priority = Priority.LOWEST)
  public void registerAdditions(final AdditionRegistryEvent event) {
    this.additions.clear();
    this.additions.putAll(this.customAdditionLoader.loadAllAsAdditions());

    for(final RegistryId charId : CustomAdditionLoader.CHAR_IDS) {
      final List<CustomAddition> additions = this.additions.get(charId);

      if(additions != null) {
        for(final CustomAddition addition : additions) {
          event.register(id(charId.entryId() + '_' + addition.id), addition);
        }
      }
    }
  }

  @EventListener
  public void onCharacterCreated(final CharacterCreatedEvent event) {
    final List<CustomAddition> additions = this.additions.get(event.template.getRegistryId());

    if(additions != null) {
      for(int additionIndex = 0; additionIndex < additions.size(); additionIndex++) {
        final CustomAddition addition = additions.get(additionIndex);
        if(event.character.getAdditionInfo(addition.getRegistryId()) == null) {
          final CharacterAdditionInfo info = new CharacterAdditionInfo(List.of());
          info.unlock(event.gameState.timestamp_a0 + additionIndex);
          event.character.addAddition(addition.getRegistryId(), info);
        }
      }
    }
  }
}
