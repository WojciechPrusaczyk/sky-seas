package pl.skyseas.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import pl.skyseas.config.WorldParams;

class SeabedColumnTest {
    private static final WorldParams P = WorldParams.DEFAULTS;

    @Test
    void densitySignMatchesSurface() {
        assertTrue(SeabedColumn.density(P, -10.5, -11) > 0.0);
        assertTrue(SeabedColumn.density(P, -10.5, -10) < 0.0);
    }

    @Test
    void roofIsTwelveSolidBlocksIncludingTopBlock() {
        double h = -10.5;
        int solidInRoofOrTop = 0;
        for (int y = 63; y >= -64; y--) {
            double d = SeabedColumn.density(P, h, y);
            boolean top = d > 0.0 && !SeabedColumn.inRoof(P, d) && d * P.seabedDensityScale() <= 1.0;
            if (SeabedColumn.inRoof(P, d) || top) {
                solidInRoofOrTop++;
            }
        }
        assertEquals((int) P.caveRoofThickness(), solidInRoofOrTop);
    }

    @Test
    void caveMaskClosedInRoofOpenBelowRamp() {
        double h = -10.0;
        assertEquals(0.0, SeabedColumn.caveMask(P, h, -15), 0.0);
        assertEquals(0.0, SeabedColumn.caveMask(P, h, (int) (h - P.caveRoofThickness())), 0.0);
        assertEquals(1.0, SeabedColumn.caveMask(P, h, (int) (h - P.caveRoofThickness() - P.caveMaskRamp())), 0.0);
        assertFalse(SeabedColumn.inRoof(P, SeabedColumn.density(P, h, 0)));
    }

    @Test
    void cheeseBiasShrinksCavesUnderThinRockOnly() {
        double thin = SeabedColumn.caveCheeseBias(P, P.worldMinY() + P.caveThinRock());
        double thick = SeabedColumn.caveCheeseBias(P, P.worldMinY() + P.caveThickRock());
        assertEquals(P.caveMaxCheeseBias() * P.caveThicknessScale(), thin, 1e-12);
        assertEquals(0.0, thick, 1e-12);
    }

    @Test
    void biomeDepthZeroInWater() {
        assertEquals(0.0, SeabedColumn.biomeDepth(P, -10.0, 40), 0.0);
        assertEquals(1.1, SeabedColumn.biomeDepth(P, 100.0, -10), 1e-12);
    }
}
