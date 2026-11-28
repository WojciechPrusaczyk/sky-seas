package pl.skyseas.layout;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import pl.skyseas.config.WorldParams;
import pl.skyseas.shape.ContinentShape;

class RiverTest {
    private static final WorldParams P = WorldParams.DEFAULTS;

    @Test
    void riversRunDownToTheCoast() {
        int continents = 0;
        int rivers = 0;
        long points = 0;
        long t0 = System.nanoTime();
        for (long seed : new long[]{20261125L, -7998477474305010018L, 42L}) {
            Layout layout = new Layout(seed, P);
            for (int rx = -1; rx <= 1; rx++) {
                for (int rz = -1; rz <= 1; rz++) {
                    RegionLayout r = layout.region(rx, rz);
                    for (int i = 0; i < r.count; i++) {
                        if (r.type[i] != RegionLayout.CONTINENT) {
                            continue;
                        }
                        continents++;
                        int off = r.paramOffset[i];
                        int count = (int) r.params[off + ContinentShape.RIVER_COUNT];
                        for (int k = 0; k < count; k++) {
                            rivers++;
                            points += (long) r.params[off + ContinentShape.RIVER0 + ContinentShape.RIVER_STRIDE * k];
                            check(r.params, off, r.shapeSeed[i], off + ContinentShape.RIVER0 + ContinentShape.RIVER_STRIDE * k);
                        }
                    }
                }
            }
        }
        System.out.printf("RIVERS %d continents, %d rivers, mean length %.0f blocks, %.0f ms%n", continents, rivers,
                8.0 * points / Math.max(1, rivers), (System.nanoTime() - t0) / 1e6);
        assertTrue(rivers >= 1.5 * continents, "about two rivers per continent");
    }

    private static void check(double[] prm, int off, long seed, int o) {
        int n = (int) prm[o];
        assertTrue(n >= 6);
        double prev = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            int q = o + ContinentShape.RIVER_HEADER + ContinentShape.RIVER_POINT * i;
            double level = prm[q + 2];
            assertTrue(level <= prev, "level never rises downstream");
            prev = level;
            if (i > 0 && i < n - 1) {
                double terrain = ContinentShape.column(P, prm, off, seed, prm[q], prm[q + 1], ContinentShape.TERRAIN);
                double top = ContinentShape.column(P, prm, off, seed, prm[q], prm[q + 1], ContinentShape.TOP);
                assertTrue(top < level, "bed below the water on the axis");
                assertTrue(terrain >= level, "water never above the original terrain");
            }
        }
        int last = o + ContinentShape.RIVER_HEADER + ContinentShape.RIVER_POINT * (n - 1);
        assertTrue(ContinentShape.edge(P, prm, off, seed, prm[last], prm[last + 1]) < 1.0, "mouth at the coast");
    }
}
