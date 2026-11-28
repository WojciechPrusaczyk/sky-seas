package pl.skyseas.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import pl.skyseas.config.WorldParams;
import pl.skyseas.shape.SimplexNoise;

class ClimateTest {
    private static final double NONE = Layout.NO_CLIMATE;

    @Test
    void layersGetTheirContinentalness() {
        double continent = RegionLayout.CONTINENT;
        // Sky island surface, its rock, air under it over deep sea, the water, the rock under the seabed.
        assertTrue(Climate.continents(continent, 150, 220, -20, 222, 0.0) >= 0.3);
        assertTrue(Climate.continents(continent, 150, 220, -20, 180, -0.5) >= 0.1);
        assertTrue(Climate.continents(continent, 150, 220, -20, 120, 0.0) <= -0.455);
        assertTrue(Climate.continents(NONE, Layout.NO_BOTTOM, Layout.NO_TOP, 40, 50, 0.0) > -0.455);
        assertTrue(Climate.continents(NONE, Layout.NO_BOTTOM, Layout.NO_TOP, -20, 0, 0.0) <= -0.455);
        assertTrue(Climate.continents(NONE, Layout.NO_BOTTOM, Layout.NO_TOP, -20, -40, -0.8) >= 0.1);
        // Ocean land: beach near the water, inland higher up, mushroom where vanilla says so.
        double beach = Climate.continents(NONE, Layout.NO_BOTTOM, Layout.NO_TOP, 64, 64, 0.0);
        assertTrue(beach > -0.19 && beach < -0.11);
        assertTrue(Climate.continents(NONE, Layout.NO_BOTTOM, Layout.NO_TOP, 80, 80, 0.0) > -0.11);
        assertTrue(Climate.continents(NONE, Layout.NO_BOTTOM, Layout.NO_TOP, 80, 80, -1.1) < -1.05);
        assertTrue(Climate.continents(RegionLayout.CHAIN, 70, 75, 75, 76, 0.0) < 0.03);
    }

    @Test
    void skyRidgesHaveNoValleys() {
        // Vanilla peaks-and-valleys: -(| |w| - 2/3 | - 1/3) * 3; river valleys need it below -0.85.
        for (double w = -1.0; w <= 1.0; w += 0.01) {
            double r = Climate.ridges(RegionLayout.CONTINENT, 150, 200, 0.0, w);
            double pv = -(Math.abs(Math.abs(r) - 2.0 / 3.0) - 1.0 / 3.0) * 3.0;
            assertTrue(pv > -0.2 && pv < 0.2, "mid peaks-and-valleys on flat sky land");
            assertEquals(w < 0.0, r < 0.0, "vanilla sign kept");
        }
        assertEquals(2.0 / 3.0, Climate.ridges(RegionLayout.CONTINENT, 150, 260, 60.0, 0.1), 1e-9);
        assertEquals(0.01, Climate.ridges(NONE, Layout.NO_BOTTOM, 70, 0.0, 0.01), 1e-9);
    }

    @Test
    void depthOnlyUnderRock() {
        double scale = WorldParams.DEFAULTS.climateDepthScale();
        assertEquals(0.0, Climate.depth(RegionLayout.CONTINENT, 150, 220, -20, 230, scale));
        assertEquals(1.1, Climate.depth(RegionLayout.CONTINENT, 100, 220, -20, 220 - (int) (1.1 * scale), scale), 1e-9);
        assertEquals(0.0, Climate.depth(NONE, Layout.NO_BOTTOM, Layout.NO_TOP, -20, 30, scale));
        // The deepest rock under the seabed stays far from deep dark (depth 1.1).
        assertTrue(Climate.depth(NONE, Layout.NO_BOTTOM, Layout.NO_TOP, -5, -64, scale) < 0.9);
    }

