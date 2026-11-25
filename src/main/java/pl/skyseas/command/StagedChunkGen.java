package pl.skyseas.command;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;
import pl.skyseas.SkySeas;

/**
 * Generates detached chunks through the biomes, noise and surface stages, optionally
 * timing each stage. Used by {@code /skyseas bench} and the GameTests.
 *
 * <p>Chunks never touch the level: the noise chunk is created up front with an empty
 * beardifier (no structures), so the generator does not look up structure references
 * in the world. That also avoids a deadlock when called from the server thread.
 */
public final class StagedChunkGen {
    public static final int BIOMES = 0;
    public static final int NOISE = 1;
    public static final int SURFACE = 2;
    public static final String[] STAGE_NAMES = {"biomes", "noise", "surface"};
    public static final ResourceKey<NoiseGeneratorSettings> SKY_SEAS = ResourceKey.create(
            Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath(SkySeas.MOD_ID, "sky_seas"));

    private final ServerLevel level;
    private final NoiseGeneratorSettings settings;
    private final NoiseBasedChunkGenerator generator;
    private final RandomState random;
    private final Registry<Biome> biomes;
    private final MultiNoiseBiomeSource biomeSource;
    private final long biomeZoomSeed;
    private final Aquifer.FluidPicker fluidPicker;

    private StagedChunkGen(ServerLevel level, Holder<NoiseGeneratorSettings> settings, long seed) {
        RegistryAccess registries = level.registryAccess();
        MultiNoiseBiomeSource biomeSource = MultiNoiseBiomeSource.createFromPreset(registries
                .registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        this.level = level;
        this.settings = settings.value();
        this.generator = new NoiseBasedChunkGenerator(biomeSource, settings);
        this.random = RandomState.create(this.settings, registries.lookupOrThrow(Registries.NOISE), seed);
        this.biomes = registries.registryOrThrow(Registries.BIOME);
        this.biomeSource = biomeSource;
        this.biomeZoomSeed = BiomeManager.obfuscateSeed(seed);
        this.fluidPicker = vanillaFluidPicker(this.settings);
    }

    /** Generator for the given noise settings with the overworld multi-noise biome source. */
    public static StagedChunkGen create(ServerLevel level, ResourceKey<NoiseGeneratorSettings> settings, long seed) {
        return new StagedChunkGen(level,
                level.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(settings), seed);
    }

    /**
     * Generates one chunk up to the surface stage.
     *
     * @param nanos if not null, receives the duration of each stage (indexed by stage constants)
     */
    public ProtoChunk generate(ChunkPos pos, long[] nanos) {
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, level, biomes, null);
        Blender blender = Blender.empty();

        long t0 = System.nanoTime();
        chunk.getOrCreateNoiseChunk(c -> NoiseChunk.forChunk(c, random,
                new Beardifier(new ObjectArrayList<Beardifier.Rigid>().iterator(),
                        new ObjectArrayList<net.minecraft.world.level.levelgen.structure.pools.JigsawJunction>().iterator()),
                settings, fluidPicker, blender));
        generator.createBiomes(random, blender, level.structureManager(), chunk).join();
        // ProtoChunk does not advance its own status; vanilla does it in ChunkStep.
        chunk.setPersistedStatus(ChunkStatus.BIOMES);
        long t1 = System.nanoTime();
        generator.fillFromNoise(blender, random, level.structureManager(), chunk).join();
        chunk.setPersistedStatus(ChunkStatus.NOISE);
        long t2 = System.nanoTime();
        BiomeManager biomeManager = biomeManager(chunk);
        generator.buildSurface(chunk, new WorldGenerationContext(generator, level), random,
                level.structureManager(), biomeManager, biomes, blender);
        chunk.setPersistedStatus(ChunkStatus.SURFACE);
        long t3 = System.nanoTime();

        if (nanos != null) {
            nanos[BIOMES] = t1 - t0;
            nanos[NOISE] = t2 - t1;
            nanos[SURFACE] = t3 - t2;
        }
        return chunk;
    }

    /**
     * Biomes as the real generator sees them in a WorldGenRegion: stored chunk biomes
     * inside the chunk; outside (biome zoom near the edges) from the biome source.
     */
    private BiomeManager biomeManager(ProtoChunk chunk) {
        int minQx = QuartPos.fromBlock(chunk.getPos().getMinBlockX());
        int minQz = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
        return new BiomeManager((qx, qy, qz) -> {
            int dx = qx - minQx;
            int dz = qz - minQz;
            return dx >= 0 && dx < 4 && dz >= 0 && dz < 4
                    ? chunk.getNoiseBiome(qx, qy, qz)
                    : biomeSource.getNoiseBiome(qx, qy, qz, random.sampler());
        }, biomeZoomSeed);
    }

    public RandomState randomState() {
        return random;
    }

    /** Copy of the private NoiseBasedChunkGenerator.createFluidPicker (1.21.1). */
    private static Aquifer.FluidPicker vanillaFluidPicker(NoiseGeneratorSettings settings) {
        Aquifer.FluidStatus lava = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
        int seaLevel = settings.seaLevel();
        Aquifer.FluidStatus sea = new Aquifer.FluidStatus(seaLevel, settings.defaultFluid());
        return (x, y, z) -> y < Math.min(-54, seaLevel) ? lava : sea;
    }
}
