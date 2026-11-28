package pl.skyseas.world;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import pl.skyseas.density.LayoutAccess;

/**
 * Water mobs in the deep ocean. Vanilla fish and squid (and most mods reusing their rules)
 * spawn only in the 13 blocks under the sea level, while the Sky Seas ocean is about 80
 * blocks deep and fish spawn only within 64 blocks of a player: a diving player saw none.
 * In a Sky Seas world, water mobs may also spawn deeper, in water with water above.
 */
public final class WaterMobs {
    private static final int SURFACE_BAND = 13;

    private WaterMobs() {
    }

    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getDefaultResult()) {
            return;
        }
        MobCategory category = event.getEntityType().getCategory();
        if (category != MobCategory.WATER_AMBIENT && category != MobCategory.WATER_CREATURE) {
            return;
        }
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        ServerLevelAccessor level = event.getLevel();
        var pos = event.getPos();
        if (pos.getY() >= level.getSeaLevel() - SURFACE_BAND
                || !level.getFluidState(pos).is(FluidTags.WATER)
                || !level.getFluidState(pos.above()).is(FluidTags.WATER)
                || LayoutAccess.get(level.getLevel().getChunkSource().randomState()) == null) {
            return;
        }
        event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED);
    }
}
