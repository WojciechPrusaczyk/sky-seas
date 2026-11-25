package pl.skyseas.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import pl.skyseas.SkySeas;
import pl.skyseas.config.WorldParams;

/**
 * {@code /skyseas bench}: times the biomes, noise and surface stages of detached chunks
 * (see {@link StagedChunkGen}) for the sky_seas settings and optionally for vanilla
 * overworld settings on the same seed and chunks.
 *
 * <p>Method: {@link #WARMUP_ROUNDS} unrecorded rounds for the JIT, then
 * {@link #MEASURED_ROUNDS} recorded rounds; with vanilla, the order of the two generators
 * alternates every round. The result per stage is the median over recorded rounds of the
 * mean time per chunk. Runs on its own thread; results go to chat and to a CSV file.
 */
public final class Bench {
    static final int WARMUP_ROUNDS = 3;
    static final int MEASURED_ROUNDS = 7;
    /** Bench area origin (chunk coordinates): far beyond the zone plateau, away from spawn. */
    private static final int ORIGIN_CHUNK = 1250;

    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private Bench() {
    }

    /** Starts a bench run in the background. Returns false if one is already running. */
    static boolean start(ServerLevel level, int chunks, boolean withVanilla, Consumer<String> reply) {
        if (!RUNNING.compareAndSet(false, true)) {
            return false;
        }
        Thread thread = new Thread(() -> {
            try {
                String summary = run(level, chunks, withVanilla);
                level.getServer().execute(() -> reply.accept(summary));
            } catch (RuntimeException | IOException e) {
                SkySeas.LOGGER.error("Bench failed", e);
                level.getServer().execute(() -> reply.accept("[Sky Seas] bench failed: " + e));
            } finally {
                RUNNING.set(false);
            }
        }, "skyseas-bench");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    private static String run(ServerLevel level, int chunks, boolean withVanilla) throws IOException {
        long seed = level.getSeed();
        List<String> names = new ArrayList<>();
        List<StagedChunkGen> gens = new ArrayList<>();
        names.add("sky_seas");
        gens.add(StagedChunkGen.create(level, StagedChunkGen.SKY_SEAS, seed));
        if (withVanilla) {
            names.add("vanilla");
            gens.add(StagedChunkGen.create(level, NoiseGeneratorSettings.OVERWORLD, seed));
        }

        ChunkPos[] positions = positions(chunks);
        int rounds = WARMUP_ROUNDS + MEASURED_ROUNDS;
        // [generator][round][stage] total nanoseconds
        long[][][] totals = new long[gens.size()][rounds][StagedChunkGen.STAGE_NAMES.length];
        long[] nanos = new long[StagedChunkGen.STAGE_NAMES.length];

        for (int round = 0; round < rounds; round++) {
            for (int k = 0; k < gens.size(); k++) {
                int g = (round % 2 == 0) ? k : gens.size() - 1 - k;
                for (ChunkPos pos : positions) {
                    gens.get(g).generate(pos, nanos);
                    for (int s = 0; s < nanos.length; s++) {
                        totals[g][round][s] += nanos[s];
                    }
                }
            }
        }

        Path csv = writeCsv(level, names, totals, chunks);
        return summary(names, totals, chunks, seed, csv);
    }

    private static ChunkPos[] positions(int chunks) {
        int side = (int) Math.ceil(Math.sqrt(chunks));
        ChunkPos[] out = new ChunkPos[chunks];
        for (int i = 0; i < chunks; i++) {
            out[i] = new ChunkPos(ORIGIN_CHUNK + i % side, ORIGIN_CHUNK + i / side);
        }
        return out;
    }

    /** Median over measured rounds of the mean milliseconds per chunk. */
    static double medianMsPerChunk(long[][] roundStageTotals, int stage, int chunks) {
        double[] values = new double[MEASURED_ROUNDS];
        for (int r = 0; r < MEASURED_ROUNDS; r++) {
            values[r] = roundStageTotals[WARMUP_ROUNDS + r][stage] / 1.0e6 / chunks;
        }
        Arrays.sort(values);
        int mid = values.length / 2;
        return values.length % 2 == 1 ? values[mid] : (values[mid - 1] + values[mid]) / 2.0;
    }

    private static String summary(List<String> names, long[][][] totals, int chunks, long seed, Path csv) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "[Sky Seas] bench: %d chunks, seed %d, %d warmup + %d measured rounds, "
                + "median ms/chunk (generatorVersion %d)", chunks, seed, WARMUP_ROUNDS, MEASURED_ROUNDS,
                WorldParams.GENERATOR_VERSION));
        for (int s = 0; s < StagedChunkGen.STAGE_NAMES.length; s++) {
            sb.append(String.format(Locale.ROOT, "%n  %-8s", StagedChunkGen.STAGE_NAMES[s]));
            for (int g = 0; g < names.size(); g++) {
                sb.append(String.format(Locale.ROOT, "  %s %.3f", names.get(g), medianMsPerChunk(totals[g], s, chunks)));
            }
            if (names.size() == 2) {
                double ratio = medianMsPerChunk(totals[0], s, chunks) / medianMsPerChunk(totals[1], s, chunks);
                sb.append(String.format(Locale.ROOT, "  ratio %.2fx", ratio));
            }
        }
        sb.append("\n  csv: ").append(csv);
        return sb.toString();
    }

    private static Path writeCsv(ServerLevel level, List<String> names, long[][][] totals, int chunks)
            throws IOException {
        Path dir = level.getServer().getServerDirectory().resolve("skyseas").resolve("bench");
        Files.createDirectories(dir);
        Path file = dir.resolve("bench-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv");
        StringBuilder sb = new StringBuilder("generator,generator_version,round,warmup,stage,chunks,total_ms,ms_per_chunk\n");
        for (int g = 0; g < names.size(); g++) {
            for (int r = 0; r < totals[g].length; r++) {
                for (int s = 0; s < StagedChunkGen.STAGE_NAMES.length; s++) {
                    double ms = totals[g][r][s] / 1.0e6;
                    sb.append(String.format(Locale.ROOT, "%s,%d,%d,%b,%s,%d,%.3f,%.4f%n", names.get(g),
                            WorldParams.GENERATOR_VERSION, r, r < WARMUP_ROUNDS, StagedChunkGen.STAGE_NAMES[s],
                            chunks, ms, ms / chunks));
                }
            }
        }
        Files.writeString(file, sb);
        return file.toAbsolutePath();
    }
}
