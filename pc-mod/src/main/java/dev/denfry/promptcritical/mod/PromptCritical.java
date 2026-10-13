package dev.denfry.promptcritical.mod;

import com.mojang.logging.LogUtils;
import dev.denfry.promptcritical.api.ApiHolder;
import dev.denfry.promptcritical.api.PromptCriticalApi;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(PromptCritical.MOD_ID)
public final class PromptCritical {
    public static final String MOD_ID = "promptcritical";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PromptCritical(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
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
