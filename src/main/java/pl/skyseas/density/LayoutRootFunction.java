package pl.skyseas.density;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import pl.skyseas.config.WorldParams;
import pl.skyseas.layout.ClimateProbe;
import pl.skyseas.layout.Hash;
import pl.skyseas.layout.Layout;

/**
 * {@code skyseas:layout_root}: holds the {@link Layout} of one RandomState. The layout
 * seed is derived from the bound noise {@code skyseas:layout_seed} (sampled at fixed
 * points), so no mixin and no global seed are needed. Always computes 0.
 *
 * <p>Equality is by the noise holder only: RandomState and NoiseChunk deduplicate equal
 * functions, so every reference to the root in the router shares one Layout.
 */
public final class LayoutRootFunction implements DensityFunction {
    public static final MapCodec<LayoutRootFunction> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NoiseHolder.CODEC.fieldOf("noise").forGetter(LayoutRootFunction::noise),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("temperature").forGetter(LayoutRootFunction::temperature),
            DensityFunction.HOLDER_HELPER_CODEC.optionalFieldOf("vegetation", DensityFunctions.zero()).forGetter(LayoutRootFunction::vegetation),
            DensityFunction.HOLDER_HELPER_CODEC.optionalFieldOf("erosion", DensityFunctions.zero()).forGetter(LayoutRootFunction::erosion)
    ).apply(i, LayoutRootFunction::new));
    private static final KeyDispatchDataCodec<LayoutRootFunction> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
    private static final double[][] SEED_POINTS = {{0, 0, 0}, {1.5e4, 7.0, -3.1e4}, {-2.7e4, 31.0, 1.3e4}, {123.4, -9.0, 567.8}};

    private final NoiseHolder noise;
    /**
     * Climate temperature for layout decisions, bound by the same RandomState as the
     * noise. Sampled outside chunk generation (no per-chunk caches), so it is thread-safe.
     */
    private final DensityFunction temperature;
    private final DensityFunction vegetation;
    private final DensityFunction erosion;
    /** Null until the noise is bound by a RandomState. */
    private final Layout layout;

    public LayoutRootFunction(NoiseHolder noise, DensityFunction temperature, DensityFunction vegetation, DensityFunction erosion) {
        this.noise = noise;
        this.temperature = temperature;
        this.vegetation = vegetation;
        this.erosion = erosion;
        this.layout = noise.noise() == null ? null : new Layout(deriveSeed(noise), WorldParams.DEFAULTS, new ClimateProbe() {
            @Override
            public double temperature(int x, int z) {
                return temperature.compute(new SinglePointContext(x, 0, z));
            }

            @Override
            public double vegetation(int x, int z) {
                return vegetation.compute(new SinglePointContext(x, 0, z));
            }

            @Override
            public double erosion(int x, int z) {
                return erosion.compute(new SinglePointContext(x, 0, z));
            }
        });
    }

    private static long deriveSeed(NoiseHolder noise) {
        long h = 0x5EED5EEDL;
        for (double[] pt : SEED_POINTS) {
            h = Hash.hash(h, Double.doubleToLongBits(noise.getValue(pt[0], pt[1], pt[2])));
        }
        return h;
    }

    public NoiseHolder noise() {
        return noise;
    }

    public DensityFunction temperature() {
        return temperature;
    }

    public DensityFunction vegetation() {
        return vegetation;
    }

    public DensityFunction erosion() {
        return erosion;
    }

    public Layout layout() {
        return layout;
    }

    /** The layout behind a reference to the root (unwraps registry holders), or null if unbound. */
    public static Layout layoutOf(DensityFunction f) {
        while (f instanceof DensityFunctions.HolderHolder holder) {
            if (!holder.function().isBound()) {
                return null; // still parsing the registry
            }
            f = holder.function().value();
        }
        return f instanceof LayoutRootFunction root ? root.layout : null;
    }

    @Override
    public double compute(FunctionContext ctx) {
        return 0.0;
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        java.util.Arrays.fill(array, 0.0);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        NoiseHolder bound = visitor.visitNoise(noise);
        // Bind once (RandomState); later visitors (NoiseChunk) keep this layout and the cache-free temperature.
        return visitor.apply(bound.equals(noise) ? this : new LayoutRootFunction(bound, temperature.mapAll(visitor),
                vegetation.mapAll(visitor), erosion.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return 0.0;
    }

    @Override
    public double maxValue() {
        return 0.0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LayoutRootFunction other && other.noise.equals(noise);
    }

    @Override
    public int hashCode() {
        return noise.hashCode();
    }
}
