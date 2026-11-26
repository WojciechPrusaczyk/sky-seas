package pl.skyseas.shape;

import pl.skyseas.config.WorldParams;
import pl.skyseas.layout.Hash;

/**
 * Continent shape: a slab with a noisy outline (domain warp, bays, peninsulas), flat
 * terrain with terraced ridged mountains on part of it, a thin crust and a few cone
 * roots underneath. All 2D; {@link SkyColumn} turns the column values into 3D density.
 *
 * <p>Parameters of one continent are packed in a {@code double[]} at an offset (see the
 * index constants); {@link #place} fills them deterministically from the shape seed.
 */
public final class ContinentShape {
    public static final int CX = 0;
    public static final int CZ = 1;
    public static final int RADIUS = 2;
    public static final int BASE_Y = 3;
    public static final int CRUST = 4;
    public static final int MOUNTAINS = 5;
    public static final int ROOT_COUNT = 6;
    public static final int EXTENT = 7;
    public static final int PLATEAU = 8;
    public static final int MAX_Y = 9;
    /** Roots: x, z, radius, depth. */
    public static final int ROOT0 = 10;
    public static final int ROOT_STRIDE = 4;

    /** Mountain mask noise above the threshold by this much reaches full strength. */
    private static final double MOUNTAIN_RAMP = 0.35;
    private static final double TERRACE_STEP = 8.0;
    private static final double TERRACE_BLEND = 0.6;
    private static final double HILLS = 3.0;
    /** Noise feature sizes of the outline, as fractions of the radius, and the detail weight. */
    private static final double WARP_SCALE = 0.5;
    private static final double OUTLINE_SCALE = 0.5;
    private static final double OUTLINE_DETAIL_SCALE = 0.22;
    private static final double OUTLINE_DETAIL = 0.25;

    private ContinentShape() {
    }

    public static int stride(WorldParams p) {
        return ROOT0 + ROOT_STRIDE * p.continentRootsMax();
    }

    /** Fills the parameters of a continent centred at cx, cz. */
    public static void place(WorldParams p, long seed, double cx, double cz, double radius, boolean starter,
                             double[] out, int off) {
        out[off + CX] = cx;
        out[off + CZ] = cz;
        out[off + RADIUS] = radius;
        double baseY = ShapeMath.lerp(p.continentBaseYMin(), p.continentBaseYMax(), u(seed, 0));
        out[off + BASE_Y] = baseY;
        out[off + CRUST] = ShapeMath.lerp(p.continentCrustMin(), p.continentCrustMax(), u(seed, 1));
        double mountains = 0.5 + 0.5 * u(seed, 2);
        out[off + MOUNTAINS] = mountains;
        out[off + EXTENT] = radius * (1.0 + p.continentOutlineNoise() + p.continentWarp());
        out[off + PLATEAU] = starter ? p.starterPlateauRadius() : 0.0;
        out[off + MAX_Y] = Math.min(p.continentPeakMaxY(),
                baseY + HILLS + mountains * (p.continentPeakMaxY() - baseY) + TERRACE_STEP);

        int span = p.continentRootsMax() - p.continentRootsMin() + 1;
        int roots = p.continentRootsMin() + (int) (u(seed, 3) * span);
        out[off + ROOT_COUNT] = roots;
        for (int k = 0; k < roots; k++) {
            int o = off + ROOT0 + k * ROOT_STRIDE;
            double angle = u(seed, 10 + 4 * k) * 2.0 * Math.PI;
            double dist = Math.sqrt(u(seed, 11 + 4 * k)) * 0.6 * radius;
            out[o] = cx + Math.cos(angle) * dist;
            out[o + 1] = cz + Math.sin(angle) * dist;
            out[o + 2] = 40.0 + u(seed, 12 + 4 * k) * 0.08 * radius;
            out[o + 3] = (0.5 + 0.5 * u(seed, 13 + 4 * k)) * p.continentRootDepthMax();
        }
    }

    private static double u(long seed, int i) {
        return Hash.unit(Hash.hash(seed, i));
    }

