package pl.skyseas.shape;

import pl.skyseas.config.WorldParams;
import pl.skyseas.layout.Hash;

/**
 * Ocean forms as 2D heights raised above the seabed: ocean islands (dome over the sea,
 * shore, shelf at Y 35-60, slope down to the seabed; atolls with a lagoon), seamounts,
 * volcano cones with a crater and sea spires. Each form returns its own surface height
 * at a column, or {@link #NONE} outside its reach; the seabed takes the maximum.
 */
public final class OceanShape {
    public static final int ISLAND = 0;
    public static final int SEAMOUNT = 1;
    public static final int VOLCANO = 2;
    public static final int SPIRE = 3;

    public static final int CX = 0;
    public static final int CZ = 1;
    /** Island land radius, mount base radius or spire radius. */
    public static final int RADIUS = 2;
    /** Island summit above sea level, seamount peak Y, volcano rim Y or spire top Y. */
    public static final int TOP = 3;
    public static final int SHELF_Y = 4;
    public static final int SHELF_WIDTH = 5;
    public static final int SLOPE_WIDTH = 6;
    /** Atoll ring width (0 = plain island). */
    public static final int RING = 7;
    public static final int LAGOON_Y = 8;
    public static final int CRATER_RADIUS = 9;
    public static final int CRATER_DEPTH = 10;
    /** Island size class: 0 small, 1 medium, 2 large. */
    public static final int SIZE_CLASS = 11;
    public static final int STRIDE = 12;

    public static final double NONE = -1000.0;
    private static final double SEA = 63.0;
    /** Outline varies by +/- this fraction of the radius. */
    private static final double OUTLINE_NOISE = 0.22;
    private static final double SHELF_NOISE = 2.5;
    private static final double VOLCANO_RIB = 2.5;
    /** Spurs lengthen the island slope by up to this fraction. */
    private static final double SPUR_REACH = 0.9;

    private OceanShape() {
    }

    private static double u(long seed, int i) {
        return Hash.unit(Hash.hash(seed, i));
    }

    /**
     * Ocean island of the given class.
     *
     * @param warm the island stands in warm water (may become an atoll)
     */
    public static void placeIsland(WorldParams p, long seed, double x, double z, int sizeClass, boolean warm,
                                   double[] out, int off) {
        clear(out, off);
        out[off + CX] = x;
        out[off + CZ] = z;
        double[] radii = p.oceanIslandRadius();
        double r = ShapeMath.lerp(radii[sizeClass], radii[sizeClass + 1], u(seed, 0));
        out[off + RADIUS] = r;
        out[off + SIZE_CLASS] = sizeClass;
        out[off + TOP] = ShapeMath.lerp(p.oceanIslandTopMin()[sizeClass], p.oceanIslandTopMax()[sizeClass], u(seed, 1));
        out[off + SHELF_Y] = ShapeMath.lerp(p.oceanShelfYMin(), p.oceanShelfYMax(), u(seed, 2));
        out[off + SHELF_WIDTH] = ShapeMath.lerp(p.oceanShelfWidthMin()[sizeClass], p.oceanShelfWidthMax()[sizeClass], u(seed, 3));
        // Small rocks get a short slope, large islands a long one.
        out[off + SLOPE_WIDTH] = ShapeMath.lerp(p.oceanShelfSlopeMin(), p.oceanShelfSlopeMax(), u(seed, 4))
                * (0.55 + 0.25 * sizeClass);
        if (warm && sizeClass > 0 && u(seed, 5) < p.atollChance()) {
            out[off + RING] = Math.max(5.0, r * ShapeMath.lerp(0.22, 0.35, u(seed, 6)));
            out[off + LAGOON_Y] = ShapeMath.lerp(p.atollLagoonYMin(), p.atollLagoonYMax(), u(seed, 7));
            // Atolls are low rings of sand and reef.
            out[off + TOP] = ShapeMath.lerp(1.5, 4.0, u(seed, 8));
        }
    }

    public static void placeSeamount(WorldParams p, long seed, double x, double z, double[] out, int off) {
        clear(out, off);
        out[off + CX] = x;
        out[off + CZ] = z;
        out[off + RADIUS] = ShapeMath.lerp(p.seamountRadiusMin(), p.seamountRadiusMax(), u(seed, 0));
        out[off + TOP] = ShapeMath.lerp(p.seamountPeakYMin(), p.seamountPeakYMax(), u(seed, 1));
    }

    public static void placeVolcano(WorldParams p, long seed, double x, double z, double[] out, int off) {
        clear(out, off);
        out[off + CX] = x;
        out[off + CZ] = z;
        double r = ShapeMath.lerp(p.volcanoRadiusMin(), p.volcanoRadiusMax(), u(seed, 0));
        out[off + RADIUS] = r;
        out[off + TOP] = ShapeMath.lerp(p.volcanoRimYMin(), p.volcanoRimYMax(), u(seed, 1));
        out[off + CRATER_RADIUS] = r * ShapeMath.lerp(0.18, 0.28, u(seed, 2));
        out[off + CRATER_DEPTH] = ShapeMath.lerp(p.volcanoCraterDepthMin(), p.volcanoCraterDepthMax(), u(seed, 3));
    }

