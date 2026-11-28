package pl.skyseas.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import pl.skyseas.density.SeabedHeightFunction;
import pl.skyseas.layout.Climate;
import pl.skyseas.layout.Layout;

/**
 * {@code skyseas:layer_filter}: keeps positions in one layer of the world. SKY: inside the
 * vertical reach of a sky island; SEABED: within a few blocks of the seabed under water;
 * OCEAN_ISLAND: on ocean land above the sea; UNDERGROUND: under the seabed (or ocean land)
 * surface. Outside Sky Seas worlds everything passes.
 */
public final class LayerFilter extends PlacementFilter {
    public enum Layer implements StringRepresentable {
        SKY, SEABED, OCEAN_ISLAND, UNDERGROUND;

        public static final Codec<Layer> CODEC = StringRepresentable.fromEnum(Layer::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final MapCodec<LayerFilter> CODEC = Layer.CODEC.fieldOf("layer").xmap(LayerFilter::new, f -> f.layer);
    private static final int SEA_LEVEL = 63;
    private static final double SEABED_BAND = 6.0;

    private final Layer layer;

    public LayerFilter(Layer layer) {
        this.layer = layer;
    }

    @Override
    protected boolean shouldPlace(PlacementContext ctx, RandomSource random, BlockPos pos) {
        Layout layout = WorldAccess.layout(ctx.getLevel());
        SeabedHeightFunction seabed = WorldAccess.seabed(ctx.getLevel());
        if (layout == null || seabed == null) {
            return true;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (layer == Layer.SKY) {
            return Climate.inSky(layout.climateColumn(x, z, Layout.CLIMATE_TYPE), layout.climateColumn(x, z, Layout.CLIMATE_BOTTOM), y);
        }
        double h = WorldAccess.seabedHeight(seabed, x, z);
        return switch (layer) {
            case SEABED -> h < SEA_LEVEL && Math.abs(y - h) <= SEABED_BAND;
            case OCEAN_ISLAND -> h >= SEA_LEVEL && y >= SEA_LEVEL - 1 && y <= h + 32;
            case UNDERGROUND -> y < h - 1.0;
            default -> false;
        };
    }

    @Override
    public PlacementModifierType<?> type() {
        return SkySeasWorldgen.LAYER_FILTER.get();
    }
}
