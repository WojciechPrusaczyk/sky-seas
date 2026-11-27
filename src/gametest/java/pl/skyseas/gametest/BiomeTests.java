package pl.skyseas.gametest;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pl.skyseas.SkySeas;
import pl.skyseas.command.RouterLookup;
import pl.skyseas.command.StagedChunkGen;
import pl.skyseas.density.LayoutAccess;
import pl.skyseas.density.SeabedHeightFunction;
import pl.skyseas.layout.Layout;
import pl.skyseas.layout.RegionLayout;

/**
 * E4 acceptance on the real overworld multi_noise source with our router: land biomes on
 * sky islands, ocean biomes in open water, deep dark only under thick rock, warm ocean
 * around the guaranteed island near spawn. Samples biomes only (no chunk generation).
 */
@GameTestHolder(SkySeas.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BiomeTests {
    private static final long[] SEEDS = {20261125L, -4172144997902289642L};
    private static final int HALF = 4000;
    private static final int STRIDE = 48;
    /** Deep dark wants depth 1.1 (110 blocks at the default scale); the nearest-point pick may go a little lower. */
    private static final int DEEP_DARK_MIN_ROCK = 80;
    /** Thematic clusters: samples on their islands that fall in the cluster's most common biome. */
    private static final double MIN_DOMINANT_SHARE = 0.6;

    private BiomeTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 4000)
    public static void biomesFollowLayers(GameTestHelper helper) {
        for (long seed : SEEDS) {
            String error = check(helper, seed);
            if (error != null) {
                helper.fail("seed " + seed + ": " + error);
                return;
            }
        }
        helper.succeed();
    }

    private static String check(GameTestHelper helper, long seed) {
        StagedChunkGen gen = StagedChunkGen.create(helper.getLevel(), StagedChunkGen.SKY_SEAS, seed);
        Layout layout = LayoutAccess.get(gen.randomState());
        MultiNoiseBiomeSource source = gen.biomeSource();
        Climate.Sampler sampler = gen.randomState().sampler();
        SeabedHeightFunction seabed = RouterLookup.find(gen.randomState().router().finalDensity(), SeabedHeightFunction.class);
        Map<String, Integer> land = new HashMap<>();
        Map<String, Integer> sea = new HashMap<>();
        int islands = 0;
        int water = 0;
        int deepDark = 0;
        int warmSamples = 0;
        Map<Float, Map<String, Integer>> clusters = new HashMap<>();
        for (int x = -HALF; x < HALF; x += STRIDE) {
            for (int z = -HALF; z < HALF; z += STRIDE) {
                double h = seabed.compute(new DensityFunction.SinglePointContext(x, 0, z));
                RegionLayout r = layout.regionAt(x, z);
                int i = layout.nearestIsland(r, x, z);
                if (i >= 0 && layout.edge(r, i, x, z) > 6.0) {
                    int top = (int) Math.floor(layout.column(x, z, Layout.TOP));
                    Holder<Biome> b = biome(source, sampler, x, top + 2, z);
                    if (b.is(BiomeTags.IS_OCEAN)) {
                        return "ocean biome " + name(b) + " on a sky island (" + RegionLayout.TYPE_NAMES[r.type[i]] + ", bottom "
                                + layout.column(x, z, Layout.BOTTOM) + ", climate type " + layout.climateColumn(x, z, Layout.CLIMATE_TYPE)
                                + " bottom " + layout.climateColumn(x, z, Layout.CLIMATE_BOTTOM) + " top "
                                + layout.climateColumn(x, z, Layout.CLIMATE_TOP) + ", seabed " + h + ") at " + x + " " + (top + 2) + " " + z;
                    }
                    land.merge(name(b), 1, Integer::sum);
                    float point = r.climate[3 * i];
                    if (!Float.isNaN(point)) {
                        clusters.computeIfAbsent(point, k -> new HashMap<>()).merge(name(b), 1, Integer::sum);
                    }
                    islands++;
                } else if (layout.climateColumn(x, z, Layout.CLIMATE_TYPE) == Layout.NO_CLIMATE && h < 40.0) {
                    Holder<Biome> b = biome(source, sampler, x, 52, z);
                    if (!b.is(BiomeTags.IS_OCEAN)) {
                        return "land biome " + name(b) + " in open water at " + x + " 52 " + z;
                    }
                    sea.merge(name(b), 1, Integer::sum);
                    water++;
                    if (layout.warmBias(x, z) >= 1.0) {
                        warmSamples++;
                        if (!b.is(Biomes.WARM_OCEAN)) {
                            return "no warm ocean next to the guaranteed island at " + x + " " + z + ": " + name(b);
                        }
                    }
                }
                for (int y = -64; y < 320; y += 8) {
                    Holder<Biome> b = biome(source, sampler, x, y, z);
                    if (!b.is(Biomes.DEEP_DARK)) {
                        continue;
                    }
                    deepDark++;
                    double skyTop = layout.climateColumn(x, z, Layout.CLIMATE_TOP);
                    double top = y > h && skyTop > Layout.NO_TOP ? skyTop : h;
                    if (top - y < DEEP_DARK_MIN_ROCK) {
                        return "deep dark at " + x + " " + y + " " + z + " under only " + (top - y) + " blocks of rock";
                    }
                }
            }
        }
        SkySeas.LOGGER.info("BiomeTests seed {}: {} island samples {}, {} water samples {}, deep dark {}, warm samples {}",
                seed, islands, top(land), water, top(sea), deepDark, warmSamples);
        int clusterSamples = 0;
        int dominant = 0;
        for (Map<String, Integer> c : clusters.values()) {
            int n = 0;
            int best = 0;
            for (int v : c.values()) {
                n += v;
                best = Math.max(best, v);
            }
            if (n >= 5) {
                clusterSamples += n;
                dominant += best;
            }
        }
        double share = clusterSamples == 0 ? 1.0 : (double) dominant / clusterSamples;
        SkySeas.LOGGER.info("BiomeTests seed {}: {} clusters, dominant biome share {}", seed, clusters.size(), String.format("%.2f", share));
        if (share < MIN_DOMINANT_SHARE) {
            return "clusters without a dominant biome: share " + share;
        }
        if (islands == 0 || water == 0) {
            return "no samples (" + islands + " islands, " + water + " water)";
        }
        // The fine grid may miss the warm disc; the JUnit test covers its presence.
        return null;
    }

    private static Holder<Biome> biome(MultiNoiseBiomeSource source, Climate.Sampler sampler, int x, int y, int z) {
        return source.getNoiseBiome(x >> 2, y >> 2, z >> 2, sampler);
    }

    private static String name(Holder<Biome> b) {
        return b.unwrapKey().map(k -> k.location().getPath()).orElse("?");
    }

    private static String top(Map<String, Integer> counts) {
        StringBuilder s = new StringBuilder();
        counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(12)
                .forEach(e -> s.append(e.getKey()).append('=').append(e.getValue()).append(' '));
        return s.toString().trim();
    }
}
