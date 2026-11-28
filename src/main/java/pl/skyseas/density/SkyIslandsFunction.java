package pl.skyseas.density;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import pl.skyseas.config.WorldParams;
import pl.skyseas.layout.Layout;
import pl.skyseas.shape.SkyColumn;

/**
 * {@code skyseas:sky_islands}: 3D density of sky islands (wrap in interpolated). The
 * column inputs are flat_cache'd {@link SkyColumnFunction}s; columns without an island
 * return {@link SkyColumn#MIN} without further work.
 */
public record SkyIslandsFunction(DensityFunction root, DensityFunction edge, DensityFunction top,
                                 DensityFunction bottom, DensityFunction detail, Layout layout)
        implements DensityFunction {
    public static final MapCodec<SkyIslandsFunction> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("root").forGetter(SkyIslandsFunction::root),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("edge").forGetter(SkyIslandsFunction::edge),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("top").forGetter(SkyIslandsFunction::top),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("bottom").forGetter(SkyIslandsFunction::bottom),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("detail").forGetter(SkyIslandsFunction::detail)
    ).apply(i, SkyIslandsFunction::of));
    private static final KeyDispatchDataCodec<SkyIslandsFunction> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    static SkyIslandsFunction of(DensityFunction root, DensityFunction edge, DensityFunction top,
                                 DensityFunction bottom, DensityFunction detail) {
        return new SkyIslandsFunction(root, edge, top, bottom, detail, LayoutRootFunction.layoutOf(root));
    }

    @Override
    public double compute(FunctionContext ctx) {
        double e = edge.compute(ctx);
        if (layout == null || e <= Layout.NO_EDGE) {
            return SkyColumn.MIN;
        }
        return SkyColumn.density(WorldParams.current(), layout.detailSeed(), ctx.blockX(), ctx.blockY(), ctx.blockZ(),
                e, top.compute(ctx), bottom.compute(ctx), detail.compute(ctx));
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(of(root.mapAll(visitor), edge.mapAll(visitor), top.mapAll(visitor),
                bottom.mapAll(visitor), detail.mapAll(visitor)));
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