    public static void placeSpire(WorldParams p, long seed, double x, double z, double[] out, int off) {
        clear(out, off);
        out[off + CX] = x;
        out[off + CZ] = z;
        out[off + RADIUS] = ShapeMath.lerp(p.seaSpireRadiusMin(), p.seaSpireRadiusMax(), u(seed, 0));
        out[off + TOP] = SEA + ShapeMath.lerp(p.seaSpireTopMin(), p.seaSpireTopMax(), u(seed, 1));
    }

    private static void clear(double[] out, int off) {
        for (int i = 0; i < STRIDE; i++) {
            out[off + i] = 0.0;
        }
    }

    /** Farthest the form reaches from its centre (blocks). */
    public static double reach(int kind, double[] prm, int off) {
        double r = prm[off + RADIUS];
        return switch (kind) {
            case ISLAND -> r * (1.0 + 1.5 * OUTLINE_NOISE) + shoreWidth(r) + 1.4 * prm[off + SHELF_WIDTH]
                    + (1.0 + SPUR_REACH) * prm[off + SLOPE_WIDTH] + 2.0;
            case SPIRE -> r * (1.0 + 1.5 * OUTLINE_NOISE) + 7.0;
            default -> r + 2.0;
        };
    }

    /** Radius of the part above sea level (for the clear-sky rule), or 0 if the form stays under water. */
    public static double landRadius(int kind, double[] prm, int off) {
        return switch (kind) {
            case ISLAND, SPIRE -> prm[off + RADIUS] * (1.0 + 1.5 * OUTLINE_NOISE) + 2.0;
            default -> 0.0;
        };
    }

    /** Highest surface of the form. */
    public static double maxY(int kind, double[] prm, int off) {
        return kind == ISLAND ? SEA + prm[off + TOP] : prm[off + TOP];
    }

    static double shoreWidth(double r) {
        return ShapeMath.clamp(0.3 * r, 4.0, 20.0);
    }

    /** Surface height of the form at a column, or {@link #NONE} outside it. */
    public static double height(int kind, double[] prm, int off, long seed, double x, double z, double base) {
        double dx = x - prm[off + CX];
        double dz = z - prm[off + CZ];
        double d2 = dx * dx + dz * dz;
        double reach = reach(kind, prm, off);
        if (d2 >= reach * reach) {
            return NONE;
        }
        double d = Math.sqrt(d2);
        return switch (kind) {
            case ISLAND -> island(prm, off, seed, x, z, d, base);
            case SEAMOUNT -> seamount(prm, off, seed, x, z, d, base);
            case VOLCANO -> volcano(prm, off, seed, x, z, d, base);
            default -> spire(prm, off, seed, x, z, d, base);
        };
    }

    /** True where the column is inside a sea spire (caves would hollow it out). */
    public static boolean insideSpire(double[] prm, int off, double x, double z) {
        double dx = x - prm[off + CX];
        double dz = z - prm[off + CZ];
        double r = prm[off + RADIUS] * (1.0 + 1.5 * OUTLINE_NOISE) + 7.0;
        return dx * dx + dz * dz < r * r;
    }

