package pl.skyseas.structure;

import com.mojang.serialization.MapCodec;
import dev.worldgen.lithostitched.api.registry.LithostitchedRegistries;
import dev.worldgen.lithostitched.api.worldgen.placementcondition.PlacementCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import pl.skyseas.SkySeas;

/** Structure spawn condition types in the Lithostitched registry (server-side, not synced). */
public final class SkySeasStructures {
    private static final DeferredRegister<MapCodec<? extends PlacementCondition>> CONDITIONS =
            DeferredRegister.create(LithostitchedRegistries.PLACEMENT_CONDITION_TYPE, SkySeas.MOD_ID);

    static {
        CONDITIONS.register("island_interior", () -> IslandInterior.CODEC);
        CONDITIONS.register("rock_above", () -> RockAbove.CODEC);
    }

    private SkySeasStructures() {
    }

    public static void register(IEventBus modBus) {
        CONDITIONS.register(modBus);
    }
}
