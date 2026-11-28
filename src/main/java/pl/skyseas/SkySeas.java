package pl.skyseas;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import pl.skyseas.command.SkySeasCommand;
import pl.skyseas.registry.SkySeasRegistries;

/**
 * Sky Seas: server-side world generator (floating islands above a deep ocean).
 *
 * <p>Server-only by design: no blocks, items, entities, network payloads or entries
 * in client-synced registries, so clients without this mod can join.
 */
@Mod(SkySeas.MOD_ID)
public final class SkySeas {
    public static final String MOD_ID = "skyseas";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SkySeas(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, pl.skyseas.params.ParamsConfig.SPEC);
        SkySeasRegistries.register(modBus);
        pl.skyseas.world.SkySeasWorldgen.register(modBus);
        pl.skyseas.structure.SkySeasStructures.register(modBus);
        NeoForge.EVENT_BUS.addListener(SkySeasCommand::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(SpawnHandler::onCreateSpawn);
        NeoForge.EVENT_BUS.addListener(pl.skyseas.world.WaterMobs::onSpawnPlacementCheck);
        NeoForge.EVENT_BUS.addListener(pl.skyseas.params.ParamsSnapshot::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(pl.skyseas.command.FlyThrough::onTickPre);
        NeoForge.EVENT_BUS.addListener(pl.skyseas.command.FlyThrough::onTickPost);
    }
}
