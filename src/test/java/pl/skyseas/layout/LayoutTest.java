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
import pl.skyseas.shape.SkyColumn;

class LayoutTest {
    private static final WorldParams P = WorldParams.DEFAULTS;
    private static final long[] SEEDS = {20261125L, -4172144997902289642L, 1L};

    @Test
    void sameRegionRegardlessOfOrderAndThreads() throws Exception {
        long seed = SEEDS[0];
        List<int[]> keys = new ArrayList<>();
        for (int rx = -6; rx <= 6; rx++) {
            for (int rz = -6; rz <= 6; rz++) {
                keys.add(new int[]{rx, rz});
            }
        }
        Layout sequential = new Layout(seed, P);
        List<RegionLayout> expected = new ArrayList<>();
        for (int[] k : keys) {
            expected.add(sequential.region(k[0], k[1]));
        }

        Layout parallel = new Layout(seed, P);
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
            assertArrayEquals(a.bucketStart, b.bucketStart);
            assertArrayEquals(a.bucketItems, b.bucketItems);
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
                assertEquals(0.0, layout.column(x, 0, Layout.MOUNTAIN), 1e-9, "mountain on the plateau at " + x);
            }
        }
    }

    @Test
    void continentsNeverOverlap() {
        Layout layout = new Layout(SEEDS[1], P);
        double margin = 2 * LayoutGenerator.indexMargin(P);
        for (int rx = -10; rx <= 10; rx++) {
            for (int rz = -10; rz <= 10; rz++) {
                RegionLayout r = layout.region(rx, rz);
                for (int i = 0; i < r.count; i++) {
                    for (int j = i + 1; j < r.count; j++) {
                        double d = Math.hypot(r.cx[i] - r.cx[j], r.cz[i] - r.cz[j]);
                        assertTrue(d > r.radius[i] + r.radius[j] + margin, "continents too close: " + d);
                    }
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
                    double mountain = layout.column(x, z, Layout.MOUNTAIN);
                    for (int y = 64; y < 320; y++) {
                        if (SkyColumn.density(P, noiseSeed, x, y, z, edge, top, bottom, mountain) > 0.0) {
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
                        if (layout.column(x, z, Layout.MOUNTAIN) < 1.0) {
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
                    assertTrue(top <= P.continentPeakMaxY() && top >= P.continentMinBottomY(), "top " + top);
                    assertTrue(bottom >= P.continentMinBottomY(), "bottom " + bottom);
                }
            }
        }
    }

    /**
     * Natural edges, no rubble: a continent may shed a few offshore islets (coves cutting
     * a headland), but no small fragments, and almost all of its area is one piece.
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
                        if (r.cx[i] < r.originX || r.cx[i] >= r.originX + P.layoutRegionSize()
                                || r.cz[i] < r.originZ || r.cz[i] >= r.originZ + P.layoutRegionSize()) {
                            continue; // each continent once, in the region of its centre
                        }
                        int[] result = pieces(r, i, step);
                        worstDetached = Math.max(worstDetached, 1.0 - (double) result[0] / result[1]);
                        smallest = Math.min(smallest, result[2]);
                    }
                }
            }
        }
        assertTrue(worstDetached < 0.03, "detached area fraction " + worstDetached);
        assertTrue(smallest >= 16, "rubble: detached piece of " + smallest + " cells of " + step + "x" + step);
    }

    /** {largest piece, total, smallest detached piece} in cells. */
    private static int[] pieces(RegionLayout r, int i, int step) {
        int off = i * r.paramStride;
        int n = (int) Math.ceil(r.radius[i] / step) * 2 + 1;
        boolean[] inside = new boolean[n * n];
        int total = 0;
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                double x = r.cx[i] + (a - n / 2) * step;
                double z = r.cz[i] + (b - n / 2) * step;
                if (ContinentShape.edge(P, r.params, off, r.shapeSeed[i], x, z) > 0.0) {
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
            LayoutGenerator.generate(SEEDS[0], P, i, -i);
        }
        long worst = 0;
        for (int i = 0; i < 100; i++) {
            long t0 = System.nanoTime();
            LayoutGenerator.generate(warm.seed(), P, 1000 + i, 7 * i);
            worst = Math.max(worst, System.nanoTime() - t0);
        }
        assertTrue(worst < 20_000_000L, "slowest region " + worst / 1e6 + " ms");
    }

    @Test
    void continentParametersAreDeterministic() {
        double[] a = new double[ContinentShape.stride(P)];
        double[] b = new double[ContinentShape.stride(P)];
        ContinentShape.place(P, 99, 10, 20, 700, false, a, 0);
        ContinentShape.place(P, 99, 10, 20, 700, false, b, 0);
        assertArrayEquals(a, b);
    }
}
