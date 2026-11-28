package pl.skyseas.structure;

import java.util.List;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import pl.skyseas.density.LayoutAccess;
import pl.skyseas.density.SeabedHeightFunction;
import pl.skyseas.world.WorldAccess;

/**
 * Strongholds and mineshafts (spec "Struktury"): vanilla moves them down so their top lies
 * at least 10 blocks under the sea level. In Sky Seas every piece must stay {@link #ROOF}
 * blocks under the seabed over its own footprint instead.
 */
public final class BelowSeabed {
    public static final int ROOF = 12;
    private static final int STEP = 8;

    private BelowSeabed() {
    }

    /**
     * Highest top of the whole structure (bounding box {@code box}) that keeps every piece
     * under the seabed roof, or {@link Integer#MAX_VALUE} outside Sky Seas.
     */
    public static int maxTop(RandomState random, BoundingBox box, List<StructurePiece> pieces) {
        SeabedHeightFunction seabed = LayoutAccess.seabed(random);
        if (seabed == null) {
            return Integer.MAX_VALUE;
        }
        double top = Double.MAX_VALUE;
        for (int i = 0, n = pieces.size(); i < n; i++) {
            BoundingBox b = pieces.get(i).getBoundingBox();
            top = Math.min(top, lowestSeabed(seabed, b) - ROOF + (box.maxY() - b.maxY()));
        }
        return (int) Math.floor(top);
    }

    /** The "sea level" for {@code moveBelowSeaLevel}: it puts the top below seaLevel - amount. */
    public static int seaLevel(int maxTop, int seaLevel, int amount) {
        return maxTop == Integer.MAX_VALUE ? seaLevel : Math.min(seaLevel, maxTop + 1 + amount);
    }

    /** Lowest seabed (ocean land included) over a footprint, sampled every {@link #STEP} blocks and at the far edges. */
    public static double lowestSeabed(SeabedHeightFunction seabed, BoundingBox box) {
        double low = Double.MAX_VALUE;
        for (int x = box.minX(); ; x = Math.min(x + STEP, box.maxX())) {
            for (int z = box.minZ(); ; z = Math.min(z + STEP, box.maxZ())) {
                low = Math.min(low, WorldAccess.seabedHeight(seabed, x, z));
                if (z == box.maxZ()) {
                    break;
                }
            }
            if (x == box.maxX()) {
                break;
            }
        }
        return low;
    }
}
