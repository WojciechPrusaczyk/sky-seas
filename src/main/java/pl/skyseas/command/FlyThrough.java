package pl.skyseas.command;

import java.util.Comparator;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * {@code /skyseas flythrough <ships> <speed> <seconds>}: the spec TPS test without players.
 * Each "ship" starts at the caller and flies straight out in its own direction at
 * {@code speed} blocks per second, holding a chunk ticket of the server view distance
 * around itself like a player, so terrain ahead is generated while the server ticks.
 * Reports the tick times and the TPS of the worst second. Main thread only.
 */
public final class FlyThrough {
    private static final TicketType<ChunkPos> TICKET = TicketType.create("skyseas_flythrough",
            Comparator.comparingLong(ChunkPos::toLong));
    private static final long BUDGET_NANOS = 50_000_000L;

    private static Run run;

    private FlyThrough() {
    }

    private static final class Run {
        ServerLevel level;
        Consumer<String> reply;
        int distance;
        double[] x;
        double[] z;
        double[] dx;
        double[] dz;
        ChunkPos[] held;
        int ticks;
        int total;
        long tickStart;
        long sumNanos;
        long maxNanos;
        int overBudget;
        long secondNanos;
        int secondTicks;
        double worstSecondTps = 20.0;
    }

    static boolean start(ServerLevel level, double x, double z, int ships, double speed, int seconds, Consumer<String> reply) {
        if (run != null) {
            return false;
        }
        Run r = new Run();
        r.level = level;
        r.reply = reply;
        r.distance = level.getServer().getPlayerList().getViewDistance();
        r.total = seconds * 20;
        r.x = new double[ships];
        r.z = new double[ships];
        r.dx = new double[ships];
        r.dz = new double[ships];
        r.held = new ChunkPos[ships];
        for (int i = 0; i < ships; i++) {
            double a = 2.0 * Math.PI * i / ships;
            r.x[i] = x;
            r.z[i] = z;
            r.dx[i] = Math.cos(a) * speed / 20.0;
            r.dz[i] = Math.sin(a) * speed / 20.0;
        }
        run = r;
        return true;
    }

    public static void onTickPre(ServerTickEvent.Pre event) {
        if (run != null) {
            run.tickStart = System.nanoTime();
        }
    }

    public static void onTickPost(ServerTickEvent.Post event) {
        Run r = run;
        if (r == null) {
            return;
        }
        long t = System.nanoTime() - r.tickStart;
        if (r.ticks > 0) {
            r.sumNanos += t;
            r.maxNanos = Math.max(r.maxNanos, t);
            if (t > BUDGET_NANOS) {
                r.overBudget++;
            }
            // A second of 20 ticks: TPS = 20 at most, less when the ticks took longer than a second.
            r.secondNanos += Math.max(t, BUDGET_NANOS);
            if (++r.secondTicks == 20) {
                r.worstSecondTps = Math.min(r.worstSecondTps, 20.0 * 1e9 / r.secondNanos);
                r.secondNanos = 0;
                r.secondTicks = 0;
            }
        }
        if (r.ticks++ >= r.total) {
            finish(r);
            return;
        }
        var chunks = r.level.getChunkSource();
        for (int i = 0; i < r.x.length; i++) {
            r.x[i] += r.dx[i];
            r.z[i] += r.dz[i];
            ChunkPos pos = new ChunkPos((int) Math.floor(r.x[i]) >> 4, (int) Math.floor(r.z[i]) >> 4);
            if (!pos.equals(r.held[i])) {
                chunks.addRegionTicket(TICKET, pos, r.distance, pos);
                if (r.held[i] != null) {
                    chunks.removeRegionTicket(TICKET, r.held[i], r.distance, r.held[i]);
                }
                r.held[i] = pos;
            }
        }
    }

    private static void finish(Run r) {
        for (ChunkPos pos : r.held) {
            if (pos != null) {
                r.level.getChunkSource().removeRegionTicket(TICKET, pos, r.distance, pos);
            }
        }
        run = null;
        int n = Math.max(1, r.ticks - 1);
        r.reply.accept(String.format(Locale.ROOT,
                "[Sky Seas] flythrough: %d ships, view distance %d, %d ticks: mean %.1f ms, max %.1f ms, %d over 50 ms, worst second %.1f TPS"
                        + "%n  ship 0 ended at %.0f %.0f",
                r.x.length, r.distance, n, r.sumNanos / 1e6 / n, r.maxNanos / 1e6, r.overBudget, r.worstSecondTps,
                r.x[0], r.z[0]));
    }
}
