package pl.skyseas.layout;

import pl.skyseas.config.WorldParams;
import pl.skyseas.shape.ContinentShape;
import pl.skyseas.shape.ShapeMath;

/**
 * Builds a {@link RegionLayout}. Level 1 (E2): continents on a jittered grid, one
 * candidate per cell, the starter continent in the cell at 0,0. Every decision depends
 * only on the seed and the cell, never on generation order.
 */
public final class LayoutGenerator {
    private static final long CONTINENT_SALT = 0x434F4E54L;

    private LayoutGenerator() {
    }

    /** Margin added to an island's outline bound for the bucket index: climate reach and edge noise. */
    static double indexMargin(WorldParams p) {
        return p.islandReachMargin() + p.continentEdgeNoise() + 4.0;
    }

    /** Largest outline extent of any continent: also bounds the cell jitter, so continents never collide. */
    static double maxContinentExtent(WorldParams p) {
        return ContinentShape.maxExtent(p, p.continentRadiusMax());
    }

    public static RegionLayout generate(long seed, WorldParams p, int rx, int rz) {
        int size = p.layoutRegionSize();
        int cell = p.continentCellSize();
        double margin = indexMargin(p);
        double jitter = cell / 2.0 - maxContinentExtent(p) - margin;
        if (jitter < 0.0) {
            throw new IllegalStateException("continent cell too small for the largest continent");
        }
        int x0 = rx * size;
        int z0 = rz * size;
        int ci0 = Math.floorDiv(x0 + cell / 2, cell);
        int ci1 = Math.floorDiv(x0 + size - 1 + cell / 2, cell);
        int cj0 = Math.floorDiv(z0 + cell / 2, cell);
        int cj1 = Math.floorDiv(z0 + size - 1 + cell / 2, cell);

        int max = (ci1 - ci0 + 1) * (cj1 - cj0 + 1);
        int stride = ContinentShape.stride(p);
        byte[] type = new byte[max];
        long[] id = new long[max];
        int[] cx = new int[max];
        int[] cz = new int[max];
        float[] radius = new float[max];
        short[] minY = new short[max];
        short[] maxY = new short[max];
        long[] shapeSeed = new long[max];
        double[] params = new double[max * stride];
        int[] flags = new int[max];
        float[] zone = new float[max];

        int n = 0;
        for (int ci = ci0; ci <= ci1; ci++) {
            for (int cj = cj0; cj <= cj1; cj++) {
                long s = Hash.hash(seed ^ CONTINENT_SALT, ci, cj);
                boolean starter = ci == 0 && cj == 0;
                int x = starter ? 0 : (int) Math.round(ci * (double) cell + (2.0 * Hash.unit(Hash.hash(s, 1)) - 1.0) * jitter);
                int z = starter ? 0 : (int) Math.round(cj * (double) cell + (2.0 * Hash.unit(Hash.hash(s, 2)) - 1.0) * jitter);
                double r = starter ? p.starterContinentRadius()
                        : ShapeMath.lerp(p.continentRadiusMin(), p.continentRadiusMax(), Hash.unit(Hash.hash(s, 3)));
                int off = n * stride;
                ContinentShape.place(p, s, x, z, r, starter, params, off);
                double reach = params[off + ContinentShape.EXTENT] + margin;
                if (x + reach < x0 || x - reach >= x0 + size || z + reach < z0 || z - reach >= z0 + size) {
                    continue;
                }
                type[n] = RegionLayout.CONTINENT;
                id[n] = s;
                cx[n] = x;
                cz[n] = z;
                radius[n] = (float) params[off + ContinentShape.EXTENT];
                minY[n] = (short) Math.floor(p.continentMinBottomY());
                maxY[n] = (short) Math.ceil(params[off + ContinentShape.MAX_Y] + p.continentEdgeNoise());
                shapeSeed[n] = s;
                flags[n] = starter ? RegionLayout.FLAG_STARTER : 0;
                zone[n] = (float) ZoneField.factorAtDistance(p, Math.sqrt((double) x * x + (double) z * z));
                n++;
            }
        }

        int bucketSize = p.layoutBucketSize();
        int buckets = size / bucketSize;
        int[] counts = new int[buckets * buckets + 1];
        for (int pass = 0; pass < 2; pass++) {
            int[] fill = pass == 0 ? null : new int[buckets * buckets];
            int[] items = pass == 0 ? null : new int[counts[buckets * buckets]];
            for (int i = 0; i < n; i++) {
                double reach = radius[i] + margin;
                int bx0 = clampBucket((int) Math.floor((cx[i] - reach - x0) / bucketSize), buckets);
                int bx1 = clampBucket((int) Math.floor((cx[i] + reach - x0) / bucketSize), buckets);
                int bz0 = clampBucket((int) Math.floor((cz[i] - reach - z0) / bucketSize), buckets);
                int bz1 = clampBucket((int) Math.floor((cz[i] + reach - z0) / bucketSize), buckets);
                for (int bz = bz0; bz <= bz1; bz++) {
                    for (int bx = bx0; bx <= bx1; bx++) {
                        int b = bz * buckets + bx;
                        if (pass == 0) {
                            counts[b + 1]++;
                        } else {
                            items[counts[b] + fill[b]++] = i;
                        }
                    }
                }
            }
            if (pass == 0) {
                for (int b = 0; b < buckets * buckets; b++) {
                    counts[b + 1] += counts[b];
                }
            } else {
                return new RegionLayout(rx, rz, size, n, type, id, cx, cz, radius, minY, maxY, shapeSeed, params,
                        stride, flags, zone, bucketSize, counts, items);
            }
        }
        throw new AssertionError();
    }

    private static int clampBucket(int b, int buckets) {
        return b < 0 ? 0 : (b >= buckets ? buckets - 1 : b);
    }
}
