package pl.skyseas.shape;

import pl.skyseas.config.WorldParams;
import pl.skyseas.layout.Hash;

/**
 * Continent shape. The silhouette (irregular, elongated, crescent, hook, branched, or
 * composite: 2-4 members joined by isthmuses or rock bridges) is a union of noisy
 * ellipses plus link capsules, with a fractal coast. The whole slab is tilted; members of
 * a composite continent sit at different heights. Terrain regions (plains, hills,
 * highlands, mesas, spires, terraced mountains), mountain ranges, caldera rings and
 * massifs on top; a thin crust with gentle hanging relief and rock spires underneath.
 * Column values are 2D ({@link #column}); {@link SkyColumn} turns them into 3D density
 * and the layout turns the spires into per-block density.
 *
 * <p>Parameters of one continent are packed in a {@code double[]} at an offset (see the
 * index constants); {@link #place} and {@link #placeSpires} fill them deterministically
 * from the shape seed.
 */
public final class ContinentShape {
    /** Column value kinds. */
    public static final int EDGE = 0;
    public static final int TOP = 1;
    public static final int BOTTOM = 2;
    public static final int MOUNTAIN = 3;
    /** Weight 0..1 of the 3D detail noise (edge band, tall walls; none on rock bridges). */
    public static final int DETAIL = 4;
    /** Terrain top without river channels (rivers exist only in layouts that hold the continent). */
    public static final int TERRAIN = 5;

    public static final int CX = 0;
    public static final int CZ = 1;
    public static final int RADIUS = 2;
    /** Flat terrain height of member 0 (other members: {@link #MEMBER_BASE0}). */
    public static final int BASE_Y = 3;
    public static final int CRUST = 4;
    public static final int MOUNTAINS = 5;
    public static final int SPIRE_COUNT = 6;
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
    /** Mountain range along the blob chains (parent to child); 0 = none. */
    public static final int SPINE_HEIGHT = 17;
    /** Ring of mountains around the core with a basin inside; 0 = none. */
    public static final int RING_HEIGHT = 18;
    public static final int RING_RADIUS = 19;
    public static final int BASIN_DEPTH = 20;
    public static final int BLOB_COUNT = 21;
    public static final int SILHOUETTE = 22;
    /** Tilt of the slab: height gradient per block along x and z. */
    public static final int TILT_X = 23;
    public static final int TILT_Z = 24;
    public static final int MEMBER_COUNT = 25;
    public static final int LINK_COUNT = 26;
    /** Massif centre, radius and summit height; summit 0 = none. */
    public static final int MASSIF_X = 27;
    public static final int MASSIF_Z = 28;
    public static final int MASSIF_RADIUS = 29;
    public static final int MASSIF_PEAK = 30;
    /** Bounding box of the outline relative to the centre (includes all noise). */
    public static final int BOX_MIN_X = 31;
    public static final int BOX_MAX_X = 32;
    public static final int BOX_MIN_Z = 33;
    public static final int BOX_MAX_Z = 34;
    public static final int ELEVATED = 35;
    /** Tilt in blocks across the continent (for tools). */
    public static final int TILT = 36;
    public static final int MEMBER_BASE0 = 37;
    public static final int MAX_MEMBERS = 4;
    /** Blobs (overlapping ellipses whose union is the land): x, z, semi-axis a, semi-axis b, angle, member, parent. */
    public static final int BLOB0 = MEMBER_BASE0 + MAX_MEMBERS;
    public static final int BLOB_STRIDE = 7;
    public static final int MAX_BLOBS = 10;
    private static final int B_MEMBER = 5;
    private static final int B_PARENT = 6;
    /** Links between member 0 and another member: a capsule from A to B (both ends inside the land). */
    public static final int LINK0 = BLOB0 + BLOB_STRIDE * MAX_BLOBS;
    public static final int LINK_STRIDE = 11;
    public static final int MAX_LINKS = MAX_MEMBERS - 1;
    private static final int L_HALF_WIDTH = 4;
    public static final int L_BRIDGE = 5;
    private static final int L_MEMBER_A = 6;
    private static final int L_MEMBER_B = 7;
    /** Fractions of the capsule where it leaves member A and reaches member B. */
    private static final int L_T0 = 8;
    private static final int L_T1 = 9;
    /** Rock bridge thickness; a negative value marks a cliff in the middle of an isthmus. */
    private static final int L_THICKNESS = 10;
    /** Hanging spires: x, z, top Y (inside the crust), length, top radius, lean angle. */
    public static final int SPIRE0 = LINK0 + LINK_STRIDE * MAX_LINKS;
    public static final int SPIRE_STRIDE = 6;
    public static final int MAX_SPIRES = 8;
    /**
     * Rivers: a count, then per river a header (point count, bounding box min x, max x,
     * min z, max z incl. banks) and points (x, z, water level, half width). The water fills
     * blocks below the level; each segment carries the level of its downstream point, so
     * levels step down along the river.
     */
    public static final int RIVER_COUNT = SPIRE0 + SPIRE_STRIDE * MAX_SPIRES;
    public static final int RIVER0 = RIVER_COUNT + 1;
    public static final int MAX_RIVERS = 3;
    public static final int RIVER_HEADER = 5;
    public static final int RIVER_POINT = 4;
    public static final int MAX_RIVER_POINTS = 140;
    public static final int RIVER_STRIDE = RIVER_HEADER + RIVER_POINT * MAX_RIVER_POINTS;
    public static final int STRIDE = RIVER0 + RIVER_STRIDE * MAX_RIVERS;

    public static final int IRREGULAR = 0;
    public static final int ELONGATED = 1;
    public static final int CRESCENT = 2;
    public static final int HOOK = 3;
    public static final int BRANCHED = 4;
    public static final int COMPOSITE = 5;
    public static final String[] SILHOUETTE_NAMES = {"irregular", "elongated", "crescent", "hook", "branched", "composite"};
    /** Cumulative chances of the single-piece silhouettes (irregular .. branched). */
    private static final double[] SILHOUETTE_CHANCE = {0.3, 0.5, 0.65, 0.8, 1.0};

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
    /** Massif radius range (blocks). */
    private static final double MASSIF_RADIUS_MIN = 110.0;
    private static final double MASSIF_RADIUS_MAX = 180.0;
    /** Small-scale relief everywhere (blocks): bumps that break up contour-like surfaces. */
    private static final double MICRO_AMP = 1.5;
    /** Warp of step and mesa noises (blocks), so scarps and terraces wander instead of following isolines. */
    private static final double STEP_WARP = 6.0;
    /** Underside relief (blocks): broad hanging bumps and a little fine roughness. */
    private static final double UNDERSIDE_AMP = 6.0;
    private static final double UNDERSIDE_FINE_AMP = 1.5;
    private static final double STEP_WARP_SCALE = 45.0;

    /** Mountain mask noise above the threshold by this much reaches full strength. */
    private static final double MOUNTAIN_RAMP = 0.35;
    private static final double TERRACE_STEP = 8.0;
    private static final double TERRACE_BLEND = 0.6;
    /** Gentle undulation of the flat base (blocks, +/-). */
    private static final double BASE_HILLS = 3.0;
    /** Warp feature size, fraction of the radius. */
    private static final double WARP_SCALE = 0.5;
    /** Irregular silhouette: core and satellite mean radius, fractions of the radius. */
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
    private static final double COAST_SUM = 12.2;
    /** Coast detail kept on isthmuses (none on rock bridges). */
    private static final double ISTHMUS_COAST = 0.4;
    /** 3D detail kept on isthmuses. */
    private static final double ISTHMUS_DETAIL = 0.4;
    /** Links reach this far into the land at both ends (blocks), beyond any coast detail. */
    private static final double LINK_OVERLAP = 16.0;
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
    /** Coasts slope down only where the plain lies above this Y (low continents keep plain cliffs into the sea). */
    private static final double COAST_DROP_FLOOR = 123.0;
    /** Thickness of the land at the coast (blocks); the crust grows from it inland. */
    private static final double EDGE_THICKNESS = 16.0;
    /** Hanging spires: top radius range, how far into the crust they start, least spacing. */
    private static final double SPIRE_RADIUS_MIN = 4.0;
    private static final double SPIRE_RADIUS_MAX = 8.0;
    private static final double SPIRE_EMBED = 8.0;
    /** Largest horizontal reach of a spire from its top centre (radius + lean). */
    public static final double SPIRE_REACH = SPIRE_RADIUS_MAX + 6.0 + 4.0;

