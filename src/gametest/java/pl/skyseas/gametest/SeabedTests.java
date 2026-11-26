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
import pl.skyseas.density.LayoutAccess;
import pl.skyseas.layout.Layout;
import pl.skyseas.shape.OceanShape;
import pl.skyseas.shape.SeabedShape;

/**
 * E1 acceptance on generated chunks (biomes, noise and surface stages): seabed within
 * the design range, a solid roof of {@code caveRoofThickness} blocks under it (no holes),
 * only water (or vanilla icebergs) between the seabed and sea level, open air from sea level
 * up to the lowest sky island, and deterministic output.
 */
@GameTestHolder(SkySeas.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SeabedTests {
    private static final long[] SEEDS = {20261125L, -4172144997902289642L};
    /** Chunk grid spanning about -12000..12000 blocks, so it covers every zone. */
    private static final int GRID = 7;
    private static final int STRIDE_CHUNKS = 250;
    private static final int SEA_LEVEL = 63;
    /**
     * p5..p95 seabed height spread expected from a relief of about +/-8..10 blocks. The upper
     * bound leaves room for canyons and trenches, which pull p5 down in far areas.
     */
    private static final double MIN_RELIEF_SPREAD = 14.0;
    private static final double MAX_RELIEF_SPREAD = 30.0;

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
            Layout layout = LayoutAccess.get(gen.randomState());
            for (int i = 0; i < GRID; i++) {
                for (int j = 0; j < GRID; j++) {
                    ChunkPos pos = new ChunkPos((i - GRID / 2) * STRIDE_CHUNKS, (j - GRID / 2) * STRIDE_CHUNKS);
                    ProtoChunk chunk = gen.generate(pos, null);
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            int bx = pos.getBlockX(x);
                            int bz = pos.getBlockZ(z);
                            boolean oceanForm = isOceanForm(layout, bx, bz);
                            int top = seabedTop(p, chunk, bx, bz, oceanForm ? layout.oceanHeight(bx, bz, SeabedShape.maxHeight(p)) : p.worldMinY());
                            String error = checkColumn(p, chunk, bx, bz, top, oceanForm);
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

    /**
     * The plain seabed (outside ocean forms) must not be flat: the spread between the 5th and 95th percentile of
     * seabed height in a 4x4 km area must match a relief of roughly +/-8..10 blocks.
     */
    @GameTest(template = "empty")
    public static void seabedReliefSpread(GameTestHelper helper) {
        StagedChunkGen gen = StagedChunkGen.create(helper.getLevel(), StagedChunkGen.SKY_SEAS, SEEDS[0]);
        SeabedHeightFunction seabed = RouterLookup.find(gen.randomState().router().finalDensity(), SeabedHeightFunction.class);
        int[][] areas = {{-2000, -2000}, {20_000, -14_000}};
        for (int[] origin : areas) {
            double[] samples = new double[250 * 250];
            int n = 0;
            Layout layout = LayoutAccess.get(gen.randomState());
            for (int i = 0; i < 250; i++) {
                for (int j = 0; j < 250; j++) {
                    int x = origin[0] + i * 16;
                    int z = origin[1] + j * 16;
                    if (!isOceanForm(layout, x, z)) {
                        samples[n++] = seabed.compute(new DensityFunction.SinglePointContext(x, 0, z));
                    }
                }
            }
            samples = java.util.Arrays.copyOf(samples, n);
            java.util.Arrays.sort(samples);
            double p5 = samples[n * 5 / 100];
            double p50 = samples[n / 2];
            double p95 = samples[n * 95 / 100];
            SkySeas.LOGGER.info("SeabedTests: area {},{}: min {} p5 {} p50 {} p95 {} max {}", origin[0], origin[1],
                    String.format("%.1f", samples[0]), String.format("%.1f", p5), String.format("%.1f", p50),
                    String.format("%.1f", p95), String.format("%.1f", samples[n - 1]));
            double spread = p95 - p5;
            if (spread < MIN_RELIEF_SPREAD || spread > MAX_RELIEF_SPREAD) {
                helper.fail("seabed p5..p95 spread " + spread + " at " + origin[0] + "," + origin[1]
                        + ", expected " + MIN_RELIEF_SPREAD + ".." + MAX_RELIEF_SPREAD);
                return;
            }
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

    /** True if an ocean form (island, shelf, seamount, volcano, spire) reaches this column. */
    private static boolean isOceanForm(Layout layout, int x, int z) {
        return layout.oceanAt(layout.regionAt(x, z), x, z, OceanShape.NONE) >= 0;
    }

    /**
     * Y of the highest seabed or ocean-form block under the sky forms (skips air, water and
     * vanilla icebergs), scanning down from just above the ocean form's surface (on the highest seabed base) or from
     * under continentMinBottomY, below every sky form.
     */
    private static int seabedTop(WorldParams p, ProtoChunk chunk, int x, int z, double oceanForm) {
        int start = Math.max((int) p.continentMinBottomY() - 1, (int) Math.ceil(oceanForm) + 2);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, start, z);
        while (pos.getY() >= SEA_LEVEL && (chunk.getBlockState(pos).isAir() || isOceanColumn(chunk.getBlockState(pos)))) {
            pos.move(0, -1, 0);
        }
        while (pos.getY() > chunk.getMinBuildHeight() && isOceanColumn(chunk.getBlockState(pos))) {
            pos.move(0, -1, 0);
        }
        return pos.getY();
    }

    /** Water, or iceberg blocks placed by SurfaceSystem.frozenOceanExtension in frozen oceans. */
    private static boolean isOceanColumn(BlockState state) {
        return state.is(Blocks.WATER) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.SNOW_BLOCK);
    }

    private static String checkColumn(WorldParams p, ProtoChunk chunk, int x, int z, int top, boolean oceanForm) {
        if (top < SeabedShape.minHeight(p) - 1 || top > (oceanForm ? OceanShape.maxHeight(p) : SeabedShape.maxHeight(p))) {
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
