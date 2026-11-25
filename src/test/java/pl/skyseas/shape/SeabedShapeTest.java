package pl.skyseas.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import pl.skyseas.config.WorldParams;
import pl.skyseas.layout.ZoneField;

class SeabedShapeTest {
    private static final WorldParams P = WorldParams.DEFAULTS;
    private static final double FROZEN = -0.6;
    private static final double TEMPERATE = 0.0;
    private static final double WARM = 0.6;
    private static final double ON_LINE = 0.0;
    private static final double OFF_LINE = 0.9;

    /** Height with relief, dunes and canyon noise neutral. */
    private static double flat(double zone, double temperature, double trench) {
        return SeabedShape.height(P, zone, temperature, 0.0, 0.0, 0.0, OFF_LINE, trench);
    }

    @Test
    void baseDepthGrowsFromSpawnToPlateau() {
        assertEquals(-5.0, flat(0.0, WARM, OFF_LINE), 1e-9);
        assertEquals(-20.0, flat(1.0, WARM, OFF_LINE), 1e-9);
    }

    @Test
    void reliefIsAsymmetricAndRegionallyModulated() {
        double rough = 1.0;
        double calm = -1.0;
        assertEquals(-5.0 + P.seabedReliefUp(), SeabedShape.height(P, 0.0, WARM, 1.0, rough, 0.0, OFF_LINE, OFF_LINE), 1e-9);
        assertEquals(-5.0 - P.seabedReliefDown(), SeabedShape.height(P, 0.0, WARM, -1.0, rough, 0.0, OFF_LINE, OFF_LINE), 1e-9);
        assertEquals(-5.0 - P.seabedReliefDown() * P.seabedReliefMinStrength(),
                SeabedShape.height(P, 0.0, WARM, -1.0, calm, 0.0, OFF_LINE, OFF_LINE), 1e-9);
    }

    @Test
    void canyonsOnlyInTemperateWater() {
        double plain = flat(0.5, TEMPERATE, OFF_LINE);
        double inCanyon = SeabedShape.height(P, 0.5, TEMPERATE, 0.0, 0.0, 0.0, ON_LINE, OFF_LINE);
        assertEquals(ShapeMath.lerp(P.seabedCanyonDepthNear(), P.seabedCanyonDepthFar(), 0.5), plain - inCanyon, 1e-9);
        assertEquals(flat(0.5, WARM, OFF_LINE), SeabedShape.height(P, 0.5, WARM, 0.0, 0.0, 0.0, ON_LINE, OFF_LINE), 1e-9);
        assertEquals(flat(0.5, FROZEN, OFF_LINE), SeabedShape.height(P, 0.5, FROZEN, 0.0, 0.0, 0.0, ON_LINE, OFF_LINE), 1e-9);
    }

    @Test
    void trenchesReachMinYOnlyInColdWaterAtPlateau() {
        assertEquals(P.seabedTrenchMinY(), flat(1.0, FROZEN, ON_LINE), 1e-9);
        assertEquals(-20.0, flat(1.0, WARM, ON_LINE), 1e-9, "no trenches in warm water");
        assertEquals(-20.0, flat(1.0, TEMPERATE, ON_LINE), 1e-9, "no trenches in temperate water");
    }

    @Test
    void noTrenchesInHaven() {
        double havenEdge = ZoneField.factorAtDistance(P, P.zoneRadii()[0]);
        for (double zone = 0.0; zone <= havenEdge; zone += havenEdge / 10) {
            assertEquals(flat(zone, FROZEN, OFF_LINE), flat(zone, FROZEN, ON_LINE), 1e-9, "trench in Haven at zone " + zone);
        }
    }

    @Test
    void alwaysWithinDeclaredRange() {
        for (double zone = 0.0; zone <= 1.0; zone += 0.1) {
            for (double temp = -1.0; temp <= 1.0; temp += 0.1) {
                for (double n = -2.0; n <= 2.0; n += 0.25) {
                    for (double line = -0.2; line <= 0.2; line += 0.05) {
                        double h = SeabedShape.height(P, zone, temp, n, -n, n, line, line);
                        assertTrue(h >= SeabedShape.minHeight(P) && h <= SeabedShape.maxHeight(P), "out of range: " + h);
                    }
                }
            }
        }
    }

    @Test
    void canyonAndTrenchWallsAreContinuous() {
        double[] temps = {TEMPERATE, FROZEN};
        for (double temp : temps) {
            double prev = SeabedShape.height(P, 1.0, temp, 0.0, 0.0, 0.0, -0.2, -0.2);
            for (double line = -0.2; line <= 0.2; line += 0.0005) {
                double h = SeabedShape.height(P, 1.0, temp, 0.0, 0.0, 0.0, line, line);
                assertTrue(Math.abs(h - prev) < 1.0, "jump in wall at " + line + " temp " + temp);
                prev = h;
            }
        }
    }
}
