package pl.skyseas.density;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import pl.skyseas.config.WorldParams;
import pl.skyseas.layout.Climate;

/**
 * Router climate parameters in 3D ({@link Climate}), one registered type per {@link Kind}:
 * {@code skyseas:climate_continents}, {@code climate_erosion}, {@code climate_temperature},
 * {@code climate_vegetation}, {@code climate_depth}, {@code climate_ridges}. {@code input} is the vanilla 2D value
 * (unused for depth); {@code island}, {@code bottom}, {@code top}, {@code point} and
 * {@code mountain} are the flat-cached {@code skyseas:sky_climate_*} columns, {@code height}
 * the cached seabed height and {@code warm} the warm-sea bias near spawn.
 */
public record ClimateFunction(Kind kind, DensityFunction input, DensityFunction height, DensityFunction island,
                              DensityFunction bottom, DensityFunction top, DensityFunction point,
                              DensityFunction mountain, DensityFunction warm, DensityFunction patch) implements DensityFunction {

    public enum Kind {
        CONTINENTS("climate_continents", -1.2, 1.0),
        EROSION("climate_erosion", -1.0, 1.0),
        TEMPERATURE("climate_temperature", -2.0, 1.0),
        VEGETATION("climate_vegetation", -1.0, 1.0),
        DEPTH("climate_depth", 0.0, 10.0),
        RIDGES("climate_ridges", -1.0, 1.0);

        public final String id;
        final double min;
        final double max;
        public final MapCodec<ClimateFunction> dataCodec;
        final KeyDispatchDataCodec<ClimateFunction> codec;

        Kind(String id, double min, double max) {
            this.id = id;
            this.min = min;
            this.max = max;
            this.dataCodec = RecordCodecBuilder.mapCodec(i -> i.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(ClimateFunction::input),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("height").forGetter(ClimateFunction::height),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("island").forGetter(ClimateFunction::island),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("bottom").forGetter(ClimateFunction::bottom),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("top").forGetter(ClimateFunction::top),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("point").forGetter(ClimateFunction::point),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("mountain").forGetter(ClimateFunction::mountain),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("warm").forGetter(ClimateFunction::warm),
                    DensityFunction.HOLDER_HELPER_CODEC.optionalFieldOf("patch", DensityFunctions.zero()).forGetter(ClimateFunction::patch)
            ).apply(i, (in, h, is, b, t, p, m, w, pa) -> new ClimateFunction(this, in, h, is, b, t, p, m, w, pa)));
            this.codec = KeyDispatchDataCodec.of(dataCodec);
        }
    }

    @Override
    public double compute(FunctionContext ctx) {
        int y = ctx.blockY();
        double is = island.compute(ctx);
        double b = bottom.compute(ctx);
        return switch (kind) {
            case CONTINENTS -> Climate.continents(is, b, top.compute(ctx), height.compute(ctx), y, input.compute(ctx));
            case EROSION -> Climate.erosion(is, b, top.compute(ctx), height.compute(ctx), y, point.compute(ctx),
                    mountain.compute(ctx), input.compute(ctx), WorldParams.current().climateDepthScale());
            case TEMPERATURE -> Climate.temperature(is, b, y, point.compute(ctx), warm.compute(ctx), input.compute(ctx),
                    patch.compute(ctx));
            case VEGETATION -> Climate.vegetation(is, b, y, point.compute(ctx), input.compute(ctx));
            case DEPTH -> Climate.depth(is, b, top.compute(ctx), height.compute(ctx), y, WorldParams.current().climateDepthScale());
            case RIDGES -> Climate.ridges(is, b, y, mountain.compute(ctx), input.compute(ctx));
        };
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new ClimateFunction(kind, input.mapAll(visitor), height.mapAll(visitor), island.mapAll(visitor),
                bottom.mapAll(visitor), top.mapAll(visitor), point.mapAll(visitor), mountain.mapAll(visitor),
                warm.mapAll(visitor), patch.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return kind.min;
    }

    @Override
    public double maxValue() {
        return kind.max;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return kind.codec;
    }
}
