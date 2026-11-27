package pl.skyseas.shape;

import pl.skyseas.config.WorldParams;
import pl.skyseas.layout.Hash;

/**
 * Seabed provinces: soft regions of the seabed with their own forms, raised on the E1
 * seabed (base depth, relief, dunes, trenches). Plains with dunes stay rare; the others
 * are hills and underwater mountain ranges, canyon networks cut down to near the bedrock,
 * fields of small cones, scarps with terraces and table plateaus, and pillar fields.
 *
 * <p>Provinces are warped Voronoi cells of about {@code provinceScale} blocks, each given one
 * province by its weight (plains by {@code plainsShare}), blended over {@code provinceBlend}
 * blocks at the borders.
 * Everything is a 2D height (the seabed stays a heightmap, so the roof and caves follow
 * it); single forms (cones, pillars) appear with a chance scaled by their province weight
 * at their centre, so they are never cut in half by a border.
 */
public final class SeabedProvinces {
    public static final int HILLS = 0;
    public static final int CANYONS = 1;
    public static final int VOLCANIC = 2;
    public static final int PLATEAUS = 3;
    public static final int ROCKY = 4;
    public static final int PLAINS = 5;
    public static final String[] NAMES = {"hills", "canyons", "volcanic", "plateaus", "rocky", "plains"};
    private static final int COUNT = 6;
    private static final double SOFT_CAP = 6.0;

    private SeabedProvinces() {
    }

