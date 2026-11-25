package pl.skyseas.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import pl.skyseas.config.WorldParams;

class ZoneFieldTest {
    private static final WorldParams P = WorldParams.DEFAULTS;

    @Test
    void zeroAtSpawnAndPlateauBeyondMaxRadius() {
        assertEquals(0.0, ZoneField.factor(P, 0, 0, 0.0), 1e-12);
        assertEquals(1.0, ZoneField.factor(P, 8000, 0, 0.0), 1e-12);
        assertEquals(1.0, ZoneField.factor(P, 50_000, -50_000, 0.0), 1e-12);
        assertEquals(1.0, ZoneField.factor(P, 9000, 0, -1.0), 1e-12, "jitter must not break the plateau far out");
    }

    @Test
    void monotonicWithDistanceAndBounded() {
        double prev = -1.0;
        for (int d = 0; d <= 10_000; d += 50) {
            double f = ZoneField.factor(P, d, 0, 0.0);
            assertTrue(f >= prev, "not monotonic at " + d);
            assertTrue(f >= 0.0 && f <= 1.0);
            prev = f;
        }
    }

    @Test
    void jitterShiftsBordersBothWays() {
        double plain = ZoneField.factor(P, 3000, 0, 0.0);
        assertTrue(ZoneField.factor(P, 3000, 0, 1.0) > plain);
        assertTrue(ZoneField.factor(P, 3000, 0, -1.0) < plain);
    }
}
