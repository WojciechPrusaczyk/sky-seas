package pl.skyseas.density;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import pl.skyseas.config.WorldParams;
import pl.skyseas.shape.SeabedColumn;

/**
 * {@code skyseas:roof_guard}: keeps the roof under the seabed solid, per block and
 * outside interpolation. The interpolated cave mask alone can leak a few blocks into
 * the roof, because cell corners are 8 blocks apart vertically.
 *
 * <p>{@code seabed} should be the interpolated {@code skyseas:seabed}: it is cheap per
 * block (NoiseChunk shares one interpolator per equal function) and matches the terrain
 * surface exactly.
 */
public record RoofGuardFunction(DensityFunction input, DensityFunction seabed) implements DensityFunction {
    /** Density forced inside the roof (any positive value means rock). */
    private static final double ROCK = 1.0;

    public static final MapCodec<RoofGuardFunction> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(RoofGuardFunction::input),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("seabed").forGetter(RoofGuardFunction::seabed)
    ).apply(i, RoofGuardFunction::new));
    private static final KeyDispatchDataCodec<RoofGuardFunction> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    @Override
    public double compute(FunctionContext ctx) {
        double v = input.compute(ctx);
        if (v < ROCK && SeabedColumn.inRoof(WorldParams.current(), seabed.compute(ctx))) {
            return ROCK;
        }
        return v;
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        input.fillArray(array, provider);
        for (int i = 0; i < array.length; i++) {
            if (array[i] < ROCK) {
                FunctionContext ctx = provider.forIndex(i);
                if (SeabedColumn.inRoof(WorldParams.current(), seabed.compute(ctx))) {
                    array[i] = ROCK;
                }
            }
        }
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new RoofGuardFunction(input.mapAll(visitor), seabed.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return input.minValue();
    }

    @Override
    public double maxValue() {
        return Math.max(input.maxValue(), ROCK);
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
