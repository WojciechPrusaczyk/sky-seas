package pl.skyseas.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import pl.skyseas.config.WorldParams;
import pl.skyseas.layout.ZoneField;

class SeabedShapeTest {
    private static final WorldParams P = WorldParams.DEFAULTS;
    private static final double COLD = -0.6;
    private static final double WARM = 0.6;
    private static final double ON_TRENCH = 0.0;
    private static final double OFF_TRENCH = 0.9;

    @Test
    void baseDepthGrowsFromSpawnToPlateau() {
        assertEquals(-5.0, SeabedShape.height(P, 0.0, WARM, 0.0, OFF_TRENCH), 1e-9);
        assertEquals(-20.0, SeabedShape.height(P, 1.0, WARM, 0.0, OFF_TRENCH), 1e-9);
    }

    @Test
    void outsideTrenchesStaysInDesignRangePlusDunes() {
        for (double zone = 0.0; zone <= 1.0; zone += 0.05) {
            for (double dunes = -1.5; dunes <= 1.5; dunes += 0.25) {
                for (double temp = -1.0; temp <= 1.0; temp += 0.25) {
                    double h = SeabedShape.height(P, zone, temp, dunes, OFF_TRENCH);
                    assertTrue(h <= -5.0 + P.seabedDuneAmplitude() + 1e-9, "too shallow: " + h);
                    assertTrue(h >= -20.0 - P.seabedDuneAmplitude() - 1e-9, "too deep: " + h);
                }
            }
        }
    }

    @Test
    void trenchesReachMinYOnlyInColdWaterAtPlateau() {
        assertEquals(P.seabedTrenchMinY(), SeabedShape.height(P, 1.0, COLD, 0.0, ON_TRENCH), 1e-9);
        assertEquals(-20.0, SeabedShape.height(P, 1.0, WARM, 0.0, ON_TRENCH), 1e-9, "no trenches in warm water");
    }

    @Test
    void noTrenchesInHaven() {
        double havenEdge = ZoneField.factorAtDistance(P, P.zoneRadii()[0]);
        for (double zone = 0.0; zone <= havenEdge; zone += havenEdge / 10) {
            double base = SeabedShape.height(P, zone, WARM, 0.0, OFF_TRENCH);
            assertEquals(base, SeabedShape.height(P, zone, COLD, 0.0, ON_TRENCH), 1e-9, "trench in Haven at zone " + zone);
        }
    }

    @Test
    void neverBelowTrenchMinYAndWithinDeclaredRange() {
        for (double zone = 0.0; zone <= 1.0; zone += 0.1) {
            for (double trench = -0.2; trench <= 0.2; trench += 0.01) {
                for (double dunes = -2.0; dunes <= 2.0; dunes += 0.5) {
                    double h = SeabedShape.height(P, zone, COLD, dunes, trench);
                    assertTrue(h >= SeabedShape.minHeight(P) && h <= SeabedShape.maxHeight(P), "out of range: " + h);
                }
            }
        }
    }

    @Test
    void trenchWallsAreContinuous() {
        double prev = SeabedShape.height(P, 1.0, COLD, 0.0, -0.2);
        for (double trench = -0.2; trench <= 0.2; trench += 0.0005) {
            double h = SeabedShape.height(P, 1.0, COLD, 0.0, trench);
            assertTrue(Math.abs(h - prev) < 1.0, "jump in trench wall at " + trench);
            prev = h;
        }
    }
}
