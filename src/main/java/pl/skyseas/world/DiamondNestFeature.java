package pl.skyseas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import pl.skyseas.layout.Hash;
import pl.skyseas.layout.Layout;
import pl.skyseas.shape.ShapeMath;

/**
 * {@code skyseas:diamond_nests}: 3-8 diamond ores hidden in the core of hanging spires under
 * sky islands, once per chunk for the spires standing in it. None in the Haven; the chance
 * per spire grows with the distance zone (design: richest at the edge of the world).
 */
public final class DiamondNestFeature extends Feature<NoneFeatureConfiguration> {
    /** Zone factor of the Haven border (about 1500 blocks). */
    private static final double HAVEN = 0.19;
    private static final double CHANCE_MIN = 0.04;
    private static final double CHANCE_MAX = 0.5;
    private static final BlockState DIAMOND = Blocks.DIAMOND_ORE.defaultBlockState();
    private static final BlockState DEEPSLATE_DIAMOND = Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();

    public DiamondNestFeature() {
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
        double[] spires = new double[8 * Layout.SPIRE_FIELDS];
        int n = layout.spiresInChunk(x0, z0, spires);
        boolean placed = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int s = 0; s < n; s++) {
            int o = s * Layout.SPIRE_FIELDS;
            int x = (int) Math.floor(spires[o + Layout.SPIRE_X]);
            int z = (int) Math.floor(spires[o + Layout.SPIRE_Z]);
            double zone = WorldAccess.zone(level, x, z);
            if (zone < HAVEN) {
                continue;
            }
            long h = Hash.hash(layout.seed() ^ 0xD1A40L, x, z);
            double chance = CHANCE_MIN + (CHANCE_MAX - CHANCE_MIN) * ShapeMath.smoothstep(HAVEN, 1.0, zone);
            if (Hash.unit(Hash.hash(h, 1)) >= chance) {
                continue;
            }
            double tip = spires[o + Layout.SPIRE_TOP] - spires[o + Layout.SPIRE_LENGTH];
            int y = (int) Math.floor(tip + spires[o + Layout.SPIRE_LENGTH] * (0.25 + 0.3 * Hash.unit(Hash.hash(h, 2))));
            int count = 3 + (int) (Hash.unit(Hash.hash(h, 3)) * 6);
            // A small random walk from the axis, only through rock: the nest stays inside the spire.
            int cx = x;
            int cy = y;
            int cz = z;
            for (int k = 0; k < count; k++) {
                BlockState state = level.getBlockState(pos.set(cx, cy, cz));
                if (state.is(Blocks.STONE) || state.is(Blocks.TUFF) || state.is(Blocks.ANDESITE)) {
                    level.setBlock(pos, DIAMOND, 2);
                    placed = true;
                } else if (state.is(Blocks.DEEPSLATE)) {
                    level.setBlock(pos, DEEPSLATE_DIAMOND, 2);
                    placed = true;
                }
                int step = (int) (Hash.unit(Hash.hash(h, 10 + k)) * 6);
                cx += step == 0 ? 1 : (step == 1 ? -1 : 0);
                cz += step == 2 ? 1 : (step == 3 ? -1 : 0);
                cy += step == 4 ? 1 : (step == 5 ? -1 : 0);
                // Stay in this chunk (decoration may write only here and in direct neighbours).
                cx = Math.max(x0, Math.min(x0 + 15, cx));
                cz = Math.max(z0, Math.min(z0 + 15, cz));
            }
        }
        return placed;
    }
}