    /** Signed horizontal distance to the outline (blocks): positive inside. */
    public static double edge(WorldParams p, double[] prm, int off, long seed, double x, double z) {
        double r = prm[off + RADIUS];
        double warp = r * p.continentWarp();
        double wf = WARP_SCALE * r;
        double wx = x + warp * SimplexNoise.noise2(seed + 1, x / wf, z / wf);
        double wz = z + warp * SimplexNoise.noise2(seed + 2, x / wf, z / wf);
        double dx = wx - prm[off + CX];
        double dz = wz - prm[off + CZ];
        double d = Math.sqrt(dx * dx + dz * dz);
        // Outline noise is sampled on the circle of radius r in the direction of the point,
        // so the outline is star-shaped: one connected piece, no rings or loose islets.
        double ux = d > 1e-9 ? dx / d * r : r;
        double uz = d > 1e-9 ? dz / d * r : 0.0;
        double of = OUTLINE_SCALE * r;
        double df = OUTLINE_DETAIL_SCALE * r;
        double n = SimplexNoise.noise2(seed + 3, ux / of, uz / of)
                + OUTLINE_DETAIL * SimplexNoise.noise2(seed + 4, ux / df, uz / df);
        double outline = r * (1.0 + p.continentOutlineNoise() * ShapeMath.clamp(n, -1.0, 1.0));
        return outline - d;
    }

    /** Flat terrain height (gentle hills) without mountains. */
    public static double flatTop(double[] prm, int off, long seed, double x, double z) {
        return prm[off + BASE_Y] + HILLS * SimplexNoise.noise2(seed + 5, x / 120.0, z / 120.0);
    }

    /** Mountain height above the flat terrain (0 on flat land). */
    public static double mountain(WorldParams p, double[] prm, int off, long seed, double x, double z, double edge) {
        double mask = ShapeMath.smoothstep(p.continentMountainThreshold(), p.continentMountainThreshold() + MOUNTAIN_RAMP,
                SimplexNoise.noise2(seed + 6, x / 700.0, z / 700.0)) * ShapeMath.smoothstep(0.0, 96.0, edge);
        double plateau = prm[off + PLATEAU];
        if (plateau > 0.0) {
            double dx = x - prm[off + CX];
            double dz = z - prm[off + CZ];
            mask *= ShapeMath.smoothstep(plateau, plateau + 150.0, Math.sqrt(dx * dx + dz * dz));
        }
        if (mask <= 0.0) {
            return 0.0;
        }
        double r1 = 1.0 - Math.abs(SimplexNoise.noise2(seed + 7, x / 280.0, z / 280.0));
        double r2 = 1.0 - Math.abs(SimplexNoise.noise2(seed + 8, x / 110.0, z / 110.0));
        double ridge = 0.75 * r1 * r1 + 0.25 * r2 * r2;
        double h = mask * prm[off + MOUNTAINS] * ridge * (p.continentPeakMaxY() - prm[off + BASE_Y]);
        double steps = h / TERRACE_STEP;
        double k = Math.floor(steps);
        double terraced = TERRACE_STEP * (k + ShapeMath.smoothstep(0.3, 0.7, steps - k));
        return ShapeMath.lerp(h, terraced, TERRACE_BLEND);
    }

    /** Top surface height. */
    public static double top(WorldParams p, double flatTop, double mountain) {
        return Math.min(flatTop + mountain, p.continentPeakMaxY());
    }

    /** Underside height: crust under the flat terrain (thinning to the edge) and cone roots; never below minBottomY. */
    public static double bottom(WorldParams p, double[] prm, int off, double x, double z, double edge, double flatTop) {
        double b = flatTop - prm[off + CRUST] * ShapeMath.smoothstep(0.0, 40.0, edge);
        double root = 0.0;
        int roots = (int) prm[off + ROOT_COUNT];
        for (int k = 0; k < roots; k++) {
            int o = off + ROOT0 + k * ROOT_STRIDE;
            double dx = x - prm[o];
            double dz = z - prm[o + 1];
            double t = 1.0 - Math.sqrt(dx * dx + dz * dz) / prm[o + 2];
            if (t > 0.0) {
                root = Math.max(root, prm[o + 3] * t);
            }
        }
        return Math.max(b - root, p.continentMinBottomY());
    }
}
