package pl.skyseas.layout;

import java.util.Arrays;

/** Growable item lists for one region, turned into a {@link RegionLayout} with its bucket indexes. */
final class RegionBuilder {
    private byte[] type = new byte[16];
    private long[] id = new long[16];
    private int[] cx = new int[16];
    private int[] cz = new int[16];
    private float[] radius = new float[16];
    private short[] minY = new short[16];
    private short[] maxY = new short[16];
    private long[] shapeSeed = new long[16];
    private int[] paramOffset = new int[16];
    private int[] flags = new int[16];
    private float[] zone = new float[16];
    private double[] params = new double[256];
    private int n;
    private int pn;

    void add(byte t, long itemId, int x, int z, double reach, int lowY, int highY, long seed, int itemFlags,
             double zoneFactor, double[] prm, int off, int len) {
        if (n == type.length) {
            int c = 2 * n;
            type = Arrays.copyOf(type, c);
            id = Arrays.copyOf(id, c);
            cx = Arrays.copyOf(cx, c);
            cz = Arrays.copyOf(cz, c);
            radius = Arrays.copyOf(radius, c);
            minY = Arrays.copyOf(minY, c);
            maxY = Arrays.copyOf(maxY, c);
            shapeSeed = Arrays.copyOf(shapeSeed, c);
            paramOffset = Arrays.copyOf(paramOffset, c);
            flags = Arrays.copyOf(flags, c);
            zone = Arrays.copyOf(zone, c);
        }
        if (pn + len > params.length) {
            params = Arrays.copyOf(params, Math.max(2 * params.length, pn + len));
        }
        type[n] = t;
        id[n] = itemId;
        cx[n] = x;
        cz[n] = z;
        radius[n] = (float) reach;
        minY[n] = (short) lowY;
        maxY[n] = (short) highY;
        shapeSeed[n] = seed;
        paramOffset[n] = pn;
        flags[n] = itemFlags;
        zone[n] = (float) zoneFactor;
        System.arraycopy(prm, off, params, pn, len);
        pn += len;
        n++;
    }

    /**
     * @param skyMargin   added to sky island reach in the index (climate reach, edge noise)
     * @param oceanMargin added to ocean form reach in the index
     */
    RegionLayout build(int rx, int rz, int size, int bucketSize, double skyMargin, double oceanMargin) {
        int buckets = size / bucketSize;
        int[][] sky = index(rx * size, rz * size, buckets, bucketSize, false, skyMargin);
        int[][] ocean = index(rx * size, rz * size, buckets, bucketSize, true, oceanMargin);
        return new RegionLayout(rx, rz, size, n, Arrays.copyOf(type, n), Arrays.copyOf(id, n), Arrays.copyOf(cx, n),
                Arrays.copyOf(cz, n), Arrays.copyOf(radius, n), Arrays.copyOf(minY, n), Arrays.copyOf(maxY, n),
                Arrays.copyOf(shapeSeed, n), Arrays.copyOf(params, pn), Arrays.copyOf(paramOffset, n),
                Arrays.copyOf(flags, n), Arrays.copyOf(zone, n), bucketSize, sky[0], sky[1], ocean[0], ocean[1]);
    }

    /** Two-pass CSR build: {start, items}. */
    private int[][] index(int x0, int z0, int buckets, int bucketSize, boolean ocean, double margin) {
        int[] counts = new int[buckets * buckets + 1];
        int[] items = null;
        int[] fill = null;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < n; i++) {
                if (RegionLayout.isOcean(type[i]) != ocean) {
                    continue;
                }
                double reach = radius[i] + margin;
                int bx0 = clamp((int) Math.floor((cx[i] - reach - x0) / bucketSize), buckets);
                int bx1 = clamp((int) Math.floor((cx[i] + reach - x0) / bucketSize), buckets);
                int bz0 = clamp((int) Math.floor((cz[i] - reach - z0) / bucketSize), buckets);
                int bz1 = clamp((int) Math.floor((cz[i] + reach - z0) / bucketSize), buckets);
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
                items = new int[counts[buckets * buckets]];
                fill = new int[buckets * buckets];
            }
        }
        return new int[][]{counts, items};
    }

    private static int clamp(int b, int buckets) {
        return b < 0 ? 0 : (b >= buckets ? buckets - 1 : b);
    }
}
