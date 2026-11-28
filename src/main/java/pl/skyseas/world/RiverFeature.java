package pl.skyseas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import pl.skyseas.layout.Layout;
import pl.skyseas.shape.ContinentShape;

/**
 * {@code skyseas:river}: water in the river channels of sky continents
 * ({@link ContinentShape#riverLevel}), once per chunk at the lakes step. Each channel column
 * is filled with water sources from the river level down to its bed. Where the water would
 * meet open air beside it: a lower river section (a step) gets a small finished fall; the
 * mouth over the cliff becomes a straight waterfall ({@link Cascades}); anywhere else the gap
 * is closed with a bank block. No fluid ticks anywhere.
 */
public final class RiverFeature extends Feature<NoneFeatureConfiguration> {
    /** Deepest water column; a channel column without a bed within it hangs over the cliff. */
    private static final int MAX_DEPTH = 16;
    /** Neighbours within the river's half width plus this of the mouth axis fall instead of getting a bank. */
    private static final double MOUTH_MARGIN = 1.0;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public RiverFeature() {
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
        int[] offs = new int[8];
        int n = layout.continentsInChunk(x0, z0, offs);
        if (n == 0) {
            return false;
        }
        double[] prm = layout.regionAt(x0, z0).params;
        int[] levels = new int[256];
        boolean any = false;
        for (int i = 0; i < 256; i++) {
            double l = level(prm, offs, n, x0 + (i & 15), z0 + (i >> 4));
            levels[i] = Double.isNaN(l) ? Integer.MIN_VALUE : (int) l;
            any |= levels[i] != Integer.MIN_VALUE;
        }
        if (!any) {
            return false;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int i = 0; i < 256; i++) {
            int top = levels[i] - 1;
            if (top == Integer.MIN_VALUE - 1) {
                continue;
            }
            int x = x0 + (i & 15);
            int z = z0 + (i >> 4);
            int bed = bed(level, p, x, top, z);
            if (bed == Integer.MIN_VALUE) {
                continue;
            }
            for (int y = top; y > bed; y--) {
                level.setBlock(p.set(x, y, z), Cascades.SOURCE, 2);
                placed = true;
            }
            // The upper half of a tall plant whose lower half is now water.
            if (level.getBlockState(p.set(x, top + 1, z)).hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && level.getBlockState(p).getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            }
            // No grass under water: dirt, with patches of gravel.
            BlockState floor = level.getBlockState(p.set(x, bed, z));
            if (bed < top && (floor.is(Blocks.GRASS_BLOCK) || floor.is(Blocks.PODZOL) || floor.is(Blocks.MYCELIUM)
                    || floor.is(Blocks.SNOW_BLOCK) || floor.is(Blocks.DIRT_PATH))) {
                boolean gravel = ((x * 31 + z * 17) & 7) < 2 ^ ((x >> 2) + (z >> 2) & 1) == 0;
                level.setBlock(p, gravel ? Blocks.GRAVEL.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 2);
            }
            for (int y = top; y > bed; y--) {
                for (int[] d : DIRS) {
                    int nx = x + d[0];
                    int nz = z + d[1];
                    if (!level.getBlockState(q.set(nx, y, nz)).isAir()) {
                        continue;
                    }
                    double nl = level(prm, offs, n, nx, nz);
                    if (!Double.isNaN(nl) && bed(level, q, nx, (int) nl - 1, nz) == Integer.MIN_VALUE) {
                        nl = Double.NaN; // channel over the cliff: open air
                    }
                    if (!Double.isNaN(nl) && nl <= y) {
                        // A step down to a lower section: a finished little fall, no ticks (a tick would let
                        // the water spread over the grass banks, and flowing water never freezes).
                        if (y == top) {
                            level.setBlock(q.set(nx, y, nz), Cascades.FLOWING_1, 2);
                            Cascades.fall(level, nx, y - 1, nz);
                        }
                    } else if (!Double.isNaN(nl) && nl > y) {
                        // The neighbour is channel too and gets filled by its own column.
                        continue;
                    } else if (y == top && nearMouth(prm, offs, n, nx, nz)) {
                        level.setBlock(q.set(nx, y, nz), Cascades.FLOWING_1, 2);
                        Cascades.fall(level, nx, y - 1, nz);
                    } else {
                        // Rare after the layout checks the banks: a bank block of soil, not a stone wall.
                        BlockState below = level.getBlockState(q.set(nx, y - 1, nz));
                        BlockState bank = below.is(Blocks.STONE) || below.is(Blocks.DEEPSLATE) ? below
                                : level.getBlockState(q.set(nx, y + 1, nz)).isAir() ? Blocks.GRASS_BLOCK.defaultBlockState()
                                : Blocks.DIRT.defaultBlockState();
                        level.setBlock(q.set(nx, y, nz), bank, 2);
                    }
                }
            }
        }
        return placed;
    }

    /** River level at a column (lowest of the continents here), or NaN. */
    private static double level(double[] prm, int[] offs, int n, int x, int z) {
        double level = Double.NaN;
        for (int c = 0; c < n; c++) {
            double l = ContinentShape.riverLevel(prm, offs[c], x + 0.5, z + 0.5);
            if (!Double.isNaN(l) && (Double.isNaN(level) || l < level)) {
                level = l;
            }
        }
        return level;
    }

    /**
     * Highest bed block at or below {@code top} within {@link #MAX_DEPTH}, or MIN_VALUE when it
     * hangs over air. Plants are not bed: features of neighbour chunks can put flowers, ferns or
     * mushrooms in the channel before its water, and the water then replaces them.
     */
    private static int bed(WorldGenLevel level, BlockPos.MutableBlockPos p, int x, int top, int z) {
        for (int y = top; y > top - MAX_DEPTH; y--) {
            if (!Cascades.passable(level.getBlockState(p.set(x, y, z)))) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }


    private static boolean nearMouth(double[] prm, int[] offs, int n, int x, int z) {
        for (int c = 0; c < n; c++) {
            int off = offs[c];
            for (int r = 0, rivers = (int) prm[off + ContinentShape.RIVER_COUNT]; r < rivers; r++) {
                int o = off + ContinentShape.RIVER0 + ContinentShape.RIVER_STRIDE * r;
                int last = o + ContinentShape.RIVER_HEADER + ContinentShape.RIVER_POINT * ((int) prm[o] - 1);
                int prev = last - ContinentShape.RIVER_POINT;
                // Distance to the last segment's axis: the mouth runs through the rim along it.
                double vx = prm[last] - prm[prev];
                double vz = prm[last + 1] - prm[prev + 1];
                double t = Math.max(0.0, Math.min(1.0, ((x + 0.5 - prm[prev]) * vx + (z + 0.5 - prm[prev + 1]) * vz)
                        / (vx * vx + vz * vz)));
                // The whole width falls: a narrower fall left a row of bank blocks in front of the water.
                if (Math.hypot(x + 0.5 - prm[prev] - t * vx, z + 0.5 - prm[prev + 1] - t * vz)
                        < prm[last + 3] + MOUTH_MARGIN) {
                    return true;
                }
            }
        }
        return false;
    }
}
