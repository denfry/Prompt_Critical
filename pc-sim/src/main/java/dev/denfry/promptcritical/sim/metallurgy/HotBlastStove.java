package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.combustion.CombustionResult;
import dev.denfry.promptcritical.sim.combustion.Combustor;

/**
 * Воздухонагреватель (каупер) {@code hot_blast_stove} (08 §8.5.2): насадка из огнеупора, два узла — верх (купол)
 * и низ. «На газе» горелка греет насадку сверху, дым уходит снизу при T_низ + 80 К; «на дутье» холодный воздух
 * входит снизу и выходит сверху при T_верх − 30 К; смесительный клапан подмешивает холодный воздух до уставки.
 * Тепло распределяется между узлами 1,3 : 0,7 — сторона входа потока меняется быстрее (моё).
 * Купол — динас: выше 1450 °C {@code STOVE_DOME_TEMP_HIGH}; горелка без пламени — {@code STOVE_FLAME_FAILURE}.
 */
public final class HotBlastStove {
    public static final double CHECKER_CP = 1_050, DOME_MAX_K = 1723.15, APPROACH_K = 30, FLAME_MARGIN_K = 100,
            FLUE_ABOVE_BOTTOM_K = 80, ISOLATED_TAU_S = 5 * 86_400, LEAD_SHARE = 1.3;

    private static final double T0 = 298.15;
    private final double mc;
    private final CombustionResult burn = new CombustionResult();
    private StoveMode mode = StoveMode.ISOLATED;
    private double topK, bottomK, fuelKgS, airKgS, hotBlastK, absorbedW;
    private boolean flameFailure;

    public HotBlastStove(double checkerMassKg, double initialK) {
        if (!(checkerMassKg > 0)) throw new IllegalArgumentException("checkerMassKg");
        mc = checkerMassKg * CHECKER_CP;
        topK = bottomK = initialK;
    }

    public void setMode(StoveMode m) {
        mode = m;
    }

    public void setHeating(double fuelKgS, double airKgS) {
        this.fuelKgS = Math.max(0, fuelKgS);
        this.airKgS = Math.max(0, airKgS);
    }

    /**
     * Шаг SLOW, с физ.
     *
     * @param gas        горелка на колошниковом (или смешанном) газе; {@code null} — газа нет
     * @param blastNm3S  дутьё через воздухонагреватель «на дутье», нм³/с
     * @param coldBlastK воздух после воздуходувки, К
     * @param setpointK  уставка горячего дутья, К
     */
    public void stepSlow(double dt, Combustor gas, double blastNm3S, double coldBlastK, double setpointK) {
        flameFailure = false;
        absorbedW = 0;
        hotBlastK = coldBlastK;
        switch (mode) {
            case ON_GAS -> {
                if (gas == null || fuelKgS <= 0) {
                    flameFailure = fuelKgS > 0;
                    break;
                }
                gas.burn(fuelKgS, airKgS, 293.15, 293.15, burn);
                if (!(burn.heatReleaseW > 0)) {
                    flameFailure = true;
                    break;
                }
                double exit = bottomK + FLUE_ABOVE_BOTTOM_K;
                absorbedW = Math.max(0, burn.heatReleaseW - burn.fluegasHeatRateW(exit));
                double mean = absorbedW * dt / mc;
                double limit = burn.adiabaticFlameK - FLAME_MARGIN_K;
                double dTop = Math.max(0, Math.min(LEAD_SHARE * mean, limit - topK));
                topK += dTop;
                bottomK += 2 * mean - dTop;
            }
            case ON_BLAST -> {
                double out = topK - APPROACH_K;
                if (blastNm3S <= 0 || out <= coldBlastK) break;
                double f = out > setpointK ? (setpointK - coldBlastK) / (out - coldBlastK) : 1;
                f = Math.max(0, Math.min(1, f));
                hotBlastK = coldBlastK + f * (out - coldBlastK);
                double q = blastNm3S * f * (BlastFurnace.airCp(out) * (out - T0)
                        - BlastFurnace.airCp(coldBlastK) * (coldBlastK - T0));
                absorbedW = -q;
                double mean = q * dt / mc;
                double dBottom = Math.min(LEAD_SHARE * mean, bottomK - coldBlastK);
                bottomK -= Math.max(0, dBottom);
                topK -= 2 * mean - Math.max(0, dBottom);
            }
            case ISOLATED -> {
                double k = Math.exp(-dt / ISOLATED_TAU_S);
                topK = T0 + (topK - T0) * k;
                bottomK = T0 + (bottomK - T0) * k;
            }
        }
    }

    public StoveMode mode() {
        return mode;
    }

    /** Горячее дутьё после смесительного клапана (если не «на дутье» — холодное), К. */
    public double hotBlastK() {
        return hotBlastK;
    }

    public double domeK() {
        return topK;
    }

    public double bottomK() {
        return bottomK;
    }

    /** Тепло в насадку (&lt; 0 — отдано дутью), Вт. */
    public double absorbedW() {
        return absorbedW;
    }

    public boolean domeOverTemp() {
        return topK > DOME_MAX_K;
    }

    public boolean flameFailure() {
        return flameFailure;
    }

    public CombustionResult combustion() {
        return burn;
    }
}
