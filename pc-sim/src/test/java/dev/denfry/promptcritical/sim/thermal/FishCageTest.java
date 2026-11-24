package dev.denfry.promptcritical.sim.thermal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FishCageTest {

    /** Пример 07 §7.13.4: пруд 0,42 км² при 100 МВт — перегрев около 8 К; река 15 м³/с: 100 МВт — 1,6 К, 300 МВт — 4,8 К. */
    @Test
    void pondAndRiverRiseMatchTheSection() {
        assertEquals(7.9, CoolingPond.pondRiseK(100e6, 420_000), 0.1);
        assertEquals(1.6, CoolingPond.riverRiseK(100e6, CoolingPond.RIVER_FLOW_M3S), 0.05);
        assertEquals(4.8, CoolingPond.riverRiseK(300e6, CoolingPond.RIVER_FLOW_M3S), 0.05);
        assertEquals(0, CoolingPond.pondRiseK(1e6, 0), 0);
    }

    @Test
    void cardinalCurveForFish() {
        assertEquals(1, FishCage.cardinal(26), 1e-12);
        assertEquals(0, FishCage.cardinal(8), 0);
        assertEquals(0, FishCage.cardinal(34), 0);
        assertTrue(FishCage.cardinal(20) > FishCage.cardinal(12));
    }

    /** При 26 °C с кормом — 2 % в сутки; корм уходит с коэффициентом 1,5; без корма роста нет. */
    @Test
    void growsOnFeedAtTheOptimum() {
        FishCage c = new FishCage(100);
        double eaten = c.step(86_400, 26, 100);
        assertEquals(102, c.biomassKg(), 1e-9);
        assertEquals(3, eaten, 1e-9);
        FishCage hungry = new FishCage(100);
        assertEquals(0, hungry.step(86_400, 26, 0), 0);
        assertEquals(100, hungry.biomassKg(), 0);
    }

    @Test
    void coldWaterHoldsAndOverheatKills() {
        FishCage c = new FishCage(100);
        c.step(86_400, 6, 100);
        assertEquals(100, c.biomassKg(), 0);
        assertFalse(c.killed());
        c.step(1, 37, 100);
        assertTrue(c.killed());
        assertEquals(0, c.biomassKg(), 0);
        c.restock(FishCage.STOCK_KG);
        assertFalse(c.killed());
    }

    @Test
    void harvestLeavesTheStockAndCapacityCapsGrowth() {
        FishCage c = new FishCage(FishCage.CAPACITY_KG - 1);
        c.step(86_400 * 10, 26, 1000);
        assertEquals(FishCage.CAPACITY_KG, c.biomassKg(), 1e-9);
        assertEquals(FishCage.CAPACITY_KG - FishCage.STOCK_KG, c.harvest(), 1e-9);
        assertEquals(FishCage.STOCK_KG, c.biomassKg(), 1e-9);
    }
}
