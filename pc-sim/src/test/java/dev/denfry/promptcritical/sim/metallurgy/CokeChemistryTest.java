package dev.denfry.promptcritical.sim.metallurgy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.combustion.BurnerKind;
import dev.denfry.promptcritical.sim.combustion.BurnerSpec;
import dev.denfry.promptcritical.sim.combustion.Combustor;
import dev.denfry.promptcritical.sim.combustion.FuelGas;
import dev.denfry.promptcritical.sim.mining.CoalAssay;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Коксохим (08 §8.4): батарея, тушение, электроды; подготовка руды (08 §8.5.1). */
class CokeChemistryTest {
    private static final double DT = 72;
    private static final IronmakingConfig CFG = IronmakingConfig.defaults();
    /** Шихта Ж+К+ОС: V^daf 27 %, A^d 8 %, W 8 %, S 0,8 %, y 17 мм. */
    private static final CoalAssay BLEND = CoalAssay.typical(0.27, 0.08, 0.08, 0.008, 17);
    private final Combustor cog = new Combustor(CokeBattery.COKE_OVEN_GAS, BurnerSpec.of(BurnerKind.GAS, 0.2));

    /** Регулятор обогрева: держит простенки у заданной температуры (как оператор по пирометру). */
    private void holdWall(CokeBattery b, double targetK) {
        double need = CokeBattery.WALL_LOSS_W_K * b.ovenCount() * (b.wallTempK() - 293.15);
        for (int i = 0; i < b.ovenCount(); i++)
            if (b.oven(i) == OvenState.COKING || b.oven(i) == OvenState.STICKER) need += 2.0e5;
        need += CokeBattery.WALL_HEAT_CAPACITY_J_K * b.ovenCount() * (targetK - b.wallTempK()) / 3600;
        double fuel = Math.max(0, need / (CokeBattery.HEATING_EFFICIENCY * cog.lhv()));
        b.setHeating(fuel, cog.airFor(fuel, 1.15));
        b.stepSlow(DT, cog);
    }

    private static CokeBattery hotBattery() {
        return new CokeBattery(CFG, 6, 6000, CokeBattery.REF_WALL_K);
    }

    /** T8.18: b = 0,45, 1300 °C — 16,2 ч физ. = 13,5 мин реальных; поправки на температуру и влагу. */
    @Test
    void t818CokingTime() {
        CokeBattery b = hotBattery();
        double tau = b.cokingTimeS(CokeBattery.REF_WALL_K, CokeBattery.REF_MOISTURE);
        assertThat(tau / 3600).isCloseTo(16.2, within(16.2 * 0.02));
        assertThat(tau / 72 / 60).isCloseTo(13.5, within(13.5 * 0.02));
        assertThat(b.cokingTimeS(CokeBattery.REF_WALL_K - 10, 0.08) / tau).isCloseTo(1.04, within(1e-9));
        assertThat(b.cokingTimeS(CokeBattery.REF_WALL_K, 0.09) / tau).isCloseTo(1.015, within(1e-9));
    }

    /** Цикл камеры: загрузка → коксование ≈ τ → выдача; выход, M₂₅, газ и продукты §8.4.2. */
    @Test
    void ovenCycleYieldsAndGas() {
        CokeBattery b = hotBattery();
        assertThat(b.charge(0, 4000, BLEND)).isEqualTo(4000);
        assertThat(b.oven(0)).isEqualTo(OvenState.CHARGING);
        assertThat(b.charge(0, 4000, BLEND)).isEqualTo(2000);
        assertThat(b.oven(0)).isEqualTo(OvenState.COKING);
        assertThat(b.chargePitch(0, 100)).isZero();

        double t = 0, gas = 0;
        while (b.oven(0) == OvenState.COKING && t < 30 * 3600) {
            holdWall(b, CokeBattery.REF_WALL_K);
            gas += b.gasMakeNm3S() * DT;
            t += DT;
        }
        assertThat(b.oven(0)).isEqualTo(OvenState.READY);
        assertThat(t / 3600).isCloseTo(16.2, within(16.2 * 0.03));
        assertThat(b.centerTempK(0)).isGreaterThanOrEqualTo(CokeBattery.CENTER_READY_K);

        double dry = 6000 * 0.92, vd = 0.27 * 0.92;
        assertThat(gas / dry * 1000).isCloseTo(320, within(10.0));
        assertThat(b.takeByproduct(CokeByproduct.TAR) / dry * 1000).isCloseTo(35, within(1.0));
        assertThat(b.takeByproduct(CokeByproduct.TAR)).isZero();
        assertThat(b.byproductKg(CokeByproduct.AMMONIA_LIQUOR) / dry * 1000).isBetween(100.0, 120.0);

        CokeBattery.PushResult r = b.push(0, false);
        double y = 1 - 0.9 * vd;
        assertThat(y).isBetween(0.75, 0.78);
        assertThat(r.cokeKg() + r.breezeKg()).isCloseTo(dry * y, within(1.0));
        assertThat(r.breezeKg() / (r.cokeKg() + r.breezeKg())).isCloseTo(0.04, within(1e-9));
        assertThat(r.assay().strengthM25()).isCloseTo(88, within(1e-9));
        assertThat(r.assay().ashDryFrac()).isCloseTo(0.08 / y, within(1e-9));
        assertThat(r.assay().sulfurDryFrac()).isCloseTo(0.65 * 0.008 / y, within(1e-9));
        assertThat(r.assay().volatileDaf()).isLessThan(0.02);
        assertThat(b.oven(0)).isEqualTo(OvenState.EMPTY);
    }

