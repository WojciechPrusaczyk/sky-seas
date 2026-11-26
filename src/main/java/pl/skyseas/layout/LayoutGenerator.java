package pl.skyseas.layout;

import pl.skyseas.config.WorldParams;
import pl.skyseas.shape.ContinentShape;
import pl.skyseas.shape.OceanShape;
import pl.skyseas.shape.ShapeMath;
import pl.skyseas.shape.SimplexNoise;

/**
 * Builds a {@link RegionLayout} level by level; each level reads only the finished
 * earlier levels, so no decision depends on generation order.
 *
 * <ol>
 *   <li>Continents on a jittered grid, one candidate per cell, the starter at 0,0.</li>
 *   <li>Ocean forms: islands (size classes by zone), seamounts, volcanoes and sea spire
 *       groups, each on its own grid; forms above water are rejected under a continent
 *       (clear-sky rule). Climate-bound forms read the temperature at their centre.</li>
 * </ol>
 */
public final class LayoutGenerator {
    private static final long CONTINENT_SALT = 0x434F4E54L;
    private static final long ISLAND_SALT = 0x4F49534CL;
    private static final long SEAMOUNT_SALT = 0x534D4E54L;
    private static final long VOLCANO_SALT = 0x564F4C43L;
    private static final long SPIRE_SALT = 0x53504952L;
    private static final double OCEAN_INDEX_MARGIN = 2.0;
    /** Wavelength of the island-density noise (blocks). */
    private static final double DENSITY_SCALE = 1800.0;
    /** Rim points checked by the clear-sky rule (besides the centre). */
    private static final int RIM_POINTS = 8;

    private LayoutGenerator() {
    }

    /** Margin added to an island's outline bound for the bucket index: climate reach and edge noise. */
    static double indexMargin(WorldParams p) {
        return p.islandReachMargin() + p.continentEdgeNoise() + 4.0;
    }

    /** Largest reach of any ocean form (blocks). */
    static double maxOceanReach(WorldParams p) {
        double[] prm = new double[OceanShape.STRIDE];
        double[] radii = p.oceanIslandRadius();
        prm[OceanShape.RADIUS] = radii[radii.length - 1];
        prm[OceanShape.SHELF_WIDTH] = p.oceanShelfWidthMax()[p.oceanShelfWidthMax().length - 1];
        prm[OceanShape.SLOPE_WIDTH] = p.oceanShelfSlopeMax();
        double island = OceanShape.reach(OceanShape.ISLAND, prm, 0);
        double mounts = Math.max(p.seamountRadiusMax(), p.volcanoRadiusMax()) + 2.0;
        return Math.max(island, Math.max(mounts, p.seaSpireSpread() + p.seaSpireRadiusMax() * 2.0 + 7.0));
    }

    public static RegionLayout generate(long seed, WorldParams p, ClimateProbe climate, int rx, int rz) {
        int size = p.layoutRegionSize();
        int x0 = rx * size;
        int z0 = rz * size;
        RegionBuilder out = new RegionBuilder();
        Continents continents = continents(seed, p, x0, z0, size, maxOceanReach(p) + p.clearSkyMargin(), out);
        double[] prm = new double[OceanShape.STRIDE];
        oceanForms(seed, p, climate, x0, z0, size, continents, prm, out, RegionLayout.OCEAN_ISLAND,
                p.oceanIslandCellSize(), p.oceanIslandChance(), ISLAND_SALT);
        oceanForms(seed, p, climate, x0, z0, size, continents, prm, out, RegionLayout.SEAMOUNT,
                p.seamountCellSize(), p.seamountChance(), SEAMOUNT_SALT);
        oceanForms(seed, p, climate, x0, z0, size, continents, prm, out, RegionLayout.VOLCANO,
                p.volcanoCellSize(), p.volcanoChance(), VOLCANO_SALT);
        oceanForms(seed, p, climate, x0, z0, size, continents, prm, out, RegionLayout.SEA_SPIRE,
                p.seaSpireCellSize(), p.seaSpireChance(), SPIRE_SALT);
        return out.build(rx, rz, size, p.layoutBucketSize(), indexMargin(p), OCEAN_INDEX_MARGIN);
    }

    /** Level 1 continents near the region (also those only needed for rejections). */
    private static final class Continents {
        int count;
        int stride;
        double[] params;
        long[] seed;
    }

