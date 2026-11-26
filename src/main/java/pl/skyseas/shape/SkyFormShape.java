package pl.skyseas.shape;

import pl.skyseas.config.WorldParams;
import pl.skyseas.layout.Hash;

/**
 * Sky forms between continents: towers, small and medium islands, archipelago islands and
 * Hallelujah bodies share one column shape (noisy round outline, flat top, underside
 * tapering from a rim of at least {@code rim} blocks to {@code depth} below the top), with
 * spires hanging under the larger ones. Rock bridges and arches between archipelago
 * islands are slabs of their own, built per block.
 *
 * <p>Form parameters: {@link #CX} .. {@link #SPIRE0}; spires use the record of
 * {@link ContinentShape#spireDensity}. Bridge parameters: {@link #AX} .. {@link #ARCH}.
 */
public final class SkyFormShape {
    public static final int CX = 0;
    public static final int CZ = 1;
    public static final int RADIUS = 2;
    public static final int TOP = 3;
    /** Underside depth below the top in the middle. */
    public static final int DEPTH = 4;
    /** Underside depth gained per block inwards from the outline. */
    public static final int TAPER = 5;
    /** Thickness at the outline. */
    public static final int RIM = 6;
    public static final int SPIRE_COUNT = 7;
    public static final int SPIRE0 = 8;
    public static final int MAX_SPIRES = 8;
    public static final int STRIDE = SPIRE0 + ContinentShape.SPIRE_STRIDE * MAX_SPIRES;

    public static final int AX = 0;
    public static final int AZ = 1;
    public static final int AY = 2;
    public static final int BX = 3;
    public static final int BZ = 4;
    public static final int BY = 5;
    public static final int HALF_WIDTH = 6;
    public static final int THICKNESS = 7;
    /** Rise of the middle above the straight line between the ends (0: flat bridge). */
    public static final int ARCH = 8;
    /** Distance from the end centres where the bridge leaves each island's outline. */
    public static final int REACH_A = 9;
    public static final int REACH_B = 10;
    public static final int BRIDGE_STRIDE = 11;

    /** Outline radius varies by +/- this fraction; the footprint bounds it. */
    private static final double OUTLINE_NOISE = 0.2;
    public static final double FOOTPRINT = 1.0 + OUTLINE_NOISE + 0.05;
    /** Thickness at the outline of interpolated forms: thinner slabs break into lumps (4x8x4 cells). */
    public static final double INTERPOLATED_RIM = 16.0;
    private static final double DETAIL_RIM = 5.0;
    private static final double TOP_NOISE = 1.5;
    private static final double UNDERSIDE_NOISE = 3.0;
    /** Bridge thickness at the abutments, where it grows out of the island. */
    private static final double ABUTMENT_THICKNESS = 16.0;
    /** Widest a bridge gets at an abutment, relative to its half width (plus a constant). */
    private static final double FLARE_WIDTH = 1.5;
    private static final double FLARE_CONST = 3.0;

    private SkyFormShape() {
    }

    /**
     * Places a form. {@code taper} (depth per block inwards) makes islands bowls and
     * towers near-vertical frusta; {@code detail} forms are built per block, so their
     * rim may be thin.
     */
    public static void place(double x, double z, double radius, double top, double depth, double taper,
                             boolean detail, double[] out, int off) {
        out[off + CX] = x;
        out[off + CZ] = z;
        out[off + RADIUS] = radius;
        out[off + TOP] = top;
        out[off + RIM] = Math.min(depth, detail ? DETAIL_RIM : INTERPOLATED_RIM);
        out[off + DEPTH] = depth;
        out[off + TAPER] = taper;
        out[off + SPIRE_COUNT] = 0;
    }

    /** Island: a bowl reaching full depth about 40% of the way in. */
    public static double islandTaper(double radius, double depth) {
        return depth / (0.6 * radius);
    }

    /** Tower or Hallelujah body: walls narrowing to half the radius at the full depth. */
    public static double towerTaper(double radius, double height) {
        return 2.0 * height / radius;
    }

    /** Lowest block of the body (underside noise included). */
    public static double bodyBottom(double[] prm, int off) {
        return prm[off + TOP] - prm[off + DEPTH] - UNDERSIDE_NOISE;
    }

    /** Highest block (top noise included). */
    public static double maxY(double[] prm, int off) {
        return prm[off + TOP] + TOP_NOISE;
    }