    private ContinentShape() {
    }

    public static int stride(WorldParams p) {
        return STRIDE;
    }

    /**
     * Fills the parameters of a continent centred at cx, cz (spires excluded, see
     * {@link #placeSpires}). {@code distance} is the distance of its grid cell from
     * spawn: it decides elevated continents and massifs, so it must not depend on cx, cz.
     */
    public static void place(WorldParams p, long seed, double cx, double cz, double radius, boolean starter,
                             double distance, double[] out, int off) {
        for (int i = 0; i < STRIDE; i++) {
            out[off + i] = 0.0;
        }
        out[off + CX] = cx;
        out[off + CZ] = cz;
        out[off + RADIUS] = radius;
        out[off + CRUST] = ShapeMath.lerp(p.continentCrustMin(), p.continentCrustMax(), u(seed, 1));
        out[off + MOUNTAINS] = 0.5 + 0.5 * u(seed, 2);
        int character = MIXED;
        if (!starter) {
            double roll = u(seed, 8);
            character = 0;
            while (roll >= CHARACTER_CHANCE[character]) {
                character++;
            }
        }
        int silhouette = IRREGULAR;
        if (!starter && character != CALDERA) {
            // Calderas are never composite, so the others are a little more often.
            if (u(seed, 5) < p.continentCompositeShare() / CHARACTER_CHANCE[CALDERA - 1]) {
                silhouette = COMPOSITE;
            } else {
                double roll = u(seed, 4);
                while (roll >= SILHOUETTE_CHANCE[silhouette]) {
                    silhouette++;
                }
            }
        }
        out[off + SILHOUETTE] = silhouette;
        switch (silhouette) {
            case ELONGATED -> walker(seed, cx, cz, 0, 2 + (int) (u(seed, 110) * 3), radius * 2.4 / 4.0,
                    radius * 2.4 / ShapeMath.lerp(2.0, 4.0, u(seed, 111)), u(seed, 112) * Math.PI,
                    (u(seed, 113) - 0.5) * 0.7, 0, -1, out, off);
            case CRESCENT -> {
                double turn = ShapeMath.lerp(3.5, 4.7, u(seed, 120)) * (u(seed, 121) < 0.5 ? -1.0 : 1.0);
                int n = 6;
                double arc = 0.72 * radius;
                walker(seed, cx, cz, 0, n, arc * Math.abs(turn) / (n - 1),
                        radius * ShapeMath.lerp(0.55, 0.75, u(seed, 122)), u(seed, 123) * 2.0 * Math.PI, turn, 0, -1, out, off);
            }
            case HOOK -> {
                double turn = ShapeMath.lerp(1.7, 2.8, u(seed, 130)) * (u(seed, 131) < 0.5 ? -1.0 : 1.0);
                walker(seed, cx, cz, 0, 6, radius * 0.45, radius * ShapeMath.lerp(0.55, 0.75, u(seed, 132)),
                        u(seed, 133) * 2.0 * Math.PI, turn, 3, -1, out, off);
            }
            case BRANCHED -> branched(seed, cx, cz, radius, out, off);
            case COMPOSITE -> composite(p, seed, cx, cz, radius, out, off);
            default -> irregular(seed, cx, cz, radius, starter, 0, 3 + (int) (u(seed, 100) * (6 - 2)), out, off);
        }
        if (out[off + MEMBER_COUNT] == 0.0) {
            out[off + MEMBER_COUNT] = 1;
        }
        bounds(p, out, off);
        out[off + PLATEAU] = starter ? p.starterPlateauRadius() : 0.0;
        placeCharacter(seed, character, out, off);
        placeHeights(p, seed, starter, character, distance, out, off);
    }

    /**
     * Heights: member bases, tilt and summit limit. The flat terrain (members and tilt)
     * stays within the base range when it fits; a composite continent whose members and
     * tilt span more keeps the lower bound and rises above the upper one.
     */
    private static void placeHeights(WorldParams p, long seed, boolean starter, int character, double distance,
                                     double[] out, int off) {
        double lo;
        double hi;
        boolean elevated = !starter && distance > p.zoneRadii()[0] && u(seed, 9) < p.continentElevatedShare();
        if (starter) {
            lo = p.starterBaseYMin();
            hi = p.starterBaseYMax();
        } else if (elevated) {
            lo = p.continentElevatedBaseYMin();
            hi = p.continentElevatedBaseYMax();
        } else if (character != CALDERA && u(seed, 16) < p.continentLowShare()) {
            // Low continents: coasts, underside and parts of the plains sink into the sea.
            lo = p.continentLowBaseYMin();
            hi = p.continentLowBaseYMax();
        } else {
            lo = character == CALDERA ? Math.max(CALDERA_BASE_MIN, p.continentBaseYMin()) : p.continentBaseYMin();
            hi = p.continentBaseYMax();
        }
        out[off + ELEVATED] = elevated ? 1.0 : 0.0;
        double tilt = starter ? 0.0 : ShapeMath.lerp(p.continentTiltMin(), p.continentTiltMax(), u(seed, 6));
        double dir = u(seed, 7) * 2.0 * Math.PI;
        double reach = Math.max(1.0, out[off + EXTENT]);
        out[off + TILT] = tilt;
        out[off + TILT_X] = Math.cos(dir) * tilt / (2.0 * reach);
        out[off + TILT_Z] = Math.sin(dir) * tilt / (2.0 * reach);
        int members = (int) out[off + MEMBER_COUNT];
        double sign = u(seed, 15) < 0.5 ? -1.0 : 1.0;
        double low = 0.0;
        double high = 0.0;
        for (int m = 1; m < members; m++) {
            double step = sign * ShapeMath.lerp(p.continentMemberStepMin(), p.continentMemberStepMax(), u(seed, 450 + m));
            out[off + MEMBER_BASE0 + m] = step;
            low = Math.min(low, step);
            high = Math.max(high, step);
        }
        low -= tilt / 2.0;
        high += tilt / 2.0;
        double from = lo - low;
        double to = hi - high;
        double base = from <= to ? ShapeMath.lerp(from, to, u(seed, 0)) : from;
        for (int m = 0; m < members; m++) {
            out[off + MEMBER_BASE0 + m] += base;
        }
        out[off + BASE_Y] = base;
        double peak = Math.min(300.0, Math.max(p.continentPeakMaxY(), base + high + 40.0));
        double massifShare = ShapeMath.lerp(p.continentMassifShareMin(), p.continentMassifShareMax(),
                ShapeMath.smoothstep(distance / p.zoneMaxRadius()));
        if (!starter && character != CALDERA && u(seed, 10) < massifShare) {
            // On the largest blob of member 0, so the massif stands inland.
            int best = off + BLOB0;
            int count = (int) out[off + BLOB_COUNT];
            for (int k = 1; k < count; k++) {
                int o = off + BLOB0 + BLOB_STRIDE * k;
                if (out[o + B_MEMBER] == 0.0 && out[o + 2] * out[o + 3] > out[best + 2] * out[best + 3]) {
                    best = o;
                }
            }
            out[off + MASSIF_X] = out[best];
            out[off + MASSIF_Z] = out[best + 1];
            out[off + MASSIF_RADIUS] = Math.min(ShapeMath.lerp(MASSIF_RADIUS_MIN, MASSIF_RADIUS_MAX, u(seed, 12)),
                    1.2 * Math.min(out[best + 2], out[best + 3]));
            out[off + MASSIF_PEAK] = ShapeMath.lerp(p.continentMassifPeakYMin(), p.continentMassifPeakYMax(), u(seed, 13));
            peak = Math.max(peak, out[off + MASSIF_PEAK]);
        }
        out[off + MAX_Y] = peak;
    }