    @Test
    void deepDarkOnlyDeepUnderOceanLand() {
        double scale = WorldParams.DEFAULTS.climateDepthScale();
        // Ancient city anchor (Y -27) under an ocean island: deep dark erosion (below -0.375).
        assertTrue(Climate.erosion(NONE, Layout.NO_BOTTOM, Layout.NO_TOP, 75, -27, NONE, 0.0, 0.5, scale) < -0.375);
        // Under the plain seabed and under shallow ocean land: the vanilla value.
        assertEquals(0.5, Climate.erosion(NONE, Layout.NO_BOTTOM, Layout.NO_TOP, -10, -60, NONE, 0.0, 0.5, scale), 1e-9);
        assertEquals(0.5, Climate.erosion(NONE, Layout.NO_BOTTOM, Layout.NO_TOP, 70, 40, NONE, 0.0, 0.5, scale), 1e-9);
    }

    @Test
    void coolingAndClusterPull() {
        assertEquals(0.3, Climate.temperature(RegionLayout.CONTINENT, 150, 180, NONE, 0.0, 0.3, 0.0), 1e-9);
        assertEquals(0.3 - 0.4, Climate.temperature(RegionLayout.CONTINENT, 150, 300, NONE, 0.0, 0.3, 0.0), 1e-9);
        assertEquals(0.3, Climate.temperature(NONE, Layout.NO_BOTTOM, 300, NONE, 0.0, 0.3, 0.0), 1e-9);
        assertEquals(0.2 * 0.5 + 0.8 * -0.5, Climate.vegetation(RegionLayout.ARCHIPELAGO, 100, 150, -0.5, 0.5), 1e-9);
        assertTrue(Climate.temperature(NONE, Layout.NO_BOTTOM, 40, NONE, 1.0, -0.2, 0.0) > 0.55);
        // Frozen water only in patches: elsewhere cold.
        assertEquals(-0.8, Climate.temperature(NONE, Layout.NO_BOTTOM, 40, NONE, 0.0, -0.8, 0.3), 1e-9);
        assertEquals(-0.44, Climate.temperature(NONE, Layout.NO_BOTTOM, 40, NONE, 0.0, -0.8, -0.3), 1e-9);
        assertEquals(-0.8, Climate.temperature(RegionLayout.CONTINENT, 150, 180, NONE, 0.0, -0.8, -0.3), 1e-9);
        assertTrue(Climate.erosion(RegionLayout.CONTINENT, 100, 230, -20, 228, NONE, 60.0, 0.5, 100.0) < -0.78);
        // No deep dark erosion under the seabed, but kept under thick sky rock.
        assertTrue(Climate.erosion(NONE, Layout.NO_BOTTOM, Layout.NO_TOP, -5, -60, NONE, 0.0, -0.9, 100.0) > -0.375);
        assertEquals(-0.9, Climate.erosion(RegionLayout.CONTINENT, 50, 230, -20, 100, NONE, 0.0, -0.9, 100.0), 1e-9);
    }

    @Test
    void clustersCarryOnePointAndSpawnHasWarmSea() {
        ClimateProbe probe = new ClimateProbe() {
            @Override
            public double temperature(int x, int z) {
                return SimplexNoise.noise2(1, x / 3000.0, z / 3000.0);
            }

            @Override
            public double vegetation(int x, int z) {
                return SimplexNoise.noise2(2, x / 3000.0, z / 3000.0);
            }
        };
        Layout layout = new Layout(20261125L, WorldParams.DEFAULTS, probe);
        int clusters = 0;
        for (int rx = -2; rx <= 1; rx++) {
            for (int rz = -2; rz <= 1; rz++) {
                RegionLayout r = layout.region(rx, rz);
                for (int i = 0; i < r.count; i++) {
                    boolean cluster = r.type[i] == RegionLayout.ARCHIPELAGO || r.type[i] == RegionLayout.HALLELUJAH;
                    assertEquals(cluster, !Float.isNaN(r.climate[3 * i]), "item " + i + " type " + r.type[i]);
                    clusters += cluster ? 1 : 0;
                }
            }
        }
        assertTrue(clusters > 0);
        boolean warm = false;
        for (int a = 0; a < 64 && !warm; a++) {
            warm = layout.warmBias((int) (720 * Math.cos(a * Math.PI / 32)), (int) (720 * Math.sin(a * Math.PI / 32))) >= 1.0;
        }
        assertTrue(warm, "no warm sea around the guaranteed island");
    }
}
