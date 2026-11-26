package pl.skyseas.shape;

import pl.skyseas.config.WorldParams;
import pl.skyseas.layout.Hash;

/**
 * Continent shape: a slab with a fractal outline (lobes, bays, coves, headlands),
 * terrain regions (plains, rolling hills, stepped highlands, mesas, spires, terraced
 * mountains), cliffs or coastal slopes at the edge, a thin crust and a few cone roots underneath. All 2D; {@link SkyColumn} turns the column values into 3D density.
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
    /** Character of the continent ({@link #LOWLAND} .. {@link #CALDERA}) and the terrain weights it sets. */
    public static final int CHARACTER = 10;
    public static final int W_HILLS = 11;
    public static final int W_HIGHLAND = 12;
    public static final int W_MESA = 13;
    public static final int W_SPIRE = 14;
    public static final int W_RIDGE = 15;
    /** Less plains: shifts the region threshold down. */
    public static final int FLAT_SHIFT = 16;
    /** Mountain range along the spine (core to satellites); 0 = none. */
    public static final int SPINE_HEIGHT = 17;
    /** Ring of mountains around the core with a basin inside; 0 = none. */
    public static final int RING_HEIGHT = 18;
    public static final int RING_RADIUS = 19;
    public static final int BASIN_DEPTH = 20;
    public static final int BLOB_COUNT = 21;
    /** Blobs (overlapping ellipses whose union is the continent): x, z, semi-axis a, semi-axis b, angle. */
    public static final int BLOB0 = 22;
    public static final int BLOB_STRIDE = 5;
    public static final int MAX_BLOBS = 6;
    /** Roots: x, z, radius, depth. */
    public static final int ROOT0 = BLOB0 + BLOB_STRIDE * MAX_BLOBS;
    public static final int ROOT_STRIDE = 4;

    public static final int LOWLAND = 0;
    public static final int MIXED = 1;
    public static final int HIGHLAND = 2;
    public static final int MOUNTAINOUS = 3;
    public static final int CALDERA = 4;
    /** Cumulative chances of the characters (lowland, mixed, highland, mountainous, caldera). */
    private static final double[] CHARACTER_CHANCE = {0.2, 0.5, 0.65, 0.85, 1.0};
    /** Terrain weights per character: hills, highland, mesa, spire, ridged mountains, flat shift. */
    private static final double[][] CHARACTER_WEIGHTS = {
            {1.0, 0.3, 0.2, 0.15, 0.2, 0.0},
            {1.0, 0.7, 0.6, 0.4, 0.7, 0.0},
            {1.0, 1.0, 1.0, 0.7, 0.5, 0.12},
            {1.0, 0.5, 0.2, 0.4, 1.0, 0.2},
            {0.8, 0.3, 0.2, 0.3, 0.3, 0.0}};
    private static final double SPINE_MIN = 55.0;
    private static final double SPINE_MAX = 90.0;
    private static final double SPINE_WIDTH = 90.0;
    private static final double RING_MIN = 40.0;
    private static final double RING_MAX = 70.0;
    private static final double RING_WIDTH = 45.0;
    private static final double BASIN_MIN = 10.0;
    private static final double BASIN_MAX = 18.0;
    /** Caldera continents sit high, so the basin keeps a full crust. */
    private static final double CALDERA_BASE_MIN = 155.0;
    /** Small-scale relief everywhere (blocks): bumps that break up contour-like surfaces. */
    private static final double MICRO_AMP = 1.5;
    /** Warp of step and mesa noises (blocks), so scarps and terraces wander instead of following isolines. */
    private static final double STEP_WARP = 6.0;
    private static final double STEP_WARP_SCALE = 45.0;

    /** Mountain mask noise above the threshold by this much reaches full strength. */
    private static final double MOUNTAIN_RAMP = 0.35;
    private static final double TERRACE_STEP = 8.0;
    private static final double TERRACE_BLEND = 0.6;
    /** Gentle undulation of the flat base (blocks, +/-). */
    private static final double BASE_HILLS = 3.0;
    /** Warp feature size, fraction of the radius. */
    private static final double WARP_SCALE = 0.5;
    /** Core blob mean radius and satellite blob mean radius, fractions of the continent radius. */
    private static final double CORE_MIN = 0.5;
    private static final double CORE_MAX = 0.65;
    private static final double STARTER_CORE = 0.8;
    private static final double SATELLITE_MIN = 0.28;
    private static final double SATELLITE_MAX = 0.5;
    /** Satellite centre distance from the core centre, fraction of the core mean radius (keeps them overlapping). */
    private static final double SATELLITE_DIST_MIN = 0.55;
    private static final double SATELLITE_DIST_MAX = 0.9;
    private static final double MAX_ASPECT = 1.8;
    /** Outline noise around each blob: feature size as a fraction of the blob radius. */
    private static final double BLOB_OUTLINE_SCALE = 1.0;
    /** Broad coast bends: a second domain warp (feature size, amplitude in blocks); below folding, so it keeps the shape connected. */
    private static final double BEND_SCALE = 220.0;
    private static final double BEND_AMP = 22.0;
    /**
     * Fractal coastline detail in blocks: feature size and amplitude per octave (coves,
     * headlands). Each octave's slope stays well below the distance field's slope of 1,
     * so the detail moves the coast without breaking off crumbs.
     */
    private static final double[] COAST_SCALE = {90.0, 36.0, 14.0};
    private static final double[] COAST_AMP = {8.0, 3.0, 1.2};
    /** Terrain regions: size of the region noise and of the features inside regions (blocks). */
    private static final double REGION_SCALE = 300.0;
    private static final double ROLLING_HILLS = 18.0;
    private static final double HIGHLAND_STEP = 22.0;
    private static final double MESA_MIN = 20.0;
    private static final double MESA_MAX = 42.0;
    private static final double SPIRE_HEIGHT = 55.0;
    /** Coastal slopes: drop of the terrain towards the edge where the coast is not a cliff. */
    private static final double COAST_DROP = 20.0;
    private static final double COAST_DROP_WIDTH = 70.0;

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
        out[off + EXTENT] = maxExtent(p, radius);
        placeBlobs(seed, cx, cz, radius, starter, out, off);
        out[off + PLATEAU] = starter ? p.starterPlateauRadius() : 0.0;
        out[off + MAX_Y] = p.continentPeakMaxY();
        placeCharacter(p, seed, starter, out, off);

        int span = p.continentRootsMax() - p.continentRootsMin() + 1;
        int roots = p.continentRootsMin() + (int) (u(seed, 3) * span);
        out[off + ROOT_COUNT] = roots;
        for (int k = 0; k < roots; k++) {
            int o = off + ROOT0 + k * ROOT_STRIDE;
            // Roots hang under a random blob, near its centre.
            int b = off + BLOB0 + BLOB_STRIDE * (int) (u(seed, 200 + k) * out[off + BLOB_COUNT]);
            double angle = u(seed, 10 + 4 * k) * 2.0 * Math.PI;
            double dist = Math.sqrt(u(seed, 11 + 4 * k)) * 0.5 * Math.min(out[b + 2], out[b + 3]);
            out[o] = out[b] + Math.cos(angle) * dist;
            out[o + 1] = out[b + 1] + Math.sin(angle) * dist;
            out[o + 2] = 40.0 + u(seed, 12 + 4 * k) * 0.08 * radius;
            out[o + 3] = (0.5 + 0.5 * u(seed, 13 + 4 * k)) * p.continentRootDepthMax();
        }
    }

    /**
     * A core ellipse at the centre and 2..5 satellite ellipses of various size, shape and
     * direction, each overlapping the core: an irregular, asymmetric but connected silhouette.
     */
    private static void placeBlobs(long seed, double cx, double cz, double radius, boolean starter, double[] out, int off) {
        int count = 3 + (int) (u(seed, 4) * (MAX_BLOBS - 2));
        out[off + BLOB_COUNT] = count;
        double core = radius * (starter ? STARTER_CORE : ShapeMath.lerp(CORE_MIN, CORE_MAX, u(seed, 5)));
        double coreAspect = starter ? 1.0 : 1.0 + (MAX_ASPECT - 1.0) * 0.5 * u(seed, 6);
        setBlob(out, off + BLOB0, cx, cz, core, coreAspect, u(seed, 7) * Math.PI);
        for (int k = 1; k < count; k++) {
            int i = 100 + 5 * k;
            double r = radius * ShapeMath.lerp(SATELLITE_MIN, SATELLITE_MAX, u(seed, i));
            double aspect = 1.0 + (MAX_ASPECT - 1.0) * u(seed, i + 1);
            double dir = u(seed, i + 2) * 2.0 * Math.PI;
            double dist = core * ShapeMath.lerp(SATELLITE_DIST_MIN, SATELLITE_DIST_MAX, u(seed, i + 3));
            setBlob(out, off + BLOB0 + BLOB_STRIDE * k, cx + Math.cos(dir) * dist, cz + Math.sin(dir) * dist,
                    r, aspect, u(seed, i + 4) * Math.PI);
        }
    }

    private static void placeCharacter(WorldParams p, long seed, boolean starter, double[] out, int off) {
        int character = MIXED;
        if (!starter) {
            double roll = u(seed, 8);
            character = 0;
            while (roll >= CHARACTER_CHANCE[character]) {
                character++;
            }
        }
        out[off + CHARACTER] = character;
        double[] w = CHARACTER_WEIGHTS[character];
        out[off + W_HILLS] = w[0];
        out[off + W_HIGHLAND] = w[1];
        out[off + W_MESA] = w[2];
        out[off + W_SPIRE] = w[3];
        out[off + W_RIDGE] = w[4];
        out[off + FLAT_SHIFT] = w[5];
        out[off + SPINE_HEIGHT] = character == MOUNTAINOUS ? ShapeMath.lerp(SPINE_MIN, SPINE_MAX, u(seed, 30)) : 0.0;
        if (character == CALDERA) {
            out[off + BASE_Y] = ShapeMath.lerp(Math.max(CALDERA_BASE_MIN, p.continentBaseYMin()), p.continentBaseYMax(), u(seed, 0));
            out[off + RING_HEIGHT] = ShapeMath.lerp(RING_MIN, RING_MAX, u(seed, 31));
            out[off + RING_RADIUS] = 0.6 * Math.min(out[off + BLOB0 + 2], out[off + BLOB0 + 3]);
            out[off + BASIN_DEPTH] = ShapeMath.lerp(BASIN_MIN, BASIN_MAX, u(seed, 32));
        }
    }

    private static void setBlob(double[] out, int o, double x, double z, double meanRadius, double aspect, double angle) {
        double sq = Math.sqrt(aspect);
        out[o] = x;
        out[o + 1] = z;
        out[o + 2] = meanRadius * sq;
        out[o + 3] = meanRadius / sq;
        out[o + 4] = angle;
    }

    private static double u(long seed, int i) {
        return Hash.unit(Hash.hash(seed, i));
    }

    /** Farthest the outline of a continent of this radius can reach from its centre. */
    public static double maxExtent(WorldParams p, double radius) {
        double coast = 0.0;
        for (double a : COAST_AMP) {
            coast += a;
        }
        double satellite = CORE_MAX * SATELLITE_DIST_MAX + SATELLITE_MAX * Math.sqrt(MAX_ASPECT);
        double reach = Math.max(Math.max(STARTER_CORE, CORE_MAX * Math.sqrt(1.0 + (MAX_ASPECT - 1.0) * 0.5)), satellite);
        return radius * (reach * (1.0 + p.continentOutlineNoise()) + p.continentWarp()) + BEND_AMP + coast;
    }

    /**
     * Signed horizontal distance to the outline (blocks): positive inside. The silhouette
     * is a smooth union of noisy ellipses; fractal detail in world space adds bays, coves
     * and headlands at several scales, so the coast is not a smooth curve.
     */
    public static double edge(WorldParams p, double[] prm, int off, long seed, double x, double z) {
        double r = prm[off + RADIUS];
        double warp = r * p.continentWarp();
        double wf = WARP_SCALE * r;
        double wx = x + warp * SimplexNoise.noise2(seed + 1, x / wf, z / wf)
                + BEND_AMP * SimplexNoise.noise2(seed + 20, x / BEND_SCALE, z / BEND_SCALE);
        double wz = z + warp * SimplexNoise.noise2(seed + 2, x / wf, z / wf)
                + BEND_AMP * SimplexNoise.noise2(seed + 24, x / BEND_SCALE, z / BEND_SCALE);
        double outline = -1.0e9;
        int count = (int) prm[off + BLOB_COUNT];
        for (int k = 0; k < count; k++) {
            int o = off + BLOB0 + BLOB_STRIDE * k;
            // Plain max: a smooth max would grow straight spikes along the bisector outside the blobs.
            outline = Math.max(outline, blobEdge(p, prm, o, seed + 40 + k, wx, wz));
        }
        double coast = 0.0;
        for (int o = 0; o < COAST_SCALE.length; o++) {
            coast += COAST_AMP[o] * SimplexNoise.noise2(seed + 21 + o, wx / COAST_SCALE[o], wz / COAST_SCALE[o]);
        }
        // Detail may add land only inside the outline, so no islets form off the coast.
        return outline + (coast > 0.0 ? coast * ShapeMath.smoothstep(0.0, 12.0, outline) : coast);
    }

    /** Signed distance to one noisy ellipse (blocks, approximate), positive inside. */
    private static double blobEdge(WorldParams p, double[] prm, int o, long seed, double x, double z) {
        double dx = x - prm[o];
        double dz = z - prm[o + 1];
        double a = prm[o + 2];
        double b = prm[o + 3];
        double c = Math.cos(prm[o + 4]);
        double s = Math.sin(prm[o + 4]);
        double u = (dx * c + dz * s) / a;
        double v = (-dx * s + dz * c) / b;
        double q = Math.sqrt(u * u + v * v);
        // Outline noise sampled on the blob's circle in the direction of the point: each blob stays star-shaped.
        double d = Math.sqrt(dx * dx + dz * dz);
        double nx = d > 1e-9 ? dx / d : 1.0;
        double nz = d > 1e-9 ? dz / d : 0.0;
        double f = BLOB_OUTLINE_SCALE;
        double n = SimplexNoise.noise2(seed, nx / f, nz / f);
        double target = 1.0 + p.continentOutlineNoise() * n;
        // First-order distance to the level set q = target: (target - q) / |grad q|, slope about 1 everywhere.
        if (q < 1e-9) {
            return target * Math.min(a, b);
        }
        double gu = u / a;
        double gv = v / b;
        double grad = Math.sqrt(gu * gu + gv * gv) / q;
        return (target - q) / grad;
    }

    /** Flat base height (gentle undulation): the crust hangs from it. */
    public static double flatTop(double[] prm, int off, long seed, double x, double z) {
        return prm[off + BASE_Y] + BASE_HILLS * SimplexNoise.noise2(seed + 5, x / 120.0, z / 120.0);
    }

    /**
     * Terrain above the flat base (negative on coastal slopes and in basins). The
     * continent's character sets the weights; inside, a region noise mixes plains, rolling
     * hills, stepped highlands, mesas with sheer walls, rare spire clusters and terraced
     * mountains. Mountainous continents add a range along their spine, calderas a ring of
     * mountains with passes around a basin. Small-scale relief breaks up every surface.
     */
    public static double mountain(WorldParams p, double[] prm, int off, long seed, double x, double z, double edge) {
        double inland = ShapeMath.smoothstep(0.0, 60.0, edge);
        double plateau = prm[off + PLATEAU];
        double open = 1.0;
        if (plateau > 0.0) {
            double dx = x - prm[off + CX];
            double dz = z - prm[off + CZ];
            open = ShapeMath.smoothstep(plateau, plateau + 120.0, Math.sqrt(dx * dx + dz * dz));
        }
        double micro = MICRO_AMP * SimplexNoise.noise2(seed + 27, x / 40.0, z / 40.0) * ShapeMath.smoothstep(0.0, 12.0, edge);
        double coastDrop = COAST_DROP * ShapeMath.smoothstep(-0.1, 0.4, SimplexNoise.noise2(seed + 17, x / 200.0, z / 200.0))
                * (1.0 - ShapeMath.smoothstep(0.0, COAST_DROP_WIDTH, edge)) * open;
        inland *= open;
        if (inland <= 0.0) {
            return micro - coastDrop;
        }

        double threshold = p.continentMountainThreshold() - prm[off + FLAT_SHIFT];
        double featured = ShapeMath.smoothstep(threshold, threshold + 0.3,
                SimplexNoise.noise2(seed + 9, x / REGION_SCALE, z / REGION_SCALE)) * inland;
        // Step and mesa noises are read at warped positions, so their edges wander.
        double sx = x + STEP_WARP * SimplexNoise.noise2(seed + 33, x / STEP_WARP_SCALE, z / STEP_WARP_SCALE);
        double sz = z + STEP_WARP * SimplexNoise.noise2(seed + 34, x / STEP_WARP_SCALE, z / STEP_WARP_SCALE);

        double terrain = 0.0;
        if (featured > 0.0) {
            double hills = ROLLING_HILLS * (0.5 + 0.5 * fbm(seed + 10, x, z, 160.0, 3));
            double highlandRegion = ShapeMath.smoothstep(0.0, 0.3, SimplexNoise.noise2(seed + 11, x / REGION_SCALE, z / REGION_SCALE));
            double steps = fbm(seed + 12, sx, sz, 150.0, 3);
            double highland = highlandRegion * HIGHLAND_STEP
                    * (ShapeMath.smoothstep(-0.05, 0.05, steps) + ShapeMath.smoothstep(0.3, 0.38, steps));
            double mesaRegion = ShapeMath.smoothstep(0.1, 0.35, SimplexNoise.noise2(seed + 13, x / REGION_SCALE, z / REGION_SCALE));
            double mesaShape = SimplexNoise.noise2(seed + 14, sx / 110.0, sz / 110.0) + 0.3 * SimplexNoise.noise2(seed + 15, sx / 30.0, sz / 30.0);
            double mesaHeight = ShapeMath.lerp(MESA_MIN, MESA_MAX, 0.5 + 0.5 * SimplexNoise.noise2(seed + 18, x / 300.0, z / 300.0));
            double mesa = mesaRegion * mesaHeight * ShapeMath.smoothstep(0.32, 0.38, mesaShape);
            // Spires: rare clusters only.
            double spireCluster = ShapeMath.smoothstep(0.35, 0.55, SimplexNoise.noise2(seed + 25, x / 220.0, z / 220.0));
            double spireT = spireCluster > 0.0
                    ? ShapeMath.clamp((SimplexNoise.noise2(seed + 16, x / 30.0, z / 30.0) - 0.72) / 0.22, 0.0, 1.0) : 0.0;
            double spire = prm[off + W_SPIRE] * spireCluster * SPIRE_HEIGHT * spireT * spireT;
            double ridged = prm[off + W_RIDGE] * ridgedMountains(p, prm, off, seed, x, z);
            terrain = featured * (Math.max(ridged, prm[off + W_HILLS] * hills
                    + Math.max(prm[off + W_HIGHLAND] * highland, prm[off + W_MESA] * mesa)) + spire);
        }

        double big = Math.max(spine(prm, off, seed, x, z), ring(prm, off, seed, x, z)) * inland;
        return Math.max(terrain, big) - basin(prm, off, seed, x, z) * inland + micro - coastDrop;
    }

    /** Mountain range along the segments from the core to each satellite blob (meandering). */
    private static double spine(double[] prm, int off, long seed, double x, double z) {
        double height = prm[off + SPINE_HEIGHT];
        if (height <= 0.0) {
            return 0.0;
        }
        double wx = x + 40.0 * SimplexNoise.noise2(seed + 35, x / 160.0, z / 160.0);
        double wz = z + 40.0 * SimplexNoise.noise2(seed + 36, x / 160.0, z / 160.0);
        double ax = prm[off + BLOB0];
        double az = prm[off + BLOB0 + 1];
        double best = Double.MAX_VALUE;
        int count = (int) prm[off + BLOB_COUNT];
        for (int k = 1; k < count; k++) {
            int o = off + BLOB0 + BLOB_STRIDE * k;
            best = Math.min(best, segmentDistance(wx, wz, ax, az, prm[o], prm[o + 1]));
        }
        double profile = 1.0 - ShapeMath.smoothstep(0.0, SPINE_WIDTH, best);
        if (profile <= 0.0) {
            return 0.0;
        }
        double r1 = 1.0 - Math.abs(SimplexNoise.noise2(seed + 37, x / 90.0, z / 90.0));
        double r2 = 1.0 - Math.abs(SimplexNoise.noise2(seed + 38, x / 35.0, z / 35.0));
        return height * Math.pow(profile, 1.5) * (0.45 + 0.4 * r1 * r1 + 0.15 * r2);
    }

    /** Ring of mountains around the core centre, with passes. */
    private static double ring(double[] prm, int off, long seed, double x, double z) {
        double height = prm[off + RING_HEIGHT];
        if (height <= 0.0) {
            return 0.0;
        }
        double d = ringDistance(prm, off, seed, x, z);
        double profile = 1.0 - ShapeMath.smoothstep(0.0, RING_WIDTH, Math.abs(d - prm[off + RING_RADIUS]));
        if (profile <= 0.0) {
            return 0.0;
        }
        double passes = ShapeMath.smoothstep(-0.55, -0.15, SimplexNoise.noise2(seed + 39, x / 110.0, z / 110.0));
        double r1 = 1.0 - Math.abs(SimplexNoise.noise2(seed + 40, x / 60.0, z / 60.0));
        return height * Math.pow(profile, 1.3) * (0.6 + 0.4 * r1 * r1) * passes;
    }

    /** Depth of the caldera basin inside the ring. */
    private static double basin(double[] prm, int off, long seed, double x, double z) {
        double depth = prm[off + BASIN_DEPTH];
        if (depth <= 0.0) {
            return 0.0;
        }
        double inner = prm[off + RING_RADIUS] - RING_WIDTH;
        return depth * (1.0 - ShapeMath.smoothstep(inner - 40.0, inner + 20.0, ringDistance(prm, off, seed, x, z)));
    }

    private static double ringDistance(double[] prm, int off, long seed, double x, double z) {
        double wx = x + 25.0 * SimplexNoise.noise2(seed + 41, x / 120.0, z / 120.0) - prm[off + BLOB0];
        double wz = z + 25.0 * SimplexNoise.noise2(seed + 42, x / 120.0, z / 120.0) - prm[off + BLOB0 + 1];
        return Math.sqrt(wx * wx + wz * wz);
    }

    private static double segmentDistance(double px, double pz, double ax, double az, double bx, double bz) {
        double vx = bx - ax;
        double vz = bz - az;
        double len2 = vx * vx + vz * vz;
        double t = len2 > 0.0 ? ShapeMath.clamp(((px - ax) * vx + (pz - az) * vz) / len2, 0.0, 1.0) : 0.0;
        double dx = px - ax - t * vx;
        double dz = pz - az - t * vz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Terraced ridged mountains where their own mask allows them. */
    private static double ridgedMountains(WorldParams p, double[] prm, int off, long seed, double x, double z) {
        double mask = ShapeMath.smoothstep(0.1, 0.1 + MOUNTAIN_RAMP, SimplexNoise.noise2(seed + 6, x / 700.0, z / 700.0));
        if (mask <= 0.0) {
            return 0.0;
        }
        double r1 = 1.0 - Math.abs(SimplexNoise.noise2(seed + 7, x / 280.0, z / 280.0));
        double r2 = 1.0 - Math.abs(SimplexNoise.noise2(seed + 8, x / 110.0, z / 110.0));
        double r3 = 1.0 - Math.abs(SimplexNoise.noise2(seed + 19, x / 45.0, z / 45.0));
        double ridge = 0.65 * r1 * r1 + 0.25 * r2 * r2 + 0.1 * r3;
        double h = mask * prm[off + MOUNTAINS] * ridge * (p.continentPeakMaxY() - prm[off + BASE_Y]);
        // Irregular terraces: the step boundary wanders, so terraces are not contour lines.
        double steps = (h + 3.0 * SimplexNoise.noise2(seed + 30, x / 60.0, z / 60.0)) / TERRACE_STEP;
        double k = Math.floor(steps);
        double terraced = TERRACE_STEP * (k + ShapeMath.smoothstep(0.3, 0.7, steps - k));
        return Math.max(0.0, ShapeMath.lerp(h, terraced, TERRACE_BLEND));
    }

    /** Fractal noise, roughly -1..1. */
    private static double fbm(long seed, double x, double z, double scale, int octaves) {
        double sum = 0.0;
        double amp = 1.0;
        double norm = 0.0;
        for (int o = 0; o < octaves; o++) {
            sum += amp * SimplexNoise.noise2(seed + 101L * o, x / scale, z / scale);
            norm += amp;
            amp *= 0.5;
            scale *= 0.5;
        }
        return sum / norm;
    }

    /** Top surface height. */
    public static double top(WorldParams p, double flatTop, double mountain) {
        return Math.min(flatTop + mountain, p.continentPeakMaxY());
    }

    /**
     * Underside height: crust under the flat terrain, or under the lowered surface in
     * basins and coastal slopes (thinning to the edge), and cone roots; never below minBottomY.
     */
    public static double bottom(WorldParams p, double[] prm, int off, double x, double z, double edge, double flatTop,
                                double mountain) {
        double b = flatTop + Math.min(0.0, mountain) - prm[off + CRUST] * ShapeMath.smoothstep(0.0, 40.0, edge);
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
