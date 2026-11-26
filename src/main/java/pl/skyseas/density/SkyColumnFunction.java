package pl.skyseas.density;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import pl.skyseas.layout.Layout;

/**
 * 2D column values of the nearest sky island, one registered type per {@link Kind}:
 * {@code skyseas:sky_edge}, {@code sky_top}, {@code sky_bottom}, {@code sky_detail} (weight of the
 * 3D detail noise) and {@code sky_spire_floor} (lowest per-block detail near the column: spire tips, rock bridges).
 * Wrap in flat_cache. The {@code root} argument is {@code skyseas:layout/root}.
 */
public record SkyColumnFunction(Kind kind, DensityFunction root, Layout layout) implements DensityFunction {

    public enum Kind {
        EDGE("sky_edge", Layout.EDGE, Layout.NO_EDGE, 2000.0),
        TOP("sky_top", Layout.TOP, Layout.NO_TOP, 320.0),
        BOTTOM("sky_bottom", Layout.BOTTOM, 0.0, Layout.NO_BOTTOM),
        DETAIL("sky_detail", Layout.DETAIL, 0.0, 1.0),
        SPIRE_FLOOR("sky_spire_floor", -1, -64.0, Layout.NO_SPIRE);

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
            };
        }
        if (kind == Kind.SPIRE_FLOOR) {
            return layout.detailFloor(ctx.blockX(), ctx.blockZ());
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
