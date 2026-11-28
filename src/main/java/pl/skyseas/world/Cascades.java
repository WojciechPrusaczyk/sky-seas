package pl.skyseas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Falling water from an island straight down to whatever it meets (usually the sea), as
 * finished fluid states without ticks. No shelves: the user rejected pools in the fall
 * (2026-11-27).
 */
final class Cascades {
    static final BlockState SOURCE = Blocks.WATER.defaultBlockState();
    static final BlockState FLOWING_1 = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1);
    static final BlockState FALLING = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);

    private Cascades() {
    }

    /** Falling water from (x, y, z) down through air. */
    /** Air or a dry block without collision (plants, snow layers): water replaces it instead of stopping on it. */
    static boolean passable(BlockState state) {
        return state.isAir() || !state.blocksMotion() && state.getFluidState().isEmpty();
    }

    static void fall(WorldGenLevel level, int x, int y, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
        for (; y > level.getMinBuildHeight() && passable(level.getBlockState(p.setY(y))); y--) {
            level.setBlock(p, FALLING, 2);
        }
        // Where it lands on ground, no grass under the water.
        if (level.getBlockState(p.setY(y)).is(Blocks.GRASS_BLOCK)) {
            level.setBlock(p, Blocks.DIRT.defaultBlockState(), 2);
        }
    }
}
