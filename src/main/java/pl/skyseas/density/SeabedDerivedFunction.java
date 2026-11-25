package pl.skyseas.density;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import pl.skyseas.config.WorldParams;
import pl.skyseas.shape.SeabedColumn;

/**
 * Density functions computed from the seabed height of the column, one registered type
 * per {@link Kind}: {@code skyseas:seabed}, {@code rock_thickness}, {@code cave_mask},
 * {@code cave_cheese_bias}, {@code aquifer_flood_floor} and {@code depth}. The {@code height} argument should be
 * {@code skyseas:seabed_height} wrapped in cache_2d.
 */
public record SeabedDerivedFunction(Kind kind, DensityFunction height) implements DensityFunction {

    public enum Kind {
        SEABED("seabed"),
        ROCK_THICKNESS("rock_thickness"),
        CAVE_MASK("cave_mask"),
        CAVE_CHEESE_BIAS("cave_cheese_bias"),
        AQUIFER_FLOOD_FLOOR("aquifer_flood_floor"),
        DEPTH("depth");

        public final String id;
        public final MapCodec<SeabedDerivedFunction> dataCodec;
        final KeyDispatchDataCodec<SeabedDerivedFunction> codec;

        Kind(String id) {
            this.id = id;
            this.dataCodec = DensityFunction.HOLDER_HELPER_CODEC.fieldOf("height")
                    .xmap(h -> new SeabedDerivedFunction(this, h), SeabedDerivedFunction::height);
            this.codec = KeyDispatchDataCodec.of(dataCodec);
        }
    }

    @Override
    public double compute(FunctionContext ctx) {
        WorldParams p = WorldParams.DEFAULTS;
        double h = height.compute(ctx);
        return switch (kind) {
            case SEABED -> SeabedColumn.density(p, h, ctx.blockY());
            case ROCK_THICKNESS -> SeabedColumn.rockThickness(p, h);
            case CAVE_MASK -> SeabedColumn.caveMask(p, h, ctx.blockY());
            case CAVE_CHEESE_BIAS -> SeabedColumn.caveCheeseBias(p, h);
            case AQUIFER_FLOOD_FLOOR -> SeabedColumn.aquiferFloodFloor(p, h, ctx.blockY());
            case DEPTH -> SeabedColumn.biomeDepth(p, h, ctx.blockY());
        };
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new SeabedDerivedFunction(kind, height.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        WorldParams p = WorldParams.DEFAULTS;
        return switch (kind) {
            case SEABED -> SeabedColumn.density(p, height.minValue(), p.worldMaxY());
            case ROCK_THICKNESS -> SeabedColumn.rockThickness(p, height.minValue());
            case CAVE_MASK, CAVE_CHEESE_BIAS, DEPTH -> 0.0;
            case AQUIFER_FLOOD_FLOOR -> -1.0;
        };
    }

    @Override
    public double maxValue() {
        WorldParams p = WorldParams.DEFAULTS;
        return switch (kind) {
            case SEABED -> SeabedColumn.density(p, height.maxValue(), p.worldMinY());
            case ROCK_THICKNESS -> SeabedColumn.rockThickness(p, height.maxValue());
            case CAVE_MASK, AQUIFER_FLOOD_FLOOR -> 1.0;
            case CAVE_CHEESE_BIAS -> p.caveThicknessScale() * p.caveMaxCheeseBias();
            case DEPTH -> SeabedColumn.biomeDepth(p, height.maxValue(), p.worldMinY());
        };
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return kind.codec;
    }
}
