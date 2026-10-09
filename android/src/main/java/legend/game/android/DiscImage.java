package legend.game.android;

import legend.game.unpacker.IsoReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Identifies a Legend of Dragoon disc image using the same ISO9660 primary
 * volume descriptor check as upstream {@code Unpacker.getIsoReader}: the PVD
 * at sector 16 must carry CD001/PLAYSTATION, and the 32-byte volume id is
 * matched against the retail disc serials.
 */
public final class DiscImage {
  private DiscImage() { }

  public enum Kind {
    /** US release - the only region the upstream unpacker accepts */
    US,
    /** A real LoD disc for a region upstream reports as wrong region */
    OTHER_REGION,
    /** Not a usable disc image (or unreadable / mid-copy) */
    OTHER,
  }

  /** disc is 1-4 for US/OTHER_REGION, 0 for OTHER */
  public record Info(Kind kind, int disc, String region, String volumeId) { }

  // Serial tables mirror Unpacker.DISK_IDS / OTHER_REGION_IDS (upstream keeps
  // them private; the retail disc set can't change).
  private static final String[] US_IDS = {"SCUS94491", "SCUS94584", "SCUS94585", "SCUS94586"};
  private static final String[][] REGION_IDS = {
    {"SCES03043", "SCES13043", "SCES23043", "SCES33043"},
    {"SCES03044", "SCES13044", "SCES23044", "SCES33044"},
    {"SCES03045", "SCES13045", "SCES23045", "SCES33045"},
    {"SCES03046", "SCES13046", "SCES23046", "SCES33046"},
    {"SCES03047", "SCES13047", "SCES23047", "SCES33047"},
    {"SCPS10119", "SCPS10120", "SCPS10121", "SCPS10122"},
    {"SCPS45461", "SCPS45462", "SCPS45463", "SCPS45464"},
  };
  private static final String[] REGION_NAMES = {"Europe", "France", "Germany", "Italy", "Spain", "Japan", "Asia"};

  private static final int PVD_SECTOR = 16; // Unpacker.PVD_SECTOR (private upstream)

  public static Info detect(final Path path) {
    try {
      if(Files.size(path) < (PVD_SECTOR + 1L) * IsoReader.SECTOR_SIZE) {
        return new Info(Kind.OTHER, 0, null, null);
      }

      final IsoReader reader;
      try {
        reader = new IsoReader(path);
      } catch(final IOException e) {
        return new Info(Kind.OTHER, 0, null, null);
      }

      try {
        final byte[] sectorData = new byte[0x800];
        final ByteBuffer sectorBuffer = ByteBuffer.wrap(sectorData).order(ByteOrder.LITTLE_ENDIAN);

        reader.seekSector(PVD_SECTOR);
        reader.advance(IsoReader.SYNC_PATTER_SIZE);
        reader.read(sectorData);

        if(sectorBuffer.get() != 1 || !"CD001".equals(readString(sectorBuffer, 5)) || sectorBuffer.get() != 0x1 || !"PLAYSTATION".equals(readString(sectorBuffer, 32).trim())) {
          return new Info(Kind.OTHER, 0, null, null);
        }

        final String id = readString(sectorBuffer, 32).trim();

        for(int i = 0; i < US_IDS.length; i++) {
          if(US_IDS[i].equals(id)) {
            return new Info(Kind.US, i + 1, null, id);
          }
        }

        for(int region = 0; region < REGION_IDS.length; region++) {
          for(int disc = 0; disc < REGION_IDS[region].length; disc++) {
            if(REGION_IDS[region][disc].equals(id)) {
              return new Info(Kind.OTHER_REGION, disc + 1, REGION_NAMES[region], id);
            }
          }
        }

        return new Info(Kind.OTHER, 0, null, id);
      } finally {
        reader.close();
      }
    } catch(final IOException | RuntimeException e) {
      return new Info(Kind.OTHER, 0, null, null);
    }
  }

  private static String readString(final ByteBuffer buffer, final int length) {
    final byte[] chars = new byte[length];
    buffer.get(chars);
    return new String(chars, StandardCharsets.US_ASCII);
  }
}
