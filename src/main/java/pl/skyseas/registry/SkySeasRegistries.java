package pl.skyseas.registry;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import pl.skyseas.SkySeas;

/**
 * Deferred registers for the mod. Only server-side worldgen registries are used
 * (none of them is synced to clients). Entries are added stage by stage.
 */
public final class SkySeasRegistries {
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, SkySeas.MOD_ID);

    private SkySeasRegistries() {
    }

    public static void register(IEventBus modBus) {
        DENSITY_FUNCTION_TYPES.register(modBus);
    }
}
