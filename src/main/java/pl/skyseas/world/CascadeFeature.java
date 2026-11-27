package pl.skyseas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import pl.skyseas.layout.Hash;
import pl.skyseas.layout.Layout;
import pl.skyseas.layout.RegionLayout;

/**
 * {@code skyseas:cascade}: waterfalls from the rim of large sky islands, once per chunk with a
 * chance. A source sits in a notch cut into the rim, the next block out holds flowing water
 * and below it falls down to whatever it meets (usually the sea). All as
 * finished fluid states, without fluid ticks; the states match what vanilla flow would give,
 * so a later update keeps the fall as it is.
 */
public final class CascadeFeature extends Feature<NoneFeatureConfiguration> {
    private static final double CHANCE = 0.125;
    private static final int DROP = 12;
    /** Sky forms at least this large (layout radius, blocks) get waterfalls; continents always. */
    static final double MIN_RADIUS = 40.0;
    private static final BlockState SOURCE = Blocks.WATER.defaultBlockState();
    private static final BlockState FLOWING = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1);
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public CascadeFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        Layout layout = WorldAccess.layout(level);
        if (layout == null) {
            return false;
        }
        int x0 = ctx.origin().getX() & ~15;
        int z0 = ctx.origin().getZ() & ~15;
        long h = Hash.hash(layout.seed() ^ 0xCA5CADEL, x0 >> 4, z0 >> 4);
        if (Hash.unit(Hash.hash(h, 1)) >= CHANCE || !largeIsland(layout, x0 + 8, z0 + 8)) {
            return false;
        }
        // Rim columns: a sky surface with a drop of at least DROP blocks next to it; not on the
        // chunk border, so the notch and the fall stay in this chunk.
        int start = (int) (Hash.unit(Hash.hash(h, 2)) * 196);
        for (int k = 0; k < 196; k++) {
            int c = (start + k) % 196;
            int x = x0 + 1 + c / 14;
            int z = z0 + 1 + c % 14;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (top < level.getSeaLevel() + 20) {
                continue;
            }
            for (int[] d : DIRS) {
                if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + d[0], z + d[1]) - 1 < top - DROP
                        && place(level, x, z, top, d)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean largeIsland(Layout layout, int x, int z) {
        RegionLayout r = layout.regionAt(x, z);
        int i = layout.nearestIsland(r, x, z);
        if (i < 0) {
            return false;
        }
        return r.type[i] == RegionLayout.CONTINENT || r.radius[i] >= MIN_RADIUS;
    }

    /** Notch at x, z in the surface block at top, fall from the column outward in direction d. */
    private static boolean place(WorldGenLevel level, int x, int z, int top, int[] d) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int ox = x + d[0];
        int oz = z + d[1];
        // The outward column must be open at the notch level and just below it.
        if (!level.getBlockState(p.set(ox, top, oz)).isAir() || !level.getBlockState(p.set(ox, top - 1, oz)).isAir()) {
            return false;
        }
        // The notch holds water on its other sides and bottom.
        if (!level.getBlockState(p.set(x, top - 1, z)).isSolid()) {
            return false;
        }
        // Open sides of the notch (ragged rim) get a lip of the rock under the notch.
        BlockState lip = level.getBlockState(p.set(x, top - 1, z));
        for (int[] e : DIRS) {
            if ((e[0] != d[0] || e[1] != d[1]) && !level.getBlockState(p.set(x + e[0], top, z + e[1])).isSolid()) {
                if (!level.getBlockState(p.set(x + e[0], top - 1, z + e[1])).isSolid()) {
                    return false;
                }
                level.setBlock(p.set(x + e[0], top, z + e[1]), lip, 2);
            }
        }
        level.setBlock(p.set(x, top + 1, z), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(p.set(x, top, z), SOURCE, 2);
        level.setBlock(p.set(ox, top, oz), FLOWING, 2);
        Cascades.fall(level, ox, top - 1, oz);
        return true;
    }
}
