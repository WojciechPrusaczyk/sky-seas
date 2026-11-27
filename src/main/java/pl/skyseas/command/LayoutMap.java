package pl.skyseas.command;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.DensityFunction;
import pl.skyseas.SkySeas;
import pl.skyseas.config.WorldParams;
import pl.skyseas.density.LayoutAccess;
import pl.skyseas.density.SeabedHeightFunction;
import pl.skyseas.density.ZoneFactorFunction;
import pl.skyseas.layout.Layout;
import pl.skyseas.layout.ZoneField;

/**
 * {@code /skyseas map}: top-down PNG computed from the layout alone (no chunk
 * generation). Ocean shaded by zone, ocean islands and shallows from the seabed height, sky islands coloured by top height (green, brown,
 * white peaks), small islands and bridges light green, island reach outlined, region grid and spawn marked; magenta
 * points (every 50 blocks) fail the sky visibility rule. Runs on its own thread.
 */
final class LayoutMap {
    static final int MAX_PIXELS = 4096;
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    /** Ocean colours: Haven, Borderlands, Wilds, Edge. */
    private static final int[] OCEAN = {0x4A90C8, 0x3A78B0, 0x2B5E92, 0x1E4470};
    private static final int REACH = 0x16324F;
    private static final int GRID = 0x5F7F9F;
    private static final int SPAWN = 0xE03030;
    private static final int SAND = 0xE2D49A;
    /** Small islands and bridges (per-block forms). */
    private static final int DETAIL = 0xB8E07A;
    /** Grid point that fails the sky visibility rule. */
    private static final int BLIND = 0xFF30C0;
    private static final int VISIBILITY_STEP = 50;

    private LayoutMap() {
    }

