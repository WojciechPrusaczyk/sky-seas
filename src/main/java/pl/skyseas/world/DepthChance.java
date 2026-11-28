package pl.skyseas.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * {@code skyseas:depth_chance}: keeps a position with a chance rising from {@code min} at
 * {@code from_y} and above to 1 at {@code to_y} and below (deeper water, more of it).
 */
public final class DepthChance extends PlacementFilter {
    public static final MapCodec<DepthChance> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("from_y").forGetter(m -> m.fromY),
            Codec.INT.fieldOf("to_y").forGetter(m -> m.toY),
            Codec.DOUBLE.fieldOf("min").forGetter(m -> m.min)
    ).apply(i, DepthChance::new));

    private final int fromY;
    private final int toY;
    private final double min;

    public DepthChance(int fromY, int toY, double min) {
        this.fromY = fromY;
        this.toY = toY;
        this.min = min;
    }

    @Override
    protected boolean shouldPlace(PlacementContext ctx, RandomSource random, BlockPos pos) {
        double t = Math.max(0.0, Math.min(1.0, (double) (fromY - pos.getY()) / (fromY - toY)));
        return random.nextDouble() < min + (1.0 - min) * t;
    }

    @Override
    public PlacementModifierType<?> type() {
        return SkySeasWorldgen.DEPTH_CHANCE.get();
    }
}
