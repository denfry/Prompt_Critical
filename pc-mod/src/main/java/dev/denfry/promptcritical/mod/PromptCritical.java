package dev.denfry.promptcritical.mod;

import com.mojang.logging.LogUtils;
import dev.denfry.promptcritical.api.ApiHolder;
import dev.denfry.promptcritical.api.PromptCriticalApi;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.foundry.FoundryBinding;
import dev.denfry.promptcritical.mod.binding.SimConfigs;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.mod.registry.PcFeatures;
import dev.denfry.promptcritical.mod.registry.PcLootFunctions;
import dev.denfry.promptcritical.sim.mining.MineSubsystem;
import dev.denfry.promptcritical.sim.mining.DrainageConfig;
import dev.denfry.promptcritical.sim.mining.MiningConfig;
import dev.denfry.promptcritical.mod.client.PcClient;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.data.PcDataGen;
import dev.denfry.promptcritical.mod.registry.PcBlockEntities;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcCreativeTabs;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(PromptCritical.MOD_ID)
public final class PromptCritical {
    public static final String MOD_ID = "promptcritical";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PromptCritical(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        PcBlocks.BLOCKS.register(modBus);
        PcItems.ITEMS.register(modBus);
        PcBlockEntities.BLOCK_ENTITIES.register(modBus);
        PcDataComponents.COMPONENTS.register(modBus);
        PcLootFunctions.FUNCTIONS.register(modBus);
        PcFeatures.FEATURES.register(modBus);
        PcFluids.TYPES.register(modBus);
        PcFluids.FLUIDS.register(modBus);
        PcCreativeTabs.TABS.register(modBus);
        SimBindings.register(modBus);
        FoundryBinding.register(modBus);
        dev.denfry.promptcritical.mod.boiler.BoilerBinding.register(modBus);
        dev.denfry.promptcritical.mod.mine.MineBlockEntities.register(modBus);
        SimBindings.registerSubsystem(level -> new MineSubsystem(
                SimConfigs.fromServerConfig().section(MiningConfig.class, MiningConfig.defaults()),
                SimConfigs.fromServerConfig().section(DrainageConfig.class, DrainageConfig.defaults())));
        dev.denfry.promptcritical.mod.mine.MineHoisting.register(modBus);
        dev.denfry.promptcritical.mod.mine.MineLongwall.register(modBus);
        modBus.addListener(PcDataGen::gather);
        if (FMLEnvironment.dist == Dist.CLIENT) PcClient.register(modBus);
        ApiHolder.install(new ApiImpl());
        LOGGER.info("Prompt Critical initialized (API v{})", PromptCriticalApi.CURRENT_API_VERSION);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static final class ApiImpl implements PromptCriticalApi {
        @Override
        public int apiVersion() {
            return CURRENT_API_VERSION;
        }
    }
}