    static boolean start(ServerLevel level, int centerX, int centerZ, int radius, int scale, Consumer<String> reply) {
        if (!RUNNING.compareAndSet(false, true)) {
            return false;
        }
        Thread thread = new Thread(() -> {
            try {
                String msg = render(level, centerX, centerZ, radius, scale);
                level.getServer().execute(() -> reply.accept(msg));
            } catch (RuntimeException | IOException e) {
                SkySeas.LOGGER.error("Map failed", e);
                level.getServer().execute(() -> reply.accept("[Sky Seas] map failed: " + e));
            } finally {
                RUNNING.set(false);
            }
        }, "skyseas-map");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    private static String render(ServerLevel level, int centerX, int centerZ, int radius, int scale) throws IOException {
        Layout layout = LayoutAccess.get(level.getChunkSource().randomState());
        if (layout == null) {
            return "[Sky Seas] map: not a Sky Seas world";
        }
        WorldParams p = layout.params();
        DensityFunction router = level.getChunkSource().randomState().router().finalDensity();
        ZoneFactorFunction zone = RouterLookup.find(router, ZoneFactorFunction.class);
        SeabedHeightFunction seabed = RouterLookup.find(router, SeabedHeightFunction.class);
        int size = 2 * radius / scale;
        int x0 = centerX - radius;
        int z0 = centerZ - radius;
        double[] zoneBorders = new double[p.zoneRadii().length];
        for (int k = 0; k < zoneBorders.length; k++) {
            zoneBorders[k] = ZoneField.factorAtDistance(p, p.zoneRadii()[k]);
        }

        long t0 = System.nanoTime();
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        int land = 0;
        int points = 0;
        int blind = 0;
        for (int py = 0; py < size; py++) {
            int z = z0 + py * scale;
            for (int px = 0; px < size; px++) {
                int x = x0 + px * scale;
                int rgb;
                double h;
                double edge = layout.column(x, z, Layout.EDGE);
                if (edge > 0.0) {
                    rgb = landColor(p, layout.column(x, z, Layout.TOP));
                    land++;
                } else if (layout.detailFloor(x & ~3, z & ~3) < Layout.NO_SPIRE) {
                    rgb = DETAIL;
                } else if (edge > -p.islandReachMargin()) {
                    rgb = REACH;
                } else if (seabed != null && (h = seabed.compute(new DensityFunction.SinglePointContext(x, 0, z))) > 30.0) {
                    rgb = oceanFormColor(h);
                    if (h >= 63.0) {
                        land++;
                    }
                } else {
                    double f = zone == null ? ZoneField.factorAtDistance(p, Math.hypot(x, z))
                            : zone.compute(new DensityFunction.SinglePointContext(x, 0, z));
                    int k = 0;
                    while (k < zoneBorders.length && f >= zoneBorders[k]) {
                        k++;
                    }
                    rgb = OCEAN[k];
                }
                if (Math.floorMod(x, p.layoutRegionSize()) < scale || Math.floorMod(z, p.layoutRegionSize()) < scale) {
                    rgb = blend(rgb, GRID);
                }
                if (Math.floorMod(x, VISIBILITY_STEP) < scale && Math.floorMod(z, VISIBILITY_STEP) < scale) {
                    points++;
                    if (!layout.skyVisible(x, z, 250.0, 500.0)) {
                        blind++;
                        rgb = BLIND;
                    }
                }
                if ((Math.abs(x) < 3 * scale && Math.abs(z) < 24 * scale) || (Math.abs(z) < 3 * scale && Math.abs(x) < 24 * scale)) {
                    rgb = SPAWN;
                }
                img.setRGB(px, py, rgb);
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;

        Path dir = level.getServer().getServerDirectory().resolve("skyseas").resolve("maps");
        Files.createDirectories(dir);
        Path file = dir.resolve("map-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".png");
        ImageIO.write(img, "png", file.toFile());
        if (seabed != null) {
            ImageIO.write(seabedImage(seabed, x0, z0, size, scale), "png",
                    dir.resolve(file.getFileName().toString().replace("map-", "seabed-")).toFile());
            biomeImage(level, layout, seabed, x0, z0, size, scale, dir.resolve(file.getFileName().toString().replace("map-", "biomes-")));
        }
        return String.format(java.util.Locale.ROOT,
                "[Sky Seas] map: %dx%d px, %d blocks/px, centre %d,%d, land %.1f%%, sky visibility %.2f%% of %d points, "
                        + "%d ms, layout regions cached %d\n  png: %s",
                size, size, scale, centerX, centerZ, 100.0 * land / ((double) size * size),
                points == 0 ? 100.0 : 100.0 * (points - blind) / points, points, ms,
                layout.cache().size(), file.toAbsolutePath());
    }

    /**
     * Surface biomes as the player sees them from above: on sky islands (top of the highest
     * island in reach), on ocean land, else in the water at Y 40. Colours are hashed from the
     * biome id; a legend with pixel counts goes to a .txt next to the png.
     */
    private static void biomeImage(ServerLevel level, Layout layout, SeabedHeightFunction seabed, int x0, int z0, int size,
                                   int scale, Path file) throws IOException {
        var source = level.getChunkSource().getGenerator().getBiomeSource();
        var sampler = level.getChunkSource().randomState().sampler();
        java.util.Map<String, int[]> legend = new java.util.TreeMap<>();
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < size; py++) {
            int z = z0 + py * scale;
            for (int px = 0; px < size; px++) {
                int x = x0 + px * scale;
                double top = layout.climateColumn(x, z, Layout.CLIMATE_TOP);
                int y;
                if (top > Layout.NO_TOP) {
                    y = (int) top + 1;
                } else {
                    double h = seabed.compute(new DensityFunction.SinglePointContext(x, 0, z));
                    y = h >= 63.0 ? (int) h + 1 : 40;
                }
                String id = source.getNoiseBiome(x >> 2, y >> 2, z >> 2, sampler).unwrapKey()
                        .map(k -> k.location().toString()).orElse("?");
                int[] e = legend.get(id);
                if (e == null) {
                    int hash = id.hashCode() * 0x9E3779B1;
                    e = new int[]{(hash >>> 8) & 0xFFFFFF, 0};
                    legend.put(id, e);
                }
                e[1]++;
                img.setRGB(px, py, e[0]);
            }
        }
        ImageIO.write(img, "png", file.toFile());
        StringBuilder sb = new StringBuilder();
        for (var en : legend.entrySet()) {
            sb.append(String.format(java.util.Locale.ROOT, "#%06X %7d %s%n", en.getValue()[0], en.getValue()[1], en.getKey()));
        }
        Files.writeString(Path.of(file.toString().replace(".png", ".txt")), sb.toString());
    }

    /** Seabed heights (deep blue to sand) with hillshade from the north-west; land above sea level grey. */
    private static BufferedImage seabedImage(SeabedHeightFunction seabed, int x0, int z0, int size, int scale) {
        double[] h = new double[(size + 1) * (size + 1)];
        for (int py = 0; py <= size; py++) {
            for (int px = 0; px <= size; px++) {
                h[py * (size + 1) + px] = seabed.compute(new DensityFunction.SinglePointContext(x0 + px * scale, 0, z0 + py * scale));
            }
        }
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                double v = h[py * (size + 1) + px];
                double slope = (2.0 * v - h[py * (size + 1) + px + 1] - h[(py + 1) * (size + 1) + px]) / scale;
                int rgb = v >= 63.0 ? 0x909090 : lerpColor(0x0A1E5A, 0xD8C890, (v + 50.0) / 80.0);
                img.setRGB(px, py, slope > 0 ? lerpColor(rgb, 0xFFFFFF, Math.min(0.5, slope * 0.4))
                        : lerpColor(rgb, 0x000000, Math.min(0.6, -slope * 0.4)));
            }
        }
        return img;
    }

    /** Ocean islands (sand to green) and shallows (turquoise, lighter when shallower). */
    private static int oceanFormColor(double h) {
        if (h >= 63.0) {
            return h < 64.5 ? SAND : lerpColor(0x5FA046, 0x7A6A4A, (h - 64.5) / 30.0);
        }
        return lerpColor(0x2E86A8, 0x6FD3D6, (h - 30.0) / 33.0);
    }

    /** Green lowlands, brown slopes, white peaks. */
    private static int landColor(WorldParams p, double top) {
        double t = (top - p.continentBaseYMin()) / (p.continentPeakMaxY() - p.continentBaseYMin());
        if (t < 0.4) {
            return lerpColor(0x4E9A3A, 0x8A9A48, t / 0.4);
        }
        if (t < 0.8) {
            return lerpColor(0x8A9A48, 0x7A5A3A, (t - 0.4) / 0.4);
        }
        return lerpColor(0x7A5A3A, 0xF0F0F0, Math.min(1.0, (t - 0.8) / 0.2));
    }

    private static int lerpColor(int a, int b, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int blend(int a, int b) {
        return lerpColor(a, b, 0.5);
    }
}
