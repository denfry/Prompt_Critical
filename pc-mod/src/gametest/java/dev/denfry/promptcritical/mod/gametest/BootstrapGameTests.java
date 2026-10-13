package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.api.PromptCriticalApi;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Смоук-тесты каркаса M0 (06 §6.1): мод, API, конфиг и Create загружены. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BootstrapGameTests {
    private static final String EMPTY = "gametest/empty_3x3x3";

    private BootstrapGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void apiIsInstalled(GameTestHelper h) {
        h.assertTrue(PromptCriticalApi.get().apiVersion() == PromptCriticalApi.CURRENT_API_VERSION, "API version");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void serverConfigIsLoadedWithDefaults(GameTestHelper h) {
        h.assertTrue(ServerConfig.SPEC.isLoaded(), "server config loaded");
        h.assertTrue(ServerConfig.K_SLOW.get() > 0, "time.k_slow > 0");
        h.assertTrue(ServerConfig.W_PER_SU.get() > 0, "energy.w_per_su > 0");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void createIsLoaded(GameTestHelper h) {
        h.assertTrue(ModList.get().isLoaded("create"), "Create is loaded");
        h.succeed();
    }
}
