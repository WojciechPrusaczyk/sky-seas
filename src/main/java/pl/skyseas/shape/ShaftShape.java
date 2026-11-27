package pl.skyseas.shape;

import pl.skyseas.layout.Hash;

/**
 * Shafts of ocean islands: cenotes (1-2 on a large island, a well from the surface into
 * a large dry chamber in the deepslate) and blue holes (on the shelf of medium and large
 * islands, a well from the shallows into a flooded chamber). Every shaft ends in its own
 * chamber, so it never stops in solid rock. Everything follows from the island's params
 * and seed; no state.
 */
public final class ShaftShape {
    /** Density far from any shaft (larger than any terrain density it is min-ed with). */
    public static final double NONE = 64.0;
    public static final double CENOTE_CHAMBER_Y_MIN = -8.0;
    public static final double CENOTE_CHAMBER_Y_MAX = 4.0;
    private static final double BLUE_HOLE_CHANCE = 0.5;
    private static final double SOFT = 3.0;
    /**
     * Flood override reaches this far beyond a shaft or chamber: the aquifer reads its
     * floodedness only at the centres of its 16x12x16 cells.
     */
    private static final double FLOOD_MARGIN = 16.0;
    private static final int MAX = 3;

    private ShaftShape() {
    }

    private static double u(long seed, int i) {
        return Hash.unit(Hash.hash(seed, i));
    }

    /** Number of shafts of an ocean island: cenotes first, then at most one blue hole. */
    public static int count(double[] prm, int off, long seed) {
        int size = (int) prm[off + OceanShape.SIZE_CLASS];
        if (size == 0) {
            return 0;
        }
        return cenotes(prm, off, seed) + (u(seed, 300) < BLUE_HOLE_CHANCE ? 1 : 0);
    }

    public static int cenotes(double[] prm, int off, long seed) {
        int size = (int) prm[off + OceanShape.SIZE_CLASS];
        return size < 2 || prm[off + OceanShape.RING] > 0.0 ? 0 : 1 + (u(seed, 301) < 0.5 ? 1 : 0);
    }

    /**
     * Shaft k of an island into {@code out}: {x, z, radius, topY, chamberY, chamberRadius,
     * chamberHeight, cenote (1) or blue hole (0)}.
     */
    public static void shaft(double[] prm, int off, long seed, int k, double[] out) {
        long s = Hash.hash(seed, 310 + k);
        double cx = prm[off + OceanShape.CX];
        double cz = prm[off + OceanShape.CZ];
        double r = prm[off + OceanShape.RADIUS];
        double a = u(s, 1) * 2.0 * Math.PI;
        boolean cenote = k < cenotes(prm, off, seed);
        double d;
        if (cenote) {
            // Two cenotes of one island on opposite sides, well inside the land.
            a = u(Hash.hash(seed, 310), 1) * 2.0 * Math.PI + k * Math.PI;
            d = r * ShapeMath.lerp(0.15, 0.4, u(s, 2));
            out[2] = ShapeMath.lerp(3.0, 7.0, u(s, 3));
            out[3] = OceanShape.maxY(OceanShape.ISLAND, prm, off) + 4.0;
            out[4] = ShapeMath.lerp(CENOTE_CHAMBER_Y_MIN, CENOTE_CHAMBER_Y_MAX, u(s, 4));
            out[5] = ShapeMath.lerp(14.0, 22.0, u(s, 5));
            out[6] = ShapeMath.lerp(9.0, 12.0, u(s, 6));
        } else {
            // Middle of the shelf ring.
            d = r * (1.0 + 0.5 * 0.22) + OceanShape.shoreWidth(r) + 0.5 * prm[off + OceanShape.SHELF_WIDTH];
            out[2] = ShapeMath.lerp(4.0, 10.0, u(s, 3));
            out[3] = 63.0;
            out[4] = prm[off + OceanShape.SHELF_Y] - ShapeMath.lerp(30.0, 42.0, u(s, 4));
            out[5] = ShapeMath.lerp(12.0, 18.0, u(s, 5));
            out[6] = ShapeMath.lerp(8.0, 10.0, u(s, 6));
        }
        out[0] = cx + Math.cos(a) * d;
        out[1] = cz + Math.sin(a) * d;
        out[7] = cenote ? 1.0 : 0.0;
    }

    /** Farthest a shaft reaches horizontally from its centre (chamber included). */
    public static double reach(double[] sh) {
        return Math.max(sh[2] + 2.0, sh[5] * 1.2) + FLOOD_MARGIN;
    }

    public static final int STRIDE = 8;

    public static boolean cenote(double[] sh, int o) {
        return sh[o + 7] > 0.5;
    }

    /** Lowest and highest block a shaft may open (flood margin included). */
    public static double minY(double[] sh) {
        return sh[4] - sh[6] - FLOOD_MARGIN;
    }

    public static double maxY(double[] sh) {
        return sh[3] + FLOOD_MARGIN;
    }

    /** Density of one shaft (negative inside), {@link #NONE} far away. */
    public static double density(double[] sh, int o, long seed, double x, double y, double z) {
        double dx = x - sh[o + 0];
        double dz = z - sh[o + 1];
        double best = NONE;
        double chamberY = sh[o + 4];
        double ch = sh[o + 6];
        if (y > chamberY && y <= sh[o + 3]) {
            // The well wobbles a little with depth.
            double wx = dx + 2.0 * SimplexNoise.noise2(seed, y / 18.0, 0.5);
            double wz = dz + 2.0 * SimplexNoise.noise2(seed + 1, y / 18.0, 0.5);
            double rr = sh[o + 2] * (1.0 + 0.15 * SimplexNoise.noise2(seed + 2, x / 5.0, z / 5.0));
            best = (Math.sqrt(wx * wx + wz * wz) - rr) / SOFT;
        }
        if (y > chamberY - ch && y < chamberY + ch) {
            double rh = sh[o + 5] * (1.0 + 0.2 * SimplexNoise.noise2(seed + 3, x / 14.0, z / 14.0));
            double h = Math.sqrt(dx * dx + dz * dz) / rh;
            // Flattened floor: below the centre the chamber is shallower.
            double v = (y - chamberY) / (y < chamberY ? 0.6 * ch : ch);
            double e = Math.sqrt(h * h + v * v) - 1.0;
            best = Math.min(best, e * rh / SOFT);
        }
        return best;
    }

    /** True if the point is in the shaft or chamber or within the flood margin of it. */
    public static boolean inside(double[] sh, int o, double x, double y, double z) {
        double dx = x - sh[o + 0];
        double dz = z - sh[o + 1];
        double d = Math.sqrt(dx * dx + dz * dz);
        if (y > sh[o + 4] && y <= sh[o + 3] + FLOOD_MARGIN && d < sh[o + 2] + 2.0 + FLOOD_MARGIN) {
            return true;
        }
        double v = (y - sh[o + 4]) / (sh[o + 6] + FLOOD_MARGIN);
        double h = d / (sh[o + 5] * 1.2 + FLOOD_MARGIN);
        return h * h + v * v < 1.0;
    }
}
