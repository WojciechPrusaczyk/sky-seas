package pl.skyseas.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import pl.skyseas.config.WorldParams;
import pl.skyseas.density.SeabedHeightFunction;
import pl.skyseas.density.ZoneFactorFunction;
import pl.skyseas.shape.SeabedColumn;

/**
 * {@code /skyseas} command tree (permission level 2). Uses only vanilla argument
 * types, because the command tree is sent to clients that do not have this mod.
 */
public final class SkySeasCommand {
    private static final int MAX_BENCH_CHUNKS = 4096;

    private SkySeasCommand() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("skyseas")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("info").executes(SkySeasCommand::info))
                .then(Commands.literal("bench")
                        .then(Commands.argument("chunks", IntegerArgumentType.integer(1, MAX_BENCH_CHUNKS))
                                .executes(ctx -> bench(ctx, false))
                                .then(Commands.literal("vanilla").executes(ctx -> bench(ctx, true))))));
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        String settings = generator instanceof NoiseBasedChunkGenerator noise
                ? noise.generatorSettings().getRegisteredName()
                : generator.getClass().getSimpleName();

        StringBuilder sb = new StringBuilder("[Sky Seas] pos ").append(pos.toShortString())
                .append("\n  dimension: ").append(level.dimension().location())
                .append("\n  generator: ").append(settings)
                .append(" (generatorVersion ").append(WorldParams.GENERATOR_VERSION).append(')')
                .append("\n  biome: ").append(level.getBiome(pos).getRegisteredName());

        DensityFunction finalDensity = level.getChunkSource().randomState().router().finalDensity();
        SeabedHeightFunction seabed = RouterLookup.find(finalDensity, SeabedHeightFunction.class);
        ZoneFactorFunction zone = RouterLookup.find(finalDensity, ZoneFactorFunction.class);
        if (seabed != null && zone != null) {
            DensityFunction.SinglePointContext point = new DensityFunction.SinglePointContext(pos.getX(), pos.getY(), pos.getZ());
            double h = seabed.compute(point);
            sb.append(String.format(Locale.ROOT, "\n  seabed Y: %.1f, rock thickness: %.1f, zone factor: %.3f",
                    h, SeabedColumn.rockThickness(WorldParams.DEFAULTS, h), zone.compute(point)));
        } else {
            sb.append("\n  seabed: not a Sky Seas generator");
        }
        sb.append("\n  layout: not implemented");

        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int bench(CommandContext<CommandSourceStack> ctx, boolean withVanilla) {
        CommandSourceStack source = ctx.getSource();
        int chunks = IntegerArgumentType.getInteger(ctx, "chunks");
        boolean started = Bench.start(source.getLevel(), chunks, withVanilla,
                msg -> source.sendSuccess(() -> Component.literal(msg), true));
        if (!started) {
            source.sendFailure(Component.literal("[Sky Seas] a bench is already running"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[Sky Seas] bench started: " + chunks + " chunks"
                + (withVanilla ? " vs vanilla" : "") + ", results will follow"), false);
        return 1;
    }
}
