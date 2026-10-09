package legend.game.modding.events.characters;

import legend.game.characters.CharacterData2c;
import org.legendofdragoon.modloader.events.CancelableEvent;

/// Called when characters are copied (i.e. Lavitz -> Albert, Shana -> Miranda)
public class CloneCharacterEvent extends CancelableEvent {
  /// The character whose stats are being copied
  public CharacterData2c sourceCharacter;
  /// The character whose stats are being set to the stats of the other character
  public CharacterData2c destinationCharacter;

  public CloneCharacterEvent(final CharacterData2c sourceCharacter, final CharacterData2c destinationCharacter) {
    this.sourceCharacter = sourceCharacter;
    this.destinationCharacter = destinationCharacter;
  }
}