    /** Lowest block including spires. */
    public static double minY(double[] prm, int off) {
        double y = bodyBottom(prm, off);
        for (int s = 0; s < (int) prm[off + SPIRE_COUNT]; s++) {
            int o = off + SPIRE0 + ContinentShape.SPIRE_STRIDE * s;
            y = Math.min(y, prm[o + 2] - prm[o + 3]);
        }
        return y;
    }

    /**
     * Hangs {@code count} spires under the underside (fewer where they do not fit), tips
     * no lower than {@code minTipY}.
     */
    public static void placeSpires(WorldParams p, long seed, int count, double minTipY, double[] prm, int off) {
        double r = prm[off + RADIUS];
        int n = 0;
        for (int a = 0; a < 4 * count && n < count; a++) {
            int i = 500 + 6 * a;
            double angle = u(seed, i) * 2.0 * Math.PI;
            double dist = Math.sqrt(u(seed, i + 1)) * 0.45 * r;
            double x = prm[off + CX] + Math.cos(angle) * dist;
            double z = prm[off + CZ] + Math.sin(angle) * dist;
            double radius = ShapeMath.clamp(ShapeMath.lerp(0.15, 0.3, u(seed, i + 2)) * r, 2.0, 8.0);
            boolean free = true;
            for (int k = 0; k < n && free; k++) {
                int o = off + SPIRE0 + ContinentShape.SPIRE_STRIDE * k;
                free = Math.hypot(prm[o] - x, prm[o + 1] - z) > radius + prm[o + 4] + 6.0;
            }
            if (!free) {
                continue;
            }
            double bottom = column(prm, off, seed, x, z, ContinentShape.BOTTOM);
            double length = Math.min(ShapeMath.lerp(p.continentSpireLengthMin(), p.continentSpireLengthMax(), u(seed, i + 3)),
                    bottom - minTipY);
            if (length < 0.8 * p.continentSpireLengthMin()) {
                continue;
            }
            // Embedded a few blocks into the underside, so the spire meets it everywhere.
            double embed = Math.min(6.0, 0.5 * prm[off + RIM]);
            int o = off + SPIRE0 + ContinentShape.SPIRE_STRIDE * n;
            prm[o] = x;
            prm[o + 1] = z;
            prm[o + 2] = bottom + embed;
            prm[o + 3] = length + embed;
            prm[o + 4] = radius;
            prm[o + 5] = u(seed, i + 4) * 2.0 * Math.PI;
            n++;
        }
        prm[off + SPIRE_COUNT] = n;
    }

    /** Signed horizontal distance to the outline (blocks), positive inside. */
    public static double edge(double[] prm, int off, long seed, double x, double z) {
        double r = prm[off + RADIUS];
        double dx = x - prm[off + CX];
        double dz = z - prm[off + CZ];
        double d = Math.sqrt(dx * dx + dz * dz);
        // Noise sampled on a circle in the direction of the point: the outline stays star-shaped,
        // so it never sheds islets; larger forms get more bays and headlands.
        double k = 0.6 + r / 40.0;
        double n = d > 1e-9 ? SimplexNoise.noise2(seed, k * dx / d, k * dz / d) : 0.0;
        return r * (1.0 + OUTLINE_NOISE * n) - d;
    }

    /**
     * Column value ({@link ContinentShape#EDGE}, {@code TOP}, {@code BOTTOM}; 0 for
     * {@code MOUNTAIN} and {@code DETAIL}: these forms get no carving).
     */
    public static double column(double[] prm, int off, long seed, double x, double z, int kind) {
        double e = edge(prm, off, seed, x, z);
        if (kind == ContinentShape.EDGE) {
            return e;
        }
        if (kind != ContinentShape.TOP && kind != ContinentShape.BOTTOM) {
            return 0.0;
        }
        double top = prm[off + TOP] + TOP_NOISE * SimplexNoise.noise2(seed + 1, x / 24.0, z / 24.0);
        if (kind == ContinentShape.TOP) {
            return top;
        }
        double rim = prm[off + RIM];
        double thick = ShapeMath.clamp(prm[off + TAPER] * e, rim, prm[off + DEPTH]);
        // Rough underside only where the body is thick, so the rim never thins below its minimum.
        double rough = UNDERSIDE_NOISE * SimplexNoise.noise2(seed + 2, x / 12.0, z / 12.0)
                * ShapeMath.smoothstep(rim, rim + 6.0, thick);
        return top - thick + rough;
    }

