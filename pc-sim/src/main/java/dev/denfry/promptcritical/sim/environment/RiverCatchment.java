package dev.denfry.promptcritical.sim.environment;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Водосбор реки одного измерения: удельный сток с квадратного километра. Ванильная река не течёт и расхода не знает —
 * его приходится задать; всё, что ниже, — гидрология (Chow, Maidment, Mays, «Applied Hydrology»):
 * <ul>
 *   <li>осадки — пока идёт ванильный дождь, {@link #RAIN_MM_H}, в грозу {@link #STORM_MM_H}; ниже 0 °C — снег в запас;</li>
 *   <li>таяние — градусо-сутки: M = DDF·T при T &gt; 0, DDF = 3 мм/(°C·сут) (обычно 2–5) — весеннее половодье;</li>
 *   <li>в сток уходит доля {@link #RUNOFF_COEFF} дождя и талой воды, остальное — испарение и грунт;</li>
 *   <li>сток проходит линейный резервуар S с постоянной {@link #RESPONSE_DAYS}: q = S/k — паводок запаздывает и
 *   растягивается;</li>
 *   <li>базовый (подземный) сток {@link #BASEFLOW_MM_DAY} — река не пересыхает.</li>
 * </ul>
 * Сутки — игровые: паводок проходит за игровые дни вместе с сезонами. Расход же — в настоящих м³/с (удельный сток
 * мм/сут на 86 400 с), иначе сжатое время раздуло бы реку в 72 раза. Расход в точке — удельный сток × площадь.
 * Константы — порядок умеренного пояса [BALANCE].
 */
public final class RiverCatchment {
    public static final double RAIN_MM_H = 2, STORM_MM_H = 8;
    public static final double DEGREE_DAY_MM = 3, RUNOFF_COEFF = 0.4, RESPONSE_DAYS = 5, BASEFLOW_MM_DAY = 0.5;
    /** Средний годовой удельный сток умеренного пояса, мм/сут (≈ 550 мм/год) — для расчётного расхода ГЭС. */
    public static final double MEAN_RUNOFF_MM_DAY = 1.5;
    private static final double DAY_S = 86_400;

    /** Запас воды в снегу и в резервуаре стока, мм слоя. */
    private double snowMm, storeMm;

    /**
     * Шаг {@code dtTicks} игровых тиков при неизменной погоде.
     *
     * @param airTempC температура воздуха над водосбором
     */
    public void step(long dtTicks, boolean raining, boolean thundering, double airTempC) {
        double days = dtTicks / (double) SeasonProvider.TICKS_PER_DAY;
        double hours = days * 24;
        double precip = raining ? (thundering ? STORM_MM_H : RAIN_MM_H) * hours : 0;
        double liquid = 0;
        if (airTempC <= 0) snowMm += precip;
        else liquid += precip;
        if (airTempC > 0 && snowMm > 0) {
            double melt = Math.min(snowMm, DEGREE_DAY_MM * airTempC * days);
            snowMm -= melt;
            liquid += melt;
        }
        // линейный резервуар dS/dt = R − S/k, R постоянен на шаге: точное решение
        double inflowPerDay = RUNOFF_COEFF * liquid / Math.max(days, 1e-12);
        double decay = Math.exp(-days / RESPONSE_DAYS);
        storeMm = storeMm * decay + inflowPerDay * RESPONSE_DAYS * (1 - decay);
    }

    /** Удельный сток, мм/сут: резервуар плюс базовый. */
    public double runoffMmPerDay() {
        return storeMm / RESPONSE_DAYS + BASEFLOW_MM_DAY;
    }

    /** Расход реки с водосбора {@code areaKm2}, м³/с. */
    public double dischargeM3s(double areaKm2) {
        return dischargeM3s(areaKm2, runoffMmPerDay());
    }

    /** Расход с водосбора {@code areaKm2} при удельном стоке {@code mmPerDay}, м³/с. */
    public static double dischargeM3s(double areaKm2, double mmPerDay) {
        return areaKm2 * 1e6 * mmPerDay * 1e-3 / DAY_S;
    }

    public double snowMm() {
        return snowMm;
    }

    public double storeMm() {
        return storeMm;
    }

    /** Восстановить запасы из сохранения мода, мм. */
    public void restore(double snow, double store) {
        snowMm = Math.max(0, snow);
        storeMm = Math.max(0, store);
    }

    public void save(StateWriter out) {
        out.putDouble("snow_mm", snowMm);
        out.putDouble("store_mm", storeMm);
    }

    public void load(StateReader in) {
        snowMm = in.optDouble("snow_mm", 0);
        storeMm = in.optDouble("store_mm", 0);
    }
}