    private static double island(double[] prm, int off, long seed, double x, double z, double d, double base) {
        double r = prm[off + RADIUS];
        double s = Math.max(8.0, 0.6 * r);
        double broad = SimplexNoise.noise2(seed, x / s, z / s);
        // Distance inside the outline (negative outside). Fine outline detail only near the
        // coast; the shelf and its slope follow the broad shape, so they stay gentle.
        double t = r * (1.0 + OUTLINE_NOISE * broad) - d;
        if (t > -shoreWidth(r)) {
            t -= r * OUTLINE_NOISE * 0.5 * SimplexNoise.noise2(seed + 1, x / (0.4 * s), z / (0.4 * s))
                    * ShapeMath.smoothstep(-shoreWidth(r), 0.0, t);
        }
        double top = prm[off + TOP];
        if (t >= 0.0) {
            double ring = prm[off + RING];
            double hills = 0.5 + 0.5 * SimplexNoise.noise2(seed + 2, x / Math.max(12.0, 0.4 * r), z / Math.max(12.0, 0.4 * r));
            if (ring <= 0.0) {
                // Cliffs up from the beach, then a rolling top instead of a dome.
                double cliff = ShapeMath.smoothstep(0.5, Math.max(2.5, 0.12 * r), t);
                double core = ShapeMath.smoothstep(0.0, Math.max(4.0, 0.5 * r), t);
                return SEA - 0.5 + top * (0.45 * cliff + 0.55 * core) * (0.55 + 0.45 * hills);
            }
            // Atoll: low ring with passes, lagoon inside.
            double ringTop = SEA - 0.5 + top * ShapeMath.smoothstep(0.0, 0.5 * ring, Math.min(t, ring - t + 0.5 * ring))
                    * (0.6 + 0.4 * hills);
            double pass = ShapeMath.smoothstep(-0.55, -0.25, SimplexNoise.noise2(seed + 3, x / 45.0, z / 45.0));
            ringTop = ShapeMath.lerp(SEA - 3.0, ringTop, pass);
            double lagoon = prm[off + LAGOON_Y] + SimplexNoise.noise2(seed + 5, x / 20.0, z / 20.0);
            return ShapeMath.lerp(ringTop, lagoon, ShapeMath.smoothstep(ring, ring + 8.0, t));
        }
        // The shelf has its own ragged outline (gentle: slope of the shift well below 1).
        double w = prm[off + SHELF_WIDTH];
        double ws = Math.max(60.0, 1.5 * w);
        double u = -t + 0.4 * w * SimplexNoise.noise2(seed + 6, x / ws, z / ws);
        double shelf = prm[off + SHELF_Y] + SHELF_NOISE * SimplexNoise.noise2(seed + 4, x / 30.0, z / 30.0);
        double shore = shoreWidth(r);
        if (u < shore) {
            return ShapeMath.lerp(SEA - 0.6, shelf, ShapeMath.smoothstep(0.0, shore, Math.max(0.0, u)));
        }
        if (u < shore + w) {
            return shelf;
        }
        // Spurs: ridges running down the slope (angular ridged noise), lengthening it in places.
        double d0 = Math.max(d, 1.0);
        double ax = 1.1 * (x - prm[off + CX]) / d0;
        double az = 1.1 * (z - prm[off + CZ]) / d0;
        double spur = 1.0 - Math.abs(SimplexNoise.noise2(seed + 7, ax, az));
        double slope = prm[off + SLOPE_WIDTH] * (1.0 + SPUR_REACH * spur * spur);
        double v = ShapeMath.clamp((u - shore - w) / slope, 0.0, 1.0);
        // A steep drop-off below the shelf edge, a break, then a long foot.
        double fall = 0.6 * ShapeMath.smoothstep(0.0, 0.25, v) + 0.4 * ShapeMath.smoothstep(0.35, 1.0, v);
        return ShapeMath.lerp(shelf, base, fall);
    }

    private static double seamount(double[] prm, int off, long seed, double x, double z, double d, double base) {
        double r = prm[off + RADIUS];
        double warp = 0.15 * r * SimplexNoise.noise2(seed, x / (0.5 * r), z / (0.5 * r));
        double profile = 1.0 - ShapeMath.smoothstep(0.0, r, d + warp);
        if (profile <= 0.0) {
            return NONE;
        }
        double ridges = 1.0 - Math.abs(SimplexNoise.noise2(seed + 1, x / 35.0, z / 35.0));
        return base + (prm[off + TOP] - base) * Math.pow(profile, 1.4) * (0.85 + 0.15 * ridges);
    }

    private static double volcano(double[] prm, int off, long seed, double x, double z, double d, double base) {
        double r = prm[off + RADIUS];
        double crater = prm[off + CRATER_RADIUS];
        double rim = prm[off + TOP];
        double warp = 0.08 * r * SimplexNoise.noise2(seed, x / (0.4 * r), z / (0.4 * r));
        double dd = d + warp;
        if (dd < crater) {
            double k = dd / crater;
            return rim - prm[off + CRATER_DEPTH] * (1.0 - k * k);
        }
        double profile = 1.0 - ShapeMath.smoothstep(crater, r, dd);
        if (profile <= 0.0) {
            return NONE;
        }
        // Lava ribs running down the flanks.
        double ribs = VOLCANO_RIB * SimplexNoise.noise2(seed + 1, x / 14.0, z / 14.0) * profile * (1.0 - profile) * 4.0;
        return base + (rim - base) * Math.pow(profile, 1.3) + ribs;
    }

    private static double spire(double[] prm, int off, long seed, double x, double z, double d, double base) {
        double r = prm[off + RADIUS];
        double t = r * (1.0 + OUTLINE_NOISE * SimplexNoise.noise2(seed, x / 6.0, z / 6.0)) - d;
        if (t >= 0.0) {
            return prm[off + TOP] - 3.0 * Math.max(0.0, SimplexNoise.noise2(seed + 1, x / 4.0, z / 4.0));
        }
        // Rubble skirt at the foot.
        return t > -6.0 ? base + 6.0 * ShapeMath.smoothstep(-6.0, 0.0, t) : NONE;
    }

    /** Highest surface any form can reach. */
    public static double maxHeight(WorldParams p) {
        double islands = SEA + p.oceanIslandTopMax()[p.oceanIslandTopMax().length - 1];
        return Math.max(islands, SEA + p.seaSpireTopMax());
    }
}