    /** Коксовый газ 07 §7.8.1: 18,1 МДж/нм³. */
    @Test
    void cokeOvenGasHeatingValue() {
        double[] x = new double[FuelGas.values().length];
        x[FuelGas.H2.ordinal()] = 0.58;
        x[FuelGas.CH4.ordinal()] = 0.26;
        x[FuelGas.CO.ordinal()] = 0.06;
        x[FuelGas.C2H4.ordinal()] = 0.03;
        x[FuelGas.CO2.ordinal()] = 0.02;
        x[FuelGas.N2.ordinal()] = 0.05;
        assertThat(FuelGas.mixtureLhvJPerNm3(x) / 1e6).isCloseTo(18.1, within(0.2));
        assertThat(CokeBattery.COKE_OVEN_GAS.lhv() * FuelGas.mixtureDensityKgPerNm3(x) / 1e6)
                .isCloseTo(18.1, within(0.2));
    }

    /** Раннее выталкивание: без force — блокировка; с force — STICKER и перегрузка поршня; потом дококсуется. */
    @Test
    void earlyPushMakesSticker() {
        CokeBattery b = hotBattery();
        List<Integer> overloads = new ArrayList<>();
        b.setEventSink(new IronmakingEvents() {
            @Override
            public void cokePushOverload(int oven) {
                overloads.add(oven);
            }
        });
        b.charge(2, 6000, BLEND);
        for (int k = 0; k < 200; k++) holdWall(b, CokeBattery.REF_WALL_K);
        assertThat(b.push(2, false)).isNull();
        assertThat(b.oven(2)).isEqualTo(OvenState.COKING);
        assertThat(b.push(2, true)).isNull();
        assertThat(b.oven(2)).isEqualTo(OvenState.STICKER);
        assertThat(overloads).containsExactly(2);
        while (b.oven(2) == OvenState.STICKER) holdWall(b, CokeBattery.REF_WALL_K);
        assertThat(b.oven(2)).isEqualTo(OvenState.READY);
        assertThat(b.push(2, false)).isNotNull();
    }

    /** Передержка: сверх 2 ч — пережог (−M₂₅, угар). */
    @Test
    void overcookingLowersStrength() {
        CokeBattery b = hotBattery();
        b.charge(0, 6000, BLEND);
        while (b.oven(0) != OvenState.READY) holdWall(b, CokeBattery.REF_WALL_K);
        for (int k = 0; k < 4 * 3600 / DT; k++) holdWall(b, CokeBattery.REF_WALL_K);
        CokeBattery.PushResult r = b.push(0, false);
        assertThat(r.overcookH()).isCloseTo(2, within(0.05));
        assertThat(r.assay().strengthM25()).isCloseTo(86, within(0.1));
        double y = 1 - 0.9 * 0.27 * 0.92;
        assertThat(r.cokeKg() + r.breezeKg()).isCloseTo(6000 * 0.92 * y * 0.99, within(5.0));
    }