    private static void irregular(long seed, double cx, double cz, double radius, boolean starter, int member, int count,
                                  double[] out, int off) {
        int salt = 100 + 40 * member;
        double core = radius * (starter ? STARTER_CORE : ShapeMath.lerp(CORE_MIN, CORE_MAX, u(seed, salt + 1)));
        double coreAspect = starter ? 1.0 : 1.0 + (MAX_ASPECT - 1.0) * 0.5 * u(seed, salt + 2);
        int root = addBlob(out, off, cx, cz, core, coreAspect, u(seed, salt + 3) * Math.PI, member, -1);
        for (int k = 1; k < count; k++) {
            int i = salt + 4 + 5 * k;
            double r = radius * ShapeMath.lerp(SATELLITE_MIN, SATELLITE_MAX, u(seed, i));
            double aspect = 1.0 + (MAX_ASPECT - 1.0) * u(seed, i + 1);
            double dir = u(seed, i + 2) * 2.0 * Math.PI;
            double dist = core * ShapeMath.lerp(SATELLITE_DIST_MIN, SATELLITE_DIST_MAX, u(seed, i + 3));
            addBlob(out, off, cx + Math.cos(dir) * dist, cz + Math.sin(dir) * dist, r, aspect, u(seed, i + 4) * Math.PI,
                    member, root);
        }
    }

    /**
     * A chain of overlapping elongated blobs along a path that turns by {@code turn}
     * radians in total (spread over the steps after {@code straight} straight ones):
     * elongated (little turn), crescent or horseshoe (turn over the whole path), hook
     * (straight shaft, then the turn). Centred on cx, cz; ends are a little thinner.
     */
    private static void walker(long seed, double cx, double cz, int salt, int n, double step, double width,
                               double heading, double turn, int straight, int parent, double[] out, int off) {
        int first = (int) out[off + BLOB_COUNT];
        double x = 0.0;
        double z = 0.0;
        double sx = 0.0;
        double sz = 0.0;
        double h = heading;
        int turning = Math.max(1, n - 1 - straight);
        for (int i = 0; i < n; i++) {
            double end = n > 1 ? Math.abs(2.0 * i / (n - 1) - 1.0) : 0.0;
            double w = width * ShapeMath.lerp(0.8, 1.2, u(seed, 140 + salt + i)) * (1.0 - 0.3 * end);
            double along = Math.max(step * 0.8, w * 0.55);
            addBlob(out, off, x, z, Math.sqrt(along * w * 0.5), along / (w * 0.5), h, 0, i == 0 ? parent : first + i - 1);
            sx += x;
            sz += z;
            if (i >= straight) {
                h += turn / turning;
            }
            x += Math.cos(h) * step;
            z += Math.sin(h) * step;
        }
        // Centre the chain on cx, cz.
        double mx = cx - sx / n;
        double mz = cz - sz / n;
        for (int k = first; k < first + n; k++) {
            int o = off + BLOB0 + BLOB_STRIDE * k;
            out[o] += mx;
            out[o + 1] += mz;
        }
    }

    /** A core with 3-4 fingers of land radiating from it. */
    private static void branched(long seed, double cx, double cz, double radius, double[] out, int off) {
        double core = radius * 0.38;
        int root = addBlob(out, off, cx, cz, core, 1.0 + 0.4 * u(seed, 150), u(seed, 151) * Math.PI, 0, -1);
        int fingers = 3 + (int) (u(seed, 152) * 2);
        double a0 = u(seed, 153) * 2.0 * Math.PI;
        for (int f = 0; f < fingers; f++) {
            double a = a0 + f * 2.0 * Math.PI / fingers + (u(seed, 154 + f) - 0.5) * 0.7;
            int n = fingers == 4 ? 2 : 3;
            double step = radius * 0.3;
            double w = radius * ShapeMath.lerp(0.22, 0.32, u(seed, 160 + f));
            // Start well inside the core, so the first finger blob always overlaps it.
            double x = cx + Math.cos(a) * core * 0.4;
            double z = cz + Math.sin(a) * core * 0.4;
            double bend = (u(seed, 165 + f) - 0.5) * 0.8;
            int parent = root;
            for (int i = 0; i < n; i++) {
                x += Math.cos(a) * step;
                z += Math.sin(a) * step;
                double wi = w * (1.0 - 0.2 * i);
                double along = Math.max(step * 0.85, wi * 0.55);
                parent = addBlob(out, off, x, z, Math.sqrt(along * wi * 0.5), along / (wi * 0.5), a, 0, parent);
                a += bend;
            }
        }
    }

    /**
     * Composite continent: member 0 in the middle, 1-3 more members around it, each an
     * irregular cluster of blobs, joined to member 0 by an isthmus (slope or cliff) or a
     * rock bridge across a gap.
     */
    private static void composite(WorldParams p, long seed, double cx, double cz, double radius, double[] out, int off) {
        double roll = u(seed, 300);
        int members = roll < 0.5 ? 2 : (roll < 0.85 ? 3 : 4);
        out[off + MEMBER_COUNT] = members;
        int perMember = members == 4 ? 2 : 3;
        double memberRadius = radius * (members == 2 ? 0.6 : 0.48);
        irregular(seed, cx, cz, memberRadius * 1.15, false, 0, 2 + (int) (u(seed, 301) * (perMember - 1)), out, off);
        double a0 = u(seed, 302) * 2.0 * Math.PI;
        double reach0 = memberReach(p, out, off, 0, cx, cz);
        for (int m = 1; m < members; m++) {
            int i = 310 + 10 * m;
            double a = a0 + (m - 1) * 2.0 * Math.PI / (members - 1) + (u(seed, i) - 0.5) * 0.6;
            boolean bridge = u(seed, i + 1) < 0.4;
            double step = ShapeMath.lerp(p.continentMemberStepMin(), p.continentMemberStepMax(), u(seed, 450 + m));
            double gap = bridge ? Math.max(ShapeMath.lerp(25.0, 50.0, u(seed, i + 2)), 1.3 * step)
                    : ShapeMath.lerp(40.0, 100.0, u(seed, i + 2));
            int first = (int) out[off + BLOB_COUNT];
            double r = memberRadius * ShapeMath.lerp(0.85, 1.15, u(seed, i + 3));
            irregular(seed, 0.0, 0.0, r, false, m, 1 + (int) (u(seed, i + 4) * perMember), out, off);
            int last = (int) out[off + BLOB_COUNT];
            double reach = memberReach(p, out, off, m, 0.0, 0.0);
            double ca = Math.cos(a);
            double sa = Math.sin(a);
            moveBlobs(out, off, first, last, cx + ca * (reach0 + reach + gap), cz + sa * (reach0 + reach + gap));
            // The reach is the farthest point in any direction; pull the member in to the wanted gap.
            double[] t = new double[2];
            double len = reach0 + reach + gap;
            if (crossing(p, seed, out, off, cx, cz, cx + ca * len, cz + sa * len, 0, m, t)) {
                double actual = (t[1] - t[0]) * len;
                moveBlobs(out, off, first, last, -ca * (actual - gap), -sa * (actual - gap));
                len -= actual - gap;
            }
            if (!crossing(p, seed, out, off, cx, cz, cx + ca * len, cz + sa * len, 0, m, t)) {
                continue; // members touch: they merge with a step between them
            }
            int l = (int) out[off + LINK_COUNT];
            int o = off + LINK0 + LINK_STRIDE * l;
            double ta = t[0] - LINK_OVERLAP / len;
            double tb = t[1] + LINK_OVERLAP / len;
            out[o] = cx + ca * len * ta;
            out[o + 1] = cz + sa * len * ta;
            out[o + 2] = cx + ca * len * tb;
            out[o + 3] = cz + sa * len * tb;
            out[o + L_HALF_WIDTH] = bridge ? ShapeMath.lerp(3.0, 5.0, u(seed, i + 5)) : ShapeMath.lerp(10.0, 20.0, u(seed, i + 5));
            out[o + L_BRIDGE] = bridge ? 1.0 : 0.0;
            out[o + L_MEMBER_A] = 0;
            out[o + L_MEMBER_B] = m;
            out[o + L_T0] = (t[0] - ta) / (tb - ta);
            out[o + L_T1] = (t[1] - ta) / (tb - ta);
            out[o + L_THICKNESS] = bridge ? ShapeMath.lerp(6.0, 10.0, u(seed, i + 6)) : (u(seed, i + 6) < 0.4 ? -1.0 : 0.0);
            out[off + LINK_COUNT] = l + 1;
        }
    }

