package dev.denfry.promptcritical.sim.combustion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.mining.CoalAssay;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Test;

/** Жаротрубный котёл I0 — газовая сторона, пробка, взрыв (07 §7.9.4). Вода задана граничными условиями. */
class FireTubeBoilerTest {
    private static final WaterProperties W = WaterProperties.tables();
    private static final double DT = 0.05, P = 1.2e6, TSAT = W.saturationP(P).tSat();
    private static final FuelSpec COAL_G = CoalAssay.typical(0.37, 0.213, 0.06, 0.016, 10).toFuel();
    private static final FireTubeBoilerSpec SPEC = FireTubeBoilerSpec.defaults();

    private static BoilerInputs water(double level) {
        BoilerInputs in = new BoilerInputs();
        in.waterTempK = TSAT;
        in.waterPressurePa = P;
        in.waterLevelM = level;
        return in;
    }

    /** Котёл, вышедший на режим при постоянной подаче угля, шибере и воде на рабочем уровне. */
    private static FireTubeBoiler steady(double feed, double damper, BoilerOutputs out) {
        FireTubeBoiler b = new FireTubeBoiler(SPEC, COAL_G, TSAT);
        b.chargeFuel(feed * SPEC.bedBurnoutTimeS());
        assertThat(b.ignite()).isTrue();
        BoilerInputs in = water(SPEC.normalLevelM());
        in.fuelFeedKgS = feed;
        in.damper = damper;
        for (int k = 0; k < 20 * 1200; k++) b.stepFast(DT, in, out);
        return b;
    }

    /** Тяга трубы 07 §7.9.4: 20 м, газы 250 °C → ≈ 110 Па. */
    @Test
    void chimneyDraft() {
        double d = Chimney.draftPa(20, 288.15, 101_325, 523.15, 0.0297);
        assertThat(d).isCloseTo(110, within(0.07 * 110));
        assertThat(Chimney.draftPa(20, 288.15, 101_325, 288.15, Chimney.AIR_MOLAR_MASS)).isCloseTo(0, within(1e-9));
        assertThat(Chimney.draftPa(40, 288.15, 101_325, 523.15, 0.0297)).isCloseTo(2 * d, within(1e-9));
    }

    /**
     * Пример 07 §7.9.4: уголь 0,3 кг/с (7,2 МВт по топливу), КПД ≈ 0,70 → 5 МВт → 1,87 кг/с пара из воды 20 °C.
     * Решётка в номинале — α 1,3–1,5 (07 §7.8.2).
     */
    @Test
    void nominalLoad() {
        BoilerOutputs out = new BoilerOutputs();
        steady(0.3, 1, out);
        assertThat(out.fuelBurnKgS).isCloseTo(0.3, within(1e-3));
        assertThat(out.fuelBurnKgS * COAL_G.lhv()).isCloseTo(7.2e6, within(0.05 * 7.2e6));
        assertThat(out.efficiency).isBetween(0.65, 0.75);
        assertThat(out.heatToWaterW).isCloseTo(5.0e6, within(0.05 * 5.0e6));
        assertThat(out.combustion.excessAir).isBetween(1.3, 1.5);
        assertThat(out.fluegasExitK - 273.15).as("уходящие газы без экономайзера").isBetween(250.0, 360.0);
        assertThat(out.combustion.xCO).isLessThan(1e-4);
        double steam = out.heatToWaterW / (W.saturationP(P).hG() - W.enthalpyPT(P, 293.15));
        assertThat(steam).isCloseTo(1.87, within(0.06 * 1.87));
        // баланс газовой стороны (энтальпии от 298,15 К): топка + её потеря (1 − φ) + трубы + уходящие газы =
        // тепловыделение + физическое тепло воздуха и угля
        double stack = out.combustion.fluegasHeatRateW(out.fluegasExitK);
        double furnaceLoss = (1 - FurnaceHeatTransfer.PHI_DEFAULT) / FurnaceHeatTransfer.PHI_DEFAULT * out.furnaceHeatW;
        double inflow = airSensible(out.airKgS) + FuelPhase.SOLID.heatCapacity * (288.15 - 298.15) * out.fuelBurnKgS;
        assertThat(out.furnaceHeatW + out.convectiveHeatW + furnaceLoss + stack)
                .isCloseTo(out.combustion.heatReleaseW + inflow, within(1e-6 * out.combustion.heatReleaseW));
        // пробка под водой холоднее олова с запасом
        assertThat(out.plugTempK).isBetween(TSAT, FireTubeBoiler.FUSIBLE_PLUG_MELT_K - 25);
    }

