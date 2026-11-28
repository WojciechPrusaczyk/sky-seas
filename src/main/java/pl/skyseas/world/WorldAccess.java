package pl.skyseas.world;

import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;
import pl.skyseas.density.LayoutAccess;
import pl.skyseas.density.SeabedHeightFunction;
import pl.skyseas.layout.Layout;

/** Layout and seabed height for features and placement modifiers; null outside a Sky Seas world. */
public final class WorldAccess {
    private WorldAccess() {
    }

    public static RandomState randomState(WorldGenLevel level) {
        return level.getLevel().getChunkSource().randomState();
    }

    public static Layout layout(WorldGenLevel level) {
        return LayoutAccess.get(randomState(level));
    }

    public static SeabedHeightFunction seabed(WorldGenLevel level) {
        return LayoutAccess.seabed(randomState(level));
    }

    /** Zone factor (0 at spawn .. 1 at the plateau) at a column, or -1 outside Sky Seas. */
    public static double zone(WorldGenLevel level, int x, int z) {
        var f = LayoutAccess.zone(randomState(level));
        return f == null ? -1.0 : f.compute(new DensityFunction.SinglePointContext(x, 0, z));
    }

    public static double seabedHeight(SeabedHeightFunction seabed, int x, int z) {
        return seabed.compute(new DensityFunction.SinglePointContext(x, 0, z));
    }
}
