package pl.skyseas.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.DensityFunction;
import pl.skyseas.SkySeas;
import pl.skyseas.config.WorldParams;
import pl.skyseas.density.LayoutAccess;
import pl.skyseas.density.SeabedHeightFunction;
import pl.skyseas.layout.Layout;
import pl.skyseas.layout.RegionLayout;
import pl.skyseas.shape.ShaftShape;
import pl.skyseas.shape.SkyFormShape;

/**
 * {@code /skyseas audit <radius>}: checks the design rules on the layout alone, per
 * region, into a CSV: sky visibility (every 50 blocks), shading (share of sea under sky
 * forms, every 8), sky forms over ocean land (every 4), return chain steps, shafts open
 * down into their chambers. Runs on its own thread.
 */
final class LayoutAudit {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final int VISIBILITY_STEP = 50;
    private static final int SHADE_STEP = 8;
    private static final int LAND_STEP = 4;

    private LayoutAudit() {
    }

    static boolean start(ServerLevel level, int centerX, int centerZ, int radius, Consumer<String> reply) {
        if (!RUNNING.compareAndSet(false, true)) {
            return false;
        }
        Thread thread = new Thread(() -> {
            try {
                String msg = run(level, centerX, centerZ, radius);
                level.getServer().execute(() -> reply.accept(msg));
            } catch (RuntimeException | IOException e) {
                SkySeas.LOGGER.error("Audit failed", e);
                level.getServer().execute(() -> reply.accept("[Sky Seas] audit failed: " + e));
            } finally {
                RUNNING.set(false);
            }
        }, "skyseas-audit");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    private static String run(ServerLevel level, int centerX, int centerZ, int radius) throws IOException {
        Layout layout = LayoutAccess.get(level.getChunkSource().randomState());
        if (layout == null) {
            return "[Sky Seas] audit: not a Sky Seas world";
        }
        WorldParams p = layout.params();
        SeabedHeightFunction seabed = RouterLookup.find(level.getChunkSource().randomState().router().finalDensity(),
                SeabedHeightFunction.class);
        int size = p.layoutRegionSize();
        int rx0 = Math.floorDiv(centerX - radius, size);
        int rx1 = Math.floorDiv(centerX + radius, size);
        int rz0 = Math.floorDiv(centerZ - radius, size);
        int rz1 = Math.floorDiv(centerZ + radius, size);
        StringBuilder csv = new StringBuilder("rx,rz,visibility_points,visibility_failed,sea_points,shaded,"
                + "ocean_land_points,sky_over_land,chain_islands,chain_without_step,shafts,shafts_closed\n");
        long[] total = new long[10];
        long t0 = System.nanoTime();
        for (int rx = rx0; rx <= rx1; rx++) {
            for (int rz = rz0; rz <= rz1; rz++) {
                long[] v = new long[10];
                int x0 = rx * size;
                int z0 = rz * size;
                for (int x = x0; x < x0 + size; x += LAND_STEP) {
                    for (int z = z0; z < z0 + size; z += LAND_STEP) {
                        if (Math.hypot(x - centerX, z - centerZ) > radius) {
                            continue;
                        }
                        boolean sky = layout.column(x, z, Layout.EDGE) > 0.0 || layout.detailFloor(x & ~3, z & ~3) < Layout.NO_SPIRE;
                        if (x % VISIBILITY_STEP == 0 && z % VISIBILITY_STEP == 0) {
                            v[0]++;
                            v[1] += layout.skyVisible(x, z, 250.0, 500.0) ? 0 : 1;
                        }
                        double h = seabed == null ? layout.oceanHeight(x, z, -10.0)
                                : seabed.compute(new DensityFunction.SinglePointContext(x, 0, z));
                        if (h >= 63.0) {
                            v[4]++;
                            boolean near = layout.column(x, z, Layout.EDGE) > -p.clearSkyMargin();
                            v[5] += near || sky ? 1 : 0;
                        } else if (x % SHADE_STEP == 0 && z % SHADE_STEP == 0) {
                            v[2]++;
                            v[3] += sky ? 1 : 0;
                        }
                    }
                }
                chainsAndShafts(layout, layout.region(rx, rz), v);
                csv.append(rx).append(',').append(rz);
                for (int k = 0; k < 10; k++) {
                    csv.append(',').append(v[k]);
                    total[k] += v[k];
                }
                csv.append('\n');
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        Path dir = level.getServer().getServerDirectory().resolve("skyseas").resolve("audit");
        Files.createDirectories(dir);
        Path file = dir.resolve("audit-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv");
        Files.writeString(file, csv);
        return String.format(Locale.ROOT,
                "[Sky Seas] audit: radius %d around %d,%d, %d ms\n  sky visibility %.2f%% of %d points; sea under sky forms %.1f%%"
                        + "\n  sky over ocean land: %d of %d points; chain islands %d, without a step up %d; shafts %d, closed %d"
                        + "\n  csv: %s",
                radius, centerX, centerZ, ms, total[0] == 0 ? 100.0 : 100.0 * (total[0] - total[1]) / total[0], total[0],
                total[2] == 0 ? 0.0 : 100.0 * total[3] / total[2], total[5], total[4], total[6], total[7], total[8], total[9],
                file.toAbsolutePath());
    }

    /** Chain islands owned by the region without a higher neighbour one step away or a coast; shafts not open to their chamber. */
    private static void chainsAndShafts(Layout layout, RegionLayout r, long[] v) {
        int size = layout.params().layoutRegionSize();
        for (int i = 0; i < r.count; i++) {
            if (!r.owns(i, size)) {
                continue;
            }
            int o = r.paramOffset[i];
            if (r.type[i] == RegionLayout.CHAIN) {
                v[6]++;
                double x = r.params[o + SkyFormShape.CX];
                double z = r.params[o + SkyFormShape.CZ];
                double rad = r.params[o + SkyFormShape.RADIUS];
                double top = r.params[o + SkyFormShape.TOP];
                boolean ok = layout.column((int) Math.round(x), (int) Math.round(z), Layout.EDGE) > -(rad + 14.0);
                for (int rx = r.rx - 1; rx <= r.rx + 1 && !ok; rx++) {
                    for (int rz = r.rz - 1; rz <= r.rz + 1 && !ok; rz++) {
                        RegionLayout q = layout.region(rx, rz);
                        for (int j = 0; j < q.count && !ok; j++) {
                            if (q.type[j] == RegionLayout.CHAIN) {
                                int p2 = q.paramOffset[j];
                                double t = q.params[p2 + SkyFormShape.TOP] - top;
                                double gap = Math.hypot(q.params[p2 + SkyFormShape.CX] - x, q.params[p2 + SkyFormShape.CZ] - z)
                                        - rad - q.params[p2 + SkyFormShape.RADIUS];
                                ok = t > 0.0 && t <= 12.0 && gap <= 6.0;
                            }
                        }
                    }
                }
                v[7] += ok ? 0 : 1;
            } else if (r.type[i] == RegionLayout.SHAFT) {
                v[8]++;
                int x = (int) Math.round(r.params[o]);
                int z = (int) Math.round(r.params[o + 1]);
                int mouth = (int) Math.floor(Math.min(layout.oceanHeight(x, z, -10.0), r.params[o + 3])) - 1;
                boolean open = true;
                for (int y = (int) Math.ceil(r.params[o + 4]); y <= mouth && open; y++) {
                    open = layout.shaftDensity(x, y, z) < 0.0;
                }
                v[9] += open ? 0 : 1;
            }
        }
    }
}