    /** Физическое тепло воздуха 288,15 К относительно 298,15 К (отрицательно), Вт. */
    private static double airSensible(double airKgS) {
        CombustionResult r = new CombustionResult();
        new Combustor(COAL_G, BurnerSpec.of(BurnerKind.GRATE, 0.3)).burn(0, airKgS, 288.15, 288.15, r);
        return r.fluegasHeatRateW(288.15);
    }

    /** Тонкий слой при открытом шибере — лишний воздух и потери с газами; прикрытый шибер возвращает КПД. */
    @Test
    void damperTrimsExcessAir() {
        BoilerOutputs open = new BoilerOutputs(), trimmed = new BoilerOutputs();
        steady(0.15, 1, open);
        steady(0.15, 0.5, trimmed);
        assertThat(open.combustion.excessAir).isGreaterThan(2.3);
        assertThat(trimmed.combustion.excessAir).isBetween(1.3, 1.5);
        assertThat(trimmed.efficiency).isGreaterThan(open.efficiency + 0.07);
    }

    /** Перегрузка решётки (слой толще, чем пропускает тяга) — недожог: CO, чёрный дым и меньше тепла. */
    @Test
    void overfiringSmothersFire() {
        BoilerOutputs nominal = new BoilerOutputs(), over = new BoilerOutputs();
        steady(0.3, 1, nominal);
        steady(0.45, 1, over);
        assertThat(over.combustion.excessAir).isLessThan(1.1);
        assertThat(over.combustion.q3).isGreaterThan(0.3);
        assertThat(over.combustion.xCO).isGreaterThan(0.05);
        assertThat(over.heatToWaterW).isLessThan(nominal.heatToWaterW);
    }

    /** Холодная труба тянет слабо: тяга растёт по мере прогрева, без угля огонь гаснет. */
    @Test
    void coldChimneyWarmsUpAndFireBurnsOut() {
        FireTubeBoiler b = new FireTubeBoiler(SPEC, COAL_G, 288.15);
        BoilerInputs in = water(SPEC.normalLevelM());
        in.waterTempK = 330;
        in.waterPressurePa = 101_325;
        BoilerOutputs out = new BoilerOutputs();
        assertThat(b.ignite()).as("пустая решётка").isFalse();
        b.chargeFuel(100);
        assertThat(b.ignite()).isTrue();
        b.stepFast(DT, in, out);
        double draft0 = out.draftPa;
        assertThat(draft0).as("горящий слой прогревает трубу").isGreaterThan(0);
        for (int k = 0; k < 20 * 300; k++) b.stepFast(DT, in, out);
        assertThat(out.draftPa).as("вода ещё холодная — газы тоже").isGreaterThan(2 * draft0);
        for (int k = 0; k < 20 * 6000 && b.isLit(); k++) b.stepFast(DT, in, out);
        assertThat(b.isLit()).isFalse();
        assertThat(b.bedKg()).isLessThan(FireTubeBoiler.MIN_BED_KG);
        for (int k = 0; k < 20 * 1200; k++) b.stepFast(DT, in, out);
        assertThat(out.fuelBurnKgS).isZero();
        assertThat(out.heatToWaterW).as("остывающий котёл теряет тепло").isLessThan(0);
        assertThat(b.stackTempK()).isLessThan(340);
    }

    /** Упуск воды: оголённый потолок жаровой трубы греется, пробка выплавляется за секунды и гасит топку. */
    @Test
    void fusiblePlugSavesCrownOnLowWater() {
        BoilerOutputs out = new BoilerOutputs();
        FireTubeBoiler b = steady(0.3, 1, out);
        BoilerInputs low = water(SPEC.crownHeightM() - 0.1);
        low.fuelFeedKgS = 0.3;
        double t = 0;
        while (!b.plugMelted() && t < 600) {
            b.stepFast(DT, low, out);
            t += DT;
        }
        assertThat(b.plugMelted()).isTrue();
        assertThat(t).as("с от оголения до выплавления").isBetween(3.0, 60.0);
        assertThat(out.crownUncovered).isTrue();
        assertThat(b.isLit()).isFalse();
        assertThat(b.ignite()).as("с выплавленной пробкой не разжечь").isFalse();
        for (int k = 0; k < 20 * 600; k++) b.stepFast(DT, low, out);
        assertThat(b.rupture()).isEqualTo(BoilerRupture.NONE);
        assertThat(out.crownTempK).isLessThan(FireTubeBoiler.CROWN_FAILURE_K);
        b.replacePlug();
        assertThat(b.ignite()).isTrue();
    }

