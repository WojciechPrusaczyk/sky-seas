package pl.skyseas.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.Fluids;

/**
 * {@code /skyseas census <radius>}: block counts in fully generated chunks around a point,
 * split at the sea level (sky and ocean land above, sea and rock below), plus moving water
 * and lava (ticking fluids) above the sea. Generates the chunks on the server thread, so
 * keep the radius small. Writes the full list to {@code skyseas/census/}.
 */
final class Census {
    private static final int SEA_LEVEL = 63;

    private Census() {
    }

    static String run(ServerLevel level, BlockPos centre, int radius) throws IOException {
        Map<String, long[]> counts = new HashMap<>();
        long flowing = 0;
        int cx0 = centre.getX() >> 4;
        int cz0 = centre.getZ() >> 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = cx0 - radius; cx <= cx0 + radius; cx++) {
            for (int cz = cz0 - radius; cz <= cz0 + radius; cz++) {
                ChunkAccess chunk = level.getChunk(cx, cz);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                            BlockState s = chunk.getBlockState(pos.set((cx << 4) + x, y, (cz << 4) + z));
                            if (s.isAir()) {
                                continue;
                            }
                            String id = s.getBlock().builtInRegistryHolder().key().location().getPath();
                            counts.computeIfAbsent(id, k -> new long[2])[y >= SEA_LEVEL ? 0 : 1]++;
                            if (y >= SEA_LEVEL && !s.getFluidState().isEmpty() && !s.getFluidState().isSource()
                                    && (s.getFluidState().is(Fluids.FLOWING_WATER) || s.getFluidState().is(Fluids.FLOWING_LAVA))) {
                                flowing++;
                            }
                        }
                    }
                }
            }
        }
        List<Map.Entry<String, long[]>> list = new ArrayList<>(counts.entrySet());
        list.sort((a, b) -> Long.compare(b.getValue()[0] + b.getValue()[1], a.getValue()[0] + a.getValue()[1]));
        StringBuilder file = new StringBuilder("block above_sea below_sea\n");
        for (Map.Entry<String, long[]> e : list) {
            file.append(String.format(Locale.ROOT, "%s %d %d%n", e.getKey(), e.getValue()[0], e.getValue()[1]));
        }
        Path dir = level.getServer().getServerDirectory().resolve("skyseas").resolve("census");
        Files.createDirectories(dir);
        Path out = dir.resolve(String.format(Locale.ROOT, "census-%d_%d-r%d-%s.txt", centre.getX(), centre.getZ(), radius,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));
        Files.writeString(out, file.toString());
        return String.format(Locale.ROOT, "[Sky Seas] census: %d chunks, %d block types, %d moving fluid blocks above the sea\n  file: %s",
                (2 * radius + 1) * (2 * radius + 1), counts.size(), flowing, out.toAbsolutePath());
    }
}
