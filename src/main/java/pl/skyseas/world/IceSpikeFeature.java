package pl.skyseas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelWriter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * {@code skyseas:ice_spike}, used by {@code minecraft:ice_spike} (data override): the vanilla
 * spike, but no ice in air under the terrain surface. Vanilla runs the spike's root down
 * through air to Y 50, which under a sky island is a pillar of packed ice to the ocean.
 */
public final class IceSpikeFeature extends net.minecraft.world.level.levelgen.feature.IceSpikeFeature {
    public IceSpikeFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    protected void setBlock(LevelWriter writer, BlockPos pos, BlockState state) {
        // The spike body is placed bottom up, so the surface is never above a block of it still to come.
        if (writer instanceof WorldGenLevel level && level.isEmptyBlock(pos)
                && pos.getY() < level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, pos.getX(), pos.getZ())) {
            return;
        }
        super.setBlock(writer, pos, state);
    }
}