    /** M₂₅: шихта не по рецепту — слабый кокс. */
    @Test
    void strengthFormula() {
        assertThat(CokeBattery.strengthM25(17, 0.27, 0.08)).isEqualTo(88);
        assertThat(CokeBattery.strengthM25(10, 0.35, 0.10)).isCloseTo(88 - 8.4 - 6.4 - 1.2, within(1e-9));
        assertThat(CokeBattery.strengthM25(10, 0.35, 0.10)).isLessThan(80);
    }

    /** Холодная камера (простенки &lt; 950 °C) не коксуется. */
    @Test
    void coldWallsDoNotCoke() {
        CokeBattery b = new CokeBattery(CFG, 3, 6000, 1173.15);
        b.charge(0, 6000, BLEND);
        b.stepSlow(DT, null);
        assertThat(b.progress(0)).isZero();
    }

    /** Динас: разогрев быстрее 7 сут — трещины; разогретую батарею нельзя охлаждать ниже 600 °C. */
    @Test
    void silicaBrickRules() {
        CokeBattery fast = new CokeBattery(CFG, 3, 6000, 293.15);
        double fuel = 0.05;
        fast.setHeating(fuel, cog.airFor(fuel, 1.15));
        for (int k = 0; k < 3 * 86_400 / DT && !fast.refractoryFailed(); k++) fast.stepSlow(DT, cog);
        assertThat(fast.refractoryFailed()).isTrue();

        CokeBattery slow = new CokeBattery(CFG, 3, 6000, 293.15);
        double rate = 0.95 * (CokeBattery.WALL_HOT_K - 293.15) / (7 * 86_400);
        double target = 293.15;
        while (!slow.heatedUp()) {
            target += rate * DT;
            holdWall(slow, target);
            assertThat(slow.refractoryFailed()).isFalse();
        }
        assertThat(slow.refractoryDamage()).isLessThan(0.2);

        boolean[] failed = {false};
        CokeBattery hot = hotBattery();
        hot.setEventSink(new IronmakingEvents() {
            @Override
            public void refractoryFailed() {
                failed[0] = true;
            }
        });
        while (hot.wallTempK() > 900) hot.stepSlow(DT, null);
        assertThat(hot.refractoryFailed()).isFalse();
        while (hot.wallTempK() > CFG.minWallTempK()) hot.stepSlow(DT, null);
        hot.stepSlow(DT, null);
        assertThat(hot.refractoryFailed()).isTrue();
        assertThat(failed[0]).isTrue();
    }

    /** Пековый кокс (08 §8.4.5): выход 0,62, зола &lt; 0,3 %; с углём не смешивается. */
    @Test
    void pitchCoke() {
        CokeBattery b = hotBattery();
        assertThat(b.chargePitch(1, 6000)).isEqualTo(6000);
        assertThat(b.charge(1, 10, BLEND)).isZero();
        while (b.oven(1) != OvenState.READY) holdWall(b, CokeBattery.REF_WALL_K);
        CokeBattery.PushResult r = b.push(1, false);
        assertThat(r.pitchCoke()).isTrue();
        assertThat(r.cokeKg()).isCloseTo(6000 * 0.62, within(1.0));
        assertThat(r.assay().ashDryFrac()).isLessThan(0.003);
    }

    /** Тушение §8.4.3: СТК 1,5·850 = 1,28 ГДж/т; мокрое — 0,45 т пара/т; газ СТК. */
    @Test
    void quenching() {
        assertThat(CokeQuenching.cdqHeatJ(1000, 1050 + 273.15) / 1e9).isCloseTo(1.275, within(0.01));
        assertThat(CokeQuenching.cdqSteamKg(1000, 1323.15, 0.85, 2.2e6)).isCloseTo(493, within(5.0));
        assertThat(CokeQuenching.wetQuenchSteamKg(1000)).isEqualTo(450);
        assertThat(CokeQuenching.cdqGasTrip(0.005, 0.10)).isFalse();
        assertThat(CokeQuenching.cdqGasTrip(0.015, 0.10)).isTrue();
        assertThat(CokeQuenching.cdqGasTrip(0.005, 0.13)).isTrue();
    }

    /** Формование: 72/28 при 160 °C — годная заготовка; связующее вне окна — трещина; холодная масса не идёт. */
    @Test
    void electrodeExtrusion() {
        ElectrodeState e = ElectrodeExtruder.extrude(720, 280, 433.15, 0.3);
        assertThat(e.cracked()).isFalse();
        assertThat(e.stage()).isEqualTo(ElectrodeState.GREEN);
        assertThat(e.lengthM()).isCloseTo(1000 / 1650.0 / (Math.PI * 0.09 / 4), within(1e-9));
        assertThat(ElectrodeExtruder.extrude(800, 200, 433.15, 0.3).cracked()).isTrue();
        assertThat(ElectrodeExtruder.extrude(720, 280, 413.15, 0.3)).isNull();
    }

