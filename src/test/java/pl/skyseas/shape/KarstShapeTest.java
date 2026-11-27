package pl.skyseas.shape;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KarstShapeTest {
    @Test
    void wellsDropIntoHalls() {
        int shafts = 0;
        int halls = 0;
        for (int x = -2000; x < 2000; x += 4) {
            for (int z = -2000; z < 2000; z += 4) {
                shafts += KarstShape.density(7L, x, -25, z) < 0.0 ? 1 : 0;
                halls += KarstShape.density(7L, x, -44, z) < 0.0 ? 1 : 0;
            }
        }
        // Area in 4x4 samples: shafts are narrow, halls wide.
        assertTrue(shafts > 200, "shafts " + shafts);
        assertTrue(halls > shafts, "halls " + halls + " vs shafts " + shafts);
        assertEquals(KarstShape.density(7L, 123, -30, 456), KarstShape.density(7L, 123, -30, 456));
    }
}
