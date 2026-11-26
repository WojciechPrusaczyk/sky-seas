package pl.skyseas.layout;

/**
 * Islands touching one layout region, as primitive arrays (struct of arrays), plus a
 * bucket index: for each bucket of {@code layoutBucketSize} blocks, the islands whose
 * reach box crosses it (including islands centred in neighbouring regions). Immutable
 * after {@link LayoutGenerator} builds it.
 */
public final class RegionLayout {
    public static final byte CONTINENT = 0;
    public static final int FLAG_STARTER = 1;

    public final int rx;
    public final int rz;
    public final int originX;
    public final int originZ;
    public final int count;
    public final byte[] type;
    public final long[] id;
    public final int[] cx;
    public final int[] cz;
    /** Outline radius bound (blocks), for the index and rejections. */
    public final float[] radius;
    public final short[] minY;
    public final short[] maxY;
    public final long[] shapeSeed;
    /** Shape parameters, {@link #paramStride} per island. */
    public final double[] params;
    public final int paramStride;
    public final int[] flags;
    /** Zone factor at the centre. */
    public final float[] zone;

    final int bucketSize;
    final int buckets;
    /** CSR index: islands of bucket b are bucketItems[bucketStart[b] .. bucketStart[b + 1]). */
    final int[] bucketStart;
    final int[] bucketItems;

    RegionLayout(int rx, int rz, int regionSize, int count, byte[] type, long[] id, int[] cx, int[] cz,
                 float[] radius, short[] minY, short[] maxY, long[] shapeSeed, double[] params, int paramStride,
                 int[] flags, float[] zone, int bucketSize, int[] bucketStart, int[] bucketItems) {
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
        this.paramStride = paramStride;
        this.flags = flags;
        this.zone = zone;
        this.bucketSize = bucketSize;
        this.buckets = regionSize / bucketSize;
        this.bucketStart = bucketStart;
        this.bucketItems = bucketItems;
    }

    /** Bucket of a block inside this region. */
    public int bucket(int x, int z) {
        return ((z - originZ) / bucketSize) * buckets + (x - originX) / bucketSize;
    }

    public int bucketStart(int bucket) {
        return bucketStart[bucket];
    }

    public int bucketEnd(int bucket) {
        return bucketStart[bucket + 1];
    }

    public int bucketItem(int k) {
        return bucketItems[k];
    }

    /** Rough heap size in bytes (for cache statistics). */
    public long bytes() {
        return 64L + count * (1L + 8 + 4 + 4 + 4 + 2 + 2 + 8 + 4 + 4 + 8L * paramStride)
                + 4L * (bucketStart.length + bucketItems.length);
    }
}