    /** Пробку заглушили болтом: упуск воды кончается смятием потолка — взрывом котла. */
    @Test
    void withoutPlugCrownCollapses() {
        BoilerOutputs out = new BoilerOutputs();
        FireTubeBoiler b = steady(0.3, 1, out);
        b.setPlugFitted(false);
        BoilerInputs low = water(SPEC.crownHeightM() - 0.1);
        low.fuelFeedKgS = 0.3;
        double t = 0;
        while (b.rupture() == BoilerRupture.NONE && t < 1200) {
            b.stepFast(DT, low, out);
            t += DT;
        }
        assertThat(b.rupture()).isEqualTo(BoilerRupture.CROWN_COLLAPSE);
        assertThat(t).as("с до смятия").isBetween(30.0, 600.0);
        assertThat(b.isLit()).isFalse();
    }

    /** Заклиненный ПК: давление выше прочности обечайки — разрыв. */
    @Test
    void overpressureRupturesShell() {
        BoilerOutputs out = new BoilerOutputs();
        FireTubeBoiler b = steady(0.3, 1, out);
        BoilerInputs in = water(SPEC.normalLevelM());
        in.waterPressurePa = 0.99 * SPEC.burstPressureFactor() * P;
        b.stepFast(DT, in, out);
        assertThat(b.rupture()).isEqualTo(BoilerRupture.NONE);
        in.waterPressurePa = SPEC.burstPressureFactor() * P;
        b.stepFast(DT, in, out);
        assertThat(b.rupture()).isEqualTo(BoilerRupture.SHELL_OVERPRESSURE);
        assertThat(SPEC.reliefSetPressure()).isCloseTo(1.32e6, within(1.0));
    }

    /** 07 §7.9.4: насыщенная вода 1,2 МПа → ≈ 64 кДж/кг; 10 т → 0,64 ГДж; f_blast 0,3 → 46 кг ТНТ. */
    @Test
    void boilerExplosionEnergy() {
        double w = BoilerExplosion.expansionWorkJPerKg(W, P, 0, BoilerExplosion.ATMOSPHERE_PA);
        assertThat(w).isCloseTo(64e3, within(0.02 * 64e3));
        double e = BoilerExplosion.energyJ(W, P, 10_000, 0);
        assertThat(e).isCloseTo(0.64e9, within(0.02 * 0.64e9));
        assertThat(BoilerExplosion.tntEquivalentKg(e, BoilerExplosion.BLAST_FRACTION)).isCloseTo(46, within(1.5));
        // пар на кг работает больше воды, но его в котле мало; при атмосферном давлении работы нет
        assertThat(BoilerExplosion.expansionWorkJPerKg(W, P, 1, BoilerExplosion.ATMOSPHERE_PA)).isGreaterThan(5 * w);
        assertThat(BoilerExplosion.energyJ(W, BoilerExplosion.ATMOSPHERE_PA, 10_000, 0)).isZero();
    }

    /** Горячий цикл котла и машины не аллоцирует (07 §7.19.2: буферы переиспользуются). */
    @Test
    void hotLoopDoesNotAllocate() {
        BoilerOutputs out = new BoilerOutputs();
        FireTubeBoiler b = steady(0.3, 1, out);
        BoilerInputs in = water(SPEC.normalLevelM());
        in.fuelFeedKgS = 0.3;
        SteamEngineUnit u = new SteamEngineUnit(SteamEngineUnit.Spec.reference(2, 16_000),
                FlyballGovernor.withDashpot());
        double load = 0.9 * u.engine().meanIndicatedTorqueNm(1.1e6, 0.11e6, 0.25);
        u.shaft().setOmega(u.nominalOmega());
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        for (int k = 0; k < 20_000; k++) {
            b.stepFast(DT, in, out);
            u.step(DT, 1.1e6, 5.64, 0.11e6, load);
        }
        long before = mx.getThreadAllocatedBytes(thread);
        for (int k = 0; k < 100_000; k++) {
            b.stepFast(DT, in, out);
            u.step(DT, 1.1e6, 5.64, 0.11e6, load);
        }
        long allocated = mx.getThreadAllocatedBytes(thread) - before;
        assertThat(allocated / 100_000).as("bytes per tick (total %d B)", allocated).isZero();
    }
}
