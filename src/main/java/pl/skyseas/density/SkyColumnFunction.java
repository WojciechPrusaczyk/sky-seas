package pl.skyseas.density;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import pl.skyseas.layout.Layout;

/**
 * 2D column values of the nearest sky island, one registered type per {@link Kind}:
 * {@code skyseas:sky_edge}, {@code sky_top}, {@code sky_bottom}, {@code sky_mountain}.
 * Wrap in flat_cache. The {@code root} argument is {@code skyseas:layout/root}.
 */
public record SkyColumnFunction(Kind kind, DensityFunction root, Layout layout) implements DensityFunction {

    public enum Kind {
        EDGE("sky_edge", Layout.EDGE, Layout.NO_EDGE, 2000.0),
        TOP("sky_top", Layout.TOP, Layout.NO_TOP, 320.0),
        BOTTOM("sky_bottom", Layout.BOTTOM, 0.0, Layout.NO_BOTTOM),
        MOUNTAIN("sky_mountain", Layout.MOUNTAIN, 0.0, 320.0);

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
            return kind == Kind.EDGE ? Layout.NO_EDGE : kind == Kind.BOTTOM ? Layout.NO_BOTTOM : Layout.NO_TOP;
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
