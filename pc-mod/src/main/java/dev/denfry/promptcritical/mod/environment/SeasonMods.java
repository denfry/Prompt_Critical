package dev.denfry.promptcritical.mod.environment;

import com.mojang.logging.LogUtils;
import dev.denfry.promptcritical.mod.compat.CompatSwitch;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.sim.environment.SeasonProvider;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * Источник времени года уровня (16 §16.10.2): Serene Seasons, иначе Ecliptic Seasons, иначе встроенный календарь.
 * Оба мода сразу — берётся Serene Seasons и один раз пишется предупреждение. Ключ {@code mod.compat.seasons} = off
 * оставляет календарь при любых модах.
 */
public final class SeasonMods {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean warnedBoth;

    private SeasonMods() {
    }

    static SeasonProvider forLevel(Level level, SeasonProvider fallback) {
        return choose(level, fallback, CompatSwitch.active(ServerConfig.COMPAT_SEASONS, "sereneseasons"),
                CompatSwitch.active(ServerConfig.COMPAT_SEASONS, "eclipticseasons"));
    }

    /**
     * Выбор при известных загруженных модах; без API мода — {@code fallback}. Отдельно от {@link #forLevel}, чтобы
     * GameTest проверил запасной путь без модов сезонов.
     */
    public static SeasonProvider choose(Level level, SeasonProvider fallback, boolean serene, boolean ecliptic) {
        if (serene) {
            if (ecliptic && !warnedBoth) {
                warnedBoth = true;
                LOGGER.warn("Both Serene Seasons and Ecliptic Seasons are loaded: seasons follow Serene Seasons");
            }
            return SereneSeasonsAdapter.of(level, fallback);
        }
        return ecliptic ? EclipticSeasonsAdapter.of(level, fallback) : fallback;
    }

    /** Доля года по солнечному термину Ecliptic Seasons (для GameTest формулы адаптера). */
    public static double eclipticYearFraction(int term, int daysPerTerm, int dayInTerm, double dayFraction) {
        return EclipticSeasonsAdapter.yearFraction(term, daysPerTerm, dayInTerm, dayFraction);
    }
}
