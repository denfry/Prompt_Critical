package dev.denfry.promptcritical.sim.waste;

/**
 * Суперпресс твёрдых отходов: бочку 200 л с отходами прокалывают и прессуют усилием {@value #FORCE_T} т в «таблетку»;
 * таблетки укладывают в бочку-контейнер.
 *
 * <p>Уплотнение — уравнение Хеккеля (Heckel, 1961, Trans. Metall. Soc. AIME 221): ln(1/(1 − D)) = K·P + ln(1/(1 − D₀)),
 * D — относительная плотность (доля сплошного материала), P — давление. Давление — усилие на площадь днища бочки
 * Ø {@value #DRUM_D_M} м: 2000 т дают ≈ 77 МПа. Коэффициент объёмного сокращения — D/D₀. Параметры материалов
 * подобраны под эксплуатационные данные суперпрессов АЭС (IAEA TRS-223, 1983, «Treatment of Low- and
 * Intermediate-Level Solid Radioactive Wastes»: сокращение 3–10 раз, у металлолома — 2–3): бумага, пластик, спецодежда — D₀ 0,15; фильтры (металлическая сетка и целлюлоза) — 0,25; металл — 0,30 при
 * пониженной податливости K.
 */
public final class Supercompactor {
    public static final double FORCE_T = 2000, DRUM_D_M = 0.571, G = 9.80665;

    public enum Material {
        DAW(0.15, 0.030), FILTERS(0.25, 0.020), METAL(0.30, 0.010);

        /** Начальная относительная плотность. */
        public final double d0;
        /** Коэффициент Хеккеля, 1/МПа. */
        public final double kPerMPa;

        Material(double d0, double kPerMPa) {
            this.d0 = d0;
            this.kPerMPa = kPerMPa;
        }
    }

    private Supercompactor() {
    }

    /** Давление прессования усилием {@code tonnes} на днище бочки, МПа. */
    public static double pressureMPa(double tonnes) {
        return tonnes * 1000 * G / (Math.PI * DRUM_D_M * DRUM_D_M / 4) / 1e6;
    }

    /** Относительная плотность после прессования. */
    public static double relativeDensity(Material m, double pMPa) {
        return 1 - (1 - m.d0) * Math.exp(-m.kPerMPa * Math.max(0, pMPa));
    }

    /** Коэффициент объёмного сокращения. */
    public static double volumeReduction(Material m, double pMPa) {
        return relativeDensity(m, pMPa) / m.d0;
    }
}
