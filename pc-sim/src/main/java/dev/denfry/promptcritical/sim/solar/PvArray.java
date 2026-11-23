package dev.denfry.promptcritical.sim.solar;

/**
 * Мощность солнечных панелей (15 §15.5.2–15.5.3):
 *
 * <pre>P = η_STC·A·G_пов·[1 + γ(T_c − 25)]·(1 − s)·k_тень,  T_c = T_a + (NOCT − 20)/800·G_пов,
 * G_пов = DNI·cos θ + DHI·(1 + cos β)/2.</pre>
 *
 * Панель неподвижная (наклон β к востоку или западу) или на одноосевом трекере восток–запад, который держит θ ≈ 0.
 * Затенённые панели строки режут её ток; байпасные диоды оставляют 10 % от затенённой панели:
 * k_тень = 1 − 0,9·N_тень/N − 0,05·[N_тень &gt; 0].
 */
public final class PvArray {
    /** η_STC раннего монокристалла I3 [BALANCE]. */
    public static final double ETA_STC = 0.15;
    public static final double GAMMA_PER_K = -0.004, NOCT_C = 45;
    /** Площадь панели, м² (1 блок). */
    public static final double PANEL_M2 = 1;

    public record Spec(int panels, double areaPerPanelM2, double etaStc, double tempCoeffPerK, double noctC, boolean tracker) {
        public static Spec of(int panels, boolean tracker) {
            return new Spec(panels, PANEL_M2, ETA_STC, GAMMA_PER_K, NOCT_C, tracker);
        }
    }

    private PvArray() {
    }

    /** Температура элемента, °C. */
    public static double cellTempC(double ambientC, double gWm2, double noctC) {
        return ambientC + (noctC - 20) / 800 * gWm2;
    }

    /** Мощность одной панели при облучённости {@code gWm2} на её плоскости, Вт. */
    public static double panelW(double eta, double areaM2, double gWm2, double ambientC, double gamma, double noctC) {
        if (gWm2 <= 0) return 0;
        double tc = cellTempC(ambientC, gWm2, noctC);
        return Math.max(0, eta * areaM2 * gWm2 * (1 + gamma * (tc - 25)));
    }

    /**
     * Облучённость плоскости панели, Вт/м². Нормаль панели — в плоскости восток–запад, наклон {@code tiltRad} к
     * востоку (&gt; 0) или западу (&lt; 0); трекер ставит нормаль на солнце.
     */
    public static double planeIrradiance(SolarSky.Sample sun, double tiltRad, boolean tracker) {
        if (sun.dniWm2() <= 0 && sun.dhiWm2() <= 0) return 0;
        double nx = tracker ? sun.sunX() : Math.sin(tiltRad), ny = tracker ? sun.sunY() : Math.cos(tiltRad);
        double cosTheta = Math.max(0, nx * sun.sunX() + ny * sun.sunY());
        double beta = tracker ? Math.acos(Math.max(-1, Math.min(1, sun.sunY()))) : Math.abs(tiltRad);
        return sun.dniWm2() * cosTheta + sun.dhiWm2() * (1 + Math.cos(beta)) / 2;
    }

    /** Множитель затенения строки из {@code panels} панелей, {@code shaded} из них в тени. */
    public static double shadeFactor(int panels, int shaded) {
        if (panels <= 0 || shaded <= 0) return 1;
        int n = Math.min(shaded, panels);
        return Math.max(0, 1 - 0.9 * n / panels - 0.05);
    }

    /**
     * Мощность строки на стороне постоянного тока, Вт.
     *
     * @param soiling загрязнение 0…1
     * @param snow    панели под снегом — 0
     */
    public static double arrayW(Spec spec, SolarSky.Sample sun, double tiltRad, double ambientC, double soiling, int shaded,
                                boolean snow) {
        if (snow || spec.panels() <= 0) return 0;
        double g = planeIrradiance(sun, tiltRad, spec.tracker());
        double one = panelW(spec.etaStc(), spec.areaPerPanelM2(), g, ambientC, spec.tempCoeffPerK(), spec.noctC());
        return spec.panels() * one * (1 - Math.max(0, Math.min(1, soiling))) * shadeFactor(spec.panels(), shaded);
    }
}
