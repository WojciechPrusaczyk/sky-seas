package pl.skyseas.layout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import pl.skyseas.config.WorldParams;
import pl.skyseas.shape.ContinentShape;
import pl.skyseas.shape.OceanShape;
import pl.skyseas.shape.SimplexNoise;
import pl.skyseas.shape.SkyColumn;

class LayoutTest {
    private static final WorldParams P = WorldParams.DEFAULTS;
    private static final long[] SEEDS = {20261125L, -4172144997902289642L, 1L};
    /** Varied climate (warm, lukewarm, temperate, cold) over a few thousand blocks. */
    /** Coast-to-coast gap to the nearest continent wanted by the design. */
    private static final double GAP_MIN = 400.0;
    private static final double GAP_MAX = 800.0;
    private static final ClimateProbe CLIMATE = (x, z) -> 0.9 * SimplexNoise.noise2(7, x / 2500.0, z / 2500.0);

    @Test
    void sameRegionRegardlessOfOrderAndThreads() throws Exception {
        long seed = SEEDS[0];
        List<int[]> keys = new ArrayList<>();
        for (int rx = -6; rx <= 6; rx++) {
            for (int rz = -6; rz <= 6; rz++) {
                keys.add(new int[]{rx, rz});
            }
        }
        Layout sequential = new Layout(seed, P, CLIMATE);
        List<RegionLayout> expected = new ArrayList<>();
        for (int[] k : keys) {
            expected.add(sequential.region(k[0], k[1]));
        }

        Layout parallel = new Layout(seed, P, CLIMATE);
        List<int[]> shuffled = new ArrayList<>(keys);
        Collections.shuffle(shuffled, new Random(42));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                futures.add(pool.submit(() -> {
                    for (int[] k : shuffled) {
                        parallel.region(k[0], k[1]);
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }
        assertEquals(keys.size(), parallel.cache().computed(), "each region computed exactly once");
        for (int n = 0; n < keys.size(); n++) {
            RegionLayout a = expected.get(n);
            RegionLayout b = parallel.region(keys.get(n)[0], keys.get(n)[1]);
            assertEquals(a.count, b.count);
            assertArrayEquals(a.id, b.id);
            assertArrayEquals(a.cx, b.cx);
            assertArrayEquals(a.cz, b.cz);
            assertArrayEquals(a.params, b.params);
            assertArrayEquals(a.type, b.type);
            assertArrayEquals(a.paramOffset, b.paramOffset);
            assertArrayEquals(a.skyStart, b.skyStart);
            assertArrayEquals(a.skyItems, b.skyItems);
            assertArrayEquals(a.oceanStart, b.oceanStart);
            assertArrayEquals(a.oceanItems, b.oceanItems);
        }
    }

    @Test
    void starterContinentAtOrigin() {
        for (long seed : SEEDS) {
            Layout layout = new Layout(seed, P);
            RegionLayout r = layout.regionAt(0, 0);
            int i = layout.islandAt(r, 0, 0);
            assertTrue(i >= 0, "no island at 0,0");
            assertEquals(RegionLayout.FLAG_STARTER, r.flags[i] & RegionLayout.FLAG_STARTER);
            assertEquals(0, r.cx[i]);
            assertEquals(0, r.cz[i]);
            // Flat spawn plateau: no mountains near the centre.
            for (int x = -120; x <= 120; x += 20) {
                double relief = layout.column(x, 0, Layout.MOUNTAIN);
                assertTrue(Math.abs(relief) <= 3.5, "relief " + relief + " on the plateau at " + x);
            }
        }
    }

    /** Each continent's outline box stays inside its grid cell minus half the smallest gap on every side. */
    @Test
    void continentsStayInsideTheirCells() {
        Layout layout = new Layout(SEEDS[1], P);
        int cell = P.continentCellSize();
        double keep = P.continentBoxGapMin() / 2.0 - 1.0;
        for (int rx = -10; rx <= 10; rx++) {
            for (int rz = -10; rz <= 10; rz++) {
                RegionLayout r = layout.region(rx, rz);
                for (int i = 0; i < r.count; i++) {
                    if (r.type[i] != RegionLayout.CONTINENT) {
                        continue;
                    }
                    int off = r.paramOffset[i];
                    int ci = Math.floorDiv(r.cx[i] + cell / 2, cell);
                    int cj = Math.floorDiv(r.cz[i] + cell / 2, cell);
                    double x0 = (ci - 0.5) * cell + keep;
                    double x1 = (ci + 0.5) * cell - keep;
                    double z0 = (cj - 0.5) * cell + keep;
                    double z1 = (cj + 0.5) * cell - keep;
                    assertTrue(r.cx[i] + r.params[off + ContinentShape.BOX_MIN_X] >= x0
                            && r.cx[i] + r.params[off + ContinentShape.BOX_MAX_X] <= x1
                            && r.cz[i] + r.params[off + ContinentShape.BOX_MIN_Z] >= z0
                            && r.cz[i] + r.params[off + ContinentShape.BOX_MAX_Z] <= z1,
                            "continent at " + r.cx[i] + "," + r.cz[i] + " leaves its cell");
                }
            }
        }
    }

    /** Solid only between minY and maxY of the descriptor, and never below minBottomY. */
    @Test
    void solidWithinDescriptorRangeAndAboveMinBottom() {
        for (long seed : SEEDS) {
            Layout layout = new Layout(seed, P);
            long noiseSeed = layout.detailSeed();
            int solid = 0;
            for (int x = -1200; x <= 1200; x += 6) {
                for (int z = -1200; z <= 1200; z += 6) {
                    RegionLayout r = layout.regionAt(x, z);
                    int i = layout.nearestIsland(r, x, z);
                    double edge = layout.column(x, z, Layout.EDGE);
                    double top = layout.column(x, z, Layout.TOP);
                    double bottom = layout.column(x, z, Layout.BOTTOM);
                    double detail = layout.column(x, z, Layout.DETAIL);
                    for (int y = 64; y < 320; y++) {
                        if (SkyColumn.density(P, noiseSeed, x, y, z, edge, top, bottom, detail) > 0.0
                                || layout.detailDensity(x, y, z) > 0.0) {
                            solid++;
                            assertTrue(y >= P.continentMinBottomY(), "solid at " + x + "," + y + "," + z);
                            assertTrue(i >= 0 && y >= r.minY[i] && y <= r.maxY[i],
                                    "solid outside descriptor Y range at " + x + "," + y + "," + z);
                        }
                    }
                }
            }
            assertTrue(solid > 0, "no continent sampled");
        }
    }

    /** The mountain mask leaves at least 35% of a continent flat. */
    @Test
    void enoughFlatTerrain() {
        int inside = 0;
        int flat = 0;
        for (long seed = 1; seed <= 20; seed++) {
            Layout layout = new Layout(seed * 7919, P);
            for (int x = -4000; x <= 4000; x += 50) {
                for (int z = -4000; z <= 4000; z += 50) {
                    if (layout.column(x, z, Layout.EDGE) > 0.0) {
                        inside++;
                        if (layout.column(x, z, Layout.MOUNTAIN) < 4.0) {
                            flat++;
                        }
                    }
                }
            }
        }
        double fraction = (double) flat / inside;
        assertTrue(fraction >= 0.35, "flat fraction " + fraction);
        assertTrue(fraction <= 0.85, "almost no mountains: " + fraction);
    }

    @Test
    void topAndBottomStayInDesignRange() {
        Layout layout = new Layout(SEEDS[2], P);
        for (int x = -5000; x <= 5000; x += 25) {
            for (int z = -5000; z <= 5000; z += 25) {
                if (layout.column(x, z, Layout.EDGE) > 0.0) {
                    double top = layout.column(x, z, Layout.TOP);
                    double bottom = layout.column(x, z, Layout.BOTTOM);
                    assertTrue(top <= P.continentMassifPeakYMax() && top >= P.continentMinBottomY(), "top " + top);
                    assertTrue(bottom >= P.continentMinBottomY(), "bottom " + bottom);
                }
            }
        }
    }

    /**
     * Natural edges, no rubble: a continent may shed a few sea stacks (coves cutting off
     * a headland tip), but no crumbs, and almost all of its area is one piece.
     */
    @Test
    void outlineHasNoRubble() {
        int step = 8;
        double worstDetached = 0.0;
        int smallest = Integer.MAX_VALUE;
        for (long seed : SEEDS) {
            Layout layout = new Layout(seed, P);
            for (int rx = -4; rx <= 4; rx++) {
                for (int rz = -4; rz <= 4; rz++) {
                    RegionLayout r = layout.region(rx, rz);
                    for (int i = 0; i < r.count; i++) {
                        if (r.type[i] != RegionLayout.CONTINENT) {
                            continue;
                        }
                        if (r.cx[i] < r.originX || r.cx[i] >= r.originX + P.layoutRegionSize()
                                || r.cz[i] < r.originZ || r.cz[i] >= r.originZ + P.layoutRegionSize()) {
                            continue; // each continent once, in the region of its centre
                        }
                        // Rock bridges are 6-10 blocks wide: a finer grid for composite continents.
                        boolean composite = r.params[r.paramOffset[i] + ContinentShape.SILHOUETTE] == ContinentShape.COMPOSITE;
                        int[] result = pieces(r, i, composite ? 2 : step);
                        if (composite) {
                            result[2] = result[2] == Integer.MAX_VALUE ? result[2] : result[2] / 16;
                        }
                        worstDetached = Math.max(worstDetached, 1.0 - (double) result[0] / result[1]);
                        smallest = Math.min(smallest, result[2]);
                    }
                }
            }
        }
        assertTrue(worstDetached < 0.03, "detached area fraction " + worstDetached);
        // A cove cutting off a headland tip leaves a sea stack (~150 blocks, 2+ cells on this grid); one cell is rubble.
        assertTrue(smallest >= 2, "rubble: detached piece of " + smallest + " cells of " + step + "x" + step);
    }

    /** {largest piece, total, smallest detached piece} in cells. */
    private static int[] pieces(RegionLayout r, int i, int step) {
        int off = r.paramOffset[i];
        int n = (int) Math.ceil(r.radius[i] / step) * 2 + 1;
        boolean[] inside = new boolean[n * n];
        int total = 0;
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                double x = r.cx[i] + (a - n / 2) * step;
                double z = r.cz[i] + (b - n / 2) * step;
                // Rock bridges join members outside the 2D outline.
                if (ContinentShape.edge(P, r.params, off, r.shapeSeed[i], x, z) > 0.0
                        || ContinentShape.bridgeFloor(P, r.params, off, r.shapeSeed[i], x - 2, z - 2, 1000.0) < 1000.0) {
                    inside[a * n + b] = true;
                    total++;
                }
            }
        }
        int[] queue = new int[n * n];
        boolean[] seen = new boolean[n * n];
        int largest = 0;
        int smallest = Integer.MAX_VALUE;
        int pieces = 0;
        for (int startCell = 0; startCell < n * n; startCell++) {
            if (!inside[startCell] || seen[startCell]) {
                continue;
            }
            int head = 0;
            int tail = 0;
            queue[tail++] = startCell;
            seen[startCell] = true;
            while (head < tail) {
                int c = queue[head++];
                int a = c / n;
                int b = c % n;
                // 8-connected: on a coarse grid a thin diagonal headland touches only at corners.
                for (int da = -1; da <= 1; da++) {
                    for (int db = -1; db <= 1; db++) {
                        int na = a + da;
                        int nb = b + db;
                        if (na < 0 || nb < 0 || na >= n || nb >= n) {
                            continue;
                        }
                        int m = na * n + nb;
                        if (inside[m] && !seen[m]) {
                            seen[m] = true;
                            queue[tail++] = m;
                        }
                    }
                }
            }
            pieces++;
            if (tail > largest) {
                if (pieces > 1) {
                    smallest = Math.min(smallest, largest);
                }
                largest = tail;
            } else {
                smallest = Math.min(smallest, tail);
            }
        }
        return new int[]{largest, total, smallest};
    }

    @Test
    void regionComputesWithinBudget() {
        Layout warm = new Layout(SEEDS[0], P);
        for (int i = 0; i < 200; i++) {
            LayoutGenerator.generate(SEEDS[0], P, ClimateProbe.TEMPERATE, i, -i);
        }
        long worst = 0;
        for (int i = 0; i < 100; i++) {
            long t0 = System.nanoTime();
            LayoutGenerator.generate(warm.seed(), P, CLIMATE, 1000 + i, 7 * i);
            worst = Math.max(worst, System.nanoTime() - t0);
        }
        assertTrue(worst < 20_000_000L, "slowest region " + worst / 1e6 + " ms");
    }

    @Test
    void continentParametersAreDeterministic() {
        double[] a = new double[ContinentShape.stride(P)];
        double[] b = new double[ContinentShape.stride(P)];
        for (long seed = 0; seed < 200; seed++) {
            ContinentShape.place(P, seed, 10, 20, 500, false, 5000, a, 0);
            ContinentShape.placeSpires(P, seed, a, 0);
            ContinentShape.place(P, seed, 10, 20, 500, false, 5000, b, 0);
            ContinentShape.placeSpires(P, seed, b, 0);
            assertArrayEquals(a, b);
        }
        assertArrayEquals(a, b);
    }

    /** Clear-sky rule: no sky island within the margin of land at sea level. */
    @Test
    void clearSkyOverOceanLand() {
        int land = 0;
        for (long seed : SEEDS) {
            Layout layout = new Layout(seed, P, CLIMATE);
            for (int x = -6000; x <= 6000; x += 4) {
                for (int z = -6000; z <= 6000; z += 4) {
                    if (layout.oceanHeight(x, z, -10.0) >= 63.0) {
                        land++;
                        double edge = layout.column(x, z, Layout.EDGE);
                        assertTrue(edge <= -P.clearSkyMargin(), "sky island " + edge + " over ocean land at " + x + "," + z);
                    }
                }
            }
        }
        assertTrue(land > 1000, "too little ocean land: " + land);
    }

    /** Every ocean form type appears; size classes roughly as designed; no large islands in the Haven. */
    @Test
    void oceanFormsAndClasses() {
        int[] types = new int[8];
        int[] classes = new int[3];
        int atolls = 0;
        Layout layout = new Layout(SEEDS[0], P, CLIMATE);
        for (int rx = -12; rx <= 12; rx++) {
            for (int rz = -12; rz <= 12; rz++) {
                RegionLayout r = layout.region(rx, rz);
                for (int i = 0; i < r.count; i++) {
                    if (!r.owns(i, P.layoutRegionSize())) {
                        continue;
                    }
                    types[r.type[i]]++;
                    if (r.type[i] == RegionLayout.OCEAN_ISLAND) {
                        int off = r.paramOffset[i];
                        int c = (int) r.params[off + OceanShape.SIZE_CLASS];
                        classes[c]++;
                        if (r.params[off + OceanShape.RING] > 0.0) {
                            atolls++;
                        }
                        if (c == 2) {
                            assertTrue(Math.hypot(r.cx[i], r.cz[i]) > P.zoneRadii()[0], "large island in the Haven");
                        }
                    }
                }
            }
        }
        System.out.println("LayoutTest: types " + java.util.Arrays.toString(types) + " classes "
                + java.util.Arrays.toString(classes) + " atolls " + atolls);
        for (byte t = RegionLayout.OCEAN_ISLAND; t <= RegionLayout.SEA_SPIRE; t++) {
            assertTrue(types[t] > 0, "no " + RegionLayout.TYPE_NAMES[t]);
        }
        assertTrue(atolls > 0, "no atolls");
        int islands = classes[0] + classes[1] + classes[2];
        assertTrue(classes[0] > 0.6 * islands && classes[1] > 0.15 * islands && classes[2] > 0, "classes "
                + java.util.Arrays.toString(classes));
    }

    /** Continents owned by regions -n..n (each once). */
    private static List<int[]> continents(Layout layout, int n) {
        List<int[]> out = new ArrayList<>();
        for (int rx = -n; rx <= n; rx++) {
            for (int rz = -n; rz <= n; rz++) {
                RegionLayout r = layout.region(rx, rz);
                for (int i = 0; i < r.count; i++) {
                    if (r.type[i] == RegionLayout.CONTINENT && r.owns(i, P.layoutRegionSize())) {
                        out.add(new int[]{rx, rz, i});
                    }
                }
            }
        }
        return out;
    }

    /** Coast points (x, z pairs) of a continent: samples on a grid just inside the outline. */
    private static double[] coast(Layout layout, RegionLayout r, int i, int step) {
        int off = r.paramOffset[i];
        double[] pts = new double[4096];
        int n = 0;
        for (double x = r.cx[i] + r.params[off + ContinentShape.BOX_MIN_X]; x <= r.cx[i] + r.params[off + ContinentShape.BOX_MAX_X]; x += step) {
            for (double z = r.cz[i] + r.params[off + ContinentShape.BOX_MIN_Z]; z <= r.cz[i] + r.params[off + ContinentShape.BOX_MAX_Z]; z += step) {
                double e = ContinentShape.edge(P, r.params, off, r.shapeSeed[i], x, z);
                if (e > 0.0 && e <= step) {
                    if (n + 2 > pts.length) {
                        pts = java.util.Arrays.copyOf(pts, pts.length * 2);
                    }
                    pts[n++] = x;
                    pts[n++] = z;
                }
            }
        }
        return java.util.Arrays.copyOf(pts, n);
    }

    /** Coast to coast, each continent's nearest neighbour is a short flight away: mostly 400-800 blocks. */
    @Test
    void gapsBetweenNeighbouringContinents() {
        int inRange = 0;
        int total = 0;
        double smallest = Double.MAX_VALUE;
        double largest = 0.0;
        int step = 16;
        for (long seed : SEEDS) {
            Layout layout = new Layout(seed, P);
            List<int[]> all = continents(layout, 7);
            List<double[]> coasts = new ArrayList<>();
            for (int[] c : all) {
                coasts.add(coast(layout, layout.region(c[0], c[1]), c[2], step));
            }
            for (int a = 0; a < all.size(); a++) {
                RegionLayout ra = layout.region(all.get(a)[0], all.get(a)[1]);
                int ia = all.get(a)[2];
                if (Math.abs(ra.cx[ia]) > 5 * P.layoutRegionSize() || Math.abs(ra.cz[ia]) > 5 * P.layoutRegionSize()) {
                    continue;
                }
                double nearest = Double.MAX_VALUE;
                double[] pa = coasts.get(a);
                for (int b = 0; b < all.size(); b++) {
                    RegionLayout rb = layout.region(all.get(b)[0], all.get(b)[1]);
                    int ib = all.get(b)[2];
                    if (b == a || Math.hypot(ra.cx[ia] - rb.cx[ib], ra.cz[ia] - rb.cz[ib]) > 2.5 * P.continentCellSize()) {
                        continue;
                    }
                    double[] pb = coasts.get(b);
                    for (int u = 0; u < pa.length; u += 2) {
                        for (int v = 0; v < pb.length; v += 2) {
                            double dx = pa[u] - pb[v];
                            double dz = pa[u + 1] - pb[v + 1];
                            nearest = Math.min(nearest, dx * dx + dz * dz);
                        }
                    }
                }
                nearest = Math.sqrt(nearest) - step;
                total++;
                if (nearest >= GAP_MIN && nearest <= GAP_MAX) {
                    inRange++;
                }
                smallest = Math.min(smallest, nearest);
                largest = Math.max(largest, nearest);
            }
        }
        System.out.printf("LayoutTest: nearest gaps %d/%d in range, %.0f .. %.0f%n", inRange, total, smallest, largest);
        assertTrue(inRange >= 0.8 * total, "only " + inRange + " of " + total + " nearest gaps in range");
        assertTrue(smallest >= 250.0, "gap of " + smallest);
    }

    /** Silhouettes, composite, elevated and massif shares; starter low and level. */
    @Test
    void continentKindsAndHeights() {
        int[] silhouettes = new int[ContinentShape.SILHOUETTE_NAMES.length];
        int total = 0;
        int beyondHaven = 0;
        int elevated = 0;
        int massifs = 0;
        int spires = 0;
        for (long seed : SEEDS) {
            Layout layout = new Layout(seed, P);
            for (int[] c : continents(layout, 12)) {
                RegionLayout r = layout.region(c[0], c[1]);
                int off = r.paramOffset[c[2]];
                double[] q = r.params;
                if ((r.flags[c[2]] & RegionLayout.FLAG_STARTER) != 0) {
                    assertEquals(ContinentShape.IRREGULAR, (int) q[off + ContinentShape.SILHOUETTE]);
                    assertTrue(q[off + ContinentShape.BASE_Y] >= P.starterBaseYMin() && q[off + ContinentShape.BASE_Y] <= P.starterBaseYMax());
                    assertEquals(0.0, q[off + ContinentShape.ELEVATED]);
                    continue;
                }
                total++;
                silhouettes[(int) q[off + ContinentShape.SILHOUETTE]]++;
                beyondHaven++;
                if (q[off + ContinentShape.ELEVATED] > 0.0) {
                    elevated++;
                }
                if (q[off + ContinentShape.MASSIF_PEAK] > 0.0) {
                    massifs++;
                }
                spires += (int) q[off + ContinentShape.SPIRE_COUNT];
                assertTrue(q[off + ContinentShape.MAX_Y] <= P.continentMassifPeakYMax(), "summit limit");
                int members = (int) q[off + ContinentShape.MEMBER_COUNT];
                for (int m = 1; m < members; m++) {
                    double step = Math.abs(q[off + ContinentShape.MEMBER_BASE0 + m] - q[off + ContinentShape.MEMBER_BASE0]);
                    assertTrue(step >= P.continentMemberStepMin() - 1e-9 && step <= P.continentMemberStepMax() + 1e-9, "member step " + step);
                }
            }
        }
        double composite = (double) silhouettes[ContinentShape.COMPOSITE] / total;
        System.out.printf("LayoutTest: %d continents, silhouettes %s, elevated %.3f, massifs %.3f, spires %.1f per continent%n",
                total, java.util.Arrays.toString(silhouettes), (double) elevated / beyondHaven, (double) massifs / total,
                (double) spires / total);
        assertTrue(composite >= 0.25 && composite <= 0.35, "composite share " + composite);
        for (int k = 0; k < silhouettes.length; k++) {
            assertTrue(silhouettes[k] > 0, "no " + ContinentShape.SILHOUETTE_NAMES[k]);
        }
        double e = (double) elevated / beyondHaven;
        assertTrue(e >= 0.1 && e <= 0.2, "elevated share " + e);
        double m = (double) massifs / total;
        assertTrue(m >= 0.12 && m <= 0.32, "massif share " + m);
        assertTrue(spires >= 2.5 * total, "too few spires: " + spires);
    }

    /**
     * No loose blocks in 3D: around coasts, links and spires of real continents, every
     * solid piece of sky island is connected to the window border (the island beyond).
     */
    @Test
    void noDetachedSolidsIn3D() {
        int windows = 0;
        int failing = 0;
        int worst = 0;
        String where = "";
        for (long seed : SEEDS) {
            Layout layout = new Layout(seed, P);
            for (int[] c : continents(layout, 2)) {
                RegionLayout r = layout.region(c[0], c[1]);
                int i = c[2];
                int off = r.paramOffset[i];
                List<int[]> centres = new ArrayList<>();
                double[] pts = coast(layout, r, i, 8);
                for (int k = 0; k < 6 && pts.length > 0; k++) {
                    int u = 2 * (int) ((long) (k * 7919 + 13) % (pts.length / 2));
                    centres.add(new int[]{(int) pts[u], (int) pts[u + 1]});
                }
                for (int s = 0; s < (int) r.params[off + ContinentShape.SPIRE_COUNT]; s++) {
                    int o = off + ContinentShape.SPIRE0 + ContinentShape.SPIRE_STRIDE * s;
                    centres.add(new int[]{(int) r.params[o], (int) r.params[o + 1], 1});
                }
                for (int l = 0; l < (int) r.params[off + ContinentShape.LINK_COUNT]; l++) {
                    int o = off + ContinentShape.LINK0 + ContinentShape.LINK_STRIDE * l;
                    centres.add(new int[]{(int) (0.5 * (r.params[o] + r.params[o + 2])), (int) (0.5 * (r.params[o + 1] + r.params[o + 3])), 2});
                }
                if (r.params[off + ContinentShape.MASSIF_PEAK] > 0.0) {
                    centres.add(new int[]{(int) r.params[off + ContinentShape.MASSIF_X], (int) r.params[off + ContinentShape.MASSIF_Z], 3});
                }
                centres.add(new int[]{r.cx[i], r.cz[i], 3});
                for (int[] w : centres) {
                    int detached = detachedBlocks(layout, w[0], w[1], 48);
                    windows++;
                    if (detached > 0) {
                        failing++;
                    }
                    if (detached > worst) {
                        worst = detached;
                        where = "seed " + seed + " around " + w[0] + "," + w[1];
                    }
                }
            }
        }
        System.out.println("LayoutTest: " + windows + " 3D windows, " + failing + " with loose pieces, largest detached piece " + worst + " blocks " + where);
        assertTrue(windows > 50, "too few windows " + windows);
        assertEquals(0, worst, "detached solid " + where);
    }

    /**
     * Blocks of the largest solid piece that does not reach the border of a window of
     * half-size h, as the game builds it: sky islands interpolated from cell corners
     * (4 x 8 x 4, column values flat-cached at the corners), spires per block.
     */
    private static int detachedBlocks(Layout layout, int cx, int cz, int h) {
        int x0 = ((cx - h) & ~3);
        int z0 = ((cz - h) & ~3);
        int n = ((2 * h) & ~3) + 1;
        int y0 = 104;
        int ny = 320 - y0;
        int cn = (n - 1) / 4 + 1;
        int cy = (ny - 1) / 8 + 1;
        double[] corner = new double[cn * cn * cy];
        long noiseSeed = layout.detailSeed();
        for (int a = 0; a < cn; a++) {
            for (int b = 0; b < cn; b++) {
                int x = x0 + 4 * a;
                int z = z0 + 4 * b;
                double edge = layout.column(x, z, Layout.EDGE);
                double top = layout.column(x, z, Layout.TOP);
                double bottom = layout.column(x, z, Layout.BOTTOM);
                double detail = layout.column(x, z, Layout.DETAIL);
                for (int c = 0; c < cy; c++) {
                    corner[(a * cn + b) * cy + c] = edge <= Layout.NO_EDGE ? SkyColumn.MIN
                            : SkyColumn.density(P, noiseSeed, x, y0 + 8 * c, z, edge, top, bottom, detail);
                }
            }
        }
        boolean[] solid = new boolean[n * n * ny];
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                int x = x0 + a;
                int z = z0 + b;
                int ca = Math.min(a / 4, cn - 2);
                int cb = Math.min(b / 4, cn - 2);
                double fa = (a - 4 * ca) / 4.0;
                double fb = (b - 4 * cb) / 4.0;
                double floor = layout.detailFloor(x & ~3, z & ~3);
                for (int y = y0; y < 320; y++) {
                    int cc = Math.min((y - y0) / 8, cy - 2);
                    double fc = (y - y0 - 8 * cc) / 8.0;
                    double v = 0.0;
                    for (int da = 0; da < 2; da++) {
                        for (int db = 0; db < 2; db++) {
                            for (int dc = 0; dc < 2; dc++) {
                                double w = (da == 0 ? 1 - fa : fa) * (db == 0 ? 1 - fb : fb) * (dc == 0 ? 1 - fc : fc);
                                v += w * corner[((ca + da) * cn + cb + db) * cy + cc + dc];
                            }
                        }
                    }
                    solid[(a * n + b) * ny + (y - y0)] = v > 0.0
                            || (y >= floor && y <= floor + 48 && layout.detailDensity(x, y, z) > 0.0);
                }
            }
        }
        boolean[] seen = new boolean[solid.length];
        int[] queue = new int[solid.length];
        int worst = 0;
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
                int ab = v / ny;
                int a = ab / n;
                int b = ab % n;
                if (a == 0 || b == 0 || a == n - 1 || b == n - 1) {
                    border = true;
                }
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
                worst = Math.max(worst, tail);
            }
        }
        return worst;
    }
}
