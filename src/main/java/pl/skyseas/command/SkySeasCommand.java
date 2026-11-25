package pl.skyseas.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /skyseas} command tree (permission level 2). Uses only vanilla argument
 * types, because the command tree is sent to clients that do not have this mod.
 */
public final class SkySeasCommand {
    private SkySeasCommand() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("skyseas")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("info").executes(SkySeasCommand::info)));
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        String settings = generator instanceof NoiseBasedChunkGenerator noise
                ? noise.generatorSettings().getRegisteredName()
                : generator.getClass().getSimpleName();
        String biome = level.getBiome(pos).getRegisteredName();
        String dimension = level.dimension().location().toString();

        source.sendSuccess(() -> Component.literal(
                "[Sky Seas] pos " + pos.toShortString()
                        + "\n  dimension: " + dimension
                        + "\n  generator: " + settings
                        + "\n  biome: " + biome
                        + "\n  layout: not implemented"), false);
        return 1;
    }
}
