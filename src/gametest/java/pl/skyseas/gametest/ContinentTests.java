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
import pl.skyseas.shape.SkyFormShape;
import pl.skyseas.shape.ContinentShape;

/**
 * E2 acceptance on generated chunks around spawn: the starter continent exists, nothing
 * solid below continentMinBottomY above the sea, its top gets a biome surface (not bare
 * stone) and land biomes reach from the island up to the top of the world. E3: no loose
 * blocks in the sky around coasts, hanging spires, rock bridges and the sky forms between
 * continents (towers, sky islands, cluster forms, archipelago bridges).
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

    /** Air blocks under thick rock seen by {@link #check} (sky corridors, and arches or bridges over other rock). */
    private static int corridorAir;

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

    /** One window per sky form kind within two regions of spawn, for each seed. */
    @GameTest(template = "empty", timeoutTicks = 4800)
    public static void noLooseSkyFormBlocks(GameTestHelper helper) {
        String[] kinds = {"tower", "medium island", "small island", "archipelago", "hallelujah", "sky bridge", "sunk island"};
        int windows = 0;
        corridorAir = 0;
        for (long seed : SEEDS) {
            StagedChunkGen gen = StagedChunkGen.create(helper.getLevel(), StagedChunkGen.SKY_SEAS, seed);
            Layout layout = LayoutAccess.get(gen.randomState());
            boolean[] found = new boolean[kinds.length];
            for (int rx = -2; rx <= 2; rx++) {
                for (int rz = -2; rz <= 2; rz++) {
                    RegionLayout r = layout.region(rx, rz);
                    for (int i = 0; i < r.count; i++) {
                        if (!r.owns(i, layout.params().layoutRegionSize())) {
                            continue;
                        }
                        WorldParams.SkyForms f = layout.params().skyForms();
                        int off = r.paramOffset[i];
                        boolean sunk = r.type[i] == RegionLayout.SKY_ISLAND && r.params[off + SkyFormShape.TOP] <= f.sunkTopMax();
                        boolean small = r.type[i] == RegionLayout.SKY_ISLAND && r.params[off + SkyFormShape.RADIUS] < f.smallIslandRadiusMax();
                        int k = sunk ? 6 : switch (r.type[i]) {
                            case RegionLayout.TOWER -> 0;
                            case RegionLayout.SKY_ISLAND -> small ? 2 : 1;
                            case RegionLayout.ARCHIPELAGO -> 3;
                            case RegionLayout.HALLELUJAH -> 4;
                            case RegionLayout.SKY_BRIDGE -> 5;
                            default -> -1;
                        };
                        if (k >= 0 && !found[k]) {
                            found[k] = true;
                            windows += check(helper, gen, seed, kinds[k], r.cx[i], r.cz[i]);
                        }
                    }
                }
            }
            for (int k = 0; k < kinds.length; k++) {
                if (!found[k]) {
                    helper.fail("seed " + seed + ": no " + kinds[k] + " near spawn");
                    return;
                }
            }
        }
        SkySeas.LOGGER.info("ContinentTests: {} sky form windows without loose sky blocks, {} corridor air blocks", windows, corridorAir);
        // Without corridors arches and overhangs give about 90 such blocks in these windows; with them about 380.
        if (corridorAir < 200) {
            helper.fail("no corridors in thick sky forms: " + corridorAir + " air blocks under thick rock");
            return;
        }
        helper.succeed();
    }

    /**
     * Generates the chunks around x, z and fails on any solid piece of sky form that
     * neither reaches the window border nor holds the centre column of a sky form (small
     * forms fit inside a window whole). The window starts just under continentMinBottomY,
     * so pieces reaching its floor are seabed or ocean forms.
     */
    private static int check(GameTestHelper helper, StagedChunkGen gen, long seed, String what, int x, int z) {
        int c0x = (x >> 4) - WINDOW_CHUNKS;
        int c0z = (z >> 4) - WINDOW_CHUNKS;
        int n = 16 * (2 * WINDOW_CHUNKS + 1);
        int y0 = (int) LayoutAccess.get(gen.randomState()).params().continentMinBottomY() - 1;
        int ny = 320 - y0;
        boolean[] solid = new boolean[n * n * ny];
        // Pieces of ice and snow alone are vanilla icebergs (SurfaceSystem.frozenOceanExtension), not sky forms.
        boolean[] ice = new boolean[n * n * ny];
        for (int cx = 0; cx <= 2 * WINDOW_CHUNKS; cx++) {
            for (int cz = 0; cz <= 2 * WINDOW_CHUNKS; cz++) {
                ProtoChunk chunk = gen.generate(new ChunkPos(c0x + cx, c0z + cz), null);
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                for (int a = 0; a < 16; a++) {
                    for (int b = 0; b < 16; b++) {
                        for (int y = y0; y < 320; y++) {
                            BlockState state = chunk.getBlockState(pos.set(chunk.getPos().getBlockX(a), y, chunk.getPos().getBlockZ(b)));
                            int v = ((16 * cx + a) * n + 16 * cz + b) * ny + y - y0;
                            solid[v] = !state.isAir() && state.getFluidState().isEmpty();
                            ice[v] = state.is(Blocks.PACKED_ICE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.BLUE_ICE)
                                    || state.is(Blocks.ICE) || state.is(Blocks.SNOW);
                        }
                    }
                }
            }
        }
        // Corridors: air with at least 12 solid blocks right above it and solid somewhere below in the column.
        for (int col = 0; col < n * n; col++) {
            boolean below = false;
            for (int y = 0; y < ny - 12; y++) {
                int v = col * ny + y;
                if (solid[v]) {
                    below = true;
                } else if (below) {
                    boolean roof = true;
                    for (int k = 1; k <= 12 && roof; k++) {
                        roof = solid[v + k];
                    }
                    corridorAir += roof ? 1 : 0;
                }
            }
        }
        boolean[] anchor = new boolean[n * n];
        Layout layout = LayoutAccess.get(gen.randomState());
        for (int[] q : new int[][]{{16 * c0x, 16 * c0z}, {16 * c0x + n, 16 * c0z}, {16 * c0x, 16 * c0z + n}, {16 * c0x + n, 16 * c0z + n}}) {
            RegionLayout r = layout.regionAt(q[0], q[1]);
            for (int i = 0; i < r.count; i++) {
                int a = r.cx[i] - 16 * c0x;
                int b = r.cz[i] - 16 * c0z;
                if (!RegionLayout.isOcean(r.type[i]) && a >= 0 && b >= 0 && a < n && b < n) {
                    anchor[a * n + b] = true;
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
            boolean iceberg = true;
            while (head < tail) {
                int v = queue[head++];
                int y = v % ny;
                int a = v / ny / n;
                int b = v / ny % n;
                iceberg &= ice[v];
                border |= a == 0 || b == 0 || a == n - 1 || b == n - 1 || anchor[a * n + b] || y == 0;
                int[] next = {a > 0 ? v - n * ny : -1, a < n - 1 ? v + n * ny : -1, b > 0 ? v - ny : -1,
                        b < n - 1 ? v + ny : -1, y > 0 ? v - 1 : -1, y < ny - 1 ? v + 1 : -1};
                for (int m : next) {
                    if (m >= 0 && solid[m] && !seen[m]) {
                        seen[m] = true;
                        queue[tail++] = m;
                    }
                }
            }
            if (!border && !iceberg) {
                int y = start % ny + y0;
                int bx = 16 * c0x + start / ny / n;
                int bz = 16 * c0z + start / ny % n;
                helper.fail("seed " + seed + " " + what + " window at " + x + "," + z + ": loose piece of " + tail
                        + " blocks at " + bx + "," + y + "," + bz + " (" + gen.generate(new ChunkPos(bx >> 4, bz >> 4), null)
                        .getBlockState(new BlockPos(bx, y, bz)).getBlock().getName().getString() + ")");
            }
        }
        return 1;
    }
}
