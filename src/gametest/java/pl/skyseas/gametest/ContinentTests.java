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
import pl.skyseas.density.LayoutAccess;
import pl.skyseas.layout.Layout;
import pl.skyseas.layout.RegionLayout;
import pl.skyseas.shape.ContinentShape;

/**
 * E2 acceptance on generated chunks around spawn: the starter continent exists, nothing
 * solid below continentMinBottomY above the sea, its top gets a biome surface (not bare
 * stone) and land biomes reach from the island up to the top of the world. E3: no loose
 * blocks in the sky around coasts, hanging spires and rock bridges.
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

    /** Half-size of a connectivity window in chunks. */
    private static final int WINDOW_CHUNKS = 2;

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void noLooseSkyBlocks(GameTestHelper helper) {
        int windows = 0;
        int kinds = 0;
        for (long seed : SEEDS) {
            StagedChunkGen gen = StagedChunkGen.create(helper.getLevel(), StagedChunkGen.SKY_SEAS, seed);
            Layout layout = LayoutAccess.get(gen.randomState());
            boolean coast = false;
            boolean spire = false;
            boolean bridge = false;
            for (int rx = -2; rx <= 2 && !(coast && spire && bridge); rx++) {
                for (int rz = -2; rz <= 2 && !(coast && spire && bridge); rz++) {
                    RegionLayout r = layout.region(rx, rz);
                    for (int i = 0; i < r.count; i++) {
                        if (r.type[i] != RegionLayout.CONTINENT || !r.owns(i, layout.params().layoutRegionSize())) {
                            continue;
                        }
                        int off = r.paramOffset[i];
                        if (!spire && r.params[off + ContinentShape.SPIRE_COUNT] > 0) {
                            spire = true;
                            int o = off + ContinentShape.SPIRE0;
                            windows += check(helper, gen, seed, "spire", (int) r.params[o], (int) r.params[o + 1]);
                        }
                        if (!bridge && r.params[off + ContinentShape.LINK_COUNT] > 0) {
                            for (int l = 0; l < (int) r.params[off + ContinentShape.LINK_COUNT] && !bridge; l++) {
                                int o = off + ContinentShape.LINK0 + ContinentShape.LINK_STRIDE * l;
                                int x = (int) (0.5 * (r.params[o] + r.params[o + 2]));
                                int z = (int) (0.5 * (r.params[o + 1] + r.params[o + 3]));
                                if (layout.detailFloor(x & ~3, z & ~3) < Layout.NO_SPIRE) {
                                    bridge = true;
                                    windows += check(helper, gen, seed, "rock bridge", x, z);
                                }
                            }
                        }
                        if (!coast) {
                            // Walk east from the centre to the coast.
                            for (int x = r.cx[i]; x < r.cx[i] + 2000; x += 2) {
                                if (layout.column(x, r.cz[i], Layout.EDGE) < 0.0) {
                                    coast = true;
                                    windows += check(helper, gen, seed, "coast", x, r.cz[i]);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            kinds += (coast ? 1 : 0) + (spire ? 1 : 0) + (bridge ? 1 : 0);
        }
        SkySeas.LOGGER.info("ContinentTests: {} windows without loose sky blocks ({} kinds found)", windows, kinds);
        if (kinds < 4) {
            helper.fail("too few windows: " + kinds + " kinds");
            return;
        }
        helper.succeed();
    }

    /** Generates the chunks around x, z and fails on any solid piece above the sea that does not reach the window border. */
    private static int check(GameTestHelper helper, StagedChunkGen gen, long seed, String what, int x, int z) {
        int c0x = (x >> 4) - WINDOW_CHUNKS;
        int c0z = (z >> 4) - WINDOW_CHUNKS;
        int n = 16 * (2 * WINDOW_CHUNKS + 1);
        int y0 = 100;
        int ny = 320 - y0;
        boolean[] solid = new boolean[n * n * ny];
        for (int cx = 0; cx <= 2 * WINDOW_CHUNKS; cx++) {
            for (int cz = 0; cz <= 2 * WINDOW_CHUNKS; cz++) {
                ProtoChunk chunk = gen.generate(new ChunkPos(c0x + cx, c0z + cz), null);
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                for (int a = 0; a < 16; a++) {
                    for (int b = 0; b < 16; b++) {
                        for (int y = y0; y < 320; y++) {
                            BlockState state = chunk.getBlockState(pos.set(chunk.getPos().getBlockX(a), y, chunk.getPos().getBlockZ(b)));
                            solid[((16 * cx + a) * n + 16 * cz + b) * ny + y - y0] = !state.isAir() && state.getFluidState().isEmpty();
                        }
                    }
                }
            }
        }
        boolean[] seen = new boolean[solid.length];
        int[] queue = new int[solid.length];
        for (int start = 0; start < solid.length; start++) {
            if (!solid[start] || seen[start]) {
                continue;
            }
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            seen[start] = true;
            boolean border = false;
            while (head < tail) {
                int v = queue[head++];
                int y = v % ny;
                int a = v / ny / n;
                int b = v / ny % n;
                border |= a == 0 || b == 0 || a == n - 1 || b == n - 1;
                int[] next = {a > 0 ? v - n * ny : -1, a < n - 1 ? v + n * ny : -1, b > 0 ? v - ny : -1,
                        b < n - 1 ? v + ny : -1, y > 0 ? v - 1 : -1, y < ny - 1 ? v + 1 : -1};
                for (int m : next) {
                    if (m >= 0 && solid[m] && !seen[m]) {
                        seen[m] = true;
                        queue[tail++] = m;
                    }
                }
            }
            if (!border) {
                int y = start % ny + y0;
                int bx = 16 * c0x + start / ny / n;
                int bz = 16 * c0z + start / ny % n;
                helper.fail("seed " + seed + " " + what + " window at " + x + "," + z + ": loose piece of " + tail
                        + " blocks at " + bx + "," + y + "," + bz);
            }
        }
        return 1;
    }
}
