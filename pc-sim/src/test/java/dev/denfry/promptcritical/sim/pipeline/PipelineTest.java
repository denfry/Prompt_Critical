package dev.denfry.promptcritical.sim.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** Трубопроводы, катодная защита, скребки и резервуары (09 §9.8). */
class PipelineTest {

    @Test
    void barlowGivesTheDesignAndTheBurstPressure() {
        // X52, Ø530×10, класс 1, бесшовная: 2·359·10/530·0,72 = 9,75 МПа; течёт при p_design/F = 13,5 МПа
        double design = Pipeline.designPressurePa(Pipeline.Grade.X52, 0.010, 0.530, Pipeline.LocationClass.CLASS_1, 1, 1);
        assertEquals(9.75e6, design, 0.01e6);
        assertEquals(design / 0.72, Pipeline.burstPressurePa(Pipeline.Grade.X52, 0.010, 0.530, 0), 1);
        // треть стенки съела коррозия — разрушение уже ниже рабочего давления с запасом 1/F
        assertTrue(Pipeline.burstPressurePa(Pipeline.Grade.X52, 0.010, 0.530, 0.33) < design);
        assertTrue(Pipeline.designPressurePa(Pipeline.Grade.X70, 0.010, 0.530, Pipeline.LocationClass.CLASS_4, 1, 1) < design);
    }

    @Test
    void linepackAndFrictionMatchHandCalculations() {
        assertEquals(52_100, Pipeline.linepackKg(7e6, 1000, 0.016043, 0.9, 288), 100);
        // вода 10 кг/с в трубе 100 мм, 100 м: Re = 1,27·10⁵, f = 0,0196, Δp ≈ 15,9 кПа
        assertEquals(15.9e3, Pipeline.pressureDropPa(10, 1000, 1e-3, 0.1, 100), 0.3e3);
        assertEquals(64 / 1000.0, Pipeline.frictionFactor(1000, 1e-4), 1e-12);
        assertEquals(0, Pipeline.imbalanceKgS(10, 9, 1), 1e-12);
        assertEquals(0.5, Pipeline.imbalanceKgS(10, 9.5, 0), 1e-12);
    }

    @Test
    void cathodicProtectionMeetsTheCriterionAtTheRequiredCurrent() {
        double area = Math.PI * 0.53 * 1000;
        double req = CathodicProtection.requiredCurrentA(area, CathodicProtection.COATING_EFFICIENCY);
        assertEquals(0.333, req, 0.001);
        assertEquals(-0.85, CathodicProtection.offPotentialV(req, req), 1e-12);
        assertTrue(CathodicProtection.protectedAt(CathodicProtection.offPotentialV(req, req)));
        assertFalse(CathodicProtection.protectedAt(CathodicProtection.offPotentialV(0.5 * req, req)));
        assertTrue(CathodicProtection.overprotected(CathodicProtection.offPotentialV(3 * req, req)));
        assertEquals(0.2e-3 * 0.02, CathodicProtection.corrosionRateMYr(-0.9), 1e-15);
        assertEquals(0.2e-3, CathodicProtection.corrosionRateMYr(-0.7), 1e-15);
        // IR-падение: «вкл» отрицательнее «выкл»
        assertTrue(CathodicProtection.onPotentialV(-0.85, 0.3, 2) < -0.85);
        // протектор 10 кг на 0,1 А — 12,5 года
        assertEquals(12.5, CathodicProtection.anodeLifeYears(10, 0.1), 0.05);
        assertEquals((-0.65 + 1.55) / 9, CathodicProtection.anodeCurrentA(-0.65, 9), 1e-12);
    }

    @Test
    void pigsSlipStickAndFindDefects() {
        assertEquals(1000 / 0.95, Pig.transitS(1000, 1), 1e-9);
        assertTrue(Double.isInfinite(Pig.transitS(1000, 0)));
        assertTrue(Pig.sticks(0.25, 5, true));
        assertTrue(Pig.sticks(0.05, 2, true));
        assertTrue(Pig.sticks(0.05, 5, false));
        assertFalse(Pig.sticks(0.05, 5, true));
        SplittableRandom r = new SplittableRandom(3);
        int found = 0;
        for (int i = 0; i < 10_000; i++) {
            List<Pig.Defect> rep = Pig.inspect(List.of(new Pig.Defect(10, 0.3), new Pig.Defect(20, 0.05)), r);
            for (Pig.Defect d : rep) {
                assertEquals(10, d.positionM(), "shallow defects are never reported");
                assertEquals(0.3, d.depthFraction(), 0.1 + 1e-12);
                found++;
            }
        }
        assertEquals(0.9, found / 1e4, 0.01);
        assertEquals(900 * Math.PI * (0.5 * 0.01 - 1e-4) * 100, Pig.waxKg(0.5, 0.01, 100), 1e-6);
    }

    @Test
    void floatingRoofCutsBreathingLosses() {
        double rho = StorageTank.Product.CRUDE.vapourDensity(293);
        assertEquals(0.513, rho, 0.002);
        assertEquals(0.0513, StorageTank.workingLossKgS(StorageTank.Product.CRUDE, 0.1, 293, false), 0.0003);
        assertEquals(0.06 * StorageTank.workingLossKgS(StorageTank.Product.CRUDE, 0.1, 293, false),
                StorageTank.workingLossKgS(StorageTank.Product.CRUDE, 0.1, 293, true), 1e-12);
        assertEquals(17.5, StorageTank.standingLossKgS(StorageTank.Product.CRUDE, 1000, 293, false) * 86_400, 0.1);
        assertTrue(StorageTank.Product.GASOLINE.vapourDensity(293) > rho);
        assertTrue(StorageTank.highHigh(0.96) && !StorageTank.highHigh(0.94) && StorageTank.high(0.94));
    }
}