    /**
     * Places the continents of every cell whose continent may matter within {@code extra}
     * blocks of the region; those whose reach touches the region go into the layout.
     */
    private static Continents continents(long seed, WorldParams p, int x0, int z0, int size, double extra,
                                         RegionBuilder out) {
        int cell = p.continentCellSize();
        double margin = indexMargin(p);
        int ci0 = Math.floorDiv((int) Math.floor(x0 - extra) + cell / 2, cell);
        int ci1 = Math.floorDiv((int) Math.ceil(x0 + size + extra) + cell / 2, cell);
        int cj0 = Math.floorDiv((int) Math.floor(z0 - extra) + cell / 2, cell);
        int cj1 = Math.floorDiv((int) Math.ceil(z0 + size + extra) + cell / 2, cell);
        int max = (ci1 - ci0 + 1) * (cj1 - cj0 + 1);
        Continents c = new Continents();
        c.stride = ContinentShape.stride(p);
        c.params = new double[max * c.stride];
        c.seed = new long[max];
        for (int ci = ci0; ci <= ci1; ci++) {
            for (int cj = cj0; cj <= cj1; cj++) {
                long s = Hash.hash(seed ^ CONTINENT_SALT, ci, cj);
                boolean starter = ci == 0 && cj == 0;
                double r = starter ? p.starterContinentRadius()
                        : ShapeMath.lerp(p.continentRadiusMin(), p.continentRadiusMax(), Hash.unit(Hash.hash(s, 3)));
                // The continent's actual reach decides how far it may wander from the cell centre
                // (less grid-like); a rare sprawling one that does not fit is scaled down. No collisions.
                int off = c.count * c.stride;
                double limit = cell / 2.0 - margin;
                ContinentShape.place(p, s, 0, 0, r, starter, c.params, off);
                while (c.params[off + ContinentShape.EXTENT] > limit) {
                    r *= 0.95;
                    ContinentShape.place(p, s, 0, 0, r, starter, c.params, off);
                }
                double jitter = limit - c.params[off + ContinentShape.EXTENT];
                int x = starter ? 0 : (int) Math.round(ci * (double) cell + (2.0 * Hash.unit(Hash.hash(s, 1)) - 1.0) * jitter);
                int z = starter ? 0 : (int) Math.round(cj * (double) cell + (2.0 * Hash.unit(Hash.hash(s, 2)) - 1.0) * jitter);
                ContinentShape.place(p, s, x, z, r, starter, c.params, off);
                c.seed[c.count] = s;
                c.count++;
                double extent = c.params[off + ContinentShape.EXTENT];
                double reach = extent + margin;
                if (x + reach < x0 || x - reach >= x0 + size || z + reach < z0 || z - reach >= z0 + size) {
                    continue;
                }
                out.add(RegionLayout.CONTINENT, s, x, z, extent, (int) Math.floor(p.continentMinBottomY()),
                        (int) Math.ceil(c.params[off + ContinentShape.MAX_Y] + p.continentEdgeNoise()), s,
                        starter ? RegionLayout.FLAG_STARTER : 0,
                        ZoneField.factorAtDistance(p, Math.sqrt((double) x * x + (double) z * z)),
                        c.params, off, c.stride);
            }
        }
        return c;
    }

    /** Level 2: one ocean form type on its own grid (sea spires come in groups). */
    private static void oceanForms(long seed, WorldParams p, ClimateProbe climate, int x0, int z0, int size,
                                   Continents continents, double[] prm, RegionBuilder out, byte type, int cell,
                                   double chance, long salt) {
        int kind = type - RegionLayout.OCEAN_ISLAND;
        double extra = maxOceanReach(p);
        int ci0 = Math.floorDiv((int) Math.floor(x0 - extra), cell);
        int ci1 = Math.floorDiv((int) Math.ceil(x0 + size + extra), cell);
        int cj0 = Math.floorDiv((int) Math.floor(z0 - extra), cell);
        int cj1 = Math.floorDiv((int) Math.ceil(z0 + size + extra), cell);
        for (int ci = ci0; ci <= ci1; ci++) {
            for (int cj = cj0; cj <= cj1; cj++) {
                long s = Hash.hash(seed ^ salt, ci, cj);
                // Candidate rolls use indexes 100+; the shapes use 0..99 of the same seed.
                double x = (ci + Hash.unit(Hash.hash(s, 101))) * cell;
                double z = (cj + Hash.unit(Hash.hash(s, 102))) * cell;
                if (Hash.unit(Hash.hash(s, 100)) >= chance * density(seed ^ salt, x, z)) {
                    continue;
                }
                double zone = ZoneField.factorAtDistance(p, Math.hypot(x, z));
                if (type == RegionLayout.OCEAN_ISLAND) {
                    int sizeClass = sizeClass(p, zone, Hash.unit(Hash.hash(s, 103)));
                    boolean warm = sizeClass > 0 && climate.temperature((int) x, (int) z) >= p.oceanWarmMinTemperature();
                    OceanShape.placeIsland(p, s, x, z, sizeClass, warm, prm, 0);
                    addOcean(p, x0, z0, size, continents, prm, out, type, kind, s, zone);
                } else if (type == RegionLayout.SEA_SPIRE) {
                    double t = climate.temperature((int) x, (int) z);
                    if (t < p.oceanLukewarmMinTemperature() || t >= p.oceanWarmMinTemperature()) {
                        continue;
                    }
                    int count = 1 + (int) (Hash.unit(Hash.hash(s, 104)) * p.seaSpireCountMax());
                    for (int k = 0; k < count; k++) {
                        long ks = Hash.hash(s, 10 + k);
                        double a = Hash.unit(Hash.hash(ks, 105)) * 2.0 * Math.PI;
                        double d = k == 0 ? 0.0 : Math.sqrt(Hash.unit(Hash.hash(ks, 106))) * p.seaSpireSpread();
                        OceanShape.placeSpire(p, ks, x + Math.cos(a) * d, z + Math.sin(a) * d, prm, 0);
                        addOcean(p, x0, z0, size, continents, prm, out, type, kind, ks, zone);
                    }
                } else {
                    double t = climate.temperature((int) x, (int) z);
                    if (type == RegionLayout.SEAMOUNT) {
                        if (t < p.oceanColdMaxTemperature() || t >= p.oceanLukewarmMinTemperature()) {
                            continue;
                        }
                        OceanShape.placeSeamount(p, s, x, z, prm, 0);
                    } else {
                        if (t < p.oceanFrozenMaxTemperature() || t >= p.oceanColdMaxTemperature()) {
                            continue;
                        }
                        OceanShape.placeVolcano(p, s, x, z, prm, 0);
                    }
                    addOcean(p, x0, z0, size, continents, prm, out, type, kind, s, zone);
                }
            }
        }
    }

