package dev.denfry.promptcritical.mod.environment;

import com.mojang.logging.LogUtils;
import dev.denfry.promptcritical.sim.environment.SeasonProvider;
import java.lang.reflect.Method;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * Время года из Ecliptic Seasons (16 §16.10.2), если мод стоит: год — 24 солнечных термина по
 * {@code getLastingDaysOfEachTerm} суток, начиная с {@code BEGINNING_OF_SPRING}. Весна, лето, осень и зима у мода
 * начинаются с терминов 0, 6, 12 и 18 — ровно на 0, ¼, ½ и ¾ нашей доли года. Без жёсткой зависимости — через
 * отражение ({@code com.teamtea.eclipticseasons.api.EclipticSeasonsApi#getInstance}, {@code #getSolarTerm},
 * {@code #getLastingDaysOfEachTerm}, {@code #getDayInTerm}); если API не нашлось или сменилось, один раз пишем в журнал
 * и до конца сессии считаем по встроенному календарю. Измерение без сезонов у мода (термин {@code NONE}) тоже идёт по
 * календарю.
 */
final class EclipticSeasonsAdapter implements SeasonProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Солнечных терминов в году. */
    static final int TERMS = 24;
    private static Method instance, solarTerm, lastingDays, dayInTerm;
    private static boolean broken;

    private final Level level;
    private final SeasonProvider fallback;

    private EclipticSeasonsAdapter(Level level, SeasonProvider fallback) {
        this.level = level;
        this.fallback = fallback;
    }

    /** Ecliptic Seasons для уровня, если его API на месте; иначе {@code fallback}. Загружен ли мод, решает вызывающий. */
    static SeasonProvider of(Level level, SeasonProvider fallback) {
        if (broken) return fallback;
        if (instance == null) {
            try {
                Class<?> api = Class.forName("com.teamtea.eclipticseasons.api.EclipticSeasonsApi");
                instance = api.getMethod("getInstance");
                solarTerm = api.getMethod("getSolarTerm", Level.class);
                lastingDays = api.getMethod("getLastingDaysOfEachTerm", Level.class);
                dayInTerm = api.getMethod("getDayInTerm", Level.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                disable(e);
                return fallback;
            }
        }
        return new EclipticSeasonsAdapter(level, fallback);
    }

    /**
     * Доля года от начала весны по термину {@code term} (0 — {@code BEGINNING_OF_SPRING}), его длине
     * {@code daysPerTerm}, суткам внутри термина и доле суток.
     */
    static double yearFraction(int term, int daysPerTerm, int dayInTerm, double dayFraction) {
        double days = (double) term * daysPerTerm + Math.max(0, Math.min(daysPerTerm - 1, dayInTerm)) + dayFraction;
        double f = days / ((double) TERMS * daysPerTerm);
        return f - Math.floor(f);
    }

    @Override
    public double yearFraction(long gameTime) {
        if (broken) return fallback.yearFraction(gameTime);
        try {
            Object api = instance.invoke(null);
            Object term = solarTerm.invoke(api, level);
            int days = ((Number) lastingDays.invoke(api, level)).intValue();
            if (!(term instanceof Enum<?> t) || t.ordinal() >= TERMS || days < 1) return fallback.yearFraction(gameTime);
            int day = ((Number) dayInTerm.invoke(api, level)).intValue();
            return yearFraction(t.ordinal(), days, day, Math.floorMod(level.getDayTime(), TICKS_PER_DAY) / (double) TICKS_PER_DAY);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disable(e);
            return fallback.yearFraction(gameTime);
        }
    }

    private static void disable(Throwable e) {
        broken = true;
        LOGGER.warn("Ecliptic Seasons API not usable, seasons follow the built-in calendar: {}", e.toString());
    }
}