    /** Per-block density (blocks, positive inside) of a form built per block. */
    public static double density(double[] prm, int off, long seed, double x, double y, double z) {
        double e = edge(prm, off, seed, x, z);
        if (e < -2.0) {
            return e;
        }
        double top = column(prm, off, seed, x, z, ContinentShape.TOP);
        double bottom = column(prm, off, seed, x, z, ContinentShape.BOTTOM);
        return Math.min(e, Math.min(top - y, y - bottom));
    }

    /** Places a bridge between two islands (centres, tops, outline radii). */
    public static void placeBridge(double ax, double az, double ay, double ra, double bx, double bz, double by,
                                   double rb, double halfWidth, double thickness, double arch, double[] out, int off) {
        out[off + AX] = ax;
        out[off + AZ] = az;
        out[off + AY] = ay;
        out[off + BX] = bx;
        out[off + BZ] = bz;
        out[off + BY] = by;
        out[off + HALF_WIDTH] = halfWidth;
        out[off + THICKNESS] = thickness;
        out[off + ARCH] = arch;
        out[off + REACH_A] = ra;
        out[off + REACH_B] = rb;
    }

    /** Horizontal reach of a bridge from its midpoint. */
    public static double bridgeReach(double[] prm, int off) {
        return 0.5 * Math.hypot(prm[off + BX] - prm[off + AX], prm[off + BZ] - prm[off + AZ])
                + bridgeHalfWidthMax(prm, off) + 2.0;
    }

    /** Half width at the abutments, the widest part. */
    public static double bridgeHalfWidthMax(double[] prm, int off) {
        return prm[off + HALF_WIDTH] * (1.0 + FLARE_WIDTH) + FLARE_CONST;
    }

    /** True if the column is within {@code margin} of the bridge's widest outline. */
    public static boolean bridgeNear(double[] prm, int off, double x, double z, double margin) {
        double ax = prm[off + AX];
        double az = prm[off + AZ];
        double vx = prm[off + BX] - ax;
        double vz = prm[off + BZ] - az;
        double t = ShapeMath.clamp(((x - ax) * vx + (z - az) * vz) / (vx * vx + vz * vz), 0.0, 1.0);
        return Math.hypot(x - ax - t * vx, z - az - t * vz) < bridgeHalfWidthMax(prm, off) + margin;
    }

    /** Lowest block of a bridge (at the abutments, which are the thickest). */
    public static double bridgeMinY(double[] prm, int off) {
        return Math.min(prm[off + AY], prm[off + BY]) - ABUTMENT_THICKNESS - 1.0;
    }

    public static double bridgeMaxY(double[] prm, int off) {
        return Math.max(prm[off + AY], prm[off + BY]) + prm[off + ARCH] + 1.0;
    }

    /**
     * Density (blocks, positive inside) of a bridge at a block: a slab from one island
     * top to the other (rising in the middle by the arch height), thickest along its
     * axis, flaring into wide, thick abutments where it leaves each island.
     */
    public static double bridgeDensity(double[] prm, int off, double x, double y, double z) {
        double ax = prm[off + AX];
        double az = prm[off + AZ];
        double vx = prm[off + BX] - ax;
        double vz = prm[off + BZ] - az;
        double len2 = vx * vx + vz * vz;
        double t = ShapeMath.clamp(((x - ax) * vx + (z - az) * vz) / len2, 0.0, 1.0);
        double len = Math.sqrt(len2);
        double fromEnd = Math.min(t * len - prm[off + REACH_A], (1.0 - t) * len - prm[off + REACH_B]);
        double span = len - prm[off + REACH_A] - prm[off + REACH_B];
        double flare = 1.0 - ShapeMath.smoothstep(0.0, Math.max(6.0, 0.35 * span), fromEnd);
        flare *= flare;
        double half = prm[off + HALF_WIDTH] * (1.0 + FLARE_WIDTH * flare) + FLARE_CONST * flare;
        double e = half - Math.hypot(x - ax - t * vx, z - az - t * vz);
        if (e <= -2.0) {
            return e;
        }
        double top = ShapeMath.lerp(prm[off + AY], prm[off + BY], t) + prm[off + ARCH] * 4.0 * t * (1.0 - t);
        double thick = ShapeMath.lerp(prm[off + THICKNESS], ABUTMENT_THICKNESS, flare);
        double bottom = top - thick * (0.45 + 0.55 * ShapeMath.clamp(e / half, 0.0, 1.0));
        return Math.min(e, Math.min(top - y, y - bottom));
    }

    private static double u(long seed, int i) {
        return Hash.unit(Hash.hash(seed, i));
    }
}
