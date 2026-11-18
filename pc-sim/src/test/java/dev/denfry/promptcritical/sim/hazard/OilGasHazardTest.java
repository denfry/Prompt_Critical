package dev.denfry.promptcritical.sim.hazard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.consequence.BlastScaling;
import dev.denfry.promptcritical.sim.consequence.GasDispersion.Stability;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** Примеры 09 §9.11 и 14 §14.5 (T-14-08 … T-14-10). */
class OilGasHazardTest {

    @Test
    void gasolinePoolFireOfTenMetresMatchesTheGddExample() {
        // §9.11.4: бензин, D = 10 м, χ = 1: Q = 189 МВт, L = 20,1 м, на 50 м — 1,8 кВт/м²
        double q = PoolFire.heatReleaseW(PoolFire.Fuel.GASOLINE, 10, 1);
        assertEquals(189e6, q, 1e6);
        assertEquals(20.1, PoolFire.flameHeightM(q, 10), 0.1);
        assertEquals(1.80, ThermalRadiation.pointSourceKwM2(PoolFire.radiantFraction(10), q, 50), 0.02);
        assertEquals(0.15, PoolFire.radiantFraction(11));
    }

    @Test
    void thermalThresholdsFollowTheTable() {
        assertEquals(0, ThermalRadiation.damagePerSecond(1.6));
        assertEquals(1.0, ThermalRadiation.damagePerSecond(4.5));
        assertTrue(ThermalRadiation.ignitesWood(12.5));
        assertFalse(ThermalRadiation.ignitesWood(12));
    }

    @Test
    void jetFireOfHundredMegawattsIsAboutTwentyTwoMetres() {
        assertEquals(22, JetFire.lengthM(100e6), 0.5);
        assertEquals(50.0e6, JetFire.heatReleaseW(1, Flammable.METHANE));
    }

    @Test
    void tenTonnesOfPropaneMakeAHundredTwentyFiveMetreFireball() {
        assertEquals(125, Fireball.diameterM(10_000), 0.5);
        assertEquals(9.7, Fireball.durationS(10_000), 0.05);
        assertEquals(Fireball.SURFACE_EMISSIVE_KW_M2, Fireball.fluxKwM2(10_000, 30));
        assertTrue(Fireball.fluxKwM2(10_000, 500) < 5);
    }

    @Test
    void ignitionProbabilityFollowsCoxLeesAng() {
        assertEquals(0.01, Ignition.probability(0.5, true));
        assertEquals(0.07, Ignition.probability(10, true));
        assertEquals(0.03, Ignition.probability(10, false));
        assertEquals(0.30, Ignition.probability(80, true));
        assertEquals(0.08, Ignition.probability(80, false));
        // доля немедленного — 40 %: на 100 000 бросков большой газовой утечки ≈ 12 % немедленных, 18 % задержанных
        SplittableRandom r = new SplittableRandom(7);
        int imm = 0, del = 0;
        for (int i = 0; i < 100_000; i++)
            switch (Ignition.roll(80, true, r)) {
                case IMMEDIATE -> imm++;
                case DELAYED -> del++;
                default -> {
                }
            }
        assertEquals(0.12, imm / 1e5, 0.005);
        assertEquals(0.18, del / 1e5, 0.005);
    }

    @Test
    void chokedFlowAboveTheCriticalRatio() {
        assertEquals(1.85, Outflow.criticalPressureRatio(1.31), 0.02);
        // метан 7 МПа, 300 К, отверстие 10 см², Cd = 0,62: ≈ 7,8 кг/с
        double m = Outflow.gasKgS(0.62, 1e-3, 7e6, 101_325, 1.31, 0.016043, 0.9, 300);
        assertEquals(7.8, m, 0.3);
        // ниже критического отношения расход меньше и обращается в ноль без перепада
        assertTrue(Outflow.gasKgS(0.62, 1e-3, 1.5e5, 101_325, 1.31, 0.016043, 1, 300) < Outflow.gasKgS(0.62, 1e-3, 3e5, 101_325, 1.31, 0.016043, 1, 300));
        assertEquals(0, Outflow.gasKgS(0.62, 1e-3, 101_325, 101_325, 1.31, 0.016043, 1, 300));
        assertEquals(0.61 * 1e-3 * Math.sqrt(2 * 850 * 1e5), Outflow.liquidKgS(1e-3, 850, 1e5), 1e-9);
    }

