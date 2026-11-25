package pl.skyseas.gametest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pl.skyseas.SkySeas;

/**
 * E0 acceptance: the {@code skyseas:sky_seas} noise settings must generate exactly the
 * same terrain as {@code minecraft:overworld}.
 *
 * <p>Compares biomes, noise and surface stages chunk by chunk. These stages are
 * deterministic per chunk, unlike full world saves, where vanilla feature placement
 * across chunk borders depends on scheduling order (two vanilla runs with the same
 * seed differ in overlapping ore blobs and kelp). Carvers and features depend only on
 * biomes, which this test covers.
 *
 * <p>Structures are not compared: both generators get the test level's structure
 * manager, so structure starts (and terrain adaptation around them) come from the
 * test level, not from the settings under test.
 *
 * <p>Remove or invert this test in E1, when the generator starts to differ from vanilla.
 */
@GameTestHolder(SkySeas.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PresetEquivalenceTests {
    private static final ResourceKey<NoiseGeneratorSettings> SKY_SEAS = ResourceKey.create(
            Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath(SkySeas.MOD_ID, "sky_seas"));
    private static final int GRID = 8;
    private static final int STRIDE_CHUNKS = 61;
    private static final long[] SEEDS = {20261125L, -4172144997902289642L};

    private PresetEquivalenceTests() {
    }

    @GameTest(template = "empty")
    public static void skySeasTerrainMatchesOverworld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RegistryAccess registries = level.registryAccess();
        Registry<NoiseGeneratorSettings> settings = registries.registryOrThrow(Registries.NOISE_SETTINGS);
        Registry<Biome> biomes = registries.registryOrThrow(Registries.BIOME);
        MultiNoiseBiomeSource biomeSource = MultiNoiseBiomeSource.createFromPreset(registries
                .registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));

        Holder<NoiseGeneratorSettings> vanillaSettings = settings.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        Holder<NoiseGeneratorSettings> skySeasSettings = settings.getHolderOrThrow(SKY_SEAS);
        NoiseBasedChunkGenerator vanilla = new NoiseBasedChunkGenerator(biomeSource, vanillaSettings);
        NoiseBasedChunkGenerator skySeas = new NoiseBasedChunkGenerator(biomeSource, skySeasSettings);

        int chunks = 0;
        for (long seed : SEEDS) {
            RandomState vanillaRandom = RandomState.create(
                    vanillaSettings.value(), registries.lookupOrThrow(Registries.NOISE), seed);
            RandomState skySeasRandom = RandomState.create(
                    skySeasSettings.value(), registries.lookupOrThrow(Registries.NOISE), seed);
            for (int i = 0; i < GRID; i++) {
                for (int j = 0; j < GRID; j++) {
                    ChunkPos pos = new ChunkPos((i - GRID / 2) * STRIDE_CHUNKS, (j - GRID / 2) * STRIDE_CHUNKS);
                    ProtoChunk a = generate(level, vanilla, vanillaRandom, biomes, biomeSource, seed, pos);
                    ProtoChunk b = generate(level, skySeas, skySeasRandom, biomes, biomeSource, seed, pos);
                    String diff = firstDifference(a, b);
                    if (diff != null) {
                        helper.fail("seed " + seed + ", chunk " + pos + ": " + diff);
                        return;
                    }
                    chunks++;
                }
            }
        }
        SkySeas.LOGGER.info("PresetEquivalenceTests: {} chunks identical to minecraft:overworld", chunks);
        helper.succeed();
    }

    private static ProtoChunk generate(ServerLevel level, NoiseBasedChunkGenerator generator, RandomState random,
                                       Registry<Biome> biomes, MultiNoiseBiomeSource biomeSource, long seed,
                                       ChunkPos pos) {
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, level, biomes, null);
        Blender blender = Blender.empty();
        // ProtoChunk does not advance its own status; vanilla does it in ChunkStep.
        await(level, generator.createBiomes(random, blender, level.structureManager(), chunk));
        chunk.setPersistedStatus(ChunkStatus.BIOMES);
        await(level, generator.fillFromNoise(blender, random, level.structureManager(), chunk));
        chunk.setPersistedStatus(ChunkStatus.NOISE);
        BiomeManager biomeManager = new BiomeManager(
                (x, y, z) -> biomeSource.getNoiseBiome(x, y, z, random.sampler()), BiomeManager.obfuscateSeed(seed));
        generator.buildSurface(chunk, new WorldGenerationContext(generator, level), random,
                level.structureManager(), biomeManager, biomes, blender);
        chunk.setPersistedStatus(ChunkStatus.SURFACE);
        return chunk;
    }

    /**
     * Waits on the server thread while running the level's chunk task queue. A plain
     * join() deadlocks: the worker computing the noise chunk asks the server thread for
     * structure references (ServerChunkCache.getChunk off-thread) and waits for it.
     * MinecraftServer.managedBlock is not enough either: it polls chunk tasks only while
     * the current tick has time left.
     */
    private static <T> T await(ServerLevel level, CompletableFuture<T> future) {
        while (!future.isDone()) {
            if (!level.getChunkSource().pollTask()) {
                LockSupport.parkNanos(100_000L);
            }
        }
        return future.join();
    }

    private static String firstDifference(ProtoChunk a, ProtoChunk b) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minX = a.getPos().getMinBlockX();
        int minZ = a.getPos().getMinBlockZ();
        for (int y = a.getMinBuildHeight(); y < a.getMaxBuildHeight(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    pos.set(minX + x, y, minZ + z);
                    if (a.getBlockState(pos) != b.getBlockState(pos)) {
                        return "block at " + pos.toShortString() + ": " + a.getBlockState(pos) + " vs " + b.getBlockState(pos);
                    }
                    if ((x & 3) == 0 && (y & 3) == 0 && (z & 3) == 0
                            && !a.getNoiseBiome(pos.getX() >> 2, y >> 2, pos.getZ() >> 2)
                            .equals(b.getNoiseBiome(pos.getX() >> 2, y >> 2, pos.getZ() >> 2))) {
                        return "biome at " + pos.toShortString();
                    }
                }
            }
        }
        return null;
    }
}
