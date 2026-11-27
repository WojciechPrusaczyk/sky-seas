package pl.skyseas.density;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import pl.skyseas.layout.Layout;

/**
 * {@code skyseas:shaft_flood}: aquifer floodedness with the shafts: blue holes always
 * flooded, cenotes left to vanilla (mostly dry), elsewhere {@code input}.
 */
public record ShaftFloodFunction(DensityFunction root, DensityFunction input, Layout layout) implements DensityFunction {
    public static final MapCodec<ShaftFloodFunction> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("root").forGetter(ShaftFloodFunction::root),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(ShaftFloodFunction::input)
    ).apply(i, ShaftFloodFunction::of));
    private static final KeyDispatchDataCodec<ShaftFloodFunction> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    static ShaftFloodFunction of(DensityFunction root, DensityFunction input) {
        return new ShaftFloodFunction(root, input, LayoutRootFunction.layoutOf(root));
    }

    @Override
    public double compute(FunctionContext ctx) {
        double v = input.compute(ctx);
        return layout == null ? v : layout.shaftFlood(ctx.blockX(), ctx.blockY(), ctx.blockZ(), v);
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(of(root.mapAll(visitor), input.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return Math.min(-1.0, input.minValue());
    }

    @Override
    public double maxValue() {
        return Math.max(1.0, input.maxValue());
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
