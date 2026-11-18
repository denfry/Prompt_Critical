package dev.denfry.promptcritical.sim.fuel;

import dev.denfry.promptcritical.sim.enrichment.Criticality;
import dev.denfry.promptcritical.sim.nuclide.PlutoniumCharge;

/**
 * Смешение МОКС-топлива для тепловых реакторов: PuO₂ из PUREX и UO₂ из обеднённого (отвал) или регенерированного урана
 * сводят к заданной доле плутония в тяжёлом металле. Типичный тепловой МОКС — {@value #TYPICAL_MIN_PU}–{@value
 * #TYPICAL_MAX_PU} Pu/ТМ (MELOX, OECD/NEA «Plutonium Fuel: An Assessment», 1989; «The Economics of the Back End of the
 * Nuclear Fuel Cycle», 2013); выше {@value #MAX_PU} — коэффициент реактивности по пустотам теплоносителя становится
 * положительным, такие смеси не лицензируют.
 *
 * <p>Эквивалентность в решётке PWR: делящийся плутоний (Pu-239 + Pu-241) стоит {@value #PU_FISS_TO_U235} от U-235 на
 * начало кампании — середина диапазона 0,7–0,8 для реакторного плутония в PWR (OECD/NEA «Plutonium Fuel: An
 * Assessment», 1989, гл. 4). Проверка снизу — эквивалентность EDF по длине кампании: 7,08 % Pu (≈ 64 % Pu_fiss) на
 * отвальном уране 0,25 % заменяет UOX 3,25 %: (3,25 − 0,25) / (7,08·0,64) ≈ 0,66 — к концу кампании Pu-эквивалент
 * падает быстрее (накопление Pu-240/242, выгорание Pu-241), поэтому начальная ценность выше средней по кампании.
 * Меньше единицы: жёсткий спектр МОКС, большое тепловое поглощение Pu-240 и резонанс Pu-239 0,3 эВ снижают ценность
 * тепловых нейтронов, хотя на одно деление Pu-239 даёт больше нейтронов.
 *
 * <p>Ядерная безопасность: плутоний в одном смесителе не больше {@link Criticality#safePuKg} (по доле Pu-240) — масса
 * порции порошка ограничена ей, а не производительностью.
 */
public final class MoxBlend {
    public static final double PU_FISS_TO_U235 = PlutoniumCharge.FISS_TO_U235, TYPICAL_MIN_PU = 0.05, TYPICAL_MAX_PU = 0.08,
            MAX_PU = PlutoniumCharge.MAX_THERMAL_FRACTION;

    private MoxBlend() {
    }

    /**
     * Смесь.
     *
     * @param hmKg        тяжёлый металл, кг
     * @param puKg        плутоний, кг
     * @param uKg         уран, кг
     * @param u235        доля U-235 в уране
     * @param pu239..242  изотопия плутония
     */
    public record Mix(double hmKg, double puKg, double uKg, double u235, double pu239, double pu240, double pu241, double pu242) {
        /** Доля Pu в тяжёлом металле. */
        public double puFraction() {
            return hmKg > 0 ? puKg / hmKg : 0;
        }

        /** Делящийся плутоний (Pu-239 + Pu-241), доля ТМ. */
        public double fissileFraction() {
            return puFraction() * (pu239 + pu241);
        }

        /** Эквивалентное обогащение по U-235 на начало кампании, доля. */
        public double equivalentU235() {
            return equivalentEnrichment(uKg / Math.max(1e-12, hmKg) * u235, fissileFraction());
        }
    }

    /** Эквивалентное обогащение: U-235 в ТМ плюс делящийся Pu с весом {@link #PU_FISS_TO_U235}. */
    public static double equivalentEnrichment(double u235InHm, double puFissileInHm) {
        return Math.max(0, u235InHm) + PU_FISS_TO_U235 * Math.max(0, puFissileInHm);
    }

    /** Урана на {@code puKg} плутония для доли Pu в ТМ {@code puFraction}, кг. */
    public static double uraniumFor(double puKg, double puFraction) {
        double f = Math.clamp(puFraction, 1e-6, MAX_PU);
        return Math.max(0, puKg) * (1 - f) / f;
    }

    /** Порция PuO₂ в смесителе не превышает безопасную массу. */
    public static boolean vesselSafe(double puKg, double pu240Fraction) {
        return puKg <= Criticality.safePuKg(pu240Fraction);
    }

    /**
     * Смешать плутоний с ураном до доли {@code puFraction} (не выше {@link #MAX_PU}).
     *
     * @return смесь; урана берётся не больше {@code uAvailableKg} — тогда доля Pu выше заданной не станет: лишний
     * плутоний не смешивается ({@code puKg} смеси меньше входного)
     */
    public static Mix blend(double puKg, double pu239, double pu240, double pu241, double pu242, double uAvailableKg, double u235,
            double puFraction) {
        double f = Math.clamp(puFraction, 1e-6, MAX_PU);
        double u = Math.min(uAvailableKg, uraniumFor(puKg, f));
        double pu = Math.min(Math.max(0, puKg), u * f / (1 - f));
        return new Mix(u + pu, pu, u, u235, pu239, pu240, pu241, pu242);
    }
}
