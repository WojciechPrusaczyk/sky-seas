package pl.skyseas.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import pl.skyseas.SkySeas;
import pl.skyseas.density.LayoutAccess;
import pl.skyseas.density.SeabedHeightFunction;
import pl.skyseas.layout.Layout;
import pl.skyseas.structure.BelowSeabed;

/**
 * {@code /skyseas audit structures <radius>}: runs the structure starts stage (vanilla
 * {@code createStructures}, Lithostitched conditions and the Sky Seas mixins included) on
 * detached chunks in a square around the caller, without generating terrain, and checks
 * the spec rules per start: underground structures under the seabed with a roof of
 * {@link BelowSeabed#ROOF} blocks, surface structures far enough inside a sky island.
 * Counts per structure into a CSV. Runs on its own thread.
 */
final class StructureAudit {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final TagKey<Structure> UNDERGROUND = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(SkySeas.MOD_ID, "audit_underground"));
    private static final TagKey<Structure> INTERIOR = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(SkySeas.MOD_ID, "requires_island_interior"));
    private static final TagKey<Structure> VILLAGE = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.withDefaultNamespace("village"));

    private StructureAudit() {
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
                SkySeas.LOGGER.error("Structure audit failed", e);
                level.getServer().execute(() -> reply.accept("[Sky Seas] structure audit failed: " + e));
            } finally {
                RUNNING.set(false);
            }
        }, "skyseas-structure-audit");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    /** Per structure: count, rule failures, worst value, a position of the worst start. */
    private static final class Row {
        String rule = "-";
        int count;
        int failed;
        /** Underground structures reaching above the seabed (roof below 0). */
        int exposed;
        double worst = Double.MAX_VALUE;
        String where = "";
    }

    private static String run(ServerLevel level, int centerX, int centerZ, int radius) throws IOException {
        var random = level.getChunkSource().randomState();
        Layout layout = LayoutAccess.get(random);
        SeabedHeightFunction seabed = LayoutAccess.seabed(random);
        if (layout == null || seabed == null) {
            return "[Sky Seas] structure audit: not a Sky Seas world";
        }
        var registries = level.registryAccess();
        var structures = registries.registryOrThrow(Registries.STRUCTURE);
        var biomes = registries.registryOrThrow(Registries.BIOME);
        var state = level.getChunkSource().getGeneratorState();
        var generator = level.getChunkSource().getGenerator();
        var templates = level.getStructureManager();
        int c0x = (centerX - radius) >> 4;
        int c0z = (centerZ - radius) >> 4;
        int c1x = (centerX + radius) >> 4;
        int c1z = (centerZ + radius) >> 4;
        Map<String, Row> rows = new TreeMap<>();
        long t0 = System.nanoTime();
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                ProtoChunk chunk = new ProtoChunk(new ChunkPos(cx, cz), UpgradeData.EMPTY, level, biomes, null);
                generator.createStructures(registries, state, level.structureManager(), chunk, templates);
                for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
                    StructureStart start = e.getValue();
                    if (!start.isValid()) {
                        continue;
                    }
                    var holder = structures.wrapAsHolder(e.getKey());
                    String id = structures.getKey(e.getKey()).toString();
                    Row row = rows.computeIfAbsent(id, k -> new Row());
                    row.count++;
                    // The pieces themselves: the start box is padded for terrain adaptation.
                    BoundingBox box = BoundingBox.encapsulatingBoxes(start.getPieces().stream().map(p -> p.getBoundingBox()).toList()).orElseThrow();
                    double value;
                    boolean fail;
                    if (holder.is(UNDERGROUND)) {
                        // Roof: rock between the seabed and the top of each piece; the thinnest counts.
                        row.rule = "roof>=" + BelowSeabed.ROOF;
                        value = BelowSeabed.ROOF + BelowSeabed.maxTop(random, box, start.getPieces()) - box.maxY();
                        fail = value < BelowSeabed.ROOF;
                    } else if (holder.is(INTERIOR) || holder.is(VILLAGE)) {
                        int min = holder.is(VILLAGE) ? 32 : 24;
                        row.rule = "edge>=" + min;
                        var p = start.getPieces().get(0).getBoundingBox().getCenter();
                        value = layout.column(p.getX(), p.getZ(), Layout.EDGE);
                        fail = value < min;
                    } else {
                        continue;
                    }
                    if (fail) {
                        row.failed++;
                        if (value < 0.0 && holder.is(UNDERGROUND)) {
                            row.exposed++;
                        }
                    }
                    if (value < row.worst) {
                        row.worst = value;
                        row.where = box.getCenter().getX() + " " + box.getCenter().getZ() + " y " + box.minY() + ".." + box.maxY();
                    }
                }
            }
        }
        StringBuilder csv = new StringBuilder("structure,rule,count,failed,exposed,worst,worst_at\n");
        StringBuilder msg = new StringBuilder(String.format(Locale.ROOT,
                "[Sky Seas] structure audit: radius %d, %d chunks, %.1f s", radius,
                (c1x - c0x + 1) * (c1z - c0z + 1), (System.nanoTime() - t0) / 1e9));
        for (Map.Entry<String, Row> e : rows.entrySet()) {
            Row r = e.getValue();
            String worst = r.worst == Double.MAX_VALUE ? "-" : String.format(Locale.ROOT, "%.0f", r.worst);
            csv.append(e.getKey()).append(',').append(r.rule).append(',').append(r.count).append(',')
                    .append(r.failed).append(',').append(r.exposed).append(',').append(worst).append(',').append(r.where).append('\n');
            msg.append(String.format(Locale.ROOT, "%n  %-34s %4d  %-9s failed %3d  exposed %3d  worst %s %s",
                    e.getKey(), r.count, r.rule, r.failed, r.exposed, worst, r.failed > 0 ? "at " + r.where : ""));
        }
        Path dir = level.getServer().getServerDirectory().resolve("skyseas").resolve("audit");
        Files.createDirectories(dir);
        Path file = dir.resolve("structures-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv");
        Files.writeString(file, csv);
        return msg.append("\n  csv: ").append(file.toAbsolutePath()).toString();
    }
}
