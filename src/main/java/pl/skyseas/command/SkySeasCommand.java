package pl.skyseas.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
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
import pl.skyseas.shape.OceanShape;
import pl.skyseas.shape.ShaftShape;
import pl.skyseas.shape.SeabedColumn;
import pl.skyseas.shape.SkyFormShape;

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
                .then(locateCommand())
                .then(Commands.literal("map")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(64, 32768))
                                .executes(ctx -> map(ctx, 0))
                                .then(Commands.argument("scale", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> map(ctx, IntegerArgumentType.getInteger(ctx, "scale"))))))
                .then(Commands.literal("census")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, 8)).executes(SkySeasCommand::census)))
                .then(Commands.literal("audit")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(64, 32768)).executes(SkySeasCommand::audit))
                        .then(Commands.literal("structures")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(64, 32768))
                                        .executes(SkySeasCommand::auditStructures))))
                .then(Commands.literal("bench")
                        .then(Commands.argument("chunks", IntegerArgumentType.integer(1, MAX_BENCH_CHUNKS))
                                .executes(ctx -> bench(ctx, false, false))
                                .then(Commands.literal("vanilla").executes(ctx -> bench(ctx, true, false)))
                                .then(Commands.literal("here").executes(ctx -> bench(ctx, false, true))
                                        .then(Commands.literal("vanilla").executes(ctx -> bench(ctx, true, true)))))));
    }

    /** Locate targets: item type, or -1 for any ocean island; extra filter in {@link #matches}. */
    private static final String[] LOCATE = {"continent", "elongated", "crescent", "hook", "branched", "composite",
            "rock_bridge", "elevated", "massif", "hanging_spire", "archipelago", "hallelujah", "tower", "sky_island",
            "sky_bridge", "ocean_island", "large_island", "atoll", "seamount",
            "volcano", "sea_spire", "chain", "cenote", "blue_hole", "river", "river_mouth"};
    private static final int LOCATE_MAX_RING = 12;

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> locateCommand() {
        var node = Commands.literal("locate");
        for (String target : LOCATE) {
            node.then(Commands.literal(target).executes(ctx -> locate(ctx, target)));
        }
        return node;
    }

    private static boolean matches(RegionLayout r, int i, String target) {
        int off = r.paramOffset[i];
        if (r.type[i] == RegionLayout.CONTINENT) {
            switch (target) {
                case "elongated", "crescent", "hook", "branched", "composite" -> {
                    return target.equals(ContinentShape.SILHOUETTE_NAMES[(int) r.params[off + ContinentShape.SILHOUETTE]]);
                }
                case "rock_bridge" -> {
                    return bridge(r, i) >= 0;
                }
                case "elevated" -> {
                    return r.params[off + ContinentShape.ELEVATED] > 0.0;
                }
                case "massif" -> {
                    return r.params[off + ContinentShape.MASSIF_PEAK] > 0.0;
                }
                case "river", "river_mouth" -> {
                    return r.params[off + ContinentShape.RIVER_COUNT] > 0;
                }
                case "hanging_spire" -> {
                    return r.params[off + ContinentShape.SPIRE_COUNT] > 0.0;
                }
                default -> {
                }
            }
        }
        return switch (target) {
            case "continent" -> r.type[i] == RegionLayout.CONTINENT;
            case "archipelago" -> r.type[i] == RegionLayout.ARCHIPELAGO;
            case "hallelujah" -> r.type[i] == RegionLayout.HALLELUJAH;
            case "tower" -> r.type[i] == RegionLayout.TOWER;
            case "sky_island" -> r.type[i] == RegionLayout.SKY_ISLAND;
            case "sky_bridge" -> r.type[i] == RegionLayout.SKY_BRIDGE;
            case "ocean_island" -> r.type[i] == RegionLayout.OCEAN_ISLAND;
            case "large_island" -> r.type[i] == RegionLayout.OCEAN_ISLAND && r.params[off + OceanShape.SIZE_CLASS] == 2;
            case "atoll" -> r.type[i] == RegionLayout.OCEAN_ISLAND && r.params[off + OceanShape.RING] > 0.0;
            case "seamount" -> r.type[i] == RegionLayout.SEAMOUNT;
            case "volcano" -> r.type[i] == RegionLayout.VOLCANO;
            case "sea_spire" -> r.type[i] == RegionLayout.SEA_SPIRE;
            case "chain" -> r.type[i] == RegionLayout.CHAIN;
            case "cenote" -> r.type[i] == RegionLayout.SHAFT && ShaftShape.cenote(r.params, off);
            case "blue_hole" -> r.type[i] == RegionLayout.SHAFT && !ShaftShape.cenote(r.params, off);
            default -> false;
        };
    }

    /** Nearest column within 100 blocks of x, z that holds a rock bridge (outside the island outlines), else x, z. */
    private static int[] bridgeColumn(Layout layout, int x, int z) {
        for (int ring = 0; ring <= 50; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int cx = x + 2 * dx;
                    int cz = z + 2 * dz;
                    if (layout.detailFloor(cx & ~3, cz & ~3) < Layout.NO_SPIRE && layout.column(cx, cz, Layout.EDGE) <= 0.0) {
                        for (int y = 319; y > (int) layout.params().continentMinBottomY(); y--) {
                            if (layout.detailDensity(cx, y, cz) > 0.0) {
                                return new int[]{cx, cz};
                            }
                        }
                    }
                }
            }
        }
        return new int[]{x, z};
    }

    /**
     * Where to stand at x, z: on the island (on the land above a hanging spire), on a rock
     * bridge, or on an ocean form or the sea.
     */
    private static int landingY(Layout layout, int x, int z) {
        if (layout.column(x, z, Layout.EDGE) > 0.0) {
            return (int) Math.ceil(layout.column(x, z, Layout.TOP)) + 2;
        }
        for (int y = 319; y > (int) layout.params().continentMinBottomY(); y--) {
            if (layout.detailDensity(x, y, z) > 0.0) {
                return y + 2;
            }
        }
        return (int) Math.ceil(Math.max(63.0, layout.oceanHeight(x, z, -10.0))) + 2;
    }

    /** Offset of the first rock bridge link of a continent, or -1. */
    private static int bridge(RegionLayout r, int i) {
        int off = r.paramOffset[i];
        for (int l = 0; l < (int) r.params[off + ContinentShape.LINK_COUNT]; l++) {
            int o = off + ContinentShape.LINK0 + ContinentShape.LINK_STRIDE * l;
            if (r.params[o + ContinentShape.L_BRIDGE] > 0.0) {
                return o;
            }
        }
        return -1;
    }

    /** Where to point for a found item: the bridge, massif or spire itself, else the centre. */
    private static double[] point(RegionLayout r, int i, String target) {
        int off = r.paramOffset[i];
        double[] q = r.params;
        return switch (target) {
            case "rock_bridge" -> {
                int o = bridge(r, i);
                yield new double[]{0.5 * (q[o] + q[o + 2]), 0.5 * (q[o + 1] + q[o + 3])};
            }
            case "massif" -> new double[]{q[off + ContinentShape.MASSIF_X], q[off + ContinentShape.MASSIF_Z]};
            case "hanging_spire" -> new double[]{q[off + ContinentShape.SPIRE0], q[off + ContinentShape.SPIRE0 + 1]};
            case "river", "river_mouth" -> {
                // First river: a point a third down its course, or its mouth.
                int o = off + ContinentShape.RIVER0;
                int k = target.equals("river") ? (int) q[o] / 3 : (int) q[o] - 1;
                int p = o + ContinentShape.RIVER_HEADER + ContinentShape.RIVER_POINT * k;
                yield new double[]{q[p], q[p + 1]};
            }
            default -> new double[]{r.cx[i], r.cz[i]};
        };
    }

    /** Nearest item of a type, from the layout alone, searching rings of regions outwards. */
    private static int locate(CommandContext<CommandSourceStack> ctx, String target) {
        CommandSourceStack source = ctx.getSource();
        Layout layout = LayoutAccess.get(source.getLevel().getChunkSource().randomState());
        if (layout == null) {
            source.sendFailure(Component.literal("[Sky Seas] not a Sky Seas world"));
            return 0;
        }
        BlockPos pos = BlockPos.containing(source.getPosition());
        int size = layout.params().layoutRegionSize();
        int prx = Math.floorDiv(pos.getX(), size);
        int prz = Math.floorDiv(pos.getZ(), size);
        double best = Double.MAX_VALUE;
        int bx = 0;
        int bz = 0;
        for (int ring = 0; ring <= LOCATE_MAX_RING; ring++) {
            if (best < (ring - 1) * (double) size) {
                break;
            }
            for (int rx = prx - ring; rx <= prx + ring; rx++) {
                for (int rz = prz - ring; rz <= prz + ring; rz++) {
                    if (Math.max(Math.abs(rx - prx), Math.abs(rz - prz)) != ring) {
                        continue;
                    }
                    RegionLayout r = layout.region(rx, rz);
                    for (int i = 0; i < r.count; i++) {
                        if (r.owns(i, size) && matches(r, i, target)) {
                            double[] at = point(r, i, target);
                            double d = Math.hypot(at[0] - pos.getX(), at[1] - pos.getZ());
                            if (d < best) {
                                best = d;
                                bx = (int) Math.round(at[0]);
                                bz = (int) Math.round(at[1]);
                            }
                        }
                    }
                }
            }
        }
        if (best == Double.MAX_VALUE) {
            source.sendFailure(Component.literal("[Sky Seas] no " + target + " within " + LOCATE_MAX_RING * size + " blocks"));
            return 0;
        }
        if (target.equals("rock_bridge")) {
            // The link is stored in the outline's warped space: find the bridge's real columns nearby.
            int[] at = bridgeColumn(layout, bx, bz);
            bx = at[0];
            bz = at[1];
        }
        int ty = landingY(layout, bx, bz);
        String tp = String.format(Locale.ROOT, "/tp @s %d %d %d", bx, ty, bz);
        Component coords = ComponentUtils.wrapInSquareBrackets(Component.literal(bx + ", " + ty + ", " + bz))
                .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tp))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Teleport: " + tp))));
        Component msg = Component.literal("[Sky Seas] nearest " + target + ": ").append(coords)
                .append(String.format(Locale.ROOT, " (%.0f blocks)", best));
        source.sendSuccess(() -> msg, false);
        return 1;
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

        var router = level.getChunkSource().randomState().router();
        DensityFunction.SinglePointContext here = new DensityFunction.SinglePointContext(pos.getX(), pos.getY(), pos.getZ());
        sb.append(String.format(Locale.ROOT, "\n  climate: temperature %.3f, vegetation %.3f, continents %.3f, erosion %.3f, "
                        + "ridges %.3f, depth %.3f", router.temperature().compute(here), router.vegetation().compute(here),
                router.continents().compute(here), router.erosion().compute(here), router.ridges().compute(here),
                router.depth().compute(here)));
        DensityFunction finalDensity = router.finalDensity();
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
            if (i >= 0 && r.type[i] != RegionLayout.CONTINENT) {
                int off = r.paramOffset[i];
                sb.append(String.format(Locale.ROOT, "\n  island: %s%s, centre %d,%d, radius %.0f, top %.0f, depth %.0f, %d spires",
                        RegionLayout.TYPE_NAMES[r.type[i]], (r.flags[i] & RegionLayout.FLAG_GUARANTEED) != 0 ? " (guaranteed)" : "",
                        r.cx[i], r.cz[i], r.params[off + SkyFormShape.RADIUS], r.params[off + SkyFormShape.TOP],
                        r.params[off + SkyFormShape.DEPTH], (int) r.params[off + SkyFormShape.SPIRE_COUNT]));
            } else if (i >= 0) {
                String[] characters = {"lowland", "mixed", "highland", "mountainous", "caldera"};
                int off = r.paramOffset[i];
                String character = characters[(int) r.params[off + ContinentShape.CHARACTER]];
                String silhouette = ContinentShape.SILHOUETTE_NAMES[(int) r.params[off + ContinentShape.SILHOUETTE]];
                sb.append(String.format(Locale.ROOT, "\n  island: %s %s%s continent %016x%s, centre %d,%d, base %.0f, tilt %.0f, "
                                + "summit limit %.0f, %d members, %d spires\n  edge %.0f, top %.1f, bottom %.1f",
                        character, silhouette, r.params[off + ContinentShape.ELEVATED] > 0.0 ? " elevated" : "",
                        r.id[i], (r.flags[i] & RegionLayout.FLAG_STARTER) != 0 ? " (starter)" : "", r.cx[i], r.cz[i],
                        r.params[off + ContinentShape.BASE_Y], r.params[off + ContinentShape.TILT],
                        r.params[off + ContinentShape.MAX_Y], (int) r.params[off + ContinentShape.MEMBER_COUNT],
                        (int) r.params[off + ContinentShape.SPIRE_COUNT],
                        layout.column(pos.getX(), pos.getZ(), Layout.EDGE), layout.column(pos.getX(), pos.getZ(), Layout.TOP),
                        layout.column(pos.getX(), pos.getZ(), Layout.BOTTOM)));
            } else {
                sb.append("\n  island: none (open sky)");
            }
            double point = layout.climateColumn(pos.getX(), pos.getZ(), Layout.CLIMATE_TEMPERATURE);
            double type = layout.climateColumn(pos.getX(), pos.getZ(), Layout.CLIMATE_TYPE);
            sb.append(String.format(Locale.ROOT, "\n  climate reach: %s, sky Y %.0f..%.0f%s, warm sea %.2f",
                    type == Layout.NO_CLIMATE ? "open sea" : RegionLayout.TYPE_NAMES[(int) type],
                    layout.climateColumn(pos.getX(), pos.getZ(), Layout.CLIMATE_BOTTOM),
                    layout.climateColumn(pos.getX(), pos.getZ(), Layout.CLIMATE_TOP),
                    point == Layout.NO_CLIMATE ? "" : String.format(Locale.ROOT, ", cluster point t %.2f v %.2f e %.2f", point,
                            layout.climateColumn(pos.getX(), pos.getZ(), Layout.CLIMATE_VEGETATION),
                            layout.climateColumn(pos.getX(), pos.getZ(), Layout.CLIMATE_EROSION)),
                    layout.warmBias(pos.getX(), pos.getZ())));
            int o = layout.oceanAt(r, pos.getX(), pos.getZ(), -1000.0);
            if (o >= 0) {
                int off = r.paramOffset[o];
                String kind = RegionLayout.TYPE_NAMES[r.type[o]];
                if (r.type[o] == RegionLayout.OCEAN_ISLAND) {
                    kind = new String[]{"small", "medium", "large"}[(int) r.params[off + OceanShape.SIZE_CLASS]]
                            + (r.params[off + OceanShape.RING] > 0.0 ? " atoll" : " ocean island");
                }
                sb.append(String.format(Locale.ROOT, "\n  ocean form: %s, centre %d,%d, radius %.0f",
                        kind, r.cx[o], r.cz[o], r.params[off + OceanShape.RADIUS]));
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

    private static int census(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            String msg = Census.run(source.getLevel(), BlockPos.containing(source.getPosition()),
                    IntegerArgumentType.getInteger(ctx, "radius"));
            source.sendSuccess(() -> Component.literal(msg), true);
            return 1;
        } catch (java.io.IOException e) {
            source.sendFailure(Component.literal("[Sky Seas] census failed: " + e));
            return 0;
        }
    }

    private static int audit(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        BlockPos pos = BlockPos.containing(source.getPosition());
        if (!LayoutAudit.start(source.getLevel(), pos.getX(), pos.getZ(), radius,
                msg -> source.sendSuccess(() -> Component.literal(msg), true))) {
            source.sendFailure(Component.literal("[Sky Seas] an audit is already running"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[Sky Seas] auditing: radius " + radius), false);
        return 1;
    }

    private static int auditStructures(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        BlockPos pos = BlockPos.containing(source.getPosition());
        if (!StructureAudit.start(source.getLevel(), pos.getX(), pos.getZ(), radius,
                msg -> source.sendSuccess(() -> Component.literal(msg), true))) {
            source.sendFailure(Component.literal("[Sky Seas] a structure audit is already running"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[Sky Seas] auditing structures: radius " + radius), false);
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
