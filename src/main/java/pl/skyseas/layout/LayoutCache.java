package pl.skyseas.layout;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicLong;
import pl.skyseas.config.WorldParams;

/**
 * Bounded, thread-safe region cache. Each region is computed exactly once (a future per
 * key; other threads wait for it); each thread keeps its last region in a ThreadLocal,
 * because most queries hit the same one. Eviction is least recently used.
 */
public final class LayoutCache {
    private final long seed;
    private final WorldParams params;
    private final ClimateProbe climate;
    private final int maxRegions;
    private final Map<Long, FutureTask<RegionLayout>> regions;
    private final ThreadLocal<RegionLayout> last = new ThreadLocal<>();
    private final AtomicLong computed = new AtomicLong();

    LayoutCache(long seed, WorldParams params, ClimateProbe climate) {
        this.seed = seed;
        this.params = params;
        this.climate = climate;
        this.maxRegions = params.layoutCacheRegions();
        this.regions = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, FutureTask<RegionLayout>> eldest) {
                return size() > maxRegions;
            }
        };
    }

    public static long key(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
    }

    public RegionLayout get(int rx, int rz) {
        RegionLayout r = last.get();
        if (r != null && r.rx == rx && r.rz == rz) {
            return r;
        }
        r = getSlow(rx, rz);
        last.set(r);
        return r;
    }

    private RegionLayout getSlow(int rx, int rz) {
        Long key = key(rx, rz);
        FutureTask<RegionLayout> task;
        boolean mine = false;
        synchronized (regions) {
            task = regions.get(key);
            if (task == null) {
                task = new FutureTask<>(() -> LayoutGenerator.generate(seed, params, climate, rx, rz));
                regions.put(key, task);
                mine = true;
            }
        }
        if (mine) {
            task.run();
            computed.incrementAndGet();
        }
        try {
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for layout region " + rx + "," + rz, e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("layout region " + rx + "," + rz + " failed", e.getCause());
        }
    }

    /** Regions currently cached. */
    public int size() {
        synchronized (regions) {
            return regions.size();
        }
    }

    /** Regions computed since creation (misses). */
    public long computed() {
        return computed.get();
    }

    /** Rough heap size of the finished cached regions. */
    public long bytes() {
        long sum = 0;
        synchronized (regions) {
            for (FutureTask<RegionLayout> task : regions.values()) {
                if (task.isDone() && !task.isCancelled()) {
                    try {
                        sum += task.get().bytes();
                    } catch (InterruptedException | ExecutionException ignored) {
                        // failed regions hold no data
                    }
                }
            }
        }
        return sum;
    }
}