    private void bake(BakingFurnace f, double rateKPerH, double topK, double soakS) {
        double sp = f.tempK();
        double soaked = 0;
        while (soaked < soakS) {
            sp = Math.min(topK, sp + rateKPerH * DT / 3600);
            double need = BakingFurnace.LOSS_W_K * (f.tempK() - 293.15)
                    + BakingFurnace.HEAT_CAPACITY_J_K * (sp - f.tempK()) / DT;
            double fuel = Math.max(0, need / (BakingFurnace.EFFICIENCY * cog.lhv()));
            f.setHeating(fuel, cog.airFor(fuel, 1.15));
            f.stepSlow(DT, cog);
            if (sp >= topK) soaked += DT;
        }
        f.setHeating(0, 0);
        while (f.tempK() > 500) f.stepSlow(DT, cog);
    }

    /** Обжиг: ≤ 10 К/ч в зоне 300–600 °C и сутки при ≥ 800 °C — BAKED; быстрее — трещины; недодержка — сырой. */
    @Test
    void electrodeBaking() {
        BakingFurnace f = new BakingFurnace(4);
        ElectrodeState green = ElectrodeExtruder.extrude(720, 280, 433.15, 0.3);
        assertThat(f.load(green)).isTrue();
        assertThat(f.load(green.withStage(ElectrodeState.BAKED))).isFalse();
        bake(f, 8, 1123.15, BakingFurnace.SOAK_S);
        assertThat(f.crackedRun()).isFalse();
        List<ElectrodeState> out = f.unload();
        assertThat(out).hasSize(1);
        assertThat(out.get(0).stage()).isEqualTo(ElectrodeState.BAKED);
        assertThat(out.get(0).cracked()).isFalse();

        f.load(green);
        bake(f, 25, 1123.15, BakingFurnace.SOAK_S);
        assertThat(f.unload().get(0).cracked()).isTrue();

        f.load(green);
        bake(f, 8, 1123.15, 3600);
        assertThat(f.unload().get(0).stage()).isEqualTo(ElectrodeState.GREEN);
    }

    /** Горячая выгрузка (&gt; 300 °C) — трещины; в горячую печь не садят. */
    @Test
    void hotUnloadCracks() {
        BakingFurnace f = new BakingFurnace(2);
        ElectrodeState green = ElectrodeExtruder.extrude(720, 280, 433.15, 0.3);
        f.load(green);
        double fuel = 0.02;
        f.setHeating(fuel, cog.airFor(fuel, 1.15));
        while (f.tempK() < 700) f.stepSlow(DT, cog);
        assertThat(f.load(green)).isFalse();
        assertThat(f.unload().get(0).cracked()).isTrue();
    }

    /** Магнитная сепарация магнетитового кварцита и агломерация §8.5.1. */
    @Test
    void oreDressing() {
        OreDressing.Split s = OreDressing.magneticSeparation(1000, 0.35, 1);
        assertThat(s.concentrateFe()).isCloseTo(0.66, within(1e-9));
        assertThat(s.concentrateKg() * s.concentrateFe()).isCloseTo(0.85 * 350, within(1e-6));
        assertThat(s.concentrateKg() * s.concentrateFe() + s.tailingsKg() * s.tailingsFe())
                .isCloseTo(350, within(1e-6));
        assertThat(OreDressing.magneticSeparation(1000, 0.55, 0).concentrateKg()).isZero();

        double lime = OreDressing.limestoneFor(1000, s.concentrateSiO2(), 0, 1.5);
        OreDressing.Sinter sinter = OreDressing.sinter(1000, 0.66, s.concentrateSiO2(), 0, 55, lime);
        assertThat(sinter.feFrac()).isBetween(0.52, 0.58);
        assertThat(sinter.basicity()).isCloseTo(1.5, within(0.06));
        assertThat(sinter.weak()).isFalse();
        assertThat(OreDressing.sinter(1000, 0.66, s.concentrateSiO2(), 0, 20, lime).weak()).isTrue();
    }
}
