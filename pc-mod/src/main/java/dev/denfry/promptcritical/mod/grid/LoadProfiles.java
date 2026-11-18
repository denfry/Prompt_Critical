package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.sim.grid.DailyLoadProfile;
import net.minecraft.server.level.ServerLevel;

/** Суточный график нагрузки 07 §7.6.4 по времени суток измерения и ключу {@code grid.load_profile_days_per_cycle}. */
public final class LoadProfiles {
    private LoadProfiles() {
    }

    /** Час суток графика; NaN — график выключен в конфиге. */
    public static double hour(ServerLevel level) {
        int days = ServerConfig.GRID_LOAD_PROFILE_DAYS.get();
        return days == 0 ? Double.NaN : DailyLoadProfile.hourOfDay(level.getDayTime(), days);
    }

    /** Форма графика $\hat p(h)$ сейчас; NaN — графика нет. */
    public static double shape(ServerLevel level) {
        double h = hour(level);
        return Double.isNaN(h) ? Double.NaN : DailyLoadProfile.shape(h);
    }
}