    /** Island-rich and empty stretches of sea: 0.25 .. 1.5 times the base chance. */
    private static double density(long seed, double x, double z) {
        double n = SimplexNoise.noise2(seed, x / DENSITY_SCALE, z / DENSITY_SCALE);
        return ShapeMath.lerp(0.25, 1.5, ShapeMath.smoothstep(-0.5, 0.5, n));
    }

    /** Small, medium or large; no large islands in the Haven, more of them further out. */
    static int sizeClass(WorldParams p, double zone, double roll) {
        double start = ZoneField.factorAtDistance(p, p.zoneRadii()[0]);
        double large = p.oceanIslandLargeShareMax() * ShapeMath.clamp((zone - start) / (1.0 - start), 0.0, 1.0);
        return roll < large ? 2 : (roll < large + p.oceanIslandMediumShare() ? 1 : 0);
    }

    private static void addOcean(WorldParams p, int x0, int z0, int size, Continents continents, double[] prm,
                                 RegionBuilder out, byte type, int kind, long id, double zone) {
        double x = prm[OceanShape.CX];
        double z = prm[OceanShape.CZ];
        double reach = OceanShape.reach(kind, prm, 0);
        if (x + reach < x0 || x - reach >= x0 + size || z + reach < z0 || z - reach >= z0 + size) {
            return;
        }
        double land = OceanShape.landRadius(kind, prm, 0);
        if (land > 0.0 && underContinent(p, continents, x, z, land)) {
            return;
        }
        out.add(type, id, (int) Math.round(x), (int) Math.round(z), reach, p.worldMinY(),
                (int) Math.ceil(OceanShape.maxY(kind, prm, 0)), id, 0, zone, prm, 0, OceanShape.STRIDE);
    }

    /**
     * Clear-sky rule: true if a continent comes within the margin (plus its overhang noise)
     * of land of this radius. The edge field is distance-like, so the centre and a ring of
     * rim points are enough.
     */
    private static boolean underContinent(WorldParams p, Continents c, double x, double z, double landRadius) {
        double clear = p.clearSkyMargin() + p.continentEdgeNoise();
        for (int j = 0; j < c.count; j++) {
            int off = j * c.stride;
            double dx = x - c.params[off + ContinentShape.CX];
            double dz = z - c.params[off + ContinentShape.CZ];
            double far = c.params[off + ContinentShape.EXTENT] + landRadius + clear;
            if (dx * dx + dz * dz > far * far) {
                continue;
            }
            if (ContinentShape.edge(p, c.params, off, c.seed[j], x, z) > -(landRadius + clear + 4.0)) {
                return true;
            }
            for (int k = 0; k < RIM_POINTS; k++) {
                double a = k * 2.0 * Math.PI / RIM_POINTS;
                if (ContinentShape.edge(p, c.params, off, c.seed[j], x + Math.cos(a) * landRadius,
                        z + Math.sin(a) * landRadius) > -clear) {
                    return true;
                }
            }
        }
        return false;
    }
}
