package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.SeasonMods;
import dev.denfry.promptcritical.sim.environment.SeasonProvider;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Время года от модов сезонов (16 §16.10.2): без мода или без его API — встроенный календарь; доля года по солнечным
 * терминам Ecliptic Seasons ложится на наш календарь (весна, лето, осень, зима с 0, ¼, ½, ¾).
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SeasonModsGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";

    private SeasonModsGameTests() {
    }

    @GameTest(template = LARGE)
    public static void seasonsFallBackToTheCalendarWithoutAModApi(GameTestHelper h) {
        var level = h.getLevel();
        SeasonProvider calendar = new SeasonProvider.Calendar(24);
        h.assertTrue(SeasonMods.choose(level, calendar, false, false) == calendar, "no seasons mod: the built-in calendar");
        // в тестовом мире модов сезонов нет: «загружен», но API не находится — тоже календарь, без исключения
        h.assertTrue(SeasonMods.choose(level, calendar, false, true) == calendar, "Ecliptic Seasons without its API: the calendar");
        h.assertTrue(SeasonMods.choose(level, calendar, true, true) == calendar, "both mods without their APIs: the calendar");

        h.assertTrue(Math.abs(SeasonMods.eclipticYearFraction(0, 7, 0, 0)) < 1e-12, "beginning of spring is the start of the year");
        double[] starts = {0, 0.25, 0.5, 0.75};
        SeasonProvider.Season[] seasons = SeasonProvider.Season.values();
        for (int s = 0; s < 4; s++) {
            double f = SeasonMods.eclipticYearFraction(6 * s, 7, 0, 0);
            h.assertTrue(Math.abs(f - starts[s]) < 1e-12, "term " + 6 * s + " starts at " + starts[s] + ", got " + f);
            h.assertValueEqual(SeasonProvider.season(SeasonMods.eclipticYearFraction(6 * s + 5, 7, 6, 0.99)), seasons[s],
                    "the last day of term " + (6 * s + 5) + " is still " + seasons[s]);
        }
        double mid = SeasonMods.eclipticYearFraction(3, 7, 3, 0.5);
        h.assertTrue(Math.abs(mid - (3 * 7 + 3.5) / (24.0 * 7)) < 1e-12, "days inside a term count, got " + mid);
        h.succeed();
    }
}
