package pl.skyseas;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.PlayerRespawnLogic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.level.LevelEvent;
import pl.skyseas.density.LayoutAccess;

/**
 * First spawn of a Sky Seas world: the middle of the starter plateau at 0,0. Vanilla
 * searches for a spawn by climate and would often pick open sea.
 */
public final class SpawnHandler {
    private SpawnHandler() {
    }

    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level) || LayoutAccess.get(level.getChunkSource().randomState()) == null) {
            return;
        }
        BlockPos pos = PlayerRespawnLogic.getSpawnPosInChunk(level, new ChunkPos(0, 0));
        if (pos == null) {
            pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(8, 0, 8));
        }
        event.getSettings().setSpawn(pos, 0.0F);
        event.setCanceled(true);
        SkySeas.LOGGER.info("Sky Seas spawn on the starter plateau at {}", pos);
    }
}
