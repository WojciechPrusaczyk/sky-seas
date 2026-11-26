package pl.skyseas.layout;

import pl.skyseas.config.WorldParams;
import pl.skyseas.shape.ContinentShape;
import pl.skyseas.shape.OceanShape;
import pl.skyseas.shape.SkyFormShape;

/**
 * Facade of the island layout of one world (one per RandomState, no global state):
 * regions, the island at a column and the column values used by the density functions.
 */
public final class Layout {
    public static final int EDGE = ContinentShape.EDGE;
    public static final int TOP = ContinentShape.TOP;
    public static final int BOTTOM = ContinentShape.BOTTOM;
    public static final int MOUNTAIN = ContinentShape.MOUNTAIN;
    public static final int DETAIL = ContinentShape.DETAIL;
    /** Column values where no island reaches: far outside, empty Y range. */
    public static final double NO_EDGE = -1000.0;
    public static final double NO_TOP = -1000.0;
    public static final double NO_BOTTOM = 1000.0;
    /** Detail floor where no spire or rock bridge reaches the column. */
    public static final double NO_SPIRE = 1000.0;
    /** Tallest per-block detail above the floor {@link #detailFloor} reports (small islands with spires). */
    public static final double DETAIL_SPAN = 96.0;

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

    /**
     * Index (in the region) of the interpolated sky form with the largest edge distance at
     * this column, or -1. Forms built per block are left to {@link #detailDensity}.
     */
    public int nearestIsland(RegionLayout r, int x, int z) {
        int b = r.bucket(x, z);
        int best = -1;
        double bestEdge = NO_EDGE;
        for (int k = r.skyStart(b), end = r.skyEnd(b); k < end; k++) {
            int i = r.skyItem(k);
            if ((r.flags[i] & RegionLayout.FLAG_DETAIL) != 0) {
                continue;
            }
            double e = edge(r, i, x, z);
            if (e > bestEdge) {
                bestEdge = e;
                best = i;
            }
        }
        return best;
    }

    /** Edge distance of sky item i (blocks, positive inside); bridges have none. */
    public double edge(RegionLayout r, int i, double x, double z) {
        return switch (r.type[i]) {
            case RegionLayout.CONTINENT -> ContinentShape.edge(params, r.params, r.paramOffset[i], r.shapeSeed[i], x, z);
            case RegionLayout.SKY_BRIDGE -> NO_EDGE;
            default -> SkyFormShape.edge(r.params, r.paramOffset[i], r.shapeSeed[i], x, z);
        };
    }

    /** Island whose outline contains the column, or -1 (interpolated forms only). */
    public int islandAt(RegionLayout r, int x, int z) {
        int i = nearestIsland(r, x, z);
        return i >= 0 && edge(r, i, x, z) > 0.0 ? i : -1;
    }

