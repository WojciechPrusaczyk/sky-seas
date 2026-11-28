package pl.skyseas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.util.RandomSource;

/**
 * {@code skyseas:frozen_sea}: in frozen oceans, once per chunk: an ice ceiling (packed ice
 * 3 blocks thick under the surface) with an air pocket under it to breathe in, or an iceberg
 * root: a tapering packed-ice body (blue ice core) hanging 20-45 blocks into the water under
 * a small cap. Everything stays inside the chunk.
 */
public final class IceFeature extends Feature<NoneFeatureConfiguration> {
    private static final double CEILING_CHANCE = 0.35;
    private static final double ROOT_CHANCE = 0.12;
    private static final BlockState PACKED = Blocks.PACKED_ICE.defaultBlockState();
    private static final BlockState BLUE = Blocks.BLUE_ICE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    public IceFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        if (WorldAccess.layout(level) == null) {
            return false;
        }
        int x0 = ctx.origin().getX() & ~15;
        int z0 = ctx.origin().getZ() & ~15;
        int sea = level.getSeaLevel() - 1;
        Holder<Biome> biome = level.getBiome(new BlockPos(x0 + 8, sea, z0 + 8));
        if (!biome.is(Biomes.FROZEN_OCEAN) && !biome.is(Biomes.DEEP_FROZEN_OCEAN)) {
            return false;
        }
        RandomSource random = ctx.random();
        double roll = random.nextDouble();
        if (roll < ROOT_CHANCE) {
            return root(level, random, x0, z0, sea);
        }
        return roll < ROOT_CHANCE + CEILING_CHANCE && ceiling(level, random, x0, z0, sea);
    }

    private static boolean ceiling(WorldGenLevel level, RandomSource random, int x0, int z0, int sea) {
        int cx = x0 + 6 + random.nextInt(4);
        int cz = z0 + 6 + random.nextInt(4);
        double radius = 5.0 + random.nextDouble() * 2.5;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                double d = Math.hypot(x - cx, z - cz) + random.nextDouble() * 1.5;
                if (d > radius) {
                    continue;
                }
                for (int y = sea - 2; y <= sea; y++) {
                    if (level.getBlockState(p.set(x, y, z)).is(Blocks.WATER) || level.getBlockState(p).is(Blocks.ICE)) {
                        level.setBlock(p, PACKED, 2);
                        placed = true;
                    }
                }
                // Air pocket: the two lower ice layers in the middle become air over the water.
                if (d < 2.0 && level.getBlockState(p.set(x, sea - 3, z)).is(Blocks.WATER)) {
                    level.setBlock(p.set(x, sea - 2, z), AIR, 2);
                    level.setBlock(p.set(x, sea - 1, z), AIR, 2);
                }
            }
        }
        return placed;
    }

    private static boolean root(WorldGenLevel level, RandomSource random, int x0, int z0, int sea) {
        int cx = x0 + 8;
        int cz = z0 + 8;
        double radius = 4.0 + random.nextDouble() * 3.0;
        int depth = 20 + random.nextInt(26);
        int cap = 3 + random.nextInt(6);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int y = sea + cap; y >= sea - depth; y--) {
            double t = y > sea ? (double) (y - sea) / (cap + 1) : (double) (sea - y) / depth;
            double r = radius * (1.0 - t * (y > sea ? 0.8 : 0.85));
            for (int x = x0; x < x0 + 16; x++) {
                for (int z = z0; z < z0 + 16; z++) {
                    double d = Math.hypot(x - cx, z - cz);
                    if (d > r + random.nextDouble() * 0.8) {
                        continue;
                    }
                    BlockState state = level.getBlockState(p.set(x, y, z));
                    if (state.is(Blocks.WATER) || state.isAir() || state.is(Blocks.ICE)) {
                        level.setBlock(p, d < r * 0.4 ? BLUE : PACKED, 2);
                        placed = true;
                    }
                }
            }
        }
        return placed;
    }
}
