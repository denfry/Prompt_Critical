package dev.denfry.promptcritical.sim.npp;

import dev.denfry.promptcritical.sim.reactor.pwr.SecondarySide;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;

/** Готовые состояния стартового блока T2a для тестов. */
final class Pwrs {
    static final WaterProperties W = WaterProperties.tables();
    static final double T_SEC = W.saturationP(SecondarySide.P_NOMINAL).tSat();
    /** РГ на 70 % хода извлечена — рабочее положение с запасом на регулирование. */
    static final double RG_WORKING = 0.3;

    private Pwrs() {
    }

    /** 19 ТВС ВВЭР-440 с обогащением {@code u235}: ≈ 2,3 т урана — стартовый блок R5. */
    static PressurizedWaterReactor loaded(double u235) {
        PressurizedWaterReactor r = new PressurizedWaterReactor();
        double kg = FuelDesign.VVER_440.uKgPerAssembly(0.96, u235);
        for (int i = 0; i < PressurizedWaterReactor.POSITIONS; i++)
            assertTrue(r.load(i, FuelDesign.VVER_440, kg, u235, 0.0003), "load " + i);
        return r;
    }

    /**
     * Номинал: насосы на ходу, ПГ с номинальным уровнем, температуры 290/320 °C, топливо по T_eff, АЗ взведена, РГ в
     * рабочем положении, бор подобран на критичность (без ксенона — первый выход на мощность).
     */
    static PressurizedWaterReactor nominal() {
        PressurizedWaterReactor r = loaded(0.044);
        for (int l = 0; l < PressurizedWaterReactor.LOOPS; l++) r.setPumpDrive(l, 1);
        r.setSecondary(T_SEC, 0.5, 2);
        double fuel = 578.15 + PressurizedWaterReactor.NOMINAL_W / (19 * 126 * 2.42 * 1000 / PressurizedWaterReactor.FUEL_R_K_PER_KW_M);
        r.setThermalState(fuel, PressurizedWaterReactor.OUTLET_K, PressurizedWaterReactor.INLET_K);
        r.setRods(0, RG_WORKING);
        double lo = 0, hi = 30;
        for (int i = 0; i < 50; i++) {
            double m = (lo + hi) / 2;
            r.setBoronGkg(m);
            if (r.evaluateReactivity() > 0) lo = m;
            else hi = m;
        }
        r.setBoronGkg((lo + hi) / 2);
        r.equilibrate(1);
        return r;
    }

    static void run(PressurizedWaterReactor r, double seconds) {
        int n = (int) Math.round(seconds / PressurizedWaterReactor.DT);
        for (int i = 0; i < n; i++) r.step(0);
    }
}
