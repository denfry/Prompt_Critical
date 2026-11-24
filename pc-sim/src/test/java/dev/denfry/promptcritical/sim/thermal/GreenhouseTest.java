package dev.denfry.promptcritical.sim.thermal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GreenhouseTest {

    /** Пример T17.10: 10×4×10 м, 260 м² одинарного стекла, 18 °C внутри при −10 °C — 42,2 + 1,8 = 44,1 кВт. */
    @Test
    void heatLossMatchesTheWorkedExample() {
        double ua = Greenhouse.U_SINGLE_GLASS * 260;
        assertEquals(42.2e3, ua * 28, 0.1e3);
        assertEquals(1.85e3, Greenhouse.AIR_WH_M3K * Greenhouse.AIR_CHANGES_PER_H * 400 * 28, 0.05e3);
        assertEquals(44.1e3, Greenhouse.heatLossW(ua, 400, 18, -10), 0.1e3);
        // 44 кВт при охлаждении воды на 10 К — около 1,05 кг/с
        assertEquals(1.05, Greenhouse.coilWaterKgS(44.1e3), 0.01);
    }

    /** При 18 °C f = 0,884 и m = 1,44; на T_opt f = 1; на краях и за ними — 0. */
    @Test
    void cardinalTemperatureCurve() {
        assertEquals(0.884, Greenhouse.cardinal(18), 0.001);
        assertEquals(1.44, Greenhouse.growthMultiplier(18), 0.005);
        assertEquals(1.0, Greenhouse.cardinal(24), 1e-12);
        assertEquals(0, Greenhouse.cardinal(5), 0);
        assertEquals(0, Greenhouse.cardinal(36), 0);
        assertEquals(0, Greenhouse.growthMultiplier(4), 0);
        assertEquals(0, Greenhouse.growthMultiplier(40), 0);
        assertTrue(Greenhouse.cardinal(10) < Greenhouse.cardinal(20));
    }

    /** Хватает тепла — теплица выходит на уставку и берёт ровно свои потери. */
    @Test
    void enoughHeatHoldsTheSetpoint() {
        double ua = Greenhouse.U_SINGLE_GLASS * 260;
        Greenhouse g = new Greenhouse(ua, 400, -10);
        for (int i = 0; i < 3600; i++) g.step(1, -10, 100e3);
        assertEquals(Greenhouse.SETPOINT_C, g.insideC(), 0.01);
        assertEquals(44.1e3, g.heatW(), 0.2e3);
    }

    /** Мало тепла — равновесие ниже уставки, T_a + Q/k; без тепла остывает к наружной. */
    @Test
    void shortHeatSettlesBelowTheSetpointAndNoHeatCoolsDown() {
        double ua = Greenhouse.U_SINGLE_GLASS * 260, k = Greenhouse.lossCoefficientWK(ua, 400);
        Greenhouse g = new Greenhouse(ua, 400, -10);
        for (int i = 0; i < 20_000; i++) g.step(1, -10, 20e3);
        assertEquals(-10 + 20e3 / k, g.insideC(), 0.05);
        assertTrue(g.insideC() < Greenhouse.T_MIN_C, "20 kW is not enough to grow at -10 C");
        Greenhouse cold = new Greenhouse(ua, 400, 18);
        for (int i = 0; i < 20_000; i++) cold.step(1, -10, 0);
        assertEquals(-10, cold.insideC(), 0.05);
        assertEquals(0, cold.heatW(), 0);
    }

    /** Большой шаг устойчив (неявная схема): не перелетает уставку и не уходит ниже наружной. */
    @Test
    void largeStepsStayBounded() {
        Greenhouse g = new Greenhouse(Greenhouse.U_SINGLE_GLASS * 260, 400, -10);
        for (int i = 0; i < 50; i++) {
            g.step(600, -10, 1e6);
            assertTrue(g.insideC() <= Greenhouse.SETPOINT_C + 1e-9 && g.insideC() >= -10 - 1e-9, "T " + g.insideC());
        }
    }

    @Test
    void coldFrameIsWarmerOnlyByDay() {
        assertEquals(10, Greenhouse.coldFrameC(2, true), 0);
        assertEquals(2, Greenhouse.coldFrameC(2, false), 0);
    }
}
