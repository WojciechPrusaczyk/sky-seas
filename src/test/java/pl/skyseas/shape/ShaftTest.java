package pl.skyseas.shape;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import pl.skyseas.config.WorldParams;
import pl.skyseas.layout.Layout;
import pl.skyseas.layout.RegionLayout;

/** Cenotes stand on island land, blue holes on the shelf; every shaft is open from its mouth down into its chamber. */
class ShaftTest {
    /** Spawn guarantee: a large ocean island with a cenote within 1000 blocks. */
    @Test
    void cenoteNearSpawn() {
        for (long seed : new long[]{20261125L, -4172144997902289642L, 1L, 77L}) {
            Layout layout = new Layout(seed, WorldParams.DEFAULTS);
            boolean found = false;
            for (int rx = -1; rx <= 0 && !found; rx++) {
                for (int rz = -1; rz <= 0 && !found; rz++) {
                    RegionLayout r = layout.region(rx, rz);
                    for (int i = 0; i < r.count; i++) {
                        found |= r.type[i] == RegionLayout.SHAFT && ShaftShape.cenote(r.params, r.paramOffset[i])
                                && Math.hypot(r.cx[i], r.cz[i]) < 1000.0;
                    }
                }
            }
            assertTrue(found, "no cenote within 1000 blocks of spawn, seed " + seed);
        }
    }

    @Test
    void shaftsOpenIntoChambers() {
        Layout layout = new Layout(20261125L, WorldParams.DEFAULTS, (x, z) -> 0.9 * SimplexNoise.noise2(7, x / 2500.0, z / 2500.0));
        int cenotes = 0;
        int holes = 0;
        int holesOnShelf = 0;
        for (int rx = -6; rx <= 6; rx++) {
            for (int rz = -6; rz <= 6; rz++) {
                RegionLayout r = layout.region(rx, rz);
                for (int i = 0; i < r.count; i++) {
                    if (r.type[i] != RegionLayout.SHAFT || !r.owns(i, WorldParams.DEFAULTS.layoutRegionSize())) {
                        continue;
                    }
                    int o = r.paramOffset[i];
                    int x = (int) Math.round(r.params[o]);
                    int z = (int) Math.round(r.params[o + 1]);
                    double surface = layout.oceanHeight(x, z, -10.0);
                    boolean cenote = ShaftShape.cenote(r.params, o);
                    int mouth = (int) Math.floor(Math.min(surface, r.params[o + 3])) - 1;
                    for (int y = (int) Math.ceil(r.params[o + 4]) + 1; y <= mouth; y++) {
                        assertTrue(layout.shaftDensity(x, y, z) < 0.0, (cenote ? "cenote" : "blue hole") + " closed at "
                                + x + "," + y + "," + z);
                    }
                    if (cenote) {
                        cenotes++;
                        assertTrue(surface > 64.0, "cenote off land at " + x + "," + z + ": " + surface);
                        assertTrue(r.params[o + 4] <= 4.0, "cenote chamber not in the deepslate");
                    } else {
                        holes++;
                        holesOnShelf += surface > 28.0 && surface < 62.0 ? 1 : 0;
                    }
                }
            }
        }
        System.out.println("ShaftTest: cenotes " + cenotes + ", blue holes " + holes + " (" + holesOnShelf + " on the shelf)");
        assertTrue(cenotes > 5 && holes > 20, "cenotes " + cenotes + ", blue holes " + holes);
        assertTrue(holesOnShelf >= 0.8 * holes, "blue holes off the shelf: " + (holes - holesOnShelf));
    }
}
