package pl.skyseas.density;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import pl.skyseas.config.WorldParams;
import pl.skyseas.shape.SeabedShape;

/**
 * {@code skyseas:seabed_height}: 2D seabed surface height in blocks (wrap in cache_2d).
 * Inputs are density functions, so noises are seeded by the RandomState and the
 * temperature is the same function the biome source uses.
 */
public record SeabedHeightFunction(DensityFunction zone, DensityFunction temperature, DensityFunction relief,
                                   DensityFunction roughness, DensityFunction dunes, DensityFunction canyon,
                                   DensityFunction trench) implements DensityFunction {
    public static final MapCodec<SeabedHeightFunction> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("zone").forGetter(SeabedHeightFunction::zone),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("temperature").forGetter(SeabedHeightFunction::temperature),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("relief").forGetter(SeabedHeightFunction::relief),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("roughness").forGetter(SeabedHeightFunction::roughness),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("dunes").forGetter(SeabedHeightFunction::dunes),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("canyon").forGetter(SeabedHeightFunction::canyon),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("trench").forGetter(SeabedHeightFunction::trench)
    ).apply(i, SeabedHeightFunction::new));
    private static final KeyDispatchDataCodec<SeabedHeightFunction> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    @Override
    public double compute(FunctionContext ctx) {
        return SeabedShape.height(WorldParams.DEFAULTS, zone.compute(ctx), temperature.compute(ctx),
                relief.compute(ctx), roughness.compute(ctx), dunes.compute(ctx), canyon.compute(ctx),
                trench.compute(ctx));
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new SeabedHeightFunction(zone.mapAll(visitor), temperature.mapAll(visitor),
                relief.mapAll(visitor), roughness.mapAll(visitor), dunes.mapAll(visitor), canyon.mapAll(visitor),
                trench.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return SeabedShape.minHeight(WorldParams.DEFAULTS);
    }

    @Override
    public double maxValue() {
        return SeabedShape.maxHeight(WorldParams.DEFAULTS);
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
