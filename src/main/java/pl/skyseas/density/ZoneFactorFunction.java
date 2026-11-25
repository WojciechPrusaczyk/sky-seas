package pl.skyseas.density;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import pl.skyseas.config.WorldParams;
import pl.skyseas.layout.ZoneField;

/** {@code skyseas:zone_factor}: 2D distance-from-spawn factor 0..1 (wrap in cache_2d). */
public record ZoneFactorFunction(DensityFunction jitter) implements DensityFunction {
    public static final MapCodec<ZoneFactorFunction> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("jitter").forGetter(ZoneFactorFunction::jitter)
    ).apply(i, ZoneFactorFunction::new));
    private static final KeyDispatchDataCodec<ZoneFactorFunction> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    @Override
    public double compute(FunctionContext ctx) {
        return ZoneField.factor(WorldParams.DEFAULTS, ctx.blockX(), ctx.blockZ(), jitter.compute(ctx));
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new ZoneFactorFunction(jitter.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return 0.0;
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
