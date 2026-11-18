package dev.denfry.promptcritical.sim.isotopes;

/**
 * Термоэлектрический РИТЭГ (12 §12.5): тепло источника идёт через термоэлементы на рёбра и в воздух (или воду).
 *
 * <p>Тепловой баланс стационарный — постоянные времени корпуса (минуты) меньше SLOW-шага: горячий спай
 * T_h = T_c + Q_h·R_hc, холодный T_c = T_a + Q_c·R_ca·k_охл, где Q_h = P_th — всё тепло источника проходит через
 * преобразователь (утечки мимо элементов — в системном коэффициенте), Q_c = P_th − P_эл. КПД материала
 *
 * <pre>η_TE = (T_h − T_c)/T_h · (√(1+ZT) − 1)/(√(1+ZT) + T_c/T_h)</pre>
 *
 * умножается на системный коэффициент (коммутация, утечки тепла, 0,8 — {@code isotopes.rtg_te_system_factor}) и на
 * деградацию элементов (1 − δ)^{годы} (сублимация и диффузия в спаях, δ = 0,03/год — {@code
 * isotopes.rtg_te_degradation_per_year}). КПД зависит от температур, температуры — от КПД: три итерации сходятся до
 * 10⁻⁶. Под водой холодная сторона охлаждается лучше ({@code coolingFactor} &lt; 1) — так стоят морские РИТЭГи.
 */
public final class Rtg {
    private Rtg() {
    }

    /**
     * Преобразователь.
     *
     * @param zt        безразмерная добротность материала при средней температуре
     * @param rHotKPerW термическое сопротивление элементов (горячий → холодный спай), К/Вт
     * @param rColdKPerW сопротивление рёбра → воздух, К/Вт
     * @param maxHotK   предел горячего спая, К: выше — перегрев (событие {@code rtg_overtemp})
     */
    public record Converter(String id, double zt, double rHotKPerW, double rColdKPerW, double maxHotK) {
        public Converter {
            if (!(zt > 0) || !(rHotKPerW > 0) || !(rColdKPerW > 0) || !(maxHotK > 0)) throw new IllegalArgumentException(id);
        }
    }

    /**
     * «Бета-М» (Sr-90, маяки Севморпути): 230 Вт тепла, ≈ 10 Вт электричества. Термобатарея на халькогенидах свинца и
     * висмута (ZT ≈ 0,5 в среднем по рабочему интервалу), горячий спай ≈ 330 °C при 230 Вт, рёбра греются на ≈ 40 К
     * над воздухом.
     */
    public static final Converter BETA_M = new Converter("beta_m", 0.5, 1.2, 0.17, 900);
    /** GPHS-RTG / MMRTG-подобный на SiGe: горячий спай до 1300 К (пример T12.7). */
    public static final Converter SIGE = new Converter("sige", 0.6, 1.6, 0.13, 1300);

    /** Режим: тепло, электричество, температуры спаев, перегрев. */
    public record State(double thermalW, double electricW, double hotK, double coldK, boolean overtemp) {
        public static final State OFF = new State(0, 0, 0, 0, false);

        public double efficiency() {
            return thermalW > 0 ? electricW / thermalW : 0;
        }
    }

    /** КПД термоэлемента (материальный), 12 §12.5.4. */
    public static double teEfficiency(double zt, double hotK, double coldK) {
        if (!(hotK > coldK) || coldK <= 0) return 0;
        double s = Math.sqrt(1 + zt);
        return (hotK - coldK) / hotK * (s - 1) / (s + coldK / hotK);
    }

    /** Остаток КПД после {@code years} работы. */
    public static double degradation(double years, double perYear) {
        return Math.pow(1 - perYear, Math.max(0, years));
    }

    /**
     * Стационарный режим.
     *
     * @param thermalW      тепло источника, Вт
     * @param ambientK      воздух или вода у рёбер, К
     * @param coolingFactor множитель R_ca (1 — воздух, ≈ 0,3 — вода)
     * @param ageYears      наработка термоэлементов, лет
     * @param systemFactor  η_сист/η_TE
     * @param degradationPerYear δ
     */
    public static State steady(Converter c, double thermalW, double ambientK, double coolingFactor, double ageYears, double systemFactor,
            double degradationPerYear) {
        if (!(thermalW > 0)) return new State(0, 0, ambientK, ambientK, false);
        double keep = degradation(ageYears, degradationPerYear) * systemFactor, eta = 0, hot = ambientK, cold = ambientK;
        for (int i = 0; i < 4; i++) {
            cold = ambientK + thermalW * (1 - eta) * c.rColdKPerW() * coolingFactor;
            hot = cold + thermalW * c.rHotKPerW();
            eta = keep * teEfficiency(c.zt(), hot, cold);
        }
        return new State(thermalW, thermalW * eta, hot, cold, hot > c.maxHotK());
    }
}
