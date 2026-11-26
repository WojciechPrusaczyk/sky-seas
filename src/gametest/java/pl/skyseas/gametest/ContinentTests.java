package pl.skyseas.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pl.skyseas.SkySeas;
import pl.skyseas.command.StagedChunkGen;
import pl.skyseas.config.WorldParams;

/**
 * E2 acceptance on generated chunks around spawn: the starter continent exists, nothing
 * solid below continentMinBottomY above the sea, its top gets a biome surface (not bare
 * stone) and land biomes reach from the island up to the top of the world.
 */
@GameTestHolder(SkySeas.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContinentTests {
    private static final long[] SEEDS = {20261125L, -4172144997902289642L};
    private static final int SEA_LEVEL = 63;
    /** Chunks around spawn, inside the starter plateau. */
    private static final int RADIUS_CHUNKS = 3;

    private ContinentTests() {
    }

    @GameTest(template = "empty")
    public static void starterContinentAtSpawn(GameTestHelper helper) {
        WorldParams p = WorldParams.DEFAULTS;
        int columns = 0;
        int minBottom = Integer.MAX_VALUE;
        int minTop = Integer.MAX_VALUE;
        int maxTop = Integer.MIN_VALUE;
        for (long seed : SEEDS) {
            StagedChunkGen gen = StagedChunkGen.create(helper.getLevel(), StagedChunkGen.SKY_SEAS, seed);
            for (int cx = -RADIUS_CHUNKS; cx <= RADIUS_CHUNKS; cx++) {
                for (int cz = -RADIUS_CHUNKS; cz <= RADIUS_CHUNKS; cz++) {
                    ProtoChunk chunk = gen.generate(new ChunkPos(cx, cz), null);
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            int bx = chunk.getPos().getBlockX(x);
                            int bz = chunk.getPos().getBlockZ(z);
                            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(bx, 0, bz);
                            int top = Integer.MIN_VALUE;
                            int bottom = Integer.MAX_VALUE;
                            for (int y = SEA_LEVEL; y < chunk.getMaxBuildHeight(); y++) {
                                BlockState state = chunk.getBlockState(pos.setY(y));
                                if (!state.isAir() && state.getFluidState().isEmpty()) {
                                    top = y;
                                    bottom = Math.min(bottom, y);
                                }
                            }
                            String where = "seed " + seed + " column " + bx + "," + bz;
                            if (top == Integer.MIN_VALUE) {
                                helper.fail(where + ": no continent above the starter plateau");
                                return;
                            }
                            if (bottom < p.continentMinBottomY()) {
                                helper.fail(where + ": solid block at Y " + bottom + " below " + p.continentMinBottomY());
                                return;
                            }
                            BlockState surface = chunk.getBlockState(pos.setY(top));
                            if (surface.is(Blocks.STONE) || surface.is(Blocks.DEEPSLATE)) {
                                helper.fail(where + ": bare " + surface + " on top at Y " + top);
                                return;
                            }
                            for (int y = top + 1; y < chunk.getMaxBuildHeight(); y += 16) {
                                var biome = chunk.getNoiseBiome(QuartPos.fromBlock(bx), QuartPos.fromBlock(y), QuartPos.fromBlock(bz));
                                if (biome.is(BiomeTags.IS_OCEAN)) {
                                    helper.fail(where + ": ocean biome " + biome.getRegisteredName() + " above the continent at Y " + y);
                                    return;
                                }
                            }
                            minBottom = Math.min(minBottom, bottom);
                            minTop = Math.min(minTop, top);
                            maxTop = Math.max(maxTop, top);
                            columns++;
                        }
                    }
                }
            }
        }
        SkySeas.LOGGER.info("ContinentTests: {} columns ok, top Y {}..{}, lowest underside Y {}",
                columns, minTop, maxTop, minBottom);
        helper.succeed();
    }
}
