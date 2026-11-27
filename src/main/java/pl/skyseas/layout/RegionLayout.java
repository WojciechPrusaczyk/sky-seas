package pl.skyseas.layout;

/**
 * Islands and ocean forms touching one layout region, as primitive arrays (struct of
 * arrays), plus two bucket indexes (sky islands, ocean forms): for each bucket of
 * {@code layoutBucketSize} blocks, the items whose reach box crosses it (including items
 * centred in neighbouring regions). Immutable after {@link LayoutGenerator} builds it.
 */
public final class RegionLayout {
    public static final byte CONTINENT = 0;
    public static final byte ARCHIPELAGO = 1;
    public static final byte HALLELUJAH = 2;
    public static final byte CHAIN = 3;
    public static final byte TOWER = 4;
    /** Small or medium sky island between continents. */
    public static final byte SKY_ISLAND = 5;
    /** Rock bridge or arch between two archipelago islands (always per block). */
    public static final byte SKY_BRIDGE = 6;
    /** First ocean form type; ocean types minus this are {@code OceanShape} kinds. */
    public static final byte OCEAN_ISLAND = 7;
    public static final byte SEAMOUNT = 8;
    public static final byte VOLCANO = 9;
    public static final byte SEA_SPIRE = 10;
    /** Cenote or blue hole of an ocean island (in the ocean index; params: see ShaftShape). */
    public static final byte SHAFT = 11;
    public static final String[] TYPE_NAMES = {"continent", "archipelago", "hallelujah", "chain", "tower", "sky_island",
            "sky_bridge", "ocean_island", "seamount", "volcano", "sea_spire", "shaft"};
    public static final int FLAG_STARTER = 1;
    /** Sky form built per block in {@code sky_details} (small islands, bridges), not interpolated. */
    public static final int FLAG_DETAIL = 2;
    /** Part of a cluster guaranteed near spawn. */
    public static final int FLAG_GUARANTEED = 4;

    public final int rx;
    public final int rz;
    public final int originX;
    public final int originZ;
    public final int count;
    public final byte[] type;
    public final long[] id;
    public final int[] cx;
    public final int[] cz;
    /** Reach radius bound (blocks), for the index and rejections. */
    public final float[] radius;
    public final short[] minY;
    public final short[] maxY;
    public final long[] shapeSeed;
    /** Shape parameters of item i start at {@code params[paramOffset[i]]}. */
    public final double[] params;
    public final int[] paramOffset;
    public final int[] flags;
    /** Zone factor at the centre. */
    public final float[] zone;

    final int bucketSize;
    final int buckets;
    /** CSR indexes: items of bucket b are items[start[b] .. start[b + 1]). */
    final int[] skyStart;
    final int[] skyItems;
    final int[] oceanStart;
    final int[] oceanItems;

    RegionLayout(int rx, int rz, int regionSize, int count, byte[] type, long[] id, int[] cx, int[] cz,
                 float[] radius, short[] minY, short[] maxY, long[] shapeSeed, double[] params, int[] paramOffset,
                 int[] flags, float[] zone, int bucketSize, int[] skyStart, int[] skyItems, int[] oceanStart,
                 int[] oceanItems) {
        this.rx = rx;
        this.rz = rz;
        this.originX = rx * regionSize;
        this.originZ = rz * regionSize;
        this.count = count;
        this.type = type;
        this.id = id;
        this.cx = cx;
        this.cz = cz;
        this.radius = radius;
        this.minY = minY;
        this.maxY = maxY;
        this.shapeSeed = shapeSeed;
        this.params = params;
        this.paramOffset = paramOffset;
        this.flags = flags;
        this.zone = zone;
        this.bucketSize = bucketSize;
        this.buckets = regionSize / bucketSize;
        this.skyStart = skyStart;
        this.skyItems = skyItems;
        this.oceanStart = oceanStart;
        this.oceanItems = oceanItems;
    }

    public static boolean isOcean(byte type) {
        return type >= OCEAN_ISLAND;
    }

    /** Bucket of a block inside this region. */
    public int bucket(int x, int z) {
        return ((z - originZ) / bucketSize) * buckets + (x - originX) / bucketSize;
    }

    public int skyStart(int bucket) {
        return skyStart[bucket];
    }

    public int skyEnd(int bucket) {
        return skyStart[bucket + 1];
    }

    public int skyItem(int k) {
        return skyItems[k];
    }

    public int oceanStart(int bucket) {
        return oceanStart[bucket];
    }

    public int oceanEnd(int bucket) {
        return oceanStart[bucket + 1];
    }

    public int oceanItem(int k) {
        return oceanItems[k];
    }

    /** True if the item's centre lies in this region (each item is counted once over all regions). */
    public boolean owns(int i, int regionSize) {
        return cx[i] >= originX && cx[i] < originX + regionSize && cz[i] >= originZ && cz[i] < originZ + regionSize;
    }

    /** Rough heap size in bytes (for cache statistics). */
    public long bytes() {
        return 64L + count * (1L + 8 + 4 + 4 + 4 + 2 + 2 + 8 + 4 + 4 + 4) + 8L * params.length
                + 4L * (skyStart.length + skyItems.length + oceanStart.length + oceanItems.length);
    }
}
