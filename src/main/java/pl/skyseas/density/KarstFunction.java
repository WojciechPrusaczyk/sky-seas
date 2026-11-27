package pl.skyseas.density;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import pl.skyseas.layout.Hash;
import pl.skyseas.layout.Layout;
import pl.skyseas.shape.KarstShape;

/** {@code skyseas:karst}: karst wells and their halls under the seabed (see {@link KarstShape}). */
public record KarstFunction(DensityFunction root, Layout layout) implements DensityFunction {
    public static final MapCodec<KarstFunction> DATA_CODEC = DensityFunction.HOLDER_HELPER_CODEC.fieldOf("root")
            .xmap(KarstFunction::of, KarstFunction::root);
    private static final KeyDispatchDataCodec<KarstFunction> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    static KarstFunction of(DensityFunction root) {
        return new KarstFunction(root, LayoutRootFunction.layoutOf(root));
    }

    @Override
    public double compute(FunctionContext ctx) {
        return layout == null ? 1.0 : KarstShape.density(Hash.hash(layout.seed(), 0x4B415253L), ctx.blockX(), ctx.blockY(), ctx.blockZ());
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(of(root.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return -1.0;
    }

    @Override
    public double maxValue() {
        return 1.0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
