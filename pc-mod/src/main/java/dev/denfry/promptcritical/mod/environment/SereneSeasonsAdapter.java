package dev.denfry.promptcritical.mod.environment;

import com.mojang.logging.LogUtils;
import dev.denfry.promptcritical.sim.environment.SeasonProvider;
import java.lang.reflect.Method;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * Время года из Serene Seasons, если мод стоит: доля года = тики цикла / длина цикла его состояния сезона.
 * Без жёсткой зависимости — через отражение ({@code sereneseasons.api.season.SeasonHelper#getSeasonState},
 * {@code ISeasonState#getSeasonCycleTicks}, {@code #getCycleDuration}); если API не нашлось или сменилось, один
 * раз пишем в журнал и до конца сессии считаем по встроенному календарю.
 */
final class SereneSeasonsAdapter implements SeasonProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Method stateOf, cycleTicks, cycleDuration;
    private static boolean broken;

    private final Level level;
    private final SeasonProvider fallback;

    private SereneSeasonsAdapter(Level level, SeasonProvider fallback) {
        this.level = level;
        this.fallback = fallback;
    }

    /** Serene Seasons для уровня, если его API на месте; иначе {@code fallback}. Загружен ли мод, решает {@link SeasonMods}. */
    static SeasonProvider of(Level level, SeasonProvider fallback) {
        if (broken) return fallback;
        if (stateOf == null) {
            try {
                Class<?> helper = Class.forName("sereneseasons.api.season.SeasonHelper");
                stateOf = helper.getMethod("getSeasonState", Level.class);
                Class<?> state = Class.forName("sereneseasons.api.season.ISeasonState");
                cycleTicks = state.getMethod("getSeasonCycleTicks");
                cycleDuration = state.getMethod("getCycleDuration");
            } catch (ReflectiveOperationException | LinkageError e) {
                disable(e);
                return fallback;
            }
        }
        return new SereneSeasonsAdapter(level, fallback);
    }

    @Override
    public double yearFraction(long gameTime) {
        if (broken) return fallback.yearFraction(gameTime);
        try {
            Object state = stateOf.invoke(null, level);
            double ticks = ((Number) cycleTicks.invoke(state)).doubleValue();
            double length = ((Number) cycleDuration.invoke(state)).doubleValue();
            if (!(length > 0)) return fallback.yearFraction(gameTime);
            double f = ticks / length;
            return f - Math.floor(f);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disable(e);
            return fallback.yearFraction(gameTime);
        }
    }

    private static void disable(Throwable e) {
        broken = true;
        LOGGER.warn("Serene Seasons API not usable, seasons follow the built-in calendar: {}", e.toString());
    }
}
