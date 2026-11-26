package pl.skyseas.registry;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import pl.skyseas.SkySeas;
import pl.skyseas.density.LayoutRootFunction;
import pl.skyseas.density.RoofGuardFunction;
import pl.skyseas.density.SeabedDerivedFunction;
import pl.skyseas.density.SeabedHeightFunction;
import pl.skyseas.density.SkyColumnFunction;
import pl.skyseas.density.SkyIslandsFunction;
import pl.skyseas.density.ZoneFactorFunction;

/**
 * Deferred registers for the mod. Only server-side worldgen registries are used
 * (none of them is synced to clients).
 */
public final class SkySeasRegistries {
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, SkySeas.MOD_ID);

    static {
        DENSITY_FUNCTION_TYPES.register("zone_factor", () -> ZoneFactorFunction.DATA_CODEC);
        DENSITY_FUNCTION_TYPES.register("seabed_height", () -> SeabedHeightFunction.DATA_CODEC);
        DENSITY_FUNCTION_TYPES.register("roof_guard", () -> RoofGuardFunction.DATA_CODEC);
        for (SeabedDerivedFunction.Kind kind : SeabedDerivedFunction.Kind.values()) {
            DENSITY_FUNCTION_TYPES.register(kind.id, () -> kind.dataCodec);
        }
        DENSITY_FUNCTION_TYPES.register("layout_root", () -> LayoutRootFunction.DATA_CODEC);
        DENSITY_FUNCTION_TYPES.register("sky_islands", () -> SkyIslandsFunction.DATA_CODEC);
        for (SkyColumnFunction.Kind kind : SkyColumnFunction.Kind.values()) {
            DENSITY_FUNCTION_TYPES.register(kind.id, () -> kind.dataCodec);
        }
    }

    private SkySeasRegistries() {
    }

    public static void register(IEventBus modBus) {
        DENSITY_FUNCTION_TYPES.register(modBus);
    }
}
