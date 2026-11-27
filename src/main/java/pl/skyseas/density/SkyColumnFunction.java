package pl.skyseas.density;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import pl.skyseas.layout.Layout;

/**
 * 2D column values of the nearest sky island, one registered type per {@link Kind}:
 * {@code skyseas:sky_edge}, {@code sky_top}, {@code sky_bottom}, {@code sky_detail} (weight of the
 * 3D detail noise) and {@code sky_spire_floor} (lowest per-block detail near the column: spire tips, rock bridges) and {@code sky_detail_ceiling} (highest).
 * Wrap in flat_cache. The {@code root} argument is {@code skyseas:layout/root}.
 */
public record SkyColumnFunction(Kind kind, DensityFunction root, Layout layout) implements DensityFunction {

    public enum Kind {
        EDGE("sky_edge", Layout.EDGE, Layout.NO_EDGE, 2000.0),
        TOP("sky_top", Layout.TOP, Layout.NO_TOP, 320.0),
        BOTTOM("sky_bottom", Layout.BOTTOM, 0.0, Layout.NO_BOTTOM),
        DETAIL("sky_detail", Layout.DETAIL, 0.0, 1.0),
        SPIRE_FLOOR("sky_spire_floor", -1, -64.0, Layout.NO_SPIRE),
        DETAIL_CEILING("sky_detail_ceiling", -2, Layout.NO_TOP, 400.0),
        CLIMATE_TYPE("sky_climate_type", -10 - Layout.CLIMATE_TYPE, Layout.NO_CLIMATE, 20.0),
        CLIMATE_BOTTOM("sky_climate_bottom", -10 - Layout.CLIMATE_BOTTOM, -64.0, Layout.NO_BOTTOM),
        CLIMATE_TOP("sky_climate_top", -10 - Layout.CLIMATE_TOP, Layout.NO_TOP, 400.0),
        CLIMATE_TEMPERATURE("sky_climate_temperature", -10 - Layout.CLIMATE_TEMPERATURE, Layout.NO_CLIMATE, 2.0),
        CLIMATE_VEGETATION("sky_climate_vegetation", -10 - Layout.CLIMATE_VEGETATION, Layout.NO_CLIMATE, 2.0),
        CLIMATE_EROSION("sky_climate_erosion", -10 - Layout.CLIMATE_EROSION, Layout.NO_CLIMATE, 2.0),
        CLIMATE_MOUNTAIN("sky_climate_mountain", -10 - Layout.CLIMATE_MOUNTAIN, 0.0, 400.0),
        WARM_BIAS("warm_bias", -3, 0.0, 1.0);

        public final String id;
        final int column;
        final double min;
        final double max;
        public final MapCodec<SkyColumnFunction> dataCodec;
        final KeyDispatchDataCodec<SkyColumnFunction> codec;

        Kind(String id, int column, double min, double max) {
            this.id = id;
            this.column = column;
            this.min = min;
            this.max = max;
            this.dataCodec = DensityFunction.HOLDER_HELPER_CODEC.fieldOf("root")
                    .xmap(r -> SkyColumnFunction.of(this, r), SkyColumnFunction::root);
            this.codec = KeyDispatchDataCodec.of(dataCodec);
        }
    }

    static SkyColumnFunction of(Kind kind, DensityFunction root) {
        return new SkyColumnFunction(kind, root, LayoutRootFunction.layoutOf(root));
    }

    @Override
    public double compute(FunctionContext ctx) {
        if (layout == null) {
            return switch (kind) {
                case EDGE -> Layout.NO_EDGE;
                case BOTTOM -> Layout.NO_BOTTOM;
                case TOP -> Layout.NO_TOP;
                case DETAIL -> 0.0;
                case SPIRE_FLOOR -> Layout.NO_SPIRE;
                case DETAIL_CEILING, CLIMATE_TOP -> Layout.NO_TOP;
                case CLIMATE_BOTTOM -> Layout.NO_BOTTOM;
                case CLIMATE_MOUNTAIN, WARM_BIAS -> 0.0;
                case CLIMATE_TYPE, CLIMATE_TEMPERATURE, CLIMATE_VEGETATION, CLIMATE_EROSION -> Layout.NO_CLIMATE;
            };
        }
        if (kind == Kind.WARM_BIAS) {
            return layout.warmBias(ctx.blockX(), ctx.blockZ());
        }
        if (kind.column <= -10) {
            return layout.climateColumn(ctx.blockX(), ctx.blockZ(), -10 - kind.column);
        }
        if (kind == Kind.SPIRE_FLOOR) {
            return layout.detailFloor(ctx.blockX(), ctx.blockZ());
        }
        if (kind == Kind.DETAIL_CEILING) {
            return layout.detailCeiling(ctx.blockX(), ctx.blockZ());
        }
        return layout.column(ctx.blockX(), ctx.blockZ(), kind.column);
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(of(kind, root.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return kind.min;
    }

    @Override
    public double maxValue() {
        return kind.max;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return kind.codec;
    }
}
