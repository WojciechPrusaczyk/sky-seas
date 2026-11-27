package pl.skyseas.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import pl.skyseas.SkySeas;

/** Features and placement modifier types (server-side registries, not synced to clients). */
public final class SkySeasWorldgen {
    private static final DeferredRegister<PlacementModifierType<?>> PLACEMENTS =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, SkySeas.MOD_ID);
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, SkySeas.MOD_ID);

    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<SeabedPlacement>> SEABED =
            PLACEMENTS.register("seabed", () -> () -> SeabedPlacement.CODEC);
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<LayerFilter>> LAYER_FILTER =
            PLACEMENTS.register("layer_filter", () -> () -> LayerFilter.CODEC);
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<IslandRelativeHeight>> ISLAND_RELATIVE_HEIGHT =
            PLACEMENTS.register("island_relative_height", () -> () -> IslandRelativeHeight.CODEC);
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<ZoneCount>> ZONE_COUNT =
            PLACEMENTS.register("zone_count", () -> () -> ZoneCount.CODEC);
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<DepthChance>> DEPTH_CHANCE =
            PLACEMENTS.register("depth_chance", () -> () -> DepthChance.CODEC);
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<IslandUnderside>> ISLAND_UNDERSIDE =
            PLACEMENTS.register("island_underside", () -> () -> IslandUnderside.CODEC);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> CASCADE =
            FEATURES.register("cascade", CascadeFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> FROZEN_SEA =
            FEATURES.register("frozen_sea", IceFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> DIAMOND_NESTS =
            FEATURES.register("diamond_nests", DiamondNestFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> SEABED_VENT =
            FEATURES.register("seabed_vent", VentFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> SEABED_MATERIALS =
            FEATURES.register("seabed_materials", SeabedMaterialsFeature::new);

    public static final DeferredHolder<Feature<?>, SeabedPlants.Kelp> SEABED_KELP = FEATURES.register("seabed_kelp", SeabedPlants.Kelp::new);
    public static final DeferredHolder<Feature<?>, SeabedPlants.Seagrass> SEABED_SEAGRASS =
            FEATURES.register("seabed_seagrass", SeabedPlants.Seagrass::new);
    public static final DeferredHolder<Feature<?>, SeabedPlants.SeaPickle> SEABED_SEA_PICKLE =
            FEATURES.register("seabed_sea_pickle", SeabedPlants.SeaPickle::new);

    private SkySeasWorldgen() {
    }

    public static void register(IEventBus modBus) {
        PLACEMENTS.register(modBus);
        FEATURES.register(modBus);
    }
}
