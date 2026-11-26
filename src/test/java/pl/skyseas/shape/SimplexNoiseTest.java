package pl.skyseas.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SimplexNoiseTest {
    @Test
    void rangeAndContinuity2d() {
        double min = 0;
        double max = 0;
        double prev = SimplexNoise.noise2(7, 0, 3.3);
        for (double x = 0.0; x < 400.0; x += 0.01) {
            double v = SimplexNoise.noise2(7, x, 3.3 + x * 0.37);
            assertTrue(Math.abs(v - prev) < 0.1, "jump at " + x);
            prev = v;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        assertTrue(min > -1.05 && max < 1.05, "range " + min + ".." + max);
        assertTrue(min < -0.6 && max > 0.6, "too flat: " + min + ".." + max);
    }

    @Test
    void rangeAndContinuity3d() {
        double min = 0;
        double max = 0;
        double prev = SimplexNoise.noise3(-3, 0, 1.1, 2.2);
        for (double x = 0.0; x < 400.0; x += 0.01) {
            double v = SimplexNoise.noise3(-3, x, 1.1 + x * 0.29, 2.2 - x * 0.61);
            assertTrue(Math.abs(v - prev) < 0.1, "jump at " + x);
            prev = v;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        assertTrue(min > -1.05 && max < 1.05, "range " + min + ".." + max);
        assertTrue(min < -0.6 && max > 0.6, "too flat: " + min + ".." + max);
    }

    @Test
    void seedChangesOutputAndIsDeterministic() {
        assertEquals(SimplexNoise.noise2(1, 10.5, 20.25), SimplexNoise.noise2(1, 10.5, 20.25));
        assertTrue(SimplexNoise.noise2(1, 10.5, 20.25) != SimplexNoise.noise2(2, 10.5, 20.25));
    }
}
