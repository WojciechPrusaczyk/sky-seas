package pl.skyseas.world;

import com.mojang.serialization.MapCodec;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import pl.skyseas.layout.Layout;

/**
 * {@code skyseas:island_underside}: the air block just under the underside of a sky island
 * (the ceiling seen from below), searched near the layout bottom; other columns are dropped.
 */
public final class IslandUnderside extends PlacementModifier {
    public static final IslandUnderside INSTANCE = new IslandUnderside();
    public static final MapCodec<IslandUnderside> CODEC = MapCodec.unit(INSTANCE);
    private static final int SEARCH = 6;

    private IslandUnderside() {
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext ctx, RandomSource random, BlockPos pos) {
        WorldGenLevel level = ctx.getLevel();
        Layout layout = WorldAccess.layout(level);
        int x = pos.getX();
        int z = pos.getZ();
        if (layout == null || layout.column(x, z, Layout.EDGE) <= 0.0) {
            return Stream.empty();
        }
        int bottom = (int) Math.floor(layout.column(x, z, Layout.BOTTOM));
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, bottom - SEARCH, z);
        boolean below = level.getBlockState(p).isAir();
        for (int y = bottom - SEARCH + 1; y <= bottom + SEARCH; y++) {
            p.setY(y);
            boolean air = level.getBlockState(p).isAir();
            if (!air && below) {
                return Stream.of(new BlockPos(x, y - 1, z));
            }
            below = air;
        }
        return Stream.empty();
    }

    @Override
    public PlacementModifierType<?> type() {
        return SkySeasWorldgen.ISLAND_UNDERSIDE.get();
    }
}
