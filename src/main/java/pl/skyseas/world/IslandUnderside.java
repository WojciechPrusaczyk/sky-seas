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
import pl.skyseas.layout.RegionLayout;

/**
 * {@code skyseas:island_underside}: the air block just under the underside of a sky island
 * (the ceiling seen from below), searched near the layout bottom; other columns are dropped.
 * With {@code small_islands_only}, only under small sky forms and Hallelujah forms.
 */
public final class IslandUnderside extends PlacementModifier {
    public static final MapCodec<IslandUnderside> CODEC = com.mojang.serialization.Codec.BOOL
            .optionalFieldOf("small_islands_only", false).xmap(IslandUnderside::new, m -> m.smallOnly);
    private static final int SEARCH = 6;

    /**
     * Only under Hallelujah forms and sky forms smaller than the waterfall size: things that
     * grow or glow (random ticks) stay off the large islands players build under.
     */
    private final boolean smallOnly;

    public IslandUnderside(boolean smallOnly) {
        this.smallOnly = smallOnly;
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
        if (smallOnly && !small(layout, x, z)) {
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

    private static boolean small(Layout layout, int x, int z) {
        RegionLayout r = layout.regionAt(x, z);
        int i = layout.nearestIsland(r, x, z);
        return i >= 0 && (r.type[i] == RegionLayout.HALLELUJAH
                || r.type[i] != RegionLayout.CONTINENT && r.radius[i] < CascadeFeature.MIN_RADIUS);
    }

    @Override
    public PlacementModifierType<?> type() {
        return SkySeasWorldgen.ISLAND_UNDERSIDE.get();
    }
}