    /** One column value ({@link #EDGE}, {@link #TOP}, {@link #BOTTOM}, {@link #MOUNTAIN}, {@link #DETAIL}) of the nearest island. */
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
        if (r.type[i] != RegionLayout.CONTINENT) {
            return SkyFormShape.column(r.params, r.paramOffset[i], r.shapeSeed[i], x, z, kind);
        }
        return ContinentShape.column(params, r.params, r.paramOffset[i], r.shapeSeed[i], x, z, kind);
    }

    /** Offset of the first spire record of sky item i; the spire count sits just before it (both layouts). */
    private static int spire0(RegionLayout r, int i) {
        return r.paramOffset[i] + (r.type[i] == RegionLayout.CONTINENT ? ContinentShape.SPIRE0 : SkyFormShape.SPIRE0);
    }

    private static int spireCount(RegionLayout r, int i) {
        if (r.type[i] == RegionLayout.SKY_BRIDGE) {
            return 0;
        }
        int off = r.paramOffset[i];
        return (int) r.params[off + (r.type[i] == RegionLayout.CONTINENT ? ContinentShape.SPIRE_COUNT : SkyFormShape.SPIRE_COUNT)];
    }

    /**
     * Lowest block of a per-block detail (spire tip, rock bridge underside, small island)
     * that may reach any block of the 4x4 column cell starting at x, z (flat_cache samples
     * one corner per cell), or {@link #NO_SPIRE}. Every such detail spans at most
     * {@link #DETAIL_SPAN} blocks above it.
     */
    public double detailFloor(int x, int z) {
        RegionLayout r = regionAt(x, z);
        int b = r.bucket(x, z);
        double floor = NO_SPIRE;
        double reach = ContinentShape.SPIRE_REACH + 6.0;
        for (int k = r.skyStart(b), end = r.skyEnd(b); k < end; k++) {
            int i = r.skyItem(k);
            int off = r.paramOffset[i];
            int o0 = spire0(r, i);
            for (int s = 0, n = spireCount(r, i); s < n; s++) {
                int o = o0 + ContinentShape.SPIRE_STRIDE * s;
                double dx = x - r.params[o];
                double dz = z - r.params[o + 1];
                if (dx * dx + dz * dz < reach * reach) {
                    floor = Math.min(floor, r.params[o + 2] - r.params[o + 3]);
                }
            }
            byte t = r.type[i];
            if (t == RegionLayout.CONTINENT) {
                floor = ContinentShape.bridgeFloor(params, r.params, off, r.shapeSeed[i], x, z, floor);
            } else if (t == RegionLayout.SKY_BRIDGE) {
                if (SkyFormShape.bridgeNear(r.params, off, x + 2.0, z + 2.0, 6.0)) {
                    floor = Math.min(floor, SkyFormShape.bridgeMinY(r.params, off));
                }
            } else if ((r.flags[i] & RegionLayout.FLAG_DETAIL) != 0 && near(r, i, x + 2.0, z + 2.0, 6.0)) {
                floor = Math.min(floor, SkyFormShape.bodyBottom(r.params, off));
            }
        }
        return floor;
    }

    /** True if the column is within the item's reach radius plus a margin. */
    private static boolean near(RegionLayout r, int i, double x, double z, double margin) {
        double dx = x - r.cx[i];
        double dz = z - r.cz[i];
        double d = r.radius[i] + margin;
        return dx * dx + dz * dz < d * d;
    }

    /** Density of per-block details (hanging spires, rock bridges, small islands) at a block (blocks, positive inside). */
    public double detailDensity(int x, int y, int z) {
        RegionLayout r = regionAt(x, z);
        int b = r.bucket(x, z);
        double best = -100.0;
        double reach = ContinentShape.SPIRE_REACH;
        long noiseSeed = detailSeed() + 7;
        for (int k = r.skyStart(b), end = r.skyEnd(b); k < end; k++) {
            int i = r.skyItem(k);
            if (y < r.minY[i] || y > r.maxY[i]) {
                continue;
            }
            int off = r.paramOffset[i];
            int o0 = spire0(r, i);
            for (int s = 0, n = spireCount(r, i); s < n; s++) {
                int o = o0 + ContinentShape.SPIRE_STRIDE * s;
                double dx = x - r.params[o];
                double dz = z - r.params[o + 1];
                if (dx * dx + dz * dz < reach * reach) {
                    best = Math.max(best, ContinentShape.spireDensity(r.params, o, noiseSeed, x, y, z));
                }
            }
            byte t = r.type[i];
            if (t == RegionLayout.CONTINENT) {
                best = Math.max(best, ContinentShape.bridgeDensity(params, r.params, off, r.shapeSeed[i], x, y, z));
            } else if ((r.flags[i] & RegionLayout.FLAG_DETAIL) != 0 && near(r, i, x, z, 2.0)) {
                best = Math.max(best, t == RegionLayout.SKY_BRIDGE ? SkyFormShape.bridgeDensity(r.params, off, x, y, z)
                        : SkyFormShape.density(r.params, off, r.shapeSeed[i], x, y, z));
            }
        }
        return best;
    }

    /**
     * Sky visibility rule at a point (horizontal distances): at least one sky form within
     * {@code near} blocks and at least three within {@code far}. Sky forms are continents,
     * towers, sky islands and cluster forms; bridges do not count.
     */
    public boolean skyVisible(int x, int z, double near, double far) {
        int size = params.layoutRegionSize();
        int rx0 = Math.floorDiv(x, size);
        int rz0 = Math.floorDiv(z, size);
        int span = (int) Math.ceil(far / size);
        int countNear = 0;
        int countFar = 0;
        for (int rx = rx0 - span; rx <= rx0 + span; rx++) {
            for (int rz = rz0 - span; rz <= rz0 + span; rz++) {
                RegionLayout r = region(rx, rz);
                for (int i = 0; i < r.count; i++) {
                    if (RegionLayout.isOcean(r.type[i]) || r.type[i] == RegionLayout.SKY_BRIDGE || !r.owns(i, size)
                            || Math.hypot(r.cx[i] - x, r.cz[i] - z) - r.radius[i] > far) {
                        continue;
                    }
                    double e = edge(r, i, x, z);
                    if (e > -far) {
                        countFar++;
                        if (e > -near) {
                            countNear++;
                        }
                        if (countNear >= 1 && countFar >= 3) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
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
