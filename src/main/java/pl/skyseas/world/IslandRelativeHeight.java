package pl.skyseas.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import pl.skyseas.layout.Layout;

/**
 * {@code skyseas:island_relative_height}: Y between {@code min_depth} and {@code max_depth}
 * blocks under the local top of a sky island (and above its underside); positions outside
 * sky islands are dropped. Islands built per block are not covered.
 */
public final class IslandRelativeHeight extends PlacementModifier {
    public static final MapCodec<IslandRelativeHeight> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            com.mojang.serialization.Codec.INT.fieldOf("min_depth").forGetter(m -> m.minDepth),
            com.mojang.serialization.Codec.INT.fieldOf("max_depth").forGetter(m -> m.maxDepth)
    ).apply(i, IslandRelativeHeight::new));

    private final int minDepth;
    private final int maxDepth;

    public IslandRelativeHeight(int minDepth, int maxDepth) {
        this.minDepth = minDepth;
        this.maxDepth = maxDepth;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext ctx, RandomSource random, BlockPos pos) {
        Layout layout = WorldAccess.layout(ctx.getLevel());
        int depth = minDepth + random.nextInt(maxDepth - minDepth + 1);
        if (layout == null || layout.column(pos.getX(), pos.getZ(), Layout.EDGE) <= 0.0) {
            return Stream.empty();
        }
        int y = (int) Math.floor(layout.column(pos.getX(), pos.getZ(), Layout.TOP)) - depth;
        if (y <= layout.column(pos.getX(), pos.getZ(), Layout.BOTTOM)) {
            return Stream.empty();
        }
        return Stream.of(new BlockPos(pos.getX(), y, pos.getZ()));
    }

    @Override
    public PlacementModifierType<?> type() {
        return SkySeasWorldgen.ISLAND_RELATIVE_HEIGHT.get();
    }
}
