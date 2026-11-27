package pl.skyseas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import pl.skyseas.config.WorldParams;
import pl.skyseas.layout.Layout;
import pl.skyseas.shape.SeabedProvinces;

/**
 * {@code skyseas:seabed_vent}: a short rock chimney on the sea floor with soul sand (bubbles
 * up: a diver's breathing spot) or magma (drags down: a trap) on top and a finished bubble
 * column above it up to the surface, placed as blocks without ticks. In cold water, and in
 * volcanic fields in cold or temperate water. Origin: the floor (skyseas:seabed placement).
 */
public final class VentFeature extends Feature<NoneFeatureConfiguration> {
    private static final double SOUL_SAND_SHARE = 0.6;
    /** Plain cold sea outside volcanic fields keeps only this share of the attempts. */
    private static final double COLD_ONLY_SHARE = 0.35;

    public VentFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        Layout layout = WorldAccess.layout(level);
        BlockPos origin = ctx.origin();
        if (layout == null || !level.getBlockState(origin).is(Blocks.WATER)) {
            return false;
        }
        Holder<Biome> biome = level.getBiome(origin);
        boolean cold = biome.is(Biomes.COLD_OCEAN) || biome.is(Biomes.DEEP_COLD_OCEAN) || biome.is(Biomes.FROZEN_OCEAN)
                || biome.is(Biomes.DEEP_FROZEN_OCEAN);
        boolean temperate = biome.is(Biomes.OCEAN) || biome.is(Biomes.DEEP_OCEAN) || biome.is(Biomes.LUKEWARM_OCEAN)
                || biome.is(Biomes.DEEP_LUKEWARM_OCEAN);
        boolean volcanic = layout.volcanoHeat(origin.getX(), origin.getZ()) >= 0.0
                || SeabedProvinces.weight(WorldParams.DEFAULTS, layout.seabedSeed(), origin.getX(), origin.getZ(),
                SeabedProvinces.VOLCANIC) > 0.5;
        if (!(volcanic ? cold || temperate : cold && ctx.random().nextDouble() < COLD_ONLY_SHARE)) {
            return false;
        }
        int height = 2 + ctx.random().nextInt(4);
        boolean up = ctx.random().nextDouble() < SOUL_SAND_SHARE;
        BlockState wall = volcanic ? Blocks.BASALT.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState();
        BlockPos.MutableBlockPos pos = origin.mutable();
        for (int k = 0; k < height - 1; k++) {
            level.setBlock(pos, wall, 2);
            pos.move(0, 1, 0);
        }
        level.setBlock(pos, up ? Blocks.SOUL_SAND.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
        BlockState column = Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, !up);
        pos.move(0, 1, 0);
        while (pos.getY() < level.getSeaLevel() && level.getBlockState(pos).is(Blocks.WATER)
                && level.getBlockState(pos).getFluidState().isSource()) {
            level.setBlock(pos, column, 2);
            pos.move(0, 1, 0);
        }
        return true;
    }
}
