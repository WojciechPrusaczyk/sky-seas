package pl.skyseas.layout;

import pl.skyseas.config.WorldParams;
import pl.skyseas.shape.ContinentShape;
import pl.skyseas.shape.OceanShape;
import pl.skyseas.shape.ShaftShape;
import pl.skyseas.shape.ShapeMath;
import pl.skyseas.shape.SimplexNoise;
import pl.skyseas.shape.SkyFormShape;

/**
 * Builds a {@link RegionLayout} level by level; each level reads only the finished
 * earlier levels, so no decision depends on generation order.
 *
 * <ol>
 *   <li>Continents on a grid, one per cell, the starter at 0,0. Each keeps half of a
 *       400-800 block gap free on every side of its cell and is scaled to fill the rest,
 *       so neighbouring coasts are a short flight apart; any slack becomes jitter.</li>
 *   <li>Ocean forms: islands (size classes by zone), seamounts, volcanoes and sea spire
 *       groups, each on its own grid; forms above water are rejected under a continent
 *       (clear-sky rule). Climate-bound forms read the temperature at their centre.</li>
 *   <li>Single sky forms: towers, small and medium islands, one candidate per grid cell,
 *       kept far enough inside the cell that neighbours never overlap.</li>
 *   <li>Clusters at corners of the continent grid (and two guaranteed near spawn):
 *       archipelagos (with rock bridges and arches) and Hallelujah clusters. A cluster's
 *       candidates depend only on its seed; continents and ocean land reject single
 *       candidates without shifting the others.</li>
 * </ol>
 * Return chains (small islands stepping down from a continent's coast to just above the
 * sea) depend only on their continent; ocean land gives way to them as it does to continents.
 * Sky forms of levels 3 and 4 never overlap continents, ocean land, clusters of other
 * corners or each other (footprint plus a gap), so no island hangs above another.
 */
public final class LayoutGenerator {
    private static final long CONTINENT_SALT = 0x434F4E54L;
    private static final long ISLAND_SALT = 0x4F49534CL;
    private static final long SEAMOUNT_SALT = 0x534D4E54L;
    private static final long VOLCANO_SALT = 0x564F4C43L;
    private static final long SPIRE_SALT = 0x53504952L;
    private static final long SINGLE_SALT = 0x534E474CL;
    private static final long CLUSTER_SALT = 0x434C5354L;
    private static final long CHAIN_SALT = 0x4348414EL;
    /** Chains per continent: one with this chance, a second with the next (the starter always has one). */
    private static final double CHAIN_CHANCE = 0.5;
    private static final double CHAIN_SECOND_CHANCE = 0.15;
    static final int CHAIN_MIN = 5;
    static final int CHAIN_MAX = 15;
    /** Vertical step between neighbouring chain islands (tops) and the horizontal gap between outlines. */
    static final double CHAIN_STEP_MAX = 12.0;
    static final double CHAIN_GAP_MIN = 2.0;
    static final double CHAIN_GAP_MAX = 6.0;
    static final double CHAIN_RADIUS_MIN = 5.0;
    static final double CHAIN_RADIUS_MAX = 11.0;
    /** Top of the lowest island: a few blocks above the sea. */
    static final double CHAIN_LOW_MIN = 67.0;
    static final double CHAIN_LOW_MAX = 71.0;
    /** Farthest a chain island reaches from its continent's coast. */
    static final double CHAIN_REACH = CHAIN_MAX * (2.0 * CHAIN_RADIUS_MAX + CHAIN_GAP_MAX) + 40.0;
    /**
     * Sky form candidates centred within this distance of the region are checked against
     * continents and ocean land (forms touching the region and the ends of its bridges).
     */
    private static final double CHECK_RANGE = 300.0;
    /** Longest gap a bridge spans between two island outlines. */
    private static final double BRIDGE_GAP_MAX = 80.0;
    private static final double BRIDGE_GAP_MIN = 12.0;
    private static final double BRIDGE_STEP_MAX = 30.0;
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

    /** Farthest block of a single or cluster sky form from its centre. */
    static double skyReach(WorldParams p) {
        double r = Math.max(Math.max(p.skyForms().archipelagoRadiusMax(), p.skyForms().mediumIslandRadiusMax()),
                Math.max(p.skyForms().towerRadiusMax(), p.skyForms().hallelujahRadiusMax()));
        return r * SkyFormShape.FOOTPRINT + 4.0;
    }

