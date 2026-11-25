package pl.skyseas.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pl.skyseas.SkySeas;
import net.minecraft.world.level.levelgen.DensityFunction;
import pl.skyseas.command.RouterLookup;
import pl.skyseas.command.StagedChunkGen;
import pl.skyseas.density.SeabedHeightFunction;
import pl.skyseas.config.WorldParams;
import pl.skyseas.shape.SeabedShape;

/**
 * E1 acceptance on generated chunks (biomes, noise and surface stages): seabed within
 * the design range, a solid roof of {@code caveRoofThickness} blocks under it (no holes),
 * only water (or vanilla icebergs) between the seabed and sea level, and deterministic output.
 */
@GameTestHolder(SkySeas.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SeabedTests {
    private static final long[] SEEDS = {20261125L, -4172144997902289642L};
    /** Chunk grid spanning about -12000..12000 blocks, so it covers every zone. */
    private static final int GRID = 7;
    private static final int STRIDE_CHUNKS = 250;
    private static final int SEA_LEVEL = 63;

    private SeabedTests() {
    }

    @GameTest(template = "empty")
    public static void seabedRangeRoofAndWater(GameTestHelper helper) {
        WorldParams p = WorldParams.DEFAULTS;
        int columns = 0;
        long caveBlocks = 0;
        int minSeen = Integer.MAX_VALUE;
        int maxSeen = Integer.MIN_VALUE;
        for (long seed : SEEDS) {
            StagedChunkGen gen = StagedChunkGen.create(helper.getLevel(), StagedChunkGen.SKY_SEAS, seed);
            for (int i = 0; i < GRID; i++) {
                for (int j = 0; j < GRID; j++) {
                    ChunkPos pos = new ChunkPos((i - GRID / 2) * STRIDE_CHUNKS, (j - GRID / 2) * STRIDE_CHUNKS);
                    ProtoChunk chunk = gen.generate(pos, null);
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            int bx = pos.getBlockX(x);
                            int bz = pos.getBlockZ(z);
                            int top = seabedTop(chunk, bx, bz);
                            String error = checkColumn(p, chunk, bx, bz, top);
                            if (error != null) {
                                helper.fail("seed " + seed + ": " + error);
                                return;
                            }
                            caveBlocks += countCaveBlocks(p, chunk, bx, bz, top);
                            minSeen = Math.min(minSeen, top);
                            maxSeen = Math.max(maxSeen, top);
                            columns++;
                        }
                    }
                }
            }
        }
        SkySeas.LOGGER.info("SeabedTests: {} columns ok, seabed top Y {}..{}, {} cave blocks below the roof",
                columns, minSeen, maxSeen, caveBlocks);
        if (caveBlocks == 0) {
            helper.fail("no caves at all below the roof");
            return;
        }
        helper.succeed();
    }

    /**
     * Trenches must reach the configured minimum somewhere in cold water at the zone
     * plateau. Samples the seabed height function on a dense grid (no chunk generation).
     */
    @GameTest(template = "empty")
    public static void trenchesReachMinY(GameTestHelper helper) {
        WorldParams p = WorldParams.DEFAULTS;
        StagedChunkGen gen = StagedChunkGen.create(helper.getLevel(), StagedChunkGen.SKY_SEAS, SEEDS[0]);
        SeabedHeightFunction seabed = RouterLookup.find(gen.randomState().router().finalDensity(), SeabedHeightFunction.class);
        double min = Double.MAX_VALUE;
        for (int x = 10_000; x < 30_000; x += 8) {
            for (int z = 10_000; z < 30_000; z += 64) {
                min = Math.min(min, seabed.compute(new DensityFunction.SinglePointContext(x, 0, z)));
            }
        }
        SkySeas.LOGGER.info("SeabedTests: deepest seabed sample {}", min);
        if (min > p.seabedTrenchMinY() + 1.0) {
            helper.fail("deepest trench " + min + ", expected about " + p.seabedTrenchMinY());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void generationIsDeterministic(GameTestHelper helper) {
        long seed = SEEDS[0];
        ChunkPos a = new ChunkPos(600, -300);
        ChunkPos b = new ChunkPos(-20, 45);
        StagedChunkGen first = StagedChunkGen.create(helper.getLevel(), StagedChunkGen.SKY_SEAS, seed);
        ProtoChunk a1 = first.generate(a, null);
        ProtoChunk b1 = first.generate(b, null);
        // A fresh RandomState and the reversed order must give the same chunks.
        StagedChunkGen second = StagedChunkGen.create(helper.getLevel(), StagedChunkGen.SKY_SEAS, seed);
        ProtoChunk b2 = second.generate(b, null);
        ProtoChunk a2 = second.generate(a, null);
        String diff = firstDifference(a1, a2);
        if (diff == null) {
            diff = firstDifference(b1, b2);
        }
        if (diff != null) {
            helper.fail(diff);
            return;
        }
        helper.succeed();
    }

    /** Y of the highest seabed block below sea level (skips water and vanilla icebergs). */
    private static int seabedTop(ProtoChunk chunk, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, SEA_LEVEL - 1, z);
        while (pos.getY() > chunk.getMinBuildHeight() && isOceanColumn(chunk.getBlockState(pos))) {
            pos.move(0, -1, 0);
        }
        return pos.getY();
    }

    /** Water, or iceberg blocks placed by SurfaceSystem.frozenOceanExtension in frozen oceans. */
    private static boolean isOceanColumn(BlockState state) {
        return state.is(Blocks.WATER) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.SNOW_BLOCK);
    }

    private static String checkColumn(WorldParams p, ProtoChunk chunk, int x, int z, int top) {
        if (top < SeabedShape.minHeight(p) - 1 || top > SeabedShape.maxHeight(p)) {
            return "seabed top " + top + " out of range at " + x + "," + z;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);
        for (int y = top; y > top - p.caveRoofThickness(); y--) {
            BlockState state = chunk.getBlockState(pos.setY(y));
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                return "hole in roof at " + x + "," + y + "," + z + " (seabed top " + top + "): " + state
                        + "\n" + describeColumn(chunk, x, z, top);
            }
        }
        for (int y = SEA_LEVEL; y < chunk.getMaxBuildHeight(); y++) {
            BlockState state = chunk.getBlockState(pos.setY(y));
            if (!state.isAir() && !isOceanColumn(state)) {
                return "unexpected block above sea level at " + x + "," + y + "," + z;
            }
        }
        return null;
    }

    /** Air or fluid blocks between the roof and the bottom of the world (caves, flooded or not). */
    private static int countCaveBlocks(WorldParams p, ProtoChunk chunk, int x, int z, int top) {
        int count = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);
        for (int y = top - (int) p.caveRoofThickness(); y > chunk.getMinBuildHeight(); y--) {
            BlockState state = chunk.getBlockState(pos.setY(y));
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** Column dump for failure messages. */
    private static String describeColumn(ProtoChunk chunk, int x, int z, int top) {
        StringBuilder sb = new StringBuilder("column ").append(x).append(',').append(z).append(':');
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);
        for (int y = top + 12; y >= top - 16; y--) {
            sb.append("\n  y=").append(y).append(' ').append(chunk.getBlockState(pos.setY(y)).getBlock().getName().getString());
        }
        return sb.toString();
    }

    private static String firstDifference(ProtoChunk a, ProtoChunk b) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = a.getMinBuildHeight(); y < a.getMaxBuildHeight(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    pos.set(a.getPos().getBlockX(x), y, a.getPos().getBlockZ(z));
                    if (a.getBlockState(pos) != b.getBlockState(pos)) {
                        return "chunk " + a.getPos() + " differs at " + pos.toShortString();
                    }
                }
            }
        }
        return null;
    }
}
