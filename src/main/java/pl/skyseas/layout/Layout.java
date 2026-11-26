package pl.skyseas.layout;

import pl.skyseas.config.WorldParams;
import pl.skyseas.shape.ContinentShape;
import pl.skyseas.shape.OceanShape;

/**
 * Facade of the island layout of one world (one per RandomState, no global state):
 * regions, the island at a column and the column values used by the density functions.
 */
public final class Layout {
    public static final int EDGE = 0;
    public static final int TOP = 1;
    public static final int BOTTOM = 2;
    public static final int MOUNTAIN = 3;
    /** Column values where no island reaches: far outside, empty Y range. */
    public static final double NO_EDGE = -1000.0;
    public static final double NO_TOP = -1000.0;
    public static final double NO_BOTTOM = 1000.0;

    private final long seed;
    private final WorldParams params;
    private final LayoutCache cache;

    /** Layout with a temperate climate everywhere (tests, tools). */
    public Layout(long seed, WorldParams params) {
        this(seed, params, ClimateProbe.TEMPERATE);
    }

    public Layout(long seed, WorldParams params, ClimateProbe climate) {
        this.seed = seed;
        this.params = params;
        this.cache = new LayoutCache(seed, params, climate);
    }

    public long seed() {
        return seed;
    }

    public WorldParams params() {
        return params;
    }

    public LayoutCache cache() {
        return cache;
    }

    /** Seed of the 3D detail noise of sky islands. */
    public long detailSeed() {
        return Hash.hash(seed, 0x3D);
    }

    public RegionLayout region(int rx, int rz) {
        return cache.get(rx, rz);
    }

    public RegionLayout regionAt(int x, int z) {
        int size = params.layoutRegionSize();
        return cache.get(Math.floorDiv(x, size), Math.floorDiv(z, size));
    }

    /** Index (in the region) of the island with the largest edge distance at this column, or -1. */
    public int nearestIsland(RegionLayout r, int x, int z) {
        int b = r.bucket(x, z);
        int best = -1;
        double bestEdge = NO_EDGE;
        for (int k = r.skyStart(b), end = r.skyEnd(b); k < end; k++) {
            int i = r.skyItem(k);
            double e = ContinentShape.edge(params, r.params, r.paramOffset[i], r.shapeSeed[i], x, z);
            if (e > bestEdge) {
                bestEdge = e;
                best = i;
            }
        }
        return best;
    }

    /** Island whose outline contains the column, or -1. */
    public int islandAt(RegionLayout r, int x, int z) {
        int i = nearestIsland(r, x, z);
        return i >= 0 && ContinentShape.edge(params, r.params, r.paramOffset[i], r.shapeSeed[i], x, z) > 0.0 ? i : -1;
    }

    /** One column value ({@link #EDGE}, {@link #TOP}, {@link #BOTTOM}, {@link #MOUNTAIN}) of the nearest island. */
    public double column(int x, int z, int kind) {
        RegionLayout r = regionAt(x, z);
        int i = nearestIsland(r, x, z);
        if (i < 0) {
            return switch (kind) {
                case EDGE -> NO_EDGE;
                case TOP -> NO_TOP;
                case BOTTOM -> NO_BOTTOM;
                default -> 0.0;
            };
        }
        int off = r.paramOffset[i];
        long s = r.shapeSeed[i];
        double edge = ContinentShape.edge(params, r.params, off, s, x, z);
        if (kind == EDGE) {
            return edge;
        }
        double flat = ContinentShape.flatTop(r.params, off, s, x, z);
        double mountain = ContinentShape.mountain(params, r.params, off, s, x, z, edge);
        return switch (kind) {
            case BOTTOM -> ContinentShape.bottom(params, r.params, off, s, x, z, edge, flat, mountain);
            case TOP -> ContinentShape.top(params, flat, mountain);
            default -> mountain;
        };
    }

    /** Seabed surface with the ocean forms of the layout raised on the plain seabed {@code base}. */
    public double oceanHeight(int x, int z, double base) {
        RegionLayout r = regionAt(x, z);
        int b = r.bucket(x, z);
        double h = base;
        for (int k = r.oceanStart(b), end = r.oceanEnd(b); k < end; k++) {
            int i = r.oceanItem(k);
            double f = OceanShape.height(r.type[i] - RegionLayout.OCEAN_ISLAND, r.params, r.paramOffset[i],
                    r.shapeSeed[i], x, z, base);
            if (f > h) {
                h = f;
            }
        }
        return h;
    }

    /**
     * Height that bounds caves from above: the seabed height, except inside sea spires,
     * where caves would hollow out the thin column (then the bottom of the world).
     */
    public double caveTop(int x, int z, double height) {
        RegionLayout r = regionAt(x, z);
        int b = r.bucket(x, z);
        for (int k = r.oceanStart(b), end = r.oceanEnd(b); k < end; k++) {
            int i = r.oceanItem(k);
            if (r.type[i] == RegionLayout.SEA_SPIRE && OceanShape.insideSpire(r.params, r.paramOffset[i], x, z)) {
                return params.worldMinY();
            }
        }
        return height;
    }

    /** Ocean form whose surface is highest at the column (above the given base), or -1. */
    public int oceanAt(RegionLayout r, int x, int z, double base) {
        int b = r.bucket(x, z);
        int best = -1;
        double h = base;
        for (int k = r.oceanStart(b), end = r.oceanEnd(b); k < end; k++) {
            int i = r.oceanItem(k);
            double f = OceanShape.height(r.type[i] - RegionLayout.OCEAN_ISLAND, r.params, r.paramOffset[i],
                    r.shapeSeed[i], x, z, base);
            if (f > h) {
                h = f;
                best = i;
            }
        }
        return best;
    }
}