    public static RegionLayout generate(long seed, WorldParams p, ClimateProbe climate, int rx, int rz) {
        int size = p.layoutRegionSize();
        int x0 = rx * size;
        int z0 = rz * size;
        RegionBuilder out = new RegionBuilder();
        double skyCheck = CHECK_RANGE + skyReach(p) + p.clearSkyMargin() + p.continentEdgeNoise();
        // Chains reach out from their continent: ocean forms near the region must see them all.
        Continents continents = continents(seed, p, x0, z0, size,
                Math.max(maxOceanReach(p) + p.clearSkyMargin(), skyCheck) + CHAIN_REACH, out);
        double[] prm = new double[Math.max(OceanShape.STRIDE, SkyFormShape.STRIDE)];
        // Ocean land is collected beyond the region too (oceanExtra): sky forms near it must keep clear of it.
        double oceanExtra = skyCheck + maxOceanReach(p);
        Chains chains = chains(p, x0, z0, size, oceanExtra + maxOceanReach(p) + p.clearSkyMargin(), continents);
        Land land = new Land();
        land.chains = chains;
        guaranteedIsland(seed, p, x0, z0, size, oceanExtra, land, prm, out);
        oceanForms(seed, p, climate, x0, z0, size, oceanExtra, continents, land, prm, out, RegionLayout.OCEAN_ISLAND,
                p.oceanIslandCellSize(), p.oceanIslandChance(), ISLAND_SALT);
        oceanForms(seed, p, climate, x0, z0, size, oceanExtra, continents, land, prm, out, RegionLayout.SEAMOUNT,
                p.seamountCellSize(), p.seamountChance(), SEAMOUNT_SALT);
        oceanForms(seed, p, climate, x0, z0, size, oceanExtra, continents, land, prm, out, RegionLayout.VOLCANO,
                p.volcanoCellSize(), p.volcanoChance(), VOLCANO_SALT);
        oceanForms(seed, p, climate, x0, z0, size, oceanExtra, continents, land, prm, out, RegionLayout.SEA_SPIRE,
                p.seaSpireCellSize(), p.seaSpireChance(), SPIRE_SALT);
        double[] clusters = clusters(seed, p, x0, z0, size);
        singles(seed, p, x0, z0, size, continents, land, clusters, prm, out);
        for (int c = 0; c < clusters.length; c += CLUSTER_STRIDE) {
            cluster(p, climate, x0, z0, size, continents, land, clusters, c, prm, out);
        }
        chains.add(p, x0, z0, size, prm, out);
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
                double distance = Math.hypot((double) ci * cell, (double) cj * cell);
                int off = c.count * c.stride;
                double half = cell / 2.0 - ShapeMath.lerp(p.continentBoxGapMin(), p.continentBoxGapMax(),
                        Hash.unit(Hash.hash(s, 901))) / 2.0;
                double r = starter ? p.starterContinentRadius() : 0.6 * half;
                ContinentShape.place(p, s, 0, 0, r, starter, distance, c.params, off);
                // The box grows almost linearly with the radius: a few rescales fill the cell.
                for (int n = 0; n < 4 && !starter; n++) {
                    r *= half / boxHalf(c.params, off);
                    ContinentShape.place(p, s, 0, 0, r, starter, distance, c.params, off);
                }
                while (boxHalf(c.params, off) > half) {
                    r *= 0.97;
                    ContinentShape.place(p, s, 0, 0, r, starter, distance, c.params, off);
                }
                int x = starter ? 0 : (int) Math.round(ci * (double) cell + jitter(c.params, off, ContinentShape.BOX_MIN_X,
                        half, Hash.unit(Hash.hash(s, 902))));
                int z = starter ? 0 : (int) Math.round(cj * (double) cell + jitter(c.params, off, ContinentShape.BOX_MIN_Z,
                        half, Hash.unit(Hash.hash(s, 903))));
                ContinentShape.place(p, s, x, z, r, starter, distance, c.params, off);
                ContinentShape.placeSpires(p, s, c.params, off);
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

    /** Half of the larger side of a continent's bounding box. */
    private static double boxHalf(double[] prm, int off) {
        return 0.5 * Math.max(prm[off + ContinentShape.BOX_MAX_X] - prm[off + ContinentShape.BOX_MIN_X],
                prm[off + ContinentShape.BOX_MAX_Z] - prm[off + ContinentShape.BOX_MIN_Z]);
    }

    /** Offset of the centre from the cell centre along one axis: box centred, then moved within the slack. */
    private static double jitter(double[] prm, int off, int boxMin, double half, double roll) {
        double min = prm[off + boxMin];
        double max = prm[off + boxMin + 1];
        double slack = Math.max(0.0, half - 0.5 * (max - min));
        return -0.5 * (min + max) + (2.0 * roll - 1.0) * slack;
    }

    /** Level 2: one ocean form type on its own grid (sea spires come in groups). */
    private static void oceanForms(long seed, WorldParams p, ClimateProbe climate, int x0, int z0, int size,
                                   double extra, Continents continents, Land land, double[] prm, RegionBuilder out,
                                   byte type, int cell, double chance, long salt) {
        int kind = type - RegionLayout.OCEAN_ISLAND;
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
                    addOcean(p, x0, z0, size, continents, land, prm, out, type, kind, s, zone);
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
                        addOcean(p, x0, z0, size, continents, land, prm, out, type, kind, ks, zone);
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
                    addOcean(p, x0, z0, size, continents, land, prm, out, type, kind, s, zone);
                }
            }
        }
    }

    private static final long GUARANTEE_SALT = 0x47524E54L;
    /** Distance from spawn of the guaranteed large island with a cenote. */
    private static final double GUARANTEED_ISLAND_DISTANCE = 720.0;
    private static final double GUARANTEED_ISLAND_RADIUS_MAX = 105.0;

    /**
     * The large ocean island with a cenote guaranteed near spawn (the Haven has no large
     * islands of its own). Laid out from the continents and chains around spawn only, so
     * every region sees the same one: the first of eight directions clear of them.
     */
    private static void guaranteedIsland(long seed, WorldParams p, int x0, int z0, int size, double extra, Land land,
                                         double[] prm, RegionBuilder out) {
        double far = GUARANTEED_ISLAND_DISTANCE + 300.0 + extra;
        if (x0 > far || x0 + size < -far || z0 > far || z0 + size < -far) {
            return;
        }
        int half = p.layoutRegionSize();
        Continents near = continents(seed, p, -half, -half, 2 * half, CHAIN_REACH + 1000.0, new RegionBuilder());
        Chains chains = chains(p, -half, -half, 2 * half, 1000.0, near);
        long g = Hash.hash(seed ^ GUARANTEE_SALT, 1);
        long s = g;
        for (int k = 0; k < 64; k++) {
            s = Hash.hash(g, 10 + k);
            if (ShapeMath.lerp(p.oceanIslandRadius()[2], p.oceanIslandRadius()[3], Hash.unit(Hash.hash(s, 0)))
                    <= GUARANTEED_ISLAND_RADIUS_MAX) {
                break;
            }
        }
        double a0 = Hash.unit(Hash.hash(g, 2)) * 2.0 * Math.PI;
        for (int t = 0; t < 8; t++) {
            double a = a0 + t * Math.PI / 4.0;
            double x = Math.cos(a) * GUARANTEED_ISLAND_DISTANCE;
            double z = Math.sin(a) * GUARANTEED_ISLAND_DISTANCE;
            OceanShape.placeIsland(p, s, x, z, 2, false, prm, 0);
            double landRadius = OceanShape.landRadius(OceanShape.ISLAND, prm, 0);
            if (underContinent(p, near, x, z, landRadius) || chains.near(x, z, landRadius + p.clearSkyMargin())) {
                continue;
            }
            Land none = new Land();
            none.chains = new Chains();
            addOcean(p, x0, z0, size, near, none, prm, out, RegionLayout.OCEAN_ISLAND, 0, s,
                    ZoneField.factorAtDistance(p, GUARANTEED_ISLAND_DISTANCE), RegionLayout.FLAG_GUARANTEED);
            land.add(x, z, landRadius);
            return;
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

    private static void addOcean(WorldParams p, int x0, int z0, int size, Continents continents, Land land, double[] prm,
                                 RegionBuilder out, byte type, int kind, long id, double zone) {
        addOcean(p, x0, z0, size, continents, land, prm, out, type, kind, id, zone, 0);
    }

    private static void addOcean(WorldParams p, int x0, int z0, int size, Continents continents, Land land, double[] prm,
                                 RegionBuilder out, byte type, int kind, long id, double zone, int flags) {
        double x = prm[OceanShape.CX];
        double z = prm[OceanShape.CZ];
        double reach = OceanShape.reach(kind, prm, 0);
        boolean inside = !(x + reach < x0 || x - reach >= x0 + size || z + reach < z0 || z - reach >= z0 + size);
        double landRadius = OceanShape.landRadius(kind, prm, 0);
        if ((!inside && landRadius <= 0.0) || (landRadius > 0.0 && (underContinent(p, continents, x, z, landRadius)
                || land.chains.near(x, z, landRadius + p.clearSkyMargin())))) {
            return;
        }
        if (landRadius > 0.0) {
            land.add(x, z, landRadius);
        }
        if (inside) {
            out.add(type, id, (int) Math.round(x), (int) Math.round(z), reach, p.worldMinY(),
                    (int) Math.ceil(OceanShape.maxY(kind, prm, 0)), id, flags, zone, prm, 0, OceanShape.STRIDE);
            if (type == RegionLayout.OCEAN_ISLAND) {
                double[] sh = new double[ShaftShape.STRIDE];
                for (int k = 0, n = ShaftShape.count(prm, 0, id); k < n; k++) {
                    ShaftShape.shaft(prm, 0, id, k, sh);
                    long ks = Hash.hash(id, 320 + k);
                    out.add(RegionLayout.SHAFT, ks, (int) Math.round(sh[0]), (int) Math.round(sh[1]), ShaftShape.reach(sh),
                            (int) Math.floor(ShaftShape.minY(sh)), (int) Math.ceil(ShaftShape.maxY(sh)), ks, 0, zone, sh, 0,
                            ShaftShape.STRIDE);
                }
            }
        }
    }

    /** Ocean land discs (centre, radius) near the region, for the clear-sky rule of sky forms. */
    private static final class Land {
        Chains chains;
        double[] v = new double[96];
        int n;

        void add(double x, double z, double r) {
            if (n + 3 > v.length) {
                v = java.util.Arrays.copyOf(v, 2 * v.length);
            }
            v[n++] = x;
            v[n++] = z;
            v[n++] = r;
        }

        /** True if ocean land comes within {@code width} of the segment a-b. */
        boolean nearSegment(double ax, double az, double bx, double bz, double width) {
            for (int k = 0; k < n; k += 3) {
                if (segmentDistance(v[k], v[k + 1], ax, az, bx, bz) < width + v[k + 2]) {
                    return true;
                }
            }
            return false;
        }

        /** True if a sky form of this footprint comes within the clear-sky margin of ocean land. */
        boolean near(double x, double z, double footprint, double margin) {
            for (int k = 0; k < n; k += 3) {
                double d = footprint + margin + v[k + 2];
                double dx = x - v[k];
                double dz = z - v[k + 1];
                if (dx * dx + dz * dz < d * d) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final int CLUSTER_STRIDE = 7;
    /** Erosion Hallelujah clusters are pulled toward (vanilla erosion class 0: peaks). */
    private static final double HALLELUJAH_EROSION = -0.85;
    /** Cluster ranks: big forms of a lower-ranked (later) cluster keep clear of higher-ranked discs. */
    private static final int RANK_GUARANTEED = 0;
    private static final int RANK_CORNER = 1;
    private static final int RANK_EDGE = 2;
    private static final long HEIGHT_SALT = 0x48474854L;
    private static final long DENSITY_SALT = 0x444E5354L;

    /**
     * Clusters that may matter for the region: {x, z, radius, type, seed bits, flags, rank}.
     * Two guaranteed in the starter's cell, on opposite axes, where the starter leaves a
     * wide ring of sky; at most one per corner of the continent grid; smaller ones at the
     * middle of the sides. Corner and side clusters next to spawn give way to the guaranteed ones.
     */
    private static double[] clusters(long seed, WorldParams p, int x0, int z0, int size) {
        WorldParams.SkyForms f = p.skyForms();
        int cell = p.continentCellSize();
        double extra = CHECK_RANGE + f.clusterRadiusMax() + 2.0 * skyReach(p) + f.skyFormGap() + f.skyCellSize();
        int ci0 = Math.floorDiv((int) Math.floor(x0 - extra), cell) - 1;
        int ci1 = Math.floorDiv((int) Math.ceil(x0 + size + extra), cell);
        int cj0 = Math.floorDiv((int) Math.floor(z0 - extra), cell) - 1;
        int cj1 = Math.floorDiv((int) Math.ceil(z0 + size + extra), cell);
        double[] out = new double[(3 * (ci1 - ci0 + 1) * (cj1 - cj0 + 1) + 2) * CLUSTER_STRIDE];
        int n = 0;
        long g = Hash.hash(seed ^ CLUSTER_SALT, 0x5157);
        double a = (int) (Hash.unit(Hash.hash(g, 100)) * 4) * 0.5 * Math.PI;
        double gx = Math.cos(a) * f.guaranteeDistance();
        double gz = Math.sin(a) * f.guaranteeDistance();
        double gr = f.guaranteeClusterRadius();
        n = addCluster(out, n, gx, gz, gr, RegionLayout.ARCHIPELAGO, Hash.hash(g, 1),
                RegionLayout.FLAG_GUARANTEED, RANK_GUARANTEED, x0, z0, size, extra);
        n = addCluster(out, n, -gx, -gz, gr, RegionLayout.HALLELUJAH, Hash.hash(g, 2),
                RegionLayout.FLAG_GUARANTEED, RANK_GUARANTEED, x0, z0, size, extra);
        double share = f.archipelagoChance() + f.hallelujahChance();
        for (int ci = ci0; ci <= ci1; ci++) {
            for (int cj = cj0; cj <= cj1; cj++) {
                // k 0: corner; 1, 2: middle of the side along x, along z.
                for (int k = 0; k < 3; k++) {
                    long s = Hash.hash(seed ^ CLUSTER_SALT, ci, cj, k);
                    double roll = Hash.unit(Hash.hash(s, 100));
                    boolean corner = k == 0;
                    double chance = corner ? share : f.edgeClusterChance();
                    if (roll >= chance) {
                        continue;
                    }
                    byte type = roll < chance * f.archipelagoChance() / share ? RegionLayout.ARCHIPELAGO : RegionLayout.HALLELUJAH;
                    double radius = corner ? ShapeMath.lerp(f.clusterRadiusMin(), f.clusterRadiusMax(), Hash.unit(Hash.hash(s, 101)))
                            : ShapeMath.lerp(f.edgeClusterRadiusMin(), f.edgeClusterRadiusMax(), Hash.unit(Hash.hash(s, 101)));
                    double jitter = corner ? 0.2 * cell : 0.1 * cell;
                    double x = (ci + (k == 2 ? 0.0 : 0.5)) * cell + (Hash.unit(Hash.hash(s, 102)) - 0.5) * jitter;
                    double z = (cj + (k == 1 ? 0.0 : 0.5)) * cell + (Hash.unit(Hash.hash(s, 103)) - 0.5) * jitter;
                    double apart = radius + gr + 2.0 * skyReach(p) + f.skyFormGap();
                    if (Math.hypot(x - gx, z - gz) < apart || Math.hypot(x + gx, z + gz) < apart) {
                        continue;
                    }
                    n = addCluster(out, n, x, z, radius, type, s, 0, corner ? RANK_CORNER : RANK_EDGE, x0, z0, size, extra);
                }
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }

    private static int addCluster(double[] out, int n, double x, double z, double radius, byte type, long seed, int flags,
                                  int rank, int x0, int z0, int size, double extra) {
        if (x + extra < x0 || x - extra >= x0 + size || z + extra < z0 || z - extra >= z0 + size) {
            return n;
        }
        out[n] = x;
        out[n + 1] = z;
        out[n + 2] = radius;
        out[n + 3] = type;
        out[n + 4] = Double.longBitsToDouble(seed);
        out[n + 5] = flags;
        out[n + 6] = rank;
        return n + CLUSTER_STRIDE;
    }

    /** True if a large form of this footprint comes within the gap of the disc of a cluster ranked above {@code rank}. */
    private static boolean inCluster(WorldParams p, double[] clusters, double x, double z, double footprint, int rank) {
        for (int c = 0; c < clusters.length; c += CLUSTER_STRIDE) {
            double d = clusters[c + 2] + skyReach(p) + p.skyForms().skyFormGap() + footprint;
            if (clusters[c + 6] < rank && Math.hypot(x - clusters[c], z - clusters[c + 1]) < d) {
                return true;
            }
        }
        return false;
    }

    /** 0..1 height band of the area: islands near each other share it, so the sky has low and high groups. */
    private static double heightBand(long seed, WorldParams p, double x, double z) {
        double scale = p.skyForms().heightScale();
        return ShapeMath.smoothstep(-0.6, 0.6, SimplexNoise.noise2(seed ^ HEIGHT_SALT, x / scale, z / scale));
    }

    /** Chance of a single form per cell: dense swarms and open stretches (squared, so swarms stand out). */
    private static double skyDensity(long seed, WorldParams p, double x, double z) {
        WorldParams.SkyForms f = p.skyForms();
        double n = SimplexNoise.noise2(seed ^ DENSITY_SALT, x / f.densityScale(), z / f.densityScale());
        double t = ShapeMath.smoothstep(-0.3, 0.4, n);
        return ShapeMath.lerp(f.skyDensityMin(), f.skyCellChance(), t * t);
    }

    /**
     * Level 3: towers, small and medium islands, at most one candidate per cell, anywhere
     * in it, with a chance from the density noise. Islands may overlap each other, towers
     * and a little of a continent; of two overlapping towers of neighbouring cells the
     * one with the lower priority roll gives way. All rolls come from the cells alone, so
     * the outcome does not depend on the order cells are visited in.
     */
    private static void singles(long seed, WorldParams p, int x0, int z0, int size, Continents continents, Land land,
                                double[] clusters, double[] prm, RegionBuilder out) {
        WorldParams.SkyForms f = p.skyForms();
        int cell = f.skyCellSize();
        double extra = skyReach(p) + indexMargin(p);
        int ci0 = Math.floorDiv((int) Math.floor(x0 - extra), cell);
        int ci1 = Math.floorDiv((int) Math.ceil(x0 + size + extra), cell);
        int cj0 = Math.floorDiv((int) Math.floor(z0 - extra), cell);
        int cj1 = Math.floorDiv((int) Math.ceil(z0 + size + extra), cell);
        double[] c = new double[SINGLE_STRIDE];
        double[] o = new double[SINGLE_STRIDE];
        for (int ci = ci0; ci <= ci1; ci++) {
            for (int cj = cj0; cj <= cj1; cj++) {
                if (!single(seed, p, ci, cj, c)) {
                    continue;
                }
                double x = c[0];
                double z = c[1];
                double radius = c[2];
                boolean tower = c[3] == 0.0;
                boolean small = c[3] == 1.0;
                double footprint = radius * SkyFormShape.FOOTPRINT;
                double reach = footprint + indexMargin(p);
                if (x + reach < x0 || x - reach >= x0 + size || z + reach < z0 || z - reach >= z0 + size
                        || (tower && (yields(seed, p, ci, cj, c, o) || inCluster(p, clusters, x, z, footprint, Integer.MAX_VALUE)))
                        || rejected(p, continents, land, x, z, radius, !tower)) {
                    continue;
                }
                long s = Hash.hash(seed ^ SINGLE_SALT, ci, cj);
                double band = ShapeMath.clamp(heightBand(seed, p, x, z) + 0.5 * (Hash.unit(Hash.hash(s, 107)) - 0.5), 0.0, 1.0);
                if (tower) {
                    double top = ShapeMath.lerp(f.towerTopMin(), f.skyMaxY() - 2.0, band);
                    double h = Math.min(ShapeMath.lerp(f.towerHeightMin(), f.towerHeightMax(), Hash.unit(Hash.hash(s, 106))),
                            top - f.skyFormMinY() - 4.0);
                    SkyFormShape.place(x, z, radius, top, h, SkyFormShape.towerTaper(radius, h), false, prm, 0);
                    spires(p, s, false, prm);
                    addSky(p, out, RegionLayout.TOWER, s, prm, 0);
                } else {
                    double depth = islandDepth(small, radius, Hash.unit(Hash.hash(s, 106)));
                    double top = islandTop(p, s, depth, band);
                    depth = sunkDepth(p, top, depth);
                    SkyFormShape.place(x, z, radius, top, depth, SkyFormShape.islandTaper(radius, depth), true, prm, 0);
                    spires(p, s, small, prm);
                    addSky(p, out, RegionLayout.SKY_ISLAND, s, prm, RegionLayout.FLAG_DETAIL);
                }
            }
        }
    }

    private static double islandDepth(boolean small, double radius, double roll) {
        return small ? Math.min(25.0, radius * ShapeMath.lerp(0.8, 1.3, roll))
                : ShapeMath.clamp(radius * ShapeMath.lerp(0.6, 1.0, roll), 18.0, 45.0);
    }

    /** Top of an island: a few sink into the sea, the rest follow the area's height band. */
    private static double islandTop(WorldParams p, long s, double depth, double band) {
        WorldParams.SkyForms f = p.skyForms();
        if (Hash.unit(Hash.hash(s, 111)) < f.sunkShare()) {
            return ShapeMath.lerp(f.sunkTopMin(), f.sunkTopMax(), Hash.unit(Hash.hash(s, 112)));
        }
        double low = Math.min(f.skyMaxY() - 2.0, Math.max(f.islandTopMin(), f.skyFormMinY() + depth + 4.0));
        return ShapeMath.lerp(low, f.skyMaxY() - 2.0, band);
    }

    /** A sunk island keeps its underside (outline noise included) above continentMinBottomY, clear of the seabed. */
    private static double sunkDepth(WorldParams p, double top, double depth) {
        return Math.min(depth, top - p.continentMinBottomY() - 6.0);
    }

    private static final int SINGLE_STRIDE = 5;

    /** Raw candidate of a cell: {x, z, radius, kind (0 tower, 1 small, 2 medium), priority}; false if none. */
    private static boolean single(long seed, WorldParams p, int ci, int cj, double[] out) {
        WorldParams.SkyForms f = p.skyForms();
        long s = Hash.hash(seed ^ SINGLE_SALT, ci, cj);
        int cell = f.skyCellSize();
        double x = (ci + Hash.unit(Hash.hash(s, 101))) * cell;
        double z = (cj + Hash.unit(Hash.hash(s, 102))) * cell;
        if (Hash.unit(Hash.hash(s, 100)) >= skyDensity(seed, p, x, z)) {
            return false;
        }
        boolean tower = Hash.unit(Hash.hash(s, 103)) < f.towerShare();
        boolean small = !tower && Hash.unit(Hash.hash(s, 104)) < f.smallIslandShare();
        double size01 = Hash.unit(Hash.hash(s, 105));
        double radius = tower ? ShapeMath.lerp(f.towerRadiusMin(), f.towerRadiusMax(), size01)
                : small ? ShapeMath.lerp(f.smallIslandRadiusMin(), f.smallIslandRadiusMax(), size01)
                : ShapeMath.lerp(f.mediumIslandRadiusMin(), f.mediumIslandRadiusMax(), size01);
        out[0] = x;
        out[1] = z;
        out[2] = radius;
        out[3] = tower ? 0.0 : small ? 1.0 : 2.0;
        out[4] = Hash.unit(Hash.hash(s, 110));
        return true;
    }

    /** True if a tower of a neighbouring cell overlaps this tower and has the higher priority. */
    private static boolean yields(long seed, WorldParams p, int ci, int cj, double[] c, double[] o) {
        double gap = p.skyForms().skyFormGap();
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                if ((di != 0 || dj != 0) && single(seed, p, ci + di, cj + dj, o) && o[3] == 0.0
                        && Math.hypot(c[0] - o[0], c[1] - o[1]) < (c[2] + o[2]) * SkyFormShape.FOOTPRINT + gap
                        && o[4] > c[4]) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 0-2 spires under small islands, 3-8 under the others (fewer where they do not fit). */
    private static void spires(WorldParams p, long s, boolean small, double[] prm) {
        int min = small ? 0 : p.continentSpiresMin();
        int max = small ? 2 : p.continentSpiresMax();
        int count = min + (int) (Hash.unit(Hash.hash(s, 108)) * (max - min + 1));
        SkyFormShape.placeSpires(p, s, count, p.skyForms().skySpireMinY(), prm, 0);
    }

    /**
     * Clear sky over ocean land always; a continent rejects large forms within the margin
     * and lets islands (built per block) reach a little over its coast.
     */
    private static boolean rejected(WorldParams p, Continents continents, Land land, double x, double z, double radius,
                                    boolean overlapping) {
        double footprint = radius * SkyFormShape.FOOTPRINT;
        if (land.near(x, z, footprint, p.clearSkyMargin())) {
            return true;
        }
        return overlapping ? onContinent(p, continents, x, z, (1.0 - p.skyForms().continentOverlap()) * footprint)
                : underContinent(p, continents, x, z, footprint);
    }

    /** True if the centre lies inside a continent or less than {@code clear} blocks off its coast. */
    private static boolean onContinent(WorldParams p, Continents c, double x, double z, double clear) {
        for (int j = 0; j < c.count; j++) {
            int off = j * c.stride;
            double far = c.params[off + ContinentShape.EXTENT] + clear;
            double dx = x - c.params[off + ContinentShape.CX];
            double dz = z - c.params[off + ContinentShape.CZ];
            if (dx * dx + dz * dz < far * far && ContinentShape.edge(p, c.params, off, c.seed[j], x, z) > -clear) {
                return true;
            }
        }
        return false;
    }

    private static void addSky(WorldParams p, RegionBuilder out, byte type, long id, double[] prm, int flags) {
        double x = prm[SkyFormShape.CX];
        double z = prm[SkyFormShape.CZ];
        out.add(type, id, (int) Math.round(x), (int) Math.round(z), prm[SkyFormShape.RADIUS] * SkyFormShape.FOOTPRINT,
                (int) Math.floor(SkyFormShape.minY(prm, 0)), (int) Math.ceil(SkyFormShape.maxY(prm, 0)), id, flags,
                ZoneField.factorAtDistance(p, Math.hypot(x, z)), prm, 0, SkyFormShape.STRIDE);
    }

    /** True if a cluster form is built per block (and may overlap). */
    private static boolean detailForm(WorldParams p, boolean arch, double r) {
        return arch && r < p.skyForms().detailRadiusMax();
    }

    /**
     * Level 4: one cluster. Candidates fill the disc around the centre, off a straight
     * corridor through it; large forms keep a gap from each other (and from the discs of
     * higher-ranked clusters), islands built per block may overlap anything partly. This
     * depends only on the cluster's seed and the cluster grid. Continents and ocean land
     * then reject single candidates, checked only for those that touch the region or end
     * one of its bridges.
     */
    private static void cluster(WorldParams p, ClimateProbe climate, int x0, int z0, int size, Continents continents, Land land,
                                double[] clusters, int c, double[] prm, RegionBuilder out) {
        WorldParams.SkyForms f = p.skyForms();
        double ccx = clusters[c];
        double ccz = clusters[c + 1];
        double radius = clusters[c + 2];
        boolean arch = clusters[c + 3] == RegionLayout.ARCHIPELAGO;
        byte type = arch ? RegionLayout.ARCHIPELAGO : RegionLayout.HALLELUJAH;
        long cs = Double.doubleToRawLongBits(clusters[c + 4]);
        int flags = (int) clusters[c + 5];
        int rank = (int) clusters[c + 6];
        int min = arch ? f.archipelagoCountMin() : f.hallelujahCountMin();
        int max = arch ? f.archipelagoCountMax() : f.hallelujahCountMax();
        int want = min + (int) (Hash.unit(Hash.hash(cs, 1)) * (max - min + 1));
        double phi = Hash.unit(Hash.hash(cs, 2)) * Math.PI;
        double ux = Math.cos(phi);
        double uz = Math.sin(phi);
        double band0 = heightBand(cs, p, ccx, ccz);
        // One climate point per cluster (its centre, so the sky stays coherent with the sea below);
        // Hallelujah clusters are pulled toward low erosion, where vanilla puts peaks.
        double ct = climate.temperature((int) ccx, (int) ccz);
        double cv = climate.vegetation((int) ccx, (int) ccz);
        double ce = arch ? climate.erosion((int) ccx, (int) ccz) : HALLELUJAH_EROSION;
        double[] cx = new double[want];
        double[] cz = new double[want];
        double[] cr = new double[want];
        long[] seeds = new long[want];
        int n = 0;
        for (int a = 0; a < 8 * want && n < want; a++) {
            long ks = Hash.hash(cs, 1000 + a);
            double r = arch ? ShapeMath.lerp(f.archipelagoRadiusMin(), f.archipelagoRadiusMax(),
                    Math.pow(Hash.unit(Hash.hash(ks, 1)), 2.0))
                    : ShapeMath.lerp(f.hallelujahRadiusMin(), f.hallelujahRadiusMax(), Hash.unit(Hash.hash(ks, 1)));
            double ang = Hash.unit(Hash.hash(ks, 2)) * 2.0 * Math.PI;
            double d = Math.sqrt(Hash.unit(Hash.hash(ks, 3))) * radius;
            double x = ccx + Math.cos(ang) * d;
            double z = ccz + Math.sin(ang) * d;
            double fp = r * SkyFormShape.FOOTPRINT;
            boolean detail = detailForm(p, arch, r);
            if (Math.abs((x - ccx) * -uz + (z - ccz) * ux) < fp + 0.5 * f.clusterCorridor()
                    || (!detail && inCluster(p, clusters, x, z, fp, rank))) {
                continue;
            }
            boolean free = true;
            for (int k = 0; k < n && free; k++) {
                double sum = fp + cr[k] * SkyFormShape.FOOTPRINT;
                double need = detail || detailForm(p, arch, cr[k]) ? f.detailOverlap() * sum : sum + f.skyFormGap();
                free = Math.hypot(x - cx[k], z - cz[k]) >= need;
            }
            if (!free) {
                continue;
            }
            cx[n] = x;
            cz[n] = z;
            cr[n] = r;
            seeds[n] = ks;
            n++;
        }
        // 0 unknown, 1 kept, 2 rejected by continents or ocean land.
        byte[] state = new byte[n];
        double[] tops = new double[n];
        for (int k = 0; k < n; k++) {
            double band = ShapeMath.clamp(band0 + 0.35 * (Hash.unit(Hash.hash(seeds[k], 5)) - 0.5), 0.0, 1.0);
            tops[k] = clusterForm(p, arch, seeds[k], cx[k], cz[k], cr[k], band, prm);
            double reach = cr[k] * SkyFormShape.FOOTPRINT + indexMargin(p);
            if (cx[k] + reach < x0 || cx[k] - reach >= x0 + size || cz[k] + reach < z0 || cz[k] - reach >= z0 + size
                    || !kept(p, arch, continents, land, cx, cz, cr, state, k)) {
                continue;
            }
            addSky(p, out, type, seeds[k], prm, flags | (detailForm(p, arch, cr[k]) ? RegionLayout.FLAG_DETAIL : 0));
            out.climate(ct, cv, ce);
        }
        if (!arch) {
            return;
        }
        int bridges = (int) (Hash.unit(Hash.hash(cs, 3)) * (f.archipelagoBridgesMax() + 1));
        long[] pairs = new long[bridges];
        for (int b = 0; b < bridges && n > 1; b++) {
            long bs = Hash.hash(cs, 2000 + b);
            int i = (int) (Hash.unit(Hash.hash(bs, 1)) * n);
            int j = -1;
            double best = BRIDGE_GAP_MAX;
            for (int k = 0; k < n; k++) {
                double gap = Math.hypot(cx[k] - cx[i], cz[k] - cz[i]) - (cr[i] + cr[k]) * SkyFormShape.FOOTPRINT;
                if (k != i && gap >= BRIDGE_GAP_MIN && gap < best && Math.abs(tops[k] - tops[i]) <= BRIDGE_STEP_MAX
                        && clearPath(cx, cz, cr, n, i, k)) {
                    best = gap;
                    j = k;
                }
            }
            long pair = j < 0 ? -1 : (long) Math.min(i, j) * n + Math.max(i, j);
            pairs[b] = pair;
            boolean duplicate = false;
            for (int q = 0; q < b; q++) {
                duplicate |= pairs[q] == pair;
            }
            if (j < 0 || duplicate) {
                continue;
            }
            double hw = ShapeMath.lerp(3.0, 5.0, Hash.unit(Hash.hash(bs, 2)));
            double thick = ShapeMath.lerp(6.0, 10.0, Hash.unit(Hash.hash(bs, 3)));
            double archRise = best >= 30.0 && Hash.unit(Hash.hash(bs, 4)) < 0.5
                    ? ShapeMath.lerp(8.0, 25.0, Hash.unit(Hash.hash(bs, 5))) : 0.0;
            archRise = Math.min(archRise, f.skyMaxY() - 2.0 - Math.max(tops[i], tops[j]));
            archRise = archRise < 6.0 ? 0.0 : archRise;
            SkyFormShape.placeBridge(cx[i], cz[i], tops[i], cr[i], cx[j], cz[j], tops[j], cr[j], hw, thick, archRise, prm, 0);
            double mx = 0.5 * (cx[i] + cx[j]);
            double mz = 0.5 * (cz[i] + cz[j]);
            double reach = SkyFormShape.bridgeReach(prm, 0) + indexMargin(p);
            if (mx + reach < x0 || mx - reach >= x0 + size || mz + reach < z0 || mz - reach >= z0 + size
                    || !kept(p, arch, continents, land, cx, cz, cr, state, i) || !kept(p, arch, continents, land, cx, cz, cr, state, j)
                    || land.nearSegment(cx[i], cz[i], cx[j], cz[j], SkyFormShape.bridgeHalfWidthMax(prm, 0) + p.clearSkyMargin())) {
                continue;
            }
            out.add(RegionLayout.SKY_BRIDGE, bs, (int) Math.round(mx), (int) Math.round(mz), SkyFormShape.bridgeReach(prm, 0),
                    (int) Math.floor(SkyFormShape.bridgeMinY(prm, 0)), (int) Math.ceil(SkyFormShape.bridgeMaxY(prm, 0)),
                    bs, flags | RegionLayout.FLAG_DETAIL, ZoneField.factorAtDistance(p, Math.hypot(mx, mz)),
                    prm, 0, SkyFormShape.BRIDGE_STRIDE);
        }
    }

    /** Return chain islands of the continents near the region: {x, z, radius, top} and seeds. */
    private static final class Chains {
        double[] v = new double[64];
        long[] seed = new long[16];
        int n;

        void put(double x, double z, double r, double top, long s) {
            if (4 * n + 4 > v.length) {
                v = java.util.Arrays.copyOf(v, 2 * v.length);
                seed = java.util.Arrays.copyOf(seed, 2 * seed.length);
            }
            v[4 * n] = x;
            v[4 * n + 1] = z;
            v[4 * n + 2] = r;
            v[4 * n + 3] = top;
            seed[n++] = s;
        }

        /** True if a chain island's footprint comes within {@code clear} blocks of the point. */
        boolean near(double x, double z, double clear) {
            for (int i = 0; i < n; i++) {
                double d = v[4 * i + 2] * SkyFormShape.FOOTPRINT + clear;
                double dx = x - v[4 * i];
                double dz = z - v[4 * i + 1];
                if (dx * dx + dz * dz < d * d) {
                    return true;
                }
            }
            return false;
        }

        /** Adds the islands that touch the region to the layout. */
        void add(WorldParams p, int x0, int z0, int size, double[] prm, RegionBuilder out) {
            for (int i = 0; i < n; i++) {
                double x = v[4 * i];
                double z = v[4 * i + 1];
                double r = v[4 * i + 2];
                double reach = r * SkyFormShape.FOOTPRINT + indexMargin(p);
                if (x + reach < x0 || x - reach >= x0 + size || z + reach < z0 || z - reach >= z0 + size) {
                    continue;
                }
                double depth = islandDepth(true, r, Hash.unit(Hash.hash(seed[i], 1)));
                SkyFormShape.place(x, z, r, v[4 * i + 3], depth, SkyFormShape.islandTaper(r, depth), true, prm, 0);
                addSky(p, out, RegionLayout.CHAIN, seed[i], prm, RegionLayout.FLAG_DETAIL);
            }
        }
    }

    /**
     * Return chains of the continents whose chains may come within {@code extra} blocks of
     * the region. A chain starts at a coast point and steps outwards and down to a few
     * blocks above the sea; it depends only on its continent, so every region sees it whole.
     */
    private static Chains chains(WorldParams p, int x0, int z0, int size, double extra, Continents continents) {
        Chains out = new Chains();
        for (int j = 0; j < continents.count; j++) {
            int off = j * continents.stride;
            double[] c = continents.params;
            long cs = Hash.hash(continents.seed[j], CHAIN_SALT);
            double ccx = c[off + ContinentShape.CX];
            double ccz = c[off + ContinentShape.CZ];
            boolean starter = ccx == 0.0 && ccz == 0.0;
            double roll = Hash.unit(Hash.hash(cs, 1));
            int count = starter ? 1 : roll < CHAIN_SECOND_CHANCE ? 2 : roll < CHAIN_CHANCE ? 1 : 0;
            double far = c[off + ContinentShape.EXTENT] + CHAIN_REACH + extra;
            if (count == 0 || ccx + far < x0 || ccx - far >= x0 + size || ccz + far < z0 || ccz - far >= z0 + size) {
                continue;
            }
            for (int k = 0; k < count; k++) {
                long ks = Hash.hash(cs, 10 + k);
                double a0 = (Hash.unit(Hash.hash(ks, 1)) + 0.5 * k) * 2.0 * Math.PI;
                // A coast too low for a chain (or none on the ray): try the next of four directions.
                for (int t = 0; t < 4 && !chain(p, continents, j, Hash.hash(ks, 100 + t), a0 + 0.5 * Math.PI * t, out); t++) {
                    // next direction
                }
            }
        }
        return out;
    }

    /** Lays out one chain; false if the coast on this ray does not allow one. */
    private static boolean chain(WorldParams p, Continents continents, int j, long s, double angle, Chains out) {
        int off = j * continents.stride;
        double[] c = continents.params;
        long cseed = continents.seed[j];
        double ux = Math.cos(angle);
        double uz = Math.sin(angle);
        double ccx = c[off + ContinentShape.CX];
        double ccz = c[off + ContinentShape.CZ];
        // Coast along the ray from the centre: last point inside the outline, walking in from the extent.
        double d = c[off + ContinentShape.EXTENT];
        while (d > 10.0 && ContinentShape.edge(p, c, off, cseed, ccx + ux * d, ccz + uz * d) <= 0.0) {
            d -= 4.0;
        }
        if (d <= 10.0) {
            return false;
        }
        double coastTop = ContinentShape.column(p, c, off, cseed, ccx + ux * (d - 10.0), ccz + uz * (d - 10.0), ContinentShape.TOP);
        double low = ShapeMath.lerp(CHAIN_LOW_MIN, CHAIN_LOW_MAX, Hash.unit(Hash.hash(s, 1)));
        // Steps of at most stepMax plus the jitter; a coast too high for 15 steps gets a longer first drop.
        double stepMax = CHAIN_STEP_MAX - 0.75;
        if (coastTop < low + 3.0 * stepMax) {
            return false;
        }
        int n = Math.min(Math.max((int) Math.ceil((coastTop - low) / stepMax), CHAIN_MIN), CHAIN_MAX);
        double step = (coastTop - low) / n;
        if (step > stepMax) {
            step = stepMax;
            coastTop = low + n * step;
        }
        double px = ccx + ux * d;
        double pz = ccz + uz * d;
        double pr = 0.0;
        double heading = angle;
        for (int i = 0; i < n; i++) {
            long is = Hash.hash(s, 10 + i);
            double r = ShapeMath.lerp(CHAIN_RADIUS_MIN, CHAIN_RADIUS_MAX, Hash.unit(Hash.hash(is, 1)));
            double gap = ShapeMath.lerp(CHAIN_GAP_MIN, CHAIN_GAP_MAX, Hash.unit(Hash.hash(is, 2)));
            // Irregular: islands also step sideways, but never back towards the continent.
            heading = angle + ShapeMath.clamp(heading - angle + 0.9 * (Hash.unit(Hash.hash(is, 3)) - 0.5), -1.1, 1.1);
            double dist = pr + r + gap;
            double x = px + Math.cos(heading) * dist;
            double z = pz + Math.sin(heading) * dist;
            if (i == 0) {
                // The first island hangs just off the coast, its outline clear of the cliff.
                for (int g = 0; g < 20 && ContinentShape.edge(p, c, off, cseed, x, z) > -(r + gap); g++) {
                    x += ux * 2.0;
                    z += uz * 2.0;
                }
                for (int g = 0; g < 20 && ContinentShape.edge(p, c, off, cseed, x, z) < -(r + gap + 2.0); g++) {
                    x -= ux * 2.0;
                    z -= uz * 2.0;
                }
            }
            double top = i == n - 1 ? low : coastTop - step * (i + 1) + 0.75 * (Hash.unit(Hash.hash(is, 4)) - 0.5);
            out.put(x, z, r, top, Hash.hash(is, 5));
            px = x;
            pz = z;
            pr = r;
        }
        return true;
    }

    /** Places one cluster form into prm; returns its top. */
    private static double clusterForm(WorldParams p, boolean arch, long ks, double x, double z, double r, double band,
                                      double[] prm) {
        WorldParams.SkyForms f = p.skyForms();
        if (arch) {
            boolean detail = detailForm(p, arch, r);
            boolean small = r < f.smallIslandRadiusMax();
            double depth = detail ? islandDepth(small, r, Hash.unit(Hash.hash(ks, 4)))
                    : ShapeMath.clamp(r * ShapeMath.lerp(0.5, 0.9, Hash.unit(Hash.hash(ks, 4))), 18.0, 60.0);
            double top = islandTop(p, ks, depth, band);
            depth = sunkDepth(p, top, depth);
            SkyFormShape.place(x, z, r, top, depth, SkyFormShape.islandTaper(r, depth), detail, prm, 0);
            spires(p, ks, small, prm);
            return top;
        }
        double h = ShapeMath.lerp(f.hallelujahHeightMin(), f.hallelujahHeightMax(), Hash.unit(Hash.hash(ks, 4)));
        double top = ShapeMath.lerp(f.skyFormMinY() + h + 4.0, f.skyMaxY() - 2.0, band);
        SkyFormShape.place(x, z, r, top, h, SkyFormShape.towerTaper(r, h), false, prm, 0);
        return top;
    }

    private static boolean kept(WorldParams p, boolean arch, Continents continents, Land land, double[] cx, double[] cz,
                                double[] cr, byte[] state, int k) {
        if (state[k] == 0) {
            state[k] = rejected(p, continents, land, cx[k], cz[k], cr[k], detailForm(p, arch, cr[k])) ? (byte) 2 : (byte) 1;
        }
        return state[k] == 1;
    }

    /** No other island of the cluster near the straight line between islands i and j. */
    private static boolean clearPath(double[] cx, double[] cz, double[] cr, int n, int i, int j) {
        for (int k = 0; k < n; k++) {
            if (k != i && k != j && segmentDistance(cx[k], cz[k], cx[i], cz[i], cx[j], cz[j])
                    < cr[k] * SkyFormShape.FOOTPRINT + 8.0) {
                return false;
            }
        }
        return true;
    }

    private static double segmentDistance(double px, double pz, double ax, double az, double bx, double bz) {
        double vx = bx - ax;
        double vz = bz - az;
        double t = ShapeMath.clamp(((px - ax) * vx + (pz - az) * vz) / (vx * vx + vz * vz), 0.0, 1.0);
        return Math.hypot(px - ax - t * vx, pz - az - t * vz);
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
