package pl.skyseas.layout;

/** SplitMix64-based positional hashing (allocation-free, order-independent). */
public final class Hash {
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    private Hash() {
    }

    /** SplitMix64 finalizer. */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static long hash(long seed, long a) {
        return mix(seed + GOLDEN * (a + 1));
    }

    public static long hash(long seed, long a, long b) {
        return mix(hash(seed, a) + GOLDEN * (b + 1));
    }

    public static long hash(long seed, long a, long b, long c) {
        return mix(hash(seed, a, b) + GOLDEN * (c + 1));
    }

    /** Uniform double in [0, 1) from a hash. */
    public static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }
}
