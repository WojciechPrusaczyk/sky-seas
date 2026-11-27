package pl.skyseas.shape;

import pl.skyseas.layout.Hash;

/**
 * Karst wells under the seabed: wobbling vertical shafts that drop from just under the
 * cave roof to a chamber near the bottom of the world, one candidate per grid cell. The
 * cave mask keeps the roof whole, so they open only into caves, never into the sea.
 * Values are in cave-density units (negative = air), like the vanilla cave noises.
 */
public final class KarstShape {
    public static final int CELL = 112;
    private static final double CHANCE = 0.4;
    private static final double SHAFT_MIN = 4.5;
    private static final double SHAFT_MAX = 8.0;
    private static final double HALL_MIN = 10.0;
    private static final double HALL_MAX = 18.0;
    private static final double HALL_Y_MIN = -52.0;
    private static final double HALL_Y_MAX = -36.0;
    private static final double WALL = 4.0;
    /** Blocks of distance per density unit: a soft wall, so interpolation keeps it round. */
    private static final double SOFTNESS = 6.0;
    private static final double NONE = 1.0;

    private KarstShape() {
    }

    /** Density of the nearest well at a block (only the cell of the column and its neighbours matter). */
    public static double density(long seed, double x, double y, double z) {
        int ci = (int) Math.floor(x / CELL);
        int cj = (int) Math.floor(z / CELL);
        double best = NONE;
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                best = Math.min(best, well(seed, ci + di, cj + dj, x, y, z));
            }
        }
        return best;
    }

    private static double well(long seed, int ci, int cj, double x, double y, double z) {
        long s = Hash.hash(seed, ci, cj);
        if (Hash.unit(Hash.hash(s, 1)) >= CHANCE) {
            return NONE;
        }
        double cx = (ci + 0.2 + 0.6 * Hash.unit(Hash.hash(s, 2))) * CELL;
        double cz = (cj + 0.2 + 0.6 * Hash.unit(Hash.hash(s, 3))) * CELL;
        double r = ShapeMath.lerp(SHAFT_MIN, SHAFT_MAX, Hash.unit(Hash.hash(s, 4)));
        double hallR = ShapeMath.lerp(HALL_MIN, HALL_MAX, Hash.unit(Hash.hash(s, 5)));
        double hallY = ShapeMath.lerp(HALL_Y_MIN, HALL_Y_MAX, Hash.unit(Hash.hash(s, 6)));
        double far = Math.max(hallR * 1.6, r + 12.0) + SOFTNESS;
        if (Math.abs(x - cx) > far || Math.abs(z - cz) > far) {
            return NONE;
        }
        // The shaft wanders a little with depth.
        double wx = cx + 6.0 * SimplexNoise.noise2(s, y / 24.0, 0.5);
        double wz = cz + 6.0 * SimplexNoise.noise2(s + 1, y / 24.0, 0.5);
        double shaft = y >= hallY ? (Math.hypot(x - wx, z - wz) - r) : 1000.0;
        // The hall: flat floor, walls at least WALL high, domed ceiling; a lens-shaped hall
        // would end in a one-block sheet along its rim.
        double rh = Math.hypot(x - cx, z - cz);
        double t = ShapeMath.clamp(rh / hallR, 0.0, 1.0);
        double ceiling = hallY + WALL + 0.35 * hallR * Math.sqrt(1.0 - t * t);
        double hall = Math.max(rh - hallR, Math.max(hallY - y, y - ceiling));
        return ShapeMath.clamp(Math.min(shaft, hall) / SOFTNESS, -1.0, NONE);
    }
}
