package pl.skyseas.params;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import pl.skyseas.SkySeas;
import pl.skyseas.config.WorldParams;

/**
 * Per-world frozen parameters (spec "Konfiguracja i parametry zamrożone per świat"). Before
 * the levels load, {@code <world>/skyseas/params.json} is read; without it, the config
 * parameters are written there together with the mod and generator versions. Differences
 * between the snapshot and the config, and a different generator version, are logged as
 * warnings. {@code allowParamChange} overwrites the snapshot with the config.
 */
public final class ParamsSnapshot {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ParamsSnapshot() {
    }

    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        Path file = event.getServer().getWorldPath(LevelResource.ROOT).resolve("skyseas").resolve("params.json");
        WorldParams config = ParamsConfig.params();
        try {
            WorldParams.setCurrent(resolve(file, config));
        } catch (IOException | RuntimeException e) {
            // Without a readable snapshot the world would silently change: refuse to start.
            throw new IllegalStateException("[Sky Seas] cannot read or write " + file, e);
        }
    }

    static WorldParams resolve(Path file, WorldParams config) throws IOException {
        if (Files.exists(file) && !ParamsConfig.ALLOW_PARAM_CHANGE.get()) {
            JsonObject saved = GSON.fromJson(Files.readString(file), JsonObject.class);
            WorldParams frozen = GSON.fromJson(saved.get("params"), WorldParams.class);
            int version = saved.get("generatorVersion").getAsInt();
            if (version != WorldParams.GENERATOR_VERSION) {
                SkySeas.LOGGER.warn("[Sky Seas] world generated with generatorVersion {}, this mod has {}: new chunks may not match old ones",
                        version, WorldParams.GENERATOR_VERSION);
            }
            if (!GSON.toJsonTree(frozen).equals(GSON.toJsonTree(config))) {
                SkySeas.LOGGER.warn("[Sky Seas] config parameters differ from the world's frozen snapshot {}; using the snapshot"
                        + " (set allowParamChange = true to overwrite it)", file);
            }
            return frozen;
        }
        if (Files.exists(file)) {
            SkySeas.LOGGER.warn("[Sky Seas] allowParamChange: overwriting the frozen parameters in {}", file);
        }
        JsonObject out = new JsonObject();
        out.addProperty("modVersion", ModList.get().getModContainerById(SkySeas.MOD_ID)
                .map(m -> m.getModInfo().getVersion().toString()).orElse("?"));
        out.addProperty("generatorVersion", WorldParams.GENERATOR_VERSION);
        out.add("params", GSON.toJsonTree(config));
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(out));
        return config;
    }
}
