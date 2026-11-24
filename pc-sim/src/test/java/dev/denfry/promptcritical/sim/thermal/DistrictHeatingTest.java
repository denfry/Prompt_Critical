package dev.denfry.promptcritical.sim.thermal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import org.junit.jupiter.api.Test;

class DistrictHeatingTest {

    @Test
    void heatingScheduleRunsFrom150To70() {
        assertEquals(150, DistrictHeating.supplyC(-25), 1e-9);
        assertEquals(150, DistrictHeating.supplyC(-40), 1e-9);
        assertEquals(70, DistrictHeating.supplyC(8), 1e-9);
        assertEquals(70, DistrictHeating.supplyC(25), 1e-9);
        assertEquals(110, DistrictHeating.supplyC(-8.5), 1e-9);
        assertEquals(40, DistrictHeating.returnC(20), 0);
        assertEquals(70, DistrictHeating.returnC(-10), 0);
    }

    /** Подача 150 °C — пар за турбиной при 155 °C, около 5,4 бар; летом 70 °C — около 0,39 бар: противодавление выше вакуума конденсатора. */
    @Test
    void backpressureFollowsTheSupplyTemperature() {
        WaterProperties w = WaterProperties.tables();
        assertEquals(5.43e5, DistrictHeating.exhaustPa(w, -25), 0.1e5);
        assertEquals(0.386e5, DistrictHeating.exhaustPa(w, 20), 0.02e5);
        assertTrue(DistrictHeating.exhaustPa(w, -25) > DistrictHeating.exhaustPa(w, 0));
    }

    /** Поселение 30 000 м³ при −10 °C: 0,4 · 30 000 · 28 = 336 кВт; летом отопления нет. */
    @Test
    void heatingLoadOfASettlement() {
        assertEquals(336e3, DistrictHeating.heatingLoadW(30_000, -10), 1e-6);
        assertEquals(0, DistrictHeating.heatingLoadW(30_000, 10), 0);
        // 336 кВт при графике −10 °C (подача ≈ 111 °C, обратка 70) — около 2 кг/с сетевой воды
        assertEquals(336e3 / (4186 * (DistrictHeating.supplyC(-10) - 70)), DistrictHeating.networkWaterKgS(336e3, -10), 1e-9);
        assertEquals(2.18e6, DistrictHeating.heatFromSteamW(1), 0);
    }
}
