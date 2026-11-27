package pl.skyseas.density;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import pl.skyseas.layout.Layout;
import pl.skyseas.shape.ShaftShape;

/**
 * Cenotes and blue holes: {@code skyseas:shaft_near} (2D, 1 near a shaft, for a cache_2d
 * gate) and {@code skyseas:shafts} (3D density, negative inside a shaft or its chamber).
 */
public record ShaftFunction(Kind kind, DensityFunction root, Layout layout) implements DensityFunction {
    public enum Kind {
        NEAR("shaft_near"),
        DENSITY("shafts");

        public final String id;
        public final MapCodec<ShaftFunction> dataCodec;
        private final KeyDispatchDataCodec<ShaftFunction> codec;

        Kind(String id) {
            this.id = id;
            this.dataCodec = DensityFunction.HOLDER_HELPER_CODEC.fieldOf("root")
                    .xmap(root -> ShaftFunction.of(this, root), ShaftFunction::root);
            this.codec = KeyDispatchDataCodec.of(dataCodec);
        }
    }

    static ShaftFunction of(Kind kind, DensityFunction root) {
        return new ShaftFunction(kind, root, LayoutRootFunction.layoutOf(root));
    }

    @Override
    public double compute(FunctionContext ctx) {
        if (layout == null) {
            return kind == Kind.NEAR ? 0.0 : ShaftShape.NONE;
        }
        return kind == Kind.NEAR ? layout.shaftNear(ctx.blockX(), ctx.blockZ())
                : layout.shaftDensity(ctx.blockX(), ctx.blockY(), ctx.blockZ());
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
        return kind == Kind.NEAR ? 0.0 : -ShaftShape.NONE;
    }

    @Override
    public double maxValue() {
        return kind == Kind.NEAR ? 1.0 : ShaftShape.NONE;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return kind.codec;
    }
}
