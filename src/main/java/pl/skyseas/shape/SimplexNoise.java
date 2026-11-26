package pl.skyseas.shape;

import pl.skyseas.layout.Hash;

/**
 * Light 2D/3D simplex noise for shapes, without Minecraft classes. Gradients come from
 * precomputed tables indexed by a positional hash of the lattice point, so any 64-bit
 * seed works without per-seed tables. Output is roughly -1..1.
 */
public final class SimplexNoise {
    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;
    private static final double F3 = 1.0 / 3.0;
    private static final double G3 = 1.0 / 6.0;
    /** Scale that brings 2D output with unit gradients to about -1..1. */
    private static final double SCALE_2D = 99.0;
    private static final double SCALE_3D = 32.0;

    private static final double[] GRAD2_X = new double[16];
    private static final double[] GRAD2_Y = new double[16];
    /** The 12 cube edge directions, 4 of them repeated (Perlin's improved noise). */
    private static final double[] GRAD3 = {
            1, 1, 0, -1, 1, 0, 1, -1, 0, -1, -1, 0,
            1, 0, 1, -1, 0, 1, 1, 0, -1, -1, 0, -1,
            0, 1, 1, 0, -1, 1, 0, 1, -1, 0, -1, -1,
            1, 1, 0, 0, -1, 1, -1, 1, 0, 0, -1, -1};

    static {
        for (int i = 0; i < 16; i++) {
            double a = (i + 0.5) * Math.PI / 8.0;
            GRAD2_X[i] = Math.cos(a);
            GRAD2_Y[i] = Math.sin(a);
        }
    }

    private SimplexNoise() {
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    public static double noise2(long seed, double x, double y) {
        double s = (x + y) * F2;
        int i = floor(x + s);
        int j = floor(y + s);
        double t = (i + j) * G2;
        double x0 = x - (i - t);
        double y0 = y - (j - t);
        int i1 = x0 > y0 ? 1 : 0;
        int j1 = 1 - i1;
        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double y2 = y0 - 1.0 + 2.0 * G2;
        return SCALE_2D * (corner2(seed, i, j, x0, y0) + corner2(seed, i + i1, j + j1, x1, y1)
                + corner2(seed, i + 1, j + 1, x2, y2));
    }

    private static double corner2(long seed, int i, int j, double x, double y) {
        double t = 0.5 - x * x - y * y;
        if (t <= 0.0) {
            return 0.0;
        }
        int g = (int) Hash.hash(seed, i, j) & 15;
        t *= t;
        return t * t * (GRAD2_X[g] * x + GRAD2_Y[g] * y);
    }

    public static double noise3(long seed, double x, double y, double z) {
        double s = (x + y + z) * F3;
        int i = floor(x + s);
        int j = floor(y + s);
        int k = floor(z + s);
        double t = (i + j + k) * G3;
        double x0 = x - (i - t);
        double y0 = y - (j - t);
        double z0 = z - (k - t);
        int i1, j1, k1, i2, j2, k2;
        if (x0 >= y0) {
            if (y0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0;
            } else if (x0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1;
            } else {
                i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1;
            }
        } else {
            if (y0 < z0) {
                i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1;
            } else if (x0 < z0) {
                i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1;
            } else {
                i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0;
            }
        }
        return SCALE_3D * (corner3(seed, i, j, k, x0, y0, z0)
                + corner3(seed, i + i1, j + j1, k + k1, x0 - i1 + G3, y0 - j1 + G3, z0 - k1 + G3)
                + corner3(seed, i + i2, j + j2, k + k2, x0 - i2 + 2 * G3, y0 - j2 + 2 * G3, z0 - k2 + 2 * G3)
                + corner3(seed, i + 1, j + 1, k + 1, x0 - 1 + 3 * G3, y0 - 1 + 3 * G3, z0 - 1 + 3 * G3));
    }

    private static double corner3(long seed, int i, int j, int k, double x, double y, double z) {
        double t = 0.6 - x * x - y * y - z * z;
        if (t <= 0.0) {
            return 0.0;
        }
        int g = ((int) Hash.hash(seed, i, j, k) & 15) * 3;
        t *= t;
        return t * t * (GRAD3[g] * x + GRAD3[g + 1] * y + GRAD3[g + 2] * z);
    }
}
