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
import pl.skyseas.density.LayoutAccess;
import pl.skyseas.density.SeabedHeightFunction;
import pl.skyseas.density.ZoneFactorFunction;
import pl.skyseas.layout.Layout;
import pl.skyseas.layout.RegionLayout;
import pl.skyseas.shape.ContinentShape;
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
                .then(Commands.literal("info").executes(SkySeasCommand::info)
                        .then(Commands.literal("cache").executes(SkySeasCommand::infoCache)))
                .then(Commands.literal("map")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(64, 32768))
                                .executes(ctx -> map(ctx, 0))
                                .then(Commands.argument("scale", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> map(ctx, IntegerArgumentType.getInteger(ctx, "scale"))))))
                .then(Commands.literal("bench")
                        .then(Commands.argument("chunks", IntegerArgumentType.integer(1, MAX_BENCH_CHUNKS))
                                .executes(ctx -> bench(ctx, false, false))
                                .then(Commands.literal("vanilla").executes(ctx -> bench(ctx, true, false)))
                                .then(Commands.literal("here").executes(ctx -> bench(ctx, false, true))
                                        .then(Commands.literal("vanilla").executes(ctx -> bench(ctx, true, true)))))));
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
        Layout layout = LayoutAccess.get(level.getChunkSource().randomState());
        if (layout != null) {
            RegionLayout r = layout.regionAt(pos.getX(), pos.getZ());
            int i = layout.islandAt(r, pos.getX(), pos.getZ());
            sb.append("\n  layout region: ").append(r.rx).append(',').append(r.rz)
                    .append(" (").append(r.count).append(" islands)");
            if (i >= 0) {
                String[] characters = {"lowland", "mixed", "highland", "mountainous", "caldera"};
                String character = characters[(int) r.params[i * r.paramStride + ContinentShape.CHARACTER]];
                sb.append(String.format(Locale.ROOT, "\n  island: %s continent %016x%s, centre %d,%d, edge %.0f, top %.1f, bottom %.1f",
                        character, r.id[i], (r.flags[i] & RegionLayout.FLAG_STARTER) != 0 ? " (starter)" : "", r.cx[i], r.cz[i],
                        layout.column(pos.getX(), pos.getZ(), Layout.EDGE), layout.column(pos.getX(), pos.getZ(), Layout.TOP),
                        layout.column(pos.getX(), pos.getZ(), Layout.BOTTOM)));
            } else {
                sb.append("\n  island: none (open sky)");
            }
        }

        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int infoCache(CommandContext<CommandSourceStack> ctx) {
        Layout layout = LayoutAccess.get(ctx.getSource().getLevel().getChunkSource().randomState());
        if (layout == null) {
            ctx.getSource().sendFailure(Component.literal("[Sky Seas] not a Sky Seas world"));
            return 0;
        }
        String msg = String.format(Locale.ROOT, "[Sky Seas] layout cache: %d/%d regions, %d computed, ~%.1f KB",
                layout.cache().size(), layout.params().layoutCacheRegions(), layout.cache().computed(),
                layout.cache().bytes() / 1024.0);
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    /** Scale 0 = pick the smallest that fits the image limit. */
    private static int map(CommandContext<CommandSourceStack> ctx, int scale) {
        CommandSourceStack source = ctx.getSource();
        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        if (scale == 0) {
            scale = Math.max(1, (2 * radius + LayoutMap.MAX_PIXELS - 1) / LayoutMap.MAX_PIXELS);
        }
        if (2 * radius / scale > LayoutMap.MAX_PIXELS) {
            source.sendFailure(Component.literal("[Sky Seas] image too large; max " + LayoutMap.MAX_PIXELS
                    + " px per side, use a larger scale"));
            return 0;
        }
        BlockPos pos = BlockPos.containing(source.getPosition());
        int s = scale;
        boolean started = LayoutMap.start(source.getLevel(), pos.getX(), pos.getZ(), radius, scale,
                msg -> source.sendSuccess(() -> Component.literal(msg), true));
        if (!started) {
            source.sendFailure(Component.literal("[Sky Seas] a map is already being rendered"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[Sky Seas] rendering map: radius " + radius + ", " + s
                + " blocks/px"), false);
        return 1;
    }

    /** here = area centred on the source position; otherwise the default far ocean area. */
    private static int bench(CommandContext<CommandSourceStack> ctx, boolean withVanilla, boolean here) {
        CommandSourceStack source = ctx.getSource();
        int chunks = IntegerArgumentType.getInteger(ctx, "chunks");
        int side = (int) Math.ceil(Math.sqrt(chunks));
        BlockPos pos = BlockPos.containing(source.getPosition());
        int originX = here ? (pos.getX() >> 4) - side / 2 : Bench.ORIGIN_CHUNK;
        int originZ = here ? (pos.getZ() >> 4) - side / 2 : Bench.ORIGIN_CHUNK;
        boolean started = Bench.start(source.getLevel(), chunks, withVanilla, originX, originZ,
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
