package dev.denfry.promptcritical.sim.reprocessing;

/**
 * Экстракция 30 % ТБФ в разбавителе (керосин, додекан) из азотнокислого раствора: коэффициенты распределения и
 * противоточный каскад смесителей-отстойников (Benedict, Pigford, Levi, «Nuclear Chemical Engineering», 1981,
 * гл. 10; Schulz, Navratil, «Science and Technology of Tributyl Phosphate», т. I, 1984; Long, «Engineering for Nuclear
 * Fuel Reprocessing», 1967).
 *
 * <p>Коэффициенты распределения D = C_орг/C_водн при малой загрузке растворителя, аппроксимации кривых BPL рис. 10.x
 * по кислотности водной фазы H (моль/л HNO₃): U(VI) 3,7·H^1,57 (≈ 20 при 3 М), Pu(IV) 1,3·H^1,4 (≈ 6), Pu(III)
 * 0,015, Zr(IV) 0,03·H^1,3, Ru (нитрозилрутений) 0,02·H, Cs, Sr, РЗЭ — 10⁻³ и меньше.
 *
 * <p>Каскад — формула Кремзера для N ступеней с фактором экстракции E = D·O/A: неизвлечённая доля
 * (E − 1)/(E^{N+1} − 1). Промывка экстракта азотной кислотой (фактор отмывки S = 1/(D·O/A_пр)) возвращает примеси в
 * водную фазу; в продукте остаётся ≈ E·S^{−M}. Снизу очистку ограничивает механический унос водной фазы с органикой
 * (≈ 2·10⁻⁴ за цикл) — поэтому на практике 10³–10⁴ за цикл и 10⁶–10⁷ за два цикла.
 */
public final class PurexFlowsheet {
    public static final int EXTRACT_STAGES = 8, SCRUB_STAGES = 8, CYCLES = 2;
    /** O/A на экстракции и на промывке. */
    public static final double OA_EXTRACT = 1.0, OA_SCRUB = 4.0;
    /** Унос водной фазы с экстрактом за цикл — нижняя граница очистки. */
    public static final double ENTRAINMENT = 2e-4;
    public static final double MIN_ACID = 0.5, MAX_ACID = 6;

    public enum Species {
        U6, PU4, PU3, ZR, RU, CS_SR;

        /** Коэффициент распределения при кислотности {@code h}, моль/л. */
        public double d(double h) {
            double a = Math.clamp(h, MIN_ACID, MAX_ACID);
            return switch (this) {
                case U6 -> 3.7 * Math.pow(a, 1.57);
                case PU4 -> 1.3 * Math.pow(a, 1.4);
                case PU3 -> 0.015;
                case ZR -> 0.03 * Math.pow(a, 1.3);
                case RU -> 0.02 * a;
                case CS_SR -> 1e-3;
            };
        }
    }

    private PurexFlowsheet() {
    }

    /** Неизвлечённая доля после {@code n} ступеней с фактором {@code e} (Кремзер). */
    public static double unextracted(double e, int n) {
        if (Math.abs(e - 1) < 1e-9) return 1.0 / (n + 1);
        return (e - 1) / (Math.pow(e, n + 1) - 1);
    }

    /** Доля, перешедшая в экстракт. */
    public static double extracted(Species s, double h) {
        return 1 - unextracted(s.d(h) * OA_EXTRACT, EXTRACT_STAGES);
    }

    /** Доля примеси, оставшаяся в экстракте после промывки. */
    public static double scrubRetained(Species s, double h) {
        double strip = 1 / (s.d(h) * OA_SCRUB);
        return unextracted(strip, SCRUB_STAGES);
    }

    /** Доля примеси, попавшая в продукт за один цикл: экстракция × промывка, не ниже уноса. */
    public static double carryOverPerCycle(Species s, double h) {
        return Math.max(ENTRAINMENT, extracted(s, h) * scrubRetained(s, h));
    }

    /** Коэффициент очистки от примеси за {@link #CYCLES} циклов. */
    public static double decontamination(Species s, double h) {
        return 1 / Math.pow(carryOverPerCycle(s, h), CYCLES);
    }
}
