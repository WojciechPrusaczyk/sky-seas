package pl.skyseas.world;

import com.mojang.serialization.MapCodec;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import pl.skyseas.density.SeabedHeightFunction;

/**
 * {@code skyseas:seabed}: like {@code heightmap OCEAN_FLOOR_WG}, but on the seabed from the
 * layout: under a sky island the heightmap finds the island, so vanilla ocean decoration
 * would never reach the sea floor there. Outside Sky Seas worlds it is the heightmap.
 */
public final class SeabedPlacement extends PlacementModifier {
    public static final SeabedPlacement INSTANCE = new SeabedPlacement();
    public static final MapCodec<SeabedPlacement> CODEC = MapCodec.unit(INSTANCE);
    /** Blocks searched around the seabed height for the actual floor (surface noise, details). */
    private static final int SEARCH = 8;

    private SeabedPlacement() {
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext ctx, RandomSource random, BlockPos pos) {
        WorldGenLevel level = ctx.getLevel();
        SeabedHeightFunction seabed = WorldAccess.seabed(level);
        int x = pos.getX();
        int z = pos.getZ();
        if (seabed == null) {
            return Stream.of(new BlockPos(x, ctx.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z), z));
        }
        int y = floor(level, x, z, WorldAccess.seabedHeight(seabed, x, z));
        return y == Integer.MIN_VALUE ? Stream.empty() : Stream.of(new BlockPos(x, y, z));
    }

    /**
     * First block above the seabed floor (the floor's top solid block + 1, as the heightmap
     * gives it), searched near the layout height, or MIN_VALUE if the column has no floor
     * under water there (ocean land, a shaft).
     */
    public static int floor(WorldGenLevel level, int x, int z, double height) {
        int top = Math.min((int) Math.floor(height) + SEARCH, level.getSeaLevel() - 1);
        int bottom = Math.max((int) Math.floor(height) - SEARCH, level.getMinBuildHeight());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, top, z);
        boolean above = level.getBlockState(p).blocksMotion();
        for (int y = top - 1; y >= bottom; y--) {
            p.setY(y);
            boolean solid = level.getBlockState(p).blocksMotion();
            if (solid && !above) {
                return y + 1;
            }
            above = solid;
        }
        return Integer.MIN_VALUE;
    }

    @Override
    public PlacementModifierType<?> type() {
        return SkySeasWorldgen.SEABED.get();
    }
}