    /**
     * Where the segment from (ax, az) to (bx, bz) leaves member a and enters member b
     * (fractions in t[0], t[1], outline without coast detail). False if they overlap.
     */
    private static boolean crossing(WorldParams p, long seed, double[] prm, int off, double ax, double az,
                                    double bx, double bz, int a, int b, double[] t) {
        double len = Math.hypot(bx - ax, bz - az);
        int steps = (int) Math.ceil(len / 2.0);
        t[0] = -1.0;
        for (int i = 0; i <= steps; i++) {
            double f = (double) i / steps;
            if (memberEdge(p, seed, prm, off, a, ShapeMath.lerp(ax, bx, f), ShapeMath.lerp(az, bz, f)) < 0.0) {
                t[0] = f;
                break;
            }
        }
        t[1] = -1.0;
        for (int i = steps; i >= 0; i--) {
            double f = (double) i / steps;
            if (memberEdge(p, seed, prm, off, b, ShapeMath.lerp(ax, bx, f), ShapeMath.lerp(az, bz, f)) < 0.0) {
                t[1] = f;
                break;
            }
        }
        return t[0] >= 0.0 && t[1] > t[0];
    }

    private static double memberEdge(WorldParams p, long seed, double[] prm, int off, int member, double x, double z) {
        double best = -1.0e9;
        int count = (int) prm[off + BLOB_COUNT];
        for (int k = 0; k < count; k++) {
            int o = off + BLOB0 + BLOB_STRIDE * k;
            if (prm[o + B_MEMBER] == member) {
                best = Math.max(best, blobEdge(p, prm, o, seed + 40 + k, x, z));
            }
        }
        return best;
    }

    /** Farthest the outline of one member reaches from (x, z). */
    private static double memberReach(WorldParams p, double[] prm, int off, int member, double x, double z) {
        double reach = 0.0;
        int count = (int) prm[off + BLOB_COUNT];
        for (int k = 0; k < count; k++) {
            int o = off + BLOB0 + BLOB_STRIDE * k;
            if (prm[o + B_MEMBER] == member) {
                double d = Math.hypot(prm[o] - x, prm[o + 1] - z);
                reach = Math.max(reach, d + Math.max(prm[o + 2], prm[o + 3]) * (1.0 + p.continentOutlineNoise()));
            }
        }
        return reach;
    }

    private static void moveBlobs(double[] out, int off, int first, int last, double dx, double dz) {
        for (int k = first; k < last; k++) {
            int o = off + BLOB0 + BLOB_STRIDE * k;
            out[o] += dx;
            out[o + 1] += dz;
        }
    }

