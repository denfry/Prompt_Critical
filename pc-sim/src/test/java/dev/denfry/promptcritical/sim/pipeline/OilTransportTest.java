package dev.denfry.promptcritical.sim.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Магистральный насос, вязкость нефти и путевой подогреватель (09 §9.3.6, §9.8). */
class OilTransportTest {
    private static final CentrifugalPump NM = CentrifugalPump.NM_1250_260;
    private static final double QN = 1250 / 3600.0;

    @Test
    void pumpCurvePassesTheRatingAndFollowsTheAffinityLaws() {
        assertEquals(260, NM.headM(QN, 1), 1e-9);
        assertEquals(312, NM.headM(0, 1), 1e-9);
        // половина частоты: подача вдвое, напор вчетверо (65 м при Qн/2)
        assertEquals(65, NM.headM(QN / 2, 0.5), 1e-9);
        assertEquals(QN * Math.sqrt(6), NM.maxFlowM3s(1), 1e-9);
        assertEquals(0, NM.headM(NM.maxFlowM3s(1) * 1.1, 1), 1e-9);
        assertEquals(0, NM.headM(QN, 0), 1e-9);
    }

    @Test
    void pumpPowerPeaksNearTheRatingAndDropsWithTheCubeOfSpeed() {
        // ρgQH/η = 850 · 9,80665 · 0,3472 · 260 / 0,80 = 940,7 кВт
        assertEquals(940.66e3, NM.shaftPowerW(QN, 1, 850), 0.1e3);
        assertEquals(0.80, NM.efficiency(QN, 1), 1e-12);
        assertTrue(NM.efficiency(QN * 1.4, 1) < 0.8 && NM.efficiency(QN * 0.6, 1) < 0.8);
        // закрытая задвижка: потери 30 % номинала, на половине частоты — в восемь раз меньше
        assertEquals(282.2e3, NM.shaftPowerW(0, 1, 850), 0.1e3);
        assertEquals(NM.shaftPowerW(0, 1, 850) / 8, NM.shaftPowerW(0, 0.5, 850), 1);
        assertEquals(2.168e6, CentrifugalPump.pressurePa(260, 850), 1e3);
    }

    @Test
    void beggsRobinsonViscosityOfA35ApiCrude() {
        assertEquals(34.80, CrudeViscosity.api(850), 0.01);
        // 34,8 °API: 36,5 сП при 20 °C, 3,78 сП при 60 °C — подогрев на 40 К снижает вязкость вдесятеро
        assertEquals(36.53, CrudeViscosity.deadOilCp(850, 20), 0.05);
        assertEquals(3.784, CrudeViscosity.deadOilCp(850, 60), 0.01);
        assertTrue(CrudeViscosity.deadOilCp(920, 20) > CrudeViscosity.deadOilCp(850, 20));
    }

    @Test
    void lineHeaterIsLimitedByItsDutyAndByTheBath() {
        double m = QN * 850;
        // вся подача магистрального насоса: 0,63 МВт греют 295 кг/с всего на 1,07 К
        assertEquals(15 + 1.0673, LineHeater.outletC(15, m, 1), 1e-3);
        // малый поток: нефть догревается до бани минус недогрев змеевика
        assertEquals(85, LineHeater.outletC(15, 2, 1), 1e-12);
        assertEquals(15, LineHeater.outletC(15, 2, 0), 1e-12);
        double heat = LineHeater.heatToOilW(15, LineHeater.outletC(15, m, 1), m);
        assertEquals(0.63e6, heat, 1);
        // 0,63 МВт в нефть + 5 % потерь корпуса при КПД 0,75 и 38 МДж/нм³ = 83,6 нм³/ч газа
        assertEquals(83.56, LineHeater.fuelNm3s(heat, 1) * 3600, 0.01);
        assertEquals(0, LineHeater.fuelNm3s(heat, 0), 0);
    }
}