    @Test
    void oneTonneOfPropaneAtThreePercentIsThreeHundredThirtyTwoKilogramsOfTnt() {
        double w = VapourCloudExplosion.tntEquivalentKg(0.03, 1000, Flammable.PROPANE);
        assertEquals(332, w, 1);
        // P_MC = 7,5 при эталоне 50 кг (§9.11.7, 04d §4d.2.4)
        assertEquals(7.5, BlastScaling.power(w * BlastScaling.TNT_J_PER_KG, 50, 12), 0.05);
    }

    @Test
    void congestionSetsTheStrengthClass() {
        assertEquals(0, VapourCloudExplosion.strengthClass(0.2, false, Flammable.PROPANE));
        assertEquals(4, VapourCloudExplosion.strengthClass(0.35, false, Flammable.PROPANE));
        assertEquals(6, VapourCloudExplosion.strengthClass(0.5, false, Flammable.PROPANE));
        assertEquals(8, VapourCloudExplosion.strengthClass(0.7, false, Flammable.PROPANE));
        assertEquals(8, VapourCloudExplosion.strengthClass(0.35, true, Flammable.PROPANE));
        assertEquals(10, VapourCloudExplosion.strengthClass(0.7, false, Flammable.HYDROGEN));
        assertEquals(0.1 * 3.5e9, VapourCloudExplosion.mechanicalEnergyJ(1000, 6), 1);
    }

    @Test
    void cloudIsFlammableOnlyBetweenTheLimitsAndGrowsWithTime() {
        assertTrue(Flammable.METHANE.flammable(Flammable.METHANE.lflKgM3() * 1.01));
        assertFalse(Flammable.METHANE.flammable(Flammable.METHANE.uflKgM3() * 1.01));
        assertTrue(Flammable.PROPANE.dense());
        assertTrue(Flammable.HYDROGEN_SULFIDE.dense());
        assertFalse(Flammable.METHANE.dense());
        FlammableCloud.Result early = FlammableCloud.integrate(Flammable.PROPANE, 10, 2, Stability.F, 0, 45, (e, u, s) -> 0.5);
        FlammableCloud.Result later = FlammableCloud.integrate(Flammable.PROPANE, 10, 2, Stability.F, 0, 120, (e, u, s) -> 0.5);
        assertTrue(later.flammableKg() > early.flammableKg() && early.flammableKg() > 0, early + " " + later);
        assertEquals(later.flammableM3(), later.congestedM3(), 1e-6);
        assertEquals(0.5, later.meanCongestion(), 1e-9);
        // северный ветер (откуда — север) несёт облако на юг (+Z)
        assertTrue(later.centroidSouth() > 0 && Math.abs(later.centroidEast()) < later.centroidSouth());
        FlammableCloud.Result open = FlammableCloud.integrate(Flammable.PROPANE, 10, 2, Stability.F, 0, 120, (e, u, s) -> 0.1);
        assertEquals(0, open.congestedM3());
        assertEquals(0, FlammableCloud.integrate(Flammable.PROPANE, 0, 2, Stability.F, 0, 60, null).flammableKg());
    }

    @Test
    void steelLosesHalfItsStrengthAtSixHundredDegrees() {
        assertEquals(1, BleveVessel.yieldFactor(300));
        assertEquals(0.47, BleveVessel.yieldFactor(600), 1e-9);
        assertEquals(0.625, BleveVessel.yieldFactor(550), 1e-9);
    }