    private static void placeCharacter(long seed, int character, double[] out, int off) {
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
            out[off + RING_HEIGHT] = ShapeMath.lerp(RING_MIN, RING_MAX, u(seed, 31));
            out[off + RING_RADIUS] = 0.6 * Math.min(out[off + BLOB0 + 2], out[off + BLOB0 + 3]);
            out[off + BASIN_DEPTH] = ShapeMath.lerp(BASIN_MIN, BASIN_MAX, u(seed, 32));
        }
    }

    /** Adds a blob of the given mean radius and aspect; returns its index. */
    private static int addBlob(double[] out, int off, double x, double z, double meanRadius, double aspect, double angle,
                               int member, int parent) {
        int k = (int) out[off + BLOB_COUNT];
        if (k >= MAX_BLOBS) {
            throw new IllegalStateException("too many blobs");
        }
        int o = off + BLOB0 + BLOB_STRIDE * k;
        double sq = Math.sqrt(aspect);
        out[o] = x;
        out[o + 1] = z;
        out[o + 2] = meanRadius * sq;
        out[o + 3] = meanRadius / sq;
        out[o + 4] = angle;
        out[o + B_MEMBER] = member;
        out[o + B_PARENT] = parent;
        out[off + BLOB_COUNT] = k + 1;
        return k;
    }

    private static double u(long seed, int i) {
        return Hash.unit(Hash.hash(seed, i));
    }

    /** Bounding box of the outline (blobs, links, outline noise, warp, bends, coast detail) and the reach. */
    private static void bounds(WorldParams p, double[] prm, int off) {
        double pad = prm[off + RADIUS] * p.continentWarp() + BEND_AMP + COAST_SUM + 2.0;
        double cx = prm[off + CX];
        double cz = prm[off + CZ];
        double minX = 0.0;
        double maxX = 0.0;
        double minZ = 0.0;
        double maxZ = 0.0;
        int count = (int) prm[off + BLOB_COUNT];
        for (int k = 0; k < count; k++) {
            int o = off + BLOB0 + BLOB_STRIDE * k;
            double c = Math.cos(prm[o + 4]);
            double s = Math.sin(prm[o + 4]);
            double f = 1.0 + p.continentOutlineNoise();
            double ex = f * Math.sqrt(prm[o + 2] * c * prm[o + 2] * c + prm[o + 3] * s * prm[o + 3] * s);
            double ez = f * Math.sqrt(prm[o + 2] * s * prm[o + 2] * s + prm[o + 3] * c * prm[o + 3] * c);
            minX = Math.min(minX, prm[o] - cx - ex);
            maxX = Math.max(maxX, prm[o] - cx + ex);
            minZ = Math.min(minZ, prm[o + 1] - cz - ez);
            maxZ = Math.max(maxZ, prm[o + 1] - cz + ez);
        }
        int links = (int) prm[off + LINK_COUNT];
        for (int l = 0; l < links; l++) {
            int o = off + LINK0 + LINK_STRIDE * l;
            double w = prm[o + L_HALF_WIDTH];
            for (int e = 0; e < 4; e += 2) {
                minX = Math.min(minX, prm[o + e] - cx - w);
                maxX = Math.max(maxX, prm[o + e] - cx + w);
                minZ = Math.min(minZ, prm[o + e + 1] - cz - w);
                maxZ = Math.max(maxZ, prm[o + e + 1] - cz + w);
            }
        }
        prm[off + BOX_MIN_X] = minX - pad;
        prm[off + BOX_MAX_X] = maxX + pad;
        prm[off + BOX_MIN_Z] = minZ - pad;
        prm[off + BOX_MAX_Z] = maxZ + pad;
        prm[off + EXTENT] = Math.hypot(Math.max(-minX, maxX) + pad, Math.max(-minZ, maxZ) + pad);
    }

    /**
     * Rock spires hanging under the crust, away from the coast and from each other; their
     * tips stay above skySpireMinY, so low continents get shorter spires (or none).
     */
    public static void placeSpires(WorldParams p, long seed, double[] prm, int off) {
        int want = p.continentSpiresMin() + (int) (u(seed, 3) * (p.continentSpiresMax() - p.continentSpiresMin() + 1));
        int count = 0;
        int blobs = (int) prm[off + BLOB_COUNT];
        for (int a = 0; a < 4 * want && count < want; a++) {
            int i = 500 + 6 * a;
            int b = off + BLOB0 + BLOB_STRIDE * (int) (u(seed, i) * blobs);
            double angle = u(seed, i + 1) * 2.0 * Math.PI;
            double dist = Math.sqrt(u(seed, i + 2)) * 0.6;
            double c = Math.cos(prm[b + 4]);
            double s = Math.sin(prm[b + 4]);
            double lu = Math.cos(angle) * dist * prm[b + 2];
            double lv = Math.sin(angle) * dist * prm[b + 3];
            double x = prm[b] + lu * c - lv * s;
            double z = prm[b + 1] + lu * s + lv * c;
            if (column(p, prm, off, seed, x, z, EDGE) < 50.0) {
                continue;
            }
            double radius = ShapeMath.lerp(SPIRE_RADIUS_MIN, SPIRE_RADIUS_MAX, u(seed, i + 3));
            boolean free = true;
            for (int k = 0; k < count && free; k++) {
                int o = off + SPIRE0 + SPIRE_STRIDE * k;
                free = Math.hypot(prm[o] - x, prm[o + 1] - z) > 2.0 * SPIRE_REACH + 4.0;
            }
            if (!free) {
                continue;
            }
            double bottom = column(p, prm, off, seed, x, z, BOTTOM);
            // The top disc sits above the underside everywhere under it (hanging bumps), or its top face shows.
            double high = bottom;
            for (int k = 0; k < 16; k++) {
                double a8 = k * 0.125 * Math.PI;
                high = Math.max(high, column(p, prm, off, seed, x + Math.cos(a8) * (radius + 2.0),
                        z + Math.sin(a8) * (radius + 2.0), BOTTOM));
            }
            double top = Math.max(bottom + SPIRE_EMBED, high + 3.0);
            double length = Math.min(ShapeMath.lerp(p.continentSpireLengthMin(), p.continentSpireLengthMax(), u(seed, i + 4)),
                    bottom - p.skyForms().skySpireMinY() - 1.0);
            if (length < 0.8 * p.continentSpireLengthMin()) {
                continue;
            }
            int o = off + SPIRE0 + SPIRE_STRIDE * count;
            prm[o] = x;
            prm[o + 1] = z;
            prm[o + 2] = top;
            prm[o + 3] = length + top - bottom;
            prm[o + 4] = radius;
            prm[o + 5] = u(seed, i + 5) * 2.0 * Math.PI;
            count++;
        }
        prm[off + SPIRE_COUNT] = count;
    }

    private static final double RIVER_STEP = 8.0;
    /** Slope of the banks above the water (blocks up per block out). */
    private static final double RIVER_BANK_SLOPE = 0.6;
    /** Deepest cut of the water level below the original terrain; deeper rivers are dropped. */
    private static final double RIVER_MAX_CUT = 14.0;
    /** Extra pull towards the coast per block of edge distance lost (blocks of height). */
    private static final double RIVER_EDGE_PULL = 0.25;
    private static final double RIVER_TURN = 0.6;
    /** Shorter walks (about 160 blocks) are dropped: rivers should cross a good part of the land. */
    private static final int RIVER_MIN_POINTS = 20;
    /** Beyond this distance past the half width the banks are above any terrain the river cuts. */
    private static final double RIVER_REACH = 1.0 + (RIVER_MAX_CUT + 1.0) / RIVER_BANK_SLOPE;

    /**
     * Rivers of a continent (call after {@link #place}): 1-3 by size, each from a high spot
     * away from the coast (not on a mountain) down the terrain to the coast, where it falls
     * off the island. The water level never rises downstream and never sits more than
     * {@link #RIVER_MAX_CUT} below the terrain; a walk that does not reach the coast is dropped.
     */
    public static void placeRivers(WorldParams p, long seed, double[] prm, int off) {
        prm[off + RIVER_COUNT] = 0;
        double r = prm[off + RADIUS];
        int want = r < 300.0 ? 1 : (r < 480.0 ? 2 : 3);
        int count = 0;
        for (int attempt = 0; attempt < 6 * want && count < want; attempt++) {
            if (walkRiver(p, seed, prm, off, count, 2000 + 100 * attempt)) {
                count++;
                prm[off + RIVER_COUNT] = count;
            }
        }
    }

    private static boolean walkRiver(WorldParams p, long seed, double[] prm, int off, int index, int salt) {
        int o = off + RIVER0 + RIVER_STRIDE * index;
        double cx = prm[off + CX];
        double cz = prm[off + CZ];
        // Source: the highest of a few interior spots off the mountains and away from other rivers.
        double sx = 0.0;
        double sz = 0.0;
        double sTop = -1.0e9;
        for (int k = 0; k < 12; k++) {
            double x = cx + ShapeMath.lerp(prm[off + BOX_MIN_X], prm[off + BOX_MAX_X], u(seed, salt + 2 * k));
            double z = cz + ShapeMath.lerp(prm[off + BOX_MIN_Z], prm[off + BOX_MAX_Z], u(seed, salt + 2 * k + 1));
            if (column(p, prm, off, seed, x, z, EDGE) < Math.max(70.0, 0.25 * prm[off + RADIUS]) || column(p, prm, off, seed, x, z, MOUNTAIN) > 30.0
                    || nearRiver(prm, off, index, x, z, 150.0)) {
                continue;
            }
            double t = column(p, prm, off, seed, x, z, TOP);
            if (t > sTop) {
                sTop = t;
                sx = x;
                sz = z;
            }
        }
        if (sTop < -1.0e8) {
            return false;
        }
        double heading = Math.atan2(sz - cz, sx - cx);
        double x = sx;
        double z = sz;
        double top = sTop;
        double edge = column(p, prm, off, seed, x, z, EDGE);
        double level = Math.floor(top - riverDepth(0.0));
        int n = 0;
        setPoint(prm, o, n++, x, z, level, 5.0);
        while (n < MAX_RIVER_POINTS) {
            double best = Double.MAX_VALUE;
            double bx = 0.0;
            double bz = 0.0;
            double bTop = 0.0;
            double bEdge = 0.0;
            double bHeading = 0.0;
            for (int k = -3; k <= 3; k++) {
                double a = heading + k * Math.PI / 8.0;
                double nx = x + RIVER_STEP * Math.cos(a);
                double nz = z + RIVER_STEP * Math.sin(a);
                double e = column(p, prm, off, seed, nx, nz, EDGE);
                double t = e > 0.0 ? column(p, prm, off, seed, nx, nz, TOP) : top - 20.0;
                double score = t - top + RIVER_EDGE_PULL * (e - edge) + RIVER_TURN * Math.abs(k)
                        + (nearRiver(prm, off, index, nx, nz, 40.0) ? 100.0 : 0.0);
                if (score < best) {
                    best = score;
                    bx = nx;
                    bz = nz;
                    bTop = t;
                    bEdge = e;
                    bHeading = a;
                }
            }
            if (best >= 100.0) {
                return false;
            }
            x = bx;
            z = bz;
            heading = bHeading;
            double frac = Math.min(1.0, n / 60.0);
            double half = n < 2 ? 5.0 : 3.0 + 3.0 * frac;
            if (bEdge <= 2.0) {
                // The mouth: just past the rim, so the channel cuts through it.
                for (int k = 0; k < 6 && column(p, prm, off, seed, x, z, EDGE) > -2.0; k++) {
                    x += 2.0 * Math.cos(heading);
                    z += 2.0 * Math.sin(heading);
                }
                setPoint(prm, o, n++, x, z, level, half);
                finishRiver(prm, o, n);
                return n >= RIVER_MIN_POINTS;
            }
            level = Math.min(level, Math.floor(bTop - riverDepth(frac)));
            if (bTop - level > RIVER_MAX_CUT) {
                return false;
            }
            top = bTop;
            edge = bEdge;
            setPoint(prm, o, n++, x, z, level, half);
        }
        return false;
    }

    /** Water depth at the axis by position along the river (0 source .. 1 lower course). */
    private static double riverDepth(double frac) {
        return 2.0 + 2.0 * frac;
    }

    private static void setPoint(double[] prm, int o, int n, double x, double z, double level, double half) {
        int q = o + RIVER_HEADER + RIVER_POINT * n;
        prm[q] = x;
        prm[q + 1] = z;
        prm[q + 2] = level;
        prm[q + 3] = half;
    }

    private static void finishRiver(double[] prm, int o, int n) {
        double reach = RIVER_REACH;
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            int q = o + RIVER_HEADER + RIVER_POINT * i;
            minX = Math.min(minX, prm[q] - prm[q + 3] - reach);
            maxX = Math.max(maxX, prm[q] + prm[q + 3] + reach);
            minZ = Math.min(minZ, prm[q + 1] - prm[q + 3] - reach);
            maxZ = Math.max(maxZ, prm[q + 1] + prm[q + 3] + reach);
        }
        prm[o] = n;
        prm[o + 1] = minX;
        prm[o + 2] = maxX;
        prm[o + 3] = minZ;
        prm[o + 4] = maxZ;
    }

    private static boolean nearRiver(double[] prm, int off, int rivers, double x, double z, double distance) {
        for (int r = 0; r < rivers; r++) {
            int o = off + RIVER0 + RIVER_STRIDE * r;
            for (int i = 0, n = (int) prm[o]; i < n; i++) {
                int q = o + RIVER_HEADER + RIVER_POINT * i;
                if (Math.abs(prm[q] - x) < distance && Math.abs(prm[q + 1] - z) < distance
                        && Math.hypot(prm[q] - x, prm[q + 1] - z) < distance) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Terrain top with the river channels cut in: a rounded bed below the water level
     * inside the half width, banks rising gently from one block above the water. Columns
     * outside every river's bounding box return {@code top} after one box test per river.
     */
    public static double riverTop(double[] prm, int off, double x, double z, double top) {
        double result = top;
        for (int r = 0, rivers = (int) prm[off + RIVER_COUNT]; r < rivers; r++) {
            int o = off + RIVER0 + RIVER_STRIDE * r;
            if (x < prm[o + 1] || x > prm[o + 2] || z < prm[o + 3] || z > prm[o + 4]) {
                continue;
            }
            int n = (int) prm[o];
            for (int i = 0; i + 1 < n; i++) {
                int a = o + RIVER_HEADER + RIVER_POINT * i;
                int b = a + RIVER_POINT;
                double half = prm[b + 3];
                double lim = half + RIVER_REACH;
                if (Math.min(prm[a], prm[b]) - lim > x || Math.max(prm[a], prm[b]) + lim < x
                        || Math.min(prm[a + 1], prm[b + 1]) - lim > z || Math.max(prm[a + 1], prm[b + 1]) + lim < z) {
                    continue;
                }
                double d = segmentDistance(x, z, prm[a], prm[a + 1], prm[b], prm[b + 1]);
                result = Math.min(result, channel(d, prm[b + 2], half));
            }
        }
        return result;
    }

    /** Channel surface at distance d from the axis for water level {@code level} and half width {@code half}. */
    private static double channel(double d, double level, double half) {
        if (d < half) {
            double q = d / half;
            return level - riverDepth(Math.min(1.0, (half - 3.0) / 3.0)) * (1.0 - q * q);
        }
        return level + 1.0 + (d - half) * RIVER_BANK_SLOPE;
    }

    /**
     * Water level (block Y; water fills the blocks below it) of the river channel at a
     * column, or {@code NaN} outside every channel. The lowest segment covering the column wins.
     */
    public static double riverLevel(double[] prm, int off, double x, double z) {
        double level = Double.NaN;
        for (int r = 0, rivers = (int) prm[off + RIVER_COUNT]; r < rivers; r++) {
            int o = off + RIVER0 + RIVER_STRIDE * r;
            if (x < prm[o + 1] || x > prm[o + 2] || z < prm[o + 3] || z > prm[o + 4]) {
                continue;
            }
            int n = (int) prm[o];
            for (int i = 0; i + 1 < n; i++) {
                int a = o + RIVER_HEADER + RIVER_POINT * i;
                int b = a + RIVER_POINT;
                double half = prm[b + 3] + 0.5;
                if (Math.min(prm[a], prm[b]) - half > x || Math.max(prm[a], prm[b]) + half < x
                        || Math.min(prm[a + 1], prm[b + 1]) - half > z || Math.max(prm[a + 1], prm[b + 1]) + half < z) {
                    continue;
                }
                if (segmentDistance(x, z, prm[a], prm[a + 1], prm[b], prm[b + 1]) < half
                        && !(prm[b + 2] >= level)) {
                    level = prm[b + 2];
                }
            }
        }
        return level;
    }

    /**
     * Density (blocks, positive inside) of the spire at {@code o} at a block: a cone
     * hanging from the crust, concave towards the tip and leaning a little, with rough
     * rock near its surface. Very negative far from it. Its top reaches deep enough into
     * the crust to meet the interpolated underside.
     */
    public static double spireDensity(double[] prm, int o, long noiseSeed, double x, double y, double z) {
        double top = prm[o + 2];
        double length = prm[o + 3];
        double s = (y - (top - length)) / length;
        if (s < 0.0 || s > 1.0) {
            return -100.0;
        }
        double lean = (1.0 - s) * 0.15 * length;
        double dx = x - prm[o] - Math.cos(prm[o + 5]) * lean;
        double dz = z - prm[o + 1] - Math.sin(prm[o + 5]) * lean;
        double d = Math.sqrt(dx * dx + dz * dz);
        // At least a block wide, so the tip stays one connected column of blocks.
        double radius = prm[o + 4] * (0.25 + 0.75 * s) * s * (1.6 - 0.6 * s) + 1.0;
        double v = radius - d;
        // Rough rock only carves, and not where the spire is thin, so the tip never breaks off.
        if (v > 0.0 && v < 2.0 && radius > 2.0) {
            v += 1.2 * Math.min(0.0, SimplexNoise.noise3(noiseSeed, x / 5.0, y / 7.0, z / 5.0))
                    * ShapeMath.smoothstep(2.0, 4.0, radius);
        }
        return v;
    }

    /** Signed horizontal distance to the outline (blocks): positive inside. */
    public static double edge(WorldParams p, double[] prm, int off, long seed, double x, double z) {
        return column(p, prm, off, seed, x, z, EDGE);
    }

    /**
     * One column value ({@link #EDGE}, {@link #TOP}, {@link #BOTTOM}, {@link #MOUNTAIN},
     * {@link #DETAIL}). The silhouette is the union of noisy ellipses and link capsules in
     * a warped domain; fractal coast detail adds bays, coves and headlands (less on
     * isthmuses, none on rock bridges). Each member has its own base height; an isthmus
     * ramps between them (or steps in the middle); a rock bridge is a thin slab of its own
     * joined to both members.
     */
    public static double column(WorldParams p, double[] prm, int off, long seed, double x, double z, int kind) {
        double r = prm[off + RADIUS];
        double warp = r * p.continentWarp();
        double wf = WARP_SCALE * r;
        double wx = x + warp * SimplexNoise.noise2(seed + 1, x / wf, z / wf)
                + BEND_AMP * SimplexNoise.noise2(seed + 20, x / BEND_SCALE, z / BEND_SCALE);
        double wz = z + warp * SimplexNoise.noise2(seed + 2, x / wf, z / wf)
                + BEND_AMP * SimplexNoise.noise2(seed + 24, x / BEND_SCALE, z / BEND_SCALE);
        double outline = -1.0e9;
        int member = 0;
        int count = (int) prm[off + BLOB_COUNT];
        for (int k = 0; k < count; k++) {
            int o = off + BLOB0 + BLOB_STRIDE * k;
            // Plain max: a smooth max would grow straight spikes along the bisector outside the blobs.
            double e = blobEdge(p, prm, o, seed + 40 + k, wx, wz);
            if (e > outline) {
                outline = e;
                member = (int) prm[o + B_MEMBER];
            }
        }
        double linkEdge = -1.0e9;
        int link = -1;
        double linkT = 0.0;
        int links = (int) prm[off + LINK_COUNT];
        for (int l = 0; l < links; l++) {
            int o = off + LINK0 + LINK_STRIDE * l;
            double vx = prm[o + 2] - prm[o];
            double vz = prm[o + 3] - prm[o + 1];
            double t = ShapeMath.clamp(((wx - prm[o]) * vx + (wz - prm[o + 1]) * vz) / (vx * vx + vz * vz), 0.0, 1.0);
            double e = prm[o + L_HALF_WIDTH] - Math.hypot(wx - prm[o] - t * vx, wz - prm[o + 1] - t * vz);
            if (e > linkEdge) {
                linkEdge = e;
                link = o;
                linkT = t;
            }
        }
        double coast = 0.0;
        for (int o = 0; o < COAST_SCALE.length; o++) {
            coast += COAST_AMP[o] * SimplexNoise.noise2(seed + 21 + o, wx / COAST_SCALE[o], wz / COAST_SCALE[o]);
        }
        // Rock bridges are thin: they are built per block ({@link #bridgeDensity}), not here.
        boolean bridge = link >= 0 && prm[link + L_BRIDGE] > 0.0;
        double share = link >= 0 && !bridge ? ShapeMath.smoothstep(-8.0, 8.0, linkEdge - outline) : 0.0;
        double land = bridge ? outline : Math.max(outline, linkEdge);
        // Detail may add land only inside the outline, so no islets form off the coast.
        double detail = coast > 0.0 ? coast * ShapeMath.smoothstep(0.0, 12.0, land) : coast;
        double edge = land + (1.0 - share * (1.0 - ISTHMUS_COAST)) * detail;
        if (kind == EDGE) {
            return edge;
        }
        double plane = prm[off + TILT_X] * (x - prm[off + CX]) + prm[off + TILT_Z] * (z - prm[off + CZ]);
        double base = link >= 0 && !bridge && linkEdge > outline ? linkBase(prm, off, link, linkT)
                : prm[off + MEMBER_BASE0 + member];
        double flat = base + plane + BASE_HILLS * SimplexNoise.noise2(seed + 5, x / 120.0, z / 120.0);
        double mountain = mountain(p, prm, off, seed, x, z, edge, flat);
        if (kind == MOUNTAIN) {
            return mountain;
        }
        if (kind == DETAIL) {
            double band = 1.0 - ShapeMath.smoothstep(0.0, p.continentEdgeBand(), edge);
            // Only tall walls (mesas, mountains) get 3D detail: on hills it carves crater-like pits.
            double w = Math.max(band, 0.5 * ShapeMath.smoothstep(20.0, 45.0, mountain));
            if (bridge) {
                // No carving at the flared abutments, which reach well beyond the bridge's own width.
                return w * ShapeMath.smoothstep(20.0, 44.0, -linkEdge);
            }
            return w * (1.0 - share * (1.0 - ISTHMUS_DETAIL));
        }
        if (kind == TOP || kind == TERRAIN) {
            double top = Math.min(flat + mountain, prm[off + MAX_Y]);
            return kind == TOP ? riverTop(prm, off, x, z, top) : top;
        }
        return Math.max(bottom(p, prm, off, seed, x, z, edge, flat, mountain), p.continentMinBottomY());
    }

    private static double warpX(WorldParams p, double[] prm, int off, long seed, double x, double z) {
        double r = prm[off + RADIUS];
        double wf = WARP_SCALE * r;
        return x + r * p.continentWarp() * SimplexNoise.noise2(seed + 1, x / wf, z / wf)
                + BEND_AMP * SimplexNoise.noise2(seed + 20, x / BEND_SCALE, z / BEND_SCALE);
    }

    private static double warpZ(WorldParams p, double[] prm, int off, long seed, double x, double z) {
        double r = prm[off + RADIUS];
        double wf = WARP_SCALE * r;
        return z + r * p.continentWarp() * SimplexNoise.noise2(seed + 2, x / wf, z / wf)
                + BEND_AMP * SimplexNoise.noise2(seed + 24, x / BEND_SCALE, z / BEND_SCALE);
    }

    /**
     * Lowest underside of a rock bridge that may reach the 4x4 column cell starting at
     * x, z (the cell is sampled at its centre with a margin), or {@code none}.
     */
    public static double bridgeFloor(WorldParams p, double[] prm, int off, long seed, double x, double z, double none) {
        double floor = none;
        int links = (int) prm[off + LINK_COUNT];
        if (links == 0) {
            return floor;
        }
        double cx = x + 2.0;
        double cz = z + 2.0;
        double wx = warpX(p, prm, off, seed, cx, cz);
        double wz = warpZ(p, prm, off, seed, cx, cz);
        for (int l = 0; l < links; l++) {
            int o = off + LINK0 + LINK_STRIDE * l;
            if (prm[o + L_BRIDGE] <= 0.0) {
                continue;
            }
            double vx = prm[o + 2] - prm[o];
            double vz = prm[o + 3] - prm[o + 1];
            double t = ShapeMath.clamp(((wx - prm[o]) * vx + (wz - prm[o + 1]) * vz) / (vx * vx + vz * vz), 0.0, 1.0);
            double d = Math.hypot(wx - prm[o] - t * vx, wz - prm[o + 1] - t * vz);
            double flare = abutment(prm, o, t);
            if (d < bridgeHalfWidth(prm, o, flare) + 5.0) {
                double plane = prm[off + TILT_X] * (cx - prm[off + CX]) + prm[off + TILT_Z] * (cz - prm[off + CZ]);
                floor = Math.min(floor, linkBase(prm, off, o, t) + plane - bridgeThickness(prm, o, flare) - 3.0);
            }
        }
        return floor;
    }

    /**
     * Density (blocks, positive inside) of the rock bridges of a continent at a block: a
     * slab ramping from one member to the other, thickest along its axis. Towards each end
     * it flares into a wide, thick abutment (an arched underside), so it grows out of the
     * cliffs instead of touching them; each end reaches {@link #LINK_OVERLAP} blocks into the land.
     */
    public static double bridgeDensity(WorldParams p, double[] prm, int off, long seed, double x, double y, double z) {
        double best = -100.0;
        int links = (int) prm[off + LINK_COUNT];
        if (links == 0) {
            return best;
        }
        double wx = warpX(p, prm, off, seed, x, z);
        double wz = warpZ(p, prm, off, seed, x, z);
        double plane = prm[off + TILT_X] * (x - prm[off + CX]) + prm[off + TILT_Z] * (z - prm[off + CZ]);
        for (int l = 0; l < links; l++) {
            int o = off + LINK0 + LINK_STRIDE * l;
            if (prm[o + L_BRIDGE] <= 0.0) {
                continue;
            }
            double vx = prm[o + 2] - prm[o];
            double vz = prm[o + 3] - prm[o + 1];
            double t = ShapeMath.clamp(((wx - prm[o]) * vx + (wz - prm[o + 1]) * vz) / (vx * vx + vz * vz), 0.0, 1.0);
            double flare = abutment(prm, o, t);
            double half = bridgeHalfWidth(prm, o, flare);
            double e = half - Math.hypot(wx - prm[o] - t * vx, wz - prm[o + 1] - t * vz);
            if (e <= -2.0) {
                continue;
            }
            double top = linkBase(prm, off, o, t) + plane;
            double bottom = top - bridgeThickness(prm, o, flare) * (0.45 + 0.55 * ShapeMath.clamp(e / half, 0.0, 1.0));
            best = Math.max(best, Math.min(e, Math.min(top - y, y - bottom)));
        }
        return best;
    }

    /**
     * 0 in the middle of a bridge's span, rising to 1 at the coasts it joins (and inside
     * the land); the flare covers about a third of the span on each side.
     */
    private static double abutment(double[] prm, int o, double t) {
        double len = Math.hypot(prm[o + 2] - prm[o], prm[o + 3] - prm[o + 1]);
        double span = (prm[o + L_T1] - prm[o + L_T0]) * len;
        double fromEnd = Math.min(t - prm[o + L_T0], prm[o + L_T1] - t) * len;
        double f = 1.0 - ShapeMath.smoothstep(0.0, Math.max(10.0, 0.35 * span), fromEnd);
        return f * f;
    }

    private static double bridgeHalfWidth(double[] prm, int o, double flare) {
        return prm[o + L_HALF_WIDTH] * (1.0 + 2.5 * flare) + 4.0 * flare;
    }

    /** Thickness from the bridge's own to deeper than the cliff rim at the abutments. */
    private static double bridgeThickness(double[] prm, int o, double flare) {
        return ShapeMath.lerp(prm[o + L_THICKNESS], EDGE_THICKNESS + 8.0, flare);
    }

    /** Base height along a link: a ramp between the members (an isthmus may step in the middle instead). */
    private static double linkBase(double[] prm, int off, int o, double t) {
        double a = prm[off + MEMBER_BASE0 + (int) prm[o + L_MEMBER_A]];
        double b = prm[off + MEMBER_BASE0 + (int) prm[o + L_MEMBER_B]];
        double t0 = prm[o + L_T0];
        double t1 = prm[o + L_T1];
        double f;
        if (prm[o + L_BRIDGE] > 0.0) {
            f = ShapeMath.clamp((t - t0) / (t1 - t0), 0.0, 1.0);
        } else if (prm[o + L_THICKNESS] < 0.0) {
            double len = Math.hypot(prm[o + 2] - prm[o], prm[o + 3] - prm[o + 1]);
            double mid = 0.5 * (t0 + t1);
            f = ShapeMath.smoothstep(mid - 3.0 / len, mid + 3.0 / len, t);
        } else {
            f = ShapeMath.smoothstep(t0, t1, t);
        }
        return ShapeMath.lerp(a, b, f);
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

    /**
     * Terrain above the flat base (negative on coastal slopes and in basins). The
     * continent's character sets the weights; inside, a region noise mixes plains, rolling
     * hills, stepped highlands, mesas with sheer walls, rare spire clusters and terraced
     * mountains. Mountainous continents add a range along their spine, calderas a ring of
     * mountains with passes around a basin, some continents a massif. Small-scale relief
     * breaks up every surface. Coastal slopes never drop the terrain to the underside limit.
     */
    static double mountain(WorldParams p, double[] prm, int off, long seed, double x, double z, double edge,
                           double flat) {
        double inland = ShapeMath.smoothstep(0.0, 60.0, edge);
        double plateau = prm[off + PLATEAU];
        double open = 1.0;
        if (plateau > 0.0) {
            double dx = x - prm[off + CX];
            double dz = z - prm[off + CZ];
            open = ShapeMath.smoothstep(plateau, plateau + 120.0, Math.sqrt(dx * dx + dz * dz));
        }
        double micro = MICRO_AMP * SimplexNoise.noise2(seed + 27, x / 40.0, z / 40.0) * ShapeMath.smoothstep(0.0, 12.0, edge);
        double drop = ShapeMath.clamp(flat - COAST_DROP_FLOOR, 0.0, COAST_DROP);
        // A wide noise band: cliffs turn into slopes gradually along the coast, never as a steep ramp.
        double coastDrop = drop * ShapeMath.smoothstep(-0.3, 0.5, SimplexNoise.noise2(seed + 17, x / 300.0, z / 300.0))
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

        double big = Math.max(Math.max(spine(prm, off, seed, x, z), ring(prm, off, seed, x, z)),
                massif(prm, off, seed, x, z, flat)) * inland;
        return Math.max(terrain, big) - basin(prm, off, seed, x, z) * inland + micro - coastDrop;
    }

    /** A big mountain mass rising to the massif summit, ridged so it is not a dome. */
    private static double massif(double[] prm, int off, long seed, double x, double z, double flat) {
        double peak = prm[off + MASSIF_PEAK];
        if (peak <= 0.0) {
            return 0.0;
        }
        double wx = x + 45.0 * SimplexNoise.noise2(seed + 45, x / 200.0, z / 200.0) - prm[off + MASSIF_X];
        double wz = z + 45.0 * SimplexNoise.noise2(seed + 46, x / 200.0, z / 200.0) - prm[off + MASSIF_Z];
        double profile = 1.0 - ShapeMath.smoothstep(0.0, prm[off + MASSIF_RADIUS], Math.sqrt(wx * wx + wz * wz));
        if (profile <= 0.0) {
            return 0.0;
        }
        double r1 = 1.0 - Math.abs(SimplexNoise.noise2(seed + 47, x / 110.0, z / 110.0));
        double r2 = 1.0 - Math.abs(SimplexNoise.noise2(seed + 48, x / 40.0, z / 40.0));
        return (peak - flat) * Math.pow(profile, 1.4) * (0.62 + 0.3 * r1 * r1 + 0.08 * r2);
    }

    /** Mountain range along the blob chains, parent to child (meandering). */
    private static double spine(double[] prm, int off, long seed, double x, double z) {
        double height = prm[off + SPINE_HEIGHT];
        if (height <= 0.0) {
            return 0.0;
        }
        double wx = x + 40.0 * SimplexNoise.noise2(seed + 35, x / 160.0, z / 160.0);
        double wz = z + 40.0 * SimplexNoise.noise2(seed + 36, x / 160.0, z / 160.0);
        double best = Double.MAX_VALUE;
        int count = (int) prm[off + BLOB_COUNT];
        for (int k = 0; k < count; k++) {
            int o = off + BLOB0 + BLOB_STRIDE * k;
            int parent = (int) prm[o + B_PARENT];
            if (parent >= 0) {
                int q = off + BLOB0 + BLOB_STRIDE * parent;
                best = Math.min(best, segmentDistance(wx, wz, prm[q], prm[q + 1], prm[o], prm[o + 1]));
            }
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
        double h = mask * prm[off + MOUNTAINS] * ridge * Math.max(0.0, p.continentPeakMaxY() - prm[off + BASE_Y]);
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

    /**
     * Underside height: crust under the flat terrain, or under the lowered surface in
     * basins and coastal slopes (thinning to a cliff rim at the edge), with gentle hanging relief.
     */
    private static double bottom(WorldParams p, double[] prm, int off, long seed, double x, double z, double edge,
                                double flatTop, double mountain) {
        double inner = ShapeMath.smoothstep(0.0, 40.0, edge);
        // Gentle bumps hanging from the underside, fading towards the edge.
        double hanging = UNDERSIDE_AMP * (0.5 + 0.5 * SimplexNoise.noise2(seed + 43, x / 50.0, z / 50.0))
                + UNDERSIDE_FINE_AMP * SimplexNoise.noise2(seed + 44, x / 18.0, z / 18.0);
        // The rim is a cliff of at least EDGE_THICKNESS: a knife-thin coast would shred into loose flakes.
        return flatTop + Math.min(0.0, mountain) - EDGE_THICKNESS - (prm[off + CRUST] - EDGE_THICKNESS + hanging * inner) * inner;
    }
}
