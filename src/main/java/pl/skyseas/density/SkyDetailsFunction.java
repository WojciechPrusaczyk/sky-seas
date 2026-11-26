package pl.skyseas.density;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import pl.skyseas.config.WorldParams;
import pl.skyseas.layout.Layout;
import pl.skyseas.shape.SkyColumn;

/**
 * {@code skyseas:sky_details}: small sky forms that interpolation would blur, per block
 * (not interpolated): rock spires hanging under continents and rock bridges between
 * members of composite continents. The flat_cache'd {@code floor} (lowest detail block
 * near the column) rejects almost every block with one
 * comparison; only blocks within a spire's height range look up the layout.
 */
public record SkyDetailsFunction(DensityFunction root, DensityFunction floor, Layout layout) implements DensityFunction {
    public static final MapCodec<SkyDetailsFunction> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("root").forGetter(SkyDetailsFunction::root),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("floor").forGetter(SkyDetailsFunction::floor)
    ).apply(i, SkyDetailsFunction::of));
    private static final KeyDispatchDataCodec<SkyDetailsFunction> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
    /** Tallest detail above its floor. */
    private static final double SPAN = Layout.DETAIL_SPAN;

    static SkyDetailsFunction of(DensityFunction root, DensityFunction floor) {
        return new SkyDetailsFunction(root, floor, LayoutRootFunction.layoutOf(root));
    }

    @Override
    public double compute(FunctionContext ctx) {
        int y = ctx.blockY();
        double f = floor.compute(ctx);
        if (layout == null || y < f || y > f + SPAN) {
            return SkyColumn.MIN;
        }
        return SkyColumn.scale(WorldParams.DEFAULTS, layout.detailDensity(ctx.blockX(), y, ctx.blockZ()));
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(of(root.mapAll(visitor), floor.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return SkyColumn.MIN;
    }

    @Override
    public double maxValue() {
        return SkyColumn.MAX;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
