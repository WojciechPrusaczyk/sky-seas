package pl.skyseas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import pl.skyseas.config.WorldParams;
import pl.skyseas.density.SeabedHeightFunction;
import pl.skyseas.layout.Hash;
import pl.skyseas.layout.Layout;
import pl.skyseas.shape.SeabedProvinces;
import pl.skyseas.shape.SimplexNoise;

/**
 * {@code skyseas:seabed_materials}: the top blocks of the sea floor by seabed region, once per
 * chunk at the start of decoration (decision 2026-11-27: a feature, not a surface rule).
 * Volcanic fields: basalt and blackstone, magma at cone vents. Rocky wastes and steep walls
 * (canyons, scarps, pillars): bare rock. Elsewhere below Y 40 outside warm seas: patches of
 * bare deepslate, tuff or basalt, so ores show. Warm shallows keep vanilla sand.
 */
public final class SeabedMaterialsFeature extends Feature<NoneFeatureConfiguration> {
    private static final int DEPTH = 3;
    private static final int SEA_LEVEL = 63;
    private static final double BARE_MAX_Y = 40.0;
    /** Height difference to a neighbour column that counts as a wall. */
    private static final double STEEP = 2.5;
    private static final BlockState BASALT = Blocks.BASALT.defaultBlockState();
    private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockState MAGMA = Blocks.MAGMA_BLOCK.defaultBlockState();
    private static final BlockState TUFF = Blocks.TUFF.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();

    public SeabedMaterialsFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        Layout layout = WorldAccess.layout(level);
        SeabedHeightFunction seabed = WorldAccess.seabed(level);
        if (layout == null || seabed == null) {
            return false;
        }
        WorldParams p = WorldParams.DEFAULTS;
        long seed = layout.seabedSeed();
        int x0 = ctx.origin().getX() & ~15;
        int z0 = ctx.origin().getZ() & ~15;
        double[] h = new double[18 * 18];
        for (int i = 0; i < 18; i++) {
            for (int j = 0; j < 18; j++) {
                h[i * 18 + j] = WorldAccess.seabedHeight(seabed, x0 - 1 + i, z0 - 1 + j);
            }
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean changed = false;
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int x = x0 + i;
                int z = z0 + j;
                double hc = h[(i + 1) * 18 + j + 1];
                if (hc >= SEA_LEVEL - 2) {
                    continue;
                }
                int top = SeabedPlacement.floor(level, x, z, hc) - 1;
                if (top < level.getMinBuildHeight()) {
                    continue;
                }
                double slope = Math.max(Math.max(Math.abs(hc - h[i * 18 + j + 1]), Math.abs(hc - h[(i + 2) * 18 + j + 1])),
                        Math.max(Math.abs(hc - h[(i + 1) * 18 + j]), Math.abs(hc - h[(i + 1) * 18 + j + 2])));
                BlockState state = material(p, seed, layout, level, pos.set(x, top, z), x, top, z, slope);
                if (state == null) {
                    continue;
                }
                for (int d = 0; d < DEPTH; d++) {
                    pos.set(x, top - d, z);
                    if (!level.getBlockState(pos).blocksMotion()) {
                        break;
                    }
                    BlockState s = d == 0 || state != MAGMA ? state : BASALT;
                    level.setBlock(pos, s, 2);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** Material of the floor at a column, or null to keep the vanilla surface. */
    private static BlockState material(WorldParams p, long seed, Layout layout, WorldGenLevel level, BlockPos pos, int x, int y, int z,
                                       double slope) {
        double volcano = layout.volcanoHeat(x, z);
        double volcanic = volcano >= 0.0 ? 1.0 : SeabedProvinces.weight(p, seed, x, z, SeabedProvinces.VOLCANIC);
        double rocky = SeabedProvinces.weight(p, seed, x, z, SeabedProvinces.ROCKY);
        double n = SimplexNoise.noise2(seed + 71, x / 24.0, z / 24.0);
        double jitter = 0.12 * SimplexNoise.noise2(seed + 72, x / 9.0, z / 9.0);
        if (volcanic + jitter > 0.5) {
            double heat = Math.max(volcano, SeabedProvinces.coneHeat(p, seed, x, z));
            if (heat > 0.55 && Hash.unit(Hash.hash(seed + 73, x, z)) < 0.35 + 0.65 * heat) {
                return MAGMA;
            }
            return n > 0.35 ? BLACKSTONE : BASALT;
        }
        boolean warm = level.getBiome(pos).is(Biomes.WARM_OCEAN);
        if (rocky + jitter > 0.5 || slope > STEEP) {
            return bareRock(y, n, warm);
        }
        if (!warm && y < BARE_MAX_Y && SimplexNoise.noise2(seed + 74, x / 70.0, z / 70.0) + jitter > 0.3) {
            return n > 0.45 ? BASALT : (n > -0.1 ? TUFF : DEEPSLATE);
        }
        return null;
    }

    private static BlockState bareRock(int y, double n, boolean warm) {
        if (warm) {
            return n > 0.0 ? ANDESITE : STONE;
        }
        if (n > 0.3) {
            return TUFF;
        }
        return y < 0 ? DEEPSLATE : (n > -0.3 ? STONE : ANDESITE);
    }
}
