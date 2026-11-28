package pl.skyseas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.TallSeagrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.CountConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.ProbabilityFeatureConfiguration;
import pl.skyseas.density.SeabedHeightFunction;

/**
 * Vanilla kelp, seagrass and sea pickle (same logic and random calls), but on the seabed
 * from the layout: the vanilla features look up the OCEAN_FLOOR heightmap themselves, which
 * under a sky island is the island. Outside Sky Seas worlds they use the heightmap.
 */
public final class SeabedPlants {
    private SeabedPlants() {
    }

    /** Block above the floor of a column (like the OCEAN_FLOOR heightmap), or MIN_VALUE. */
    static int floor(WorldGenLevel level, int x, int z) {
        SeabedHeightFunction seabed = WorldAccess.seabed(level);
        if (seabed == null) {
            return level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
        }
        return SeabedPlacement.floor(level, x, z, WorldAccess.seabedHeight(seabed, x, z));
    }

    public static final class Kelp extends Feature<NoneFeatureConfiguration> {
        public Kelp() {
            super(NoneFeatureConfiguration.CODEC);
        }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
            int placed = 0;
            WorldGenLevel level = ctx.level();
            BlockPos origin = ctx.origin();
            RandomSource random = ctx.random();
            int y = floor(level, origin.getX(), origin.getZ());
            if (y == Integer.MIN_VALUE) {
                return false;
            }
            BlockPos pos = new BlockPos(origin.getX(), y, origin.getZ());
            if (level.getBlockState(pos).is(Blocks.WATER)) {
                BlockState top = Blocks.KELP.defaultBlockState();
                BlockState plant = Blocks.KELP_PLANT.defaultBlockState();
                int height = 1 + random.nextInt(10);
                for (int l = 0; l <= height; l++) {
                    if (level.getBlockState(pos).is(Blocks.WATER) && level.getBlockState(pos.above()).is(Blocks.WATER)
                            && plant.canSurvive(level, pos)) {
                        if (l == height) {
                            level.setBlock(pos, top.setValue(KelpBlock.AGE, random.nextInt(4) + 20), 2);
                            placed++;
                        } else {
                            level.setBlock(pos, plant, 2);
                        }
                    } else if (l > 0) {
                        BlockPos below = pos.below();
                        if (top.canSurvive(level, below) && !level.getBlockState(below.below()).is(Blocks.KELP)) {
                            level.setBlock(below, top.setValue(KelpBlock.AGE, random.nextInt(4) + 20), 2);
                            placed++;
                        }
                        break;
                    }
                    pos = pos.above();
                }
            }
            return placed > 0;
        }
    }

    public static final class Seagrass extends Feature<ProbabilityFeatureConfiguration> {
        public Seagrass() {
            super(ProbabilityFeatureConfiguration.CODEC);
        }

        @Override
        public boolean place(FeaturePlaceContext<ProbabilityFeatureConfiguration> ctx) {
            RandomSource random = ctx.random();
            WorldGenLevel level = ctx.level();
            BlockPos origin = ctx.origin();
            int dx = random.nextInt(8) - random.nextInt(8);
            int dz = random.nextInt(8) - random.nextInt(8);
            int y = floor(level, origin.getX() + dx, origin.getZ() + dz);
            if (y == Integer.MIN_VALUE) {
                return false;
            }
            BlockPos pos = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
            if (!level.getBlockState(pos).is(Blocks.WATER)) {
                return false;
            }
            boolean tall = random.nextDouble() < ctx.config().probability;
            BlockState state = tall ? Blocks.TALL_SEAGRASS.defaultBlockState() : Blocks.SEAGRASS.defaultBlockState();
            if (!state.canSurvive(level, pos)) {
                return false;
            }
            if (tall) {
                BlockPos up = pos.above();
                if (level.getBlockState(up).is(Blocks.WATER)) {
                    level.setBlock(pos, state, 2);
                    level.setBlock(up, state.setValue(TallSeagrassBlock.HALF, DoubleBlockHalf.UPPER), 2);
                }
            } else {
                level.setBlock(pos, state, 2);
            }
            return true;
        }
    }

    public static final class SeaPickle extends Feature<CountConfiguration> {
        public SeaPickle() {
            super(CountConfiguration.CODEC);
        }

        @Override
        public boolean place(FeaturePlaceContext<CountConfiguration> ctx) {
            int placed = 0;
            RandomSource random = ctx.random();
            WorldGenLevel level = ctx.level();
            BlockPos origin = ctx.origin();
            int count = ctx.config().count().sample(random);
            for (int k = 0; k < count; k++) {
                int dx = random.nextInt(8) - random.nextInt(8);
                int dz = random.nextInt(8) - random.nextInt(8);
                int y = floor(level, origin.getX() + dx, origin.getZ() + dz);
                BlockState state = Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, random.nextInt(4) + 1);
                if (y == Integer.MIN_VALUE) {
                    continue;
                }
                BlockPos pos = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
                if (level.getBlockState(pos).is(Blocks.WATER) && state.canSurvive(level, pos)) {
                    level.setBlock(pos, state, 2);
                    placed++;
                }
            }
            return placed > 0;
        }
    }
}