    @Test
    void anEngulfedPropaneBulletBlowsUpInMinutesAndADelugeSavesIt() {
        BleveVessel v = bullet();
        double t = 0;
        boolean relieved = false;
        while (!v.failed() && t < 3600) {
            v.step(1, 100, false);
            relieved |= v.reliefOpen();
            t++;
        }
        assertTrue(v.failed(), "an engulfed bullet fails");
        assertTrue(relieved, "the relief valve lifts before the failure");
        // §9.11.6: 10–30 мин при открытом пламени; наша стенка 20 мм под 100 кВт/м² — около 10 мин
        assertTrue(t > 300 && t < 1800, "time to BLEVE " + t);
        assertTrue(v.wallK() - 273.15 > 500 && v.wallK() - 273.15 < 600, "wall at failure " + v.wallK());
        assertTrue(v.blastEnergyJ() > 1e8, "blast " + v.blastEnergyJ());
        assertTrue(v.flashFraction() > 0.3 && v.flashFraction() < 0.5);

        BleveVessel cooled = bullet();
        for (int i = 0; i < 3600; i++) cooled.step(1, 100, true);
        assertFalse(cooled.failed(), "the deluge keeps the wall below 300 °C");
        assertTrue(cooled.wallK() < 573);

        BleveVessel jet = bullet();
        int tj = 0;
        while (!jet.failed() && tj < 3600) {
            jet.step(1, JetFire.IMPINGEMENT_KW_M2, false);
            tj++;
        }
        assertTrue(jet.failed() && tj < t, "a jet fire is faster: " + tj);
    }

    private static BleveVessel bullet() {
        BleveVessel v = new BleveVessel(BleveVessel.Liquefied.PROPANE, 50, 1.8e6, 0.02);
        v.setLiquidKg(0.8 * v.capacityKg());
        return v;
    }

    @Test
    void faySpreadsTenCubicMetresToEightyTwoMetresInAnHour() {
        // T-14-08: V = 10 м³, Δ = 0,15, t = 1 ч → r = 81,7 м, A = 2,10·10⁴ м², h = 0,48 мм
        double r = OilSpill.fayRadiusM(10, 0.15, 3600);
        assertEquals(81.7, r, 0.1);
        OilSpill s = new OilSpill(8500, 850, 30, false);
        s.advance(3600, -1000); // испарение здесь не нужно: холод его гасит
        assertEquals(2.10e4, s.areaM2(), 0.01e4);
        assertEquals(0.48e-3, s.thicknessM(s.areaM2()), 0.01e-3);
        assertEquals(0.19, OilSpill.skimmerEfficiency(0.48e-3), 0.005);
    }

    @Test
    void fingasEvaporatesThirtySixPercentInADay() {
        // T-14-09: D180 = 30 %, 15 °C, 24 ч → 36,0 % по формуле Фингаса; но 14 §14.5.3 ограничивает испарение
        // сверху D180 + 5 % = 35 % — пример с ограничением расходится, ограничение физическое и остаётся
        assertEquals(36.0, OilSpill.fingasPct(30, 15, 24 * 60), 0.05);
        assertEquals(0.35, OilSpill.evaporatedFraction(30, 15, 24 * 60), 1e-9);
        assertEquals(0.35, OilSpill.evaporatedFraction(30, 40, 1e6), 1e-9);
        OilSpill s = new OilSpill(1000, 850, 30, true);
        s.advance(86_400, 15);
        assertEquals(350, s.evaporatedKg(), 1);
        assertEquals(500, s.recover(500), 1e-9);
        assertEquals(150, s.recover(500), 1);
        assertEquals(0, s.remainingKg(), 1e-9);
    }

    @Test
    void aBoomOnAHalfMetreRiverMustLieWithinFortyFourDegrees() {
        // T-14-10
        assertEquals(44.4, Math.toDegrees(OilSpill.maxBoomAngleRad(0.5)), 0.05);
        assertTrue(OilSpill.boomHolds(0.5, Math.toRadians(40)));
        assertFalse(OilSpill.boomHolds(0.5, Math.toRadians(90)));
    }
}