    /** Weight 0..1 of a province at a column; the six weights sum to 1. */
    public static double weight(WorldParams p, long seed, double x, double z, int province) {
        WorldParams.SeabedForms f = p.seabedForms();
        double cell = f.provinceScale();
        // Warped cells give organic borders instead of straight Voronoi edges.
        double wx = x + 0.3 * cell * SimplexNoise.noise2(seed + 12, x / (0.6 * cell), z / (0.6 * cell));
        double wz = z + 0.3 * cell * SimplexNoise.noise2(seed + 13, x / (0.6 * cell), z / (0.6 * cell));
        int ci = (int) Math.floor(wx / cell);
        int cj = (int) Math.floor(wz / cell);
        double dmin = Double.MAX_VALUE;
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                dmin = Math.min(dmin, distance(seed, cell, ci + di, cj + dj, wx, wz));
            }
        }
        double mine = 0.0;
        double total = 0.0;
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                double d = distance(seed, cell, ci + di, cj + dj, wx, wz);
                double w = 1.0 - ShapeMath.smoothstep(0.0, f.provinceBlend(), d - dmin);
                if (w > 0.0) {
                    total += w;
                    if (provinceOf(f, seed, ci + di, cj + dj) == province) {
                        mine += w;
                    }
                }
            }
        }
        return mine / total;
    }

    /** Plains weight 0..1 at a column. */
    public static double plains(WorldParams p, long seed, double x, double z) {
        return weight(p, seed, x, z, PLAINS);
    }

    private static double distance(long seed, double cell, int ci, int cj, double x, double z) {
        long s = Hash.hash(seed + 14, ci, cj);
        double cx = (ci + 0.15 + 0.7 * Hash.unit(Hash.hash(s, 1))) * cell;
        double cz = (cj + 0.15 + 0.7 * Hash.unit(Hash.hash(s, 2))) * cell;
        return Math.hypot(x - cx, z - cz);
    }

    private static int provinceOf(WorldParams.SeabedForms f, long seed, int ci, int cj) {
        double u = Hash.unit(Hash.hash(Hash.hash(seed + 14, ci, cj), 3));
        if (u < f.plainsShare()) {
            return PLAINS;
        }
        double total = f.hillsWeight() + f.canyonsWeight() + f.volcanicWeight() + f.plateausWeight() + f.rockyWeight();
        double v = (u - f.plainsShare()) / (1.0 - f.plainsShare()) * total;
        if ((v -= f.hillsWeight()) < 0.0) {
            return HILLS;
        }
        if ((v -= f.canyonsWeight()) < 0.0) {
            return CANYONS;
        }
        if ((v -= f.volcanicWeight()) < 0.0) {
            return VOLCANIC;
        }
        return v - f.plateausWeight() < 0.0 ? PLATEAUS : ROCKY;
    }

    /** Dominant province at a column (tools, map, tests). */
    public static int dominant(WorldParams p, long seed, double x, double z) {
        int best = 0;
        double bw = -1.0;
        for (int k = 0; k < COUNT; k++) {
            double w = weight(p, seed, x, z, k);
            if (w > bw) {
                bw = w;
                best = k;
            }
        }
        return best;
    }

    /** Seabed height with the province forms raised on the E1 seabed {@code base}. */
    public static double height(WorldParams p, long seed, double x, double z, double base) {
        WorldParams.SeabedForms f = p.seabedForms();
        double q = 1.0 - plains(p, seed, x, z);
        if (q <= 0.0) {
            return base;
        }
        double h = base + q * f.midAmplitude() * (0.65 * SimplexNoise.noise2(seed + 20, x / 110.0, z / 110.0)
                + 0.35 * SimplexNoise.noise2(seed + 21, x / 55.0, z / 55.0));

        double wh = weight(p, seed, x, z, HILLS);
        if (wh > 0.0) {
            double hills = f.hillAmplitude() * SimplexNoise.noise2(seed + 22, x / 160.0, z / 160.0);
            double ridge = 1.0 - Math.abs(SimplexNoise.noise2(seed + 23, x / 280.0, z / 280.0));
            double ranges = ShapeMath.smoothstep(-0.3, 0.4, SimplexNoise.noise2(seed + 24, x / 700.0, z / 700.0));
            h += wh * (hills + f.mountainAmplitude() * ridge * ridge * ridge * ranges);
        }

        double wt = weight(p, seed, x, z, PLATEAUS);
        if (wt > 0.0) {
            double u = 0.5 * (SimplexNoise.noise2(seed + 25, x / 320.0, z / 320.0) + 1.0) * f.terraceSteps();
            double level = Math.floor(u);
            // Sharp risers: the terrace stays flat for most of a step, then climbs a scarp.
            double terrace = (level + ShapeMath.smoothstep(0.8, 1.0, u - level)) * f.terraceStep();
            double mesa = ShapeMath.smoothstep(0.3, 0.36, SimplexNoise.noise2(seed + 26, x / 180.0, z / 180.0))
                    * 1.5 * f.terraceStep();
            h += wt * (terrace + mesa - 0.5 * f.terraceStep());
        }

        double wc = weight(p, seed, x, z, CANYONS);
        if (wc > 0.0) {
            double cut = ShapeMath.smoothstep(0.1, 0.45, wc);
            double wx = x + 35.0 * SimplexNoise.noise2(seed + 27, x / 300.0, z / 300.0);
            double wz = z + 35.0 * SimplexNoise.noise2(seed + 28, x / 300.0, z / 300.0);
            double main = canyon(SimplexNoise.noise2(seed + 29, wx / f.canyonScale(), wz / f.canyonScale()), f.canyonWidth());
            double side = canyon(SimplexNoise.noise2(seed + 30, wx / f.branchScale(), wz / f.branchScale()), f.branchWidth());
            // Floors stay well above Y -54: aquifer cells below it (vanilla lava level) dry out open water nearby.
            double floor = f.canyonFloorY() + 1.5 * SimplexNoise.noise2(seed + 31, x / 40.0, z / 40.0);
            double sideFloor = f.branchFloorY() + 3.0 * SimplexNoise.noise2(seed + 32, x / 40.0, z / 40.0);
            double hc = h;
            if (side > 0.0 && sideFloor < h) {
                hc -= (h - sideFloor) * side * cut;
            }
            if (main > 0.0 && floor < hc) {
                hc -= (hc - floor) * main * cut;
            }
            h = hc;
        }

        double wv = weight(p, seed, x, z, VOLCANIC);
        if (wv > 0.0) {
            // Cone fields stand on a broad volcanic swell.
            h += wv * 6.0 * (1.0 + SimplexNoise.noise2(seed + 33, x / 260.0, z / 260.0));
        }
        double wr = weight(p, seed, x, z, ROCKY);
        if (wr > 0.0) {
            double crag = 1.0 - Math.abs(SimplexNoise.noise2(seed + 34, x / 48.0, z / 48.0));
            h += wr * 12.0 * crag * crag * crag;
        }
        h += cones(p, seed, x, z);
        h += pillars(p, seed, x, z);

        double cap = f.maxY() - SOFT_CAP;
        if (h > cap) {
            return cap + SOFT_CAP * Math.tanh((h - cap) / SOFT_CAP);
        }
        // Nothing deeper than the canyon floors (trench, hills and terraces could add up below them).
        double floor = lowest(p) + SOFT_CAP;
        return h < floor ? floor - SOFT_CAP * Math.tanh((floor - h) / SOFT_CAP) : h;
    }

    /** Lowest seabed the provinces leave (canyon floors with their noise). */
    public static double lowest(WorldParams p) {
        return Math.min(p.seabedForms().canyonFloorY(), p.seabedTrenchMinY()) - 1.5;
    }

    /** Canyon profile 0..1 across a ridge-noise line: steep upper wall, a shelf, a steep lower wall, a flat floor. */
    private static double canyon(double noise, double width) {
        double ridge = 1.0 - Math.abs(noise) / width;
        if (ridge <= 0.0) {
            return 0.0;
        }
        return 0.4 * ShapeMath.smoothstep(0.0, 0.1, ridge) + 0.6 * ShapeMath.smoothstep(0.35, 0.45, ridge);
    }

    private static double cones(WorldParams p, long seed, double x, double z) {
        WorldParams.SeabedForms f = p.seabedForms();
        int cell = f.coneCell();
        int ci = (int) Math.floor(x / cell);
        int cj = (int) Math.floor(z / cell);
        double best = 0.0;
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                long s = Hash.hash(seed + 40, ci + di, cj + dj);
                double roll = Hash.unit(Hash.hash(s, 1));
                if (roll >= f.coneChance()) {
                    continue;
                }
                double cx = (ci + di + Hash.unit(Hash.hash(s, 2))) * cell;
                double cz = (cj + dj + Hash.unit(Hash.hash(s, 3))) * cell;
                // Small cones are common, large ones rare.
                double size = Math.pow(Hash.unit(Hash.hash(s, 4)), 1.2);
                double r = ShapeMath.lerp(f.coneRadiusMin(), f.coneRadiusMax(), size);
                double d = Math.hypot(x - cx, z - cz);
                if (d >= r) {
                    continue;
                }
                double w = weight(p, seed, cx, cz, VOLCANIC);
                if (roll >= f.coneChance() * w) {
                    continue;
                }
                double hgt = ShapeMath.lerp(f.coneHeightMin(), f.coneHeightMax(),
                        ShapeMath.clamp(size + 0.3 * (Hash.unit(Hash.hash(s, 5)) - 0.5), 0.0, 1.0));
                double rc = 0.28 * r;
                double v;
                if (d >= rc) {
                    v = hgt * Math.pow((r - d) / (r - rc), 1.4);
                } else {
                    double crater = hgt > 12.0 ? 0.35 * hgt : 0.0;
                    v = hgt - crater * (1.0 - (d / rc) * (d / rc));
                }
                best = Math.max(best, v);
            }
        }
        return best;
    }

    private static double pillars(WorldParams p, long seed, double x, double z) {
        WorldParams.SeabedForms f = p.seabedForms();
        int cell = f.pillarCell();
        int ci = (int) Math.floor(x / cell);
        int cj = (int) Math.floor(z / cell);
        double best = 0.0;
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                long s = Hash.hash(seed + 50, ci + di, cj + dj);
                double roll = Hash.unit(Hash.hash(s, 1));
                if (roll >= f.pillarChance()) {
                    continue;
                }
                double cx = (ci + di + 0.15 + 0.7 * Hash.unit(Hash.hash(s, 2))) * cell;
                double cz = (cj + dj + 0.15 + 0.7 * Hash.unit(Hash.hash(s, 3))) * cell;
                double r = ShapeMath.lerp(f.pillarRadiusMin(), f.pillarRadiusMax(), Hash.unit(Hash.hash(s, 4)));
                double d = Math.hypot(x - cx, z - cz);
                if (d >= 2.2 * r) {
                    continue;
                }
                double w = weight(p, seed, cx, cz, ROCKY);
                if (roll >= f.pillarChance() * w) {
                    continue;
                }
                double hgt = ShapeMath.lerp(f.pillarHeightMin(), f.pillarHeightMax(), Hash.unit(Hash.hash(s, 5)));
                // A steep column with a flared foot and a slightly uneven top.
                double column = hgt * (1.0 - ShapeMath.smoothstep(r - 1.0, r + 1.5, d))
                        + 1.5 * SimplexNoise.noise2(s, x / 6.0, z / 6.0) * (1.0 - ShapeMath.smoothstep(r - 1.0, r, d));
                double foot = 0.3 * hgt * (1.0 - ShapeMath.smoothstep(r, 2.2 * r, d));
                best = Math.max(best, Math.max(column, foot));
            }
        }
        return best;
    }
}
