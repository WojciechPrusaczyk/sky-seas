package pl.skyseas.shape;

import org.junit.jupiter.api.Test;
import pl.skyseas.config.WorldParams;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SeabedProvincesTest {
    private static final WorldParams P = WorldParams.DEFAULTS;
    private static final long SEED = 20261127L;
    /** E1 seabed without its vanilla noises: the provinces alone must break the flatness. */
    private static final double BASE = -10.0;

    @Test
    void provincesShareTheSeabedAndPlainsAreRare() {
        int[] count = new int[SeabedProvinces.NAMES.length];
        int n = 0;
        for (int x = -12000; x <= 12000; x += 60) {
            for (int z = -12000; z <= 12000; z += 60) {
                count[SeabedProvinces.dominant(P, SEED, 0.0, x, z)]++;
                n++;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < count.length; k++) {
            sb.append(SeabedProvinces.NAMES[k]).append(' ').append(String.format(java.util.Locale.ROOT, "%.3f ", (double) count[k] / n));
        }
        System.out.println("SeabedProvincesTest: " + sb);
        double plains = (double) count[SeabedProvinces.PLAINS] / n;
        assertTrue(plains >= 0.05 && plains <= 0.16, "plains " + plains);
        for (int k = 0; k < SeabedProvinces.PLAINS; k++) {
            assertTrue((double) count[k] / n >= 0.08, SeabedProvinces.NAMES[k] + " " + count[k]);
        }
    }

    /** Few flat windows: a 64x64 window counts as flat when its heights span under 6 blocks. */
    @Test
    void seabedIsRarelyFlat() {
        int windows = 0;
        int flat = 0;
        double lowest = 1000.0;
        double highest = -1000.0;
        for (int wx = -6000; wx < 6000; wx += 256) {
            for (int wz = -6000; wz < 6000; wz += 256) {
                double lo = 1000.0;
                double hi = -1000.0;
                for (int x = wx; x < wx + 64; x += 4) {
                    for (int z = wz; z < wz + 64; z += 4) {
                        double h = SeabedProvinces.height(P, SEED, 0.0, x, z, BASE);
                        lo = Math.min(lo, h);
                        hi = Math.max(hi, h);
                    }
                }
                windows++;
                flat += hi - lo < 6.0 ? 1 : 0;
                lowest = Math.min(lowest, lo);
                highest = Math.max(highest, hi);
            }
        }
        double share = (double) flat / windows;
        System.out.printf(java.util.Locale.ROOT, "SeabedProvincesTest: flat windows %.3f, height %.1f..%.1f%n", share, lowest, highest);
        assertTrue(share <= 0.2, "flat share " + share);
        assertTrue(lowest <= P.seabedForms().canyonFloorY() + 3.0, "no canyon reaches its floor: " + lowest);
        assertTrue(lowest >= SeabedShape.minHeight(P), "below the lowest seabed: " + lowest);
        assertTrue(highest <= P.seabedForms().maxY(), "over the cap: " + highest);
    }

    @Test
    void conesAndPillarsStandOut() {
        int cones = 0;
        int pillars = 0;
        for (int x = -4000; x <= 4000; x += 8) {
            for (int z = -4000; z <= 4000; z += 8) {
                int k = SeabedProvinces.dominant(P, SEED, 0.0, x, z);
                double rise = SeabedProvinces.height(P, SEED, 0.0, x, z, BASE) - BASE;
                cones += k == SeabedProvinces.VOLCANIC && rise > 20.0 ? 1 : 0;
                pillars += k == SeabedProvinces.ROCKY && rise > 25.0 ? 1 : 0;
            }
        }
        System.out.println("SeabedProvincesTest: tall cone samples " + cones + ", tall pillar samples " + pillars);
        assertTrue(cones > 100 && pillars > 100, "cones " + cones + ", pillars " + pillars);
    }
}
