package pl.skyseas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Falling water from an island down to whatever it meets (usually the sea), with shelves
 * every 20-30 blocks: a small rock cup holding a 3x3 pool (air above its corners to
 * breathe when swimming up), spilling out on one side into the next fall. Finished fluid
 * states without ticks. Shelves alternate their outlet side, so the fall zigzags in place
 * instead of drifting away (at most 5 blocks from the start).
 */
final class Cascades {
    static final BlockState SOURCE = Blocks.WATER.defaultBlockState();
    static final BlockState FLOWING_1 = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1);
    static final BlockState FLOWING_2 = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 2);
    static final BlockState FALLING = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);
    private static final int SHELF_MIN = 20;
    private static final int SHELF_SPREAD = 11;
    /** No shelf this close above the sea. */
    private static final int SHELF_SEA_MARGIN = 10;

    private Cascades() {
    }

    /** Falling water from (x, y, z) down; {@code ox, oz} is the outward direction (unit axis step). */
    static void fall(WorldGenLevel level, RandomSource random, int x, int y, int z, int ox, int oz) {
        BlockState rock = Blocks.STONE.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int next = y - SHELF_MIN - random.nextInt(SHELF_SPREAD);
        int sea = level.getSeaLevel();
        while (y > level.getMinBuildHeight()) {
            if (!level.getBlockState(p.set(x, y, z)).isAir()) {
                return;
            }
            if (y <= next && y > sea + SHELF_SEA_MARGIN && shelfFits(level, p, x, y, z, ox, oz)) {
                shelf(level, p, rock, x, y, z, ox, oz);
                x += 3 * ox;
                z += 3 * oz;
                ox = -ox;
                oz = -oz;
                y--;
                next = y - SHELF_MIN - random.nextInt(SHELF_SPREAD);
                continue;
            }
            level.setBlock(p, FALLING, 2);
            y--;
        }
    }

    private static boolean shelfFits(WorldGenLevel level, BlockPos.MutableBlockPos p, int x, int y, int z, int ox, int oz) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!level.getBlockState(p.set(x + dx, y, z + dz)).isAir()
                        || !level.getBlockState(p.set(x + dx, y - 1, z + dz)).isAir()) {
                    return false;
                }
            }
        }
        return level.getBlockState(p.set(x + 3 * ox, y, z + 3 * oz)).isAir()
                && level.getBlockState(p.set(x + 3 * ox, y - 1, z + 3 * oz)).isAir();
    }

    /** Cup: 5x5 floor, rim around a 3x3 pool, a gap in the rim towards ox, oz. */
    private static void shelf(WorldGenLevel level, BlockPos.MutableBlockPos p, BlockState rock, int x, int y, int z, int ox, int oz) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlock(p.set(x + dx, y - 1, z + dz), rock, 2);
                boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                level.setBlock(p.set(x + dx, y, z + dz), rim ? rock : SOURCE, 2);
            }
        }
        level.setBlock(p.set(x + 2 * ox, y, z + 2 * oz), FLOWING_1, 2);
        level.setBlock(p.set(x + 3 * ox, y, z + 3 * oz), FLOWING_2, 2);
    }
}
