package pl.skyseas.density;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import pl.skyseas.layout.Layout;

/**
 * {@code skyseas:cave_top}: the height that bounds caves from above (wrap in cache_2d):
 * the seabed height, except inside sea spires, which caves would hollow out.
 */
public record CaveTopFunction(DensityFunction root, Layout layout, DensityFunction height) implements DensityFunction {
    public static final MapCodec<CaveTopFunction> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("root").forGetter(CaveTopFunction::root),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("height").forGetter(CaveTopFunction::height)
    ).apply(i, CaveTopFunction::of));
    private static final KeyDispatchDataCodec<CaveTopFunction> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    static CaveTopFunction of(DensityFunction root, DensityFunction height) {
        return new CaveTopFunction(root, LayoutRootFunction.layoutOf(root), height);
    }

    @Override
    public double compute(FunctionContext ctx) {
        double h = height.compute(ctx);
        return layout == null ? h : layout.caveTop(ctx.blockX(), ctx.blockZ(), h);
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(of(root.mapAll(visitor), height.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return Math.min(height.minValue(), -64.0);
    }

    @Override
    public double maxValue() {
        return height.maxValue();
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
