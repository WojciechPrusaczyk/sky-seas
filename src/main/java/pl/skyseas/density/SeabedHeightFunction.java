package pl.skyseas.density;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import pl.skyseas.config.WorldParams;
import pl.skyseas.layout.Layout;
import pl.skyseas.shape.OceanShape;
import pl.skyseas.shape.SeabedProvinces;
import pl.skyseas.shape.SeabedShape;

/**
 * {@code skyseas:seabed_height}: 2D seabed surface height in blocks (wrap in cache_2d),
 * including the ocean forms of the layout (islands, shelves, seamounts, volcanoes, spires).
 * Inputs are density functions, so noises are seeded by the RandomState and the
 * temperature is the same function the biome source uses.
 */
public record SeabedHeightFunction(DensityFunction root, Layout layout, DensityFunction zone, DensityFunction temperature, DensityFunction relief,
                                   DensityFunction roughness, DensityFunction dunes, DensityFunction canyon,
                                   DensityFunction trench) implements DensityFunction {
    public static final MapCodec<SeabedHeightFunction> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("root").forGetter(SeabedHeightFunction::root),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("zone").forGetter(SeabedHeightFunction::zone),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("temperature").forGetter(SeabedHeightFunction::temperature),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("relief").forGetter(SeabedHeightFunction::relief),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("roughness").forGetter(SeabedHeightFunction::roughness),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("dunes").forGetter(SeabedHeightFunction::dunes),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("canyon").forGetter(SeabedHeightFunction::canyon),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("trench").forGetter(SeabedHeightFunction::trench)
    ).apply(i, SeabedHeightFunction::of));
    private static final KeyDispatchDataCodec<SeabedHeightFunction> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    static SeabedHeightFunction of(DensityFunction root, DensityFunction zone, DensityFunction temperature,
                                   DensityFunction relief, DensityFunction roughness, DensityFunction dunes,
                                   DensityFunction canyon, DensityFunction trench) {
        return new SeabedHeightFunction(root, LayoutRootFunction.layoutOf(root), zone, temperature, relief, roughness,
                dunes, canyon, trench);
    }

    @Override
    public double compute(FunctionContext ctx) {
        double base = SeabedShape.height(WorldParams.DEFAULTS, zone.compute(ctx), temperature.compute(ctx),
                relief.compute(ctx), roughness.compute(ctx), dunes.compute(ctx), canyon.compute(ctx),
                trench.compute(ctx));
        if (layout == null) {
            return base;
        }
        base = SeabedProvinces.height(WorldParams.DEFAULTS, layout.seabedSeed(), temperature.compute(ctx), ctx.blockX(),
                ctx.blockZ(), base);
        return layout.oceanHeight(ctx.blockX(), ctx.blockZ(), base);
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(of(root.mapAll(visitor), zone.mapAll(visitor), temperature.mapAll(visitor),
                relief.mapAll(visitor), roughness.mapAll(visitor), dunes.mapAll(visitor), canyon.mapAll(visitor),
                trench.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return SeabedShape.minHeight(WorldParams.DEFAULTS);
    }

    @Override
    public double maxValue() {
        return Math.max(SeabedShape.maxHeight(WorldParams.DEFAULTS), OceanShape.maxHeight(WorldParams.DEFAULTS));
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
