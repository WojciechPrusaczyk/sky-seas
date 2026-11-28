package pl.skyseas.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * {@code skyseas:zone_count}: repeats each position {@code max(0, lerp(min, max, zone))}
 * times (fraction by chance), zone = distance zone factor of the chunk (0 at spawn, 1 from
 * about 8000 blocks). A negative {@code min} keeps a feature out of the inner zones.
 * Outside Sky Seas worlds the multiplier is 1.
 */
public final class ZoneCount extends PlacementModifier {
    public static final MapCodec<ZoneCount> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("min").forGetter(m -> m.min),
            Codec.DOUBLE.fieldOf("max").forGetter(m -> m.max)
    ).apply(i, ZoneCount::new));

    private final double min;
    private final double max;

    public ZoneCount(double min, double max) {
        this.min = min;
        this.max = max;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext ctx, RandomSource random, BlockPos pos) {
        double zone = WorldAccess.zone(ctx.getLevel(), pos.getX(), pos.getZ());
        double n = zone < 0.0 ? 1.0 : Math.max(0.0, min + (max - min) * zone);
        int count = (int) n + (random.nextDouble() < n - (int) n ? 1 : 0);
        return Stream.generate(() -> pos).limit(count);
    }

    @Override
    public PlacementModifierType<?> type() {
        return SkySeasWorldgen.ZONE_COUNT.get();
    }
}
