package dev.denfry.promptcritical.sim.combustion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Водотрубный котёл I1 (07 §7.9.5), BMS (07 §7.9.6, доменная часть T7.24), экраны: накипь, коррозия, ползучесть
 * (07 §7.9.3). Вода и пар заданы граничными условиями.
 */
class WaterTubeBoilerTest {
    private static final WaterProperties W = WaterProperties.tables();
    private static final double DT = 0.05, P = 9.8e6, TSAT = W.saturationP(P).tSat();
    /** Природный газ: CH₄ 95 %, C₂H₆ 2,5 %, C₃H₈ 0,5 %, CO₂ 0,5 %, N₂ 1,5 %. */
    private static final double[] NG = {0.95, 0.025, 0.005, 0, 0, 0, 0, 0.005, 0.015, 0, 0, 0};
    private static final FuelSpec GAS = FuelSpec.fromGasComposition("natural_gas", NG);
    private static final WaterTubeBoilerSpec SPEC = WaterTubeBoilerSpec.defaults();
    private static final double AIR = 1.1 * GAS.stoichAirKgPerKg() * SPEC.ratedFuelKgS();

    /** Журнал событий котла. */
    private static final class Log implements BoilerEvents {
        final List<MftCause> mft = new ArrayList<>();
        final List<Integer> burnerTrips = new ArrayList<>();
        double explosionJ;
        int leaks;

        @Override public void masterFuelTrip(MftCause cause) { mft.add(cause); }
        @Override public void burnerTripped(int burner) { burnerTrips.add(burner); }
        @Override public void furnaceExplosion(double energyJ) { explosionJ = energyJ; }
        @Override public void tubeLeak() { leaks++; }
    }

    private static WaterTubeBoiler boiler(Log log) {
        return new WaterTubeBoiler(SPEC, GAS, FlammabilityLimits.ofGas(NG), log, 288.15);
    }

    /** Барабан на рабочем уровне, вентиляторы на номинале, номинальные пар и питательная вода. */
    private static BoilerInputs inputs() {
        BoilerInputs in = new BoilerInputs();
        in.waterTempK = TSAT;
        in.waterPressurePa = P;
        in.waterLevelM = 0.5;
        in.forcedAirKgS = AIR;
        in.inducedDraftKgS = 1.05 * (AIR + SPEC.ratedFuelKgS());
        in.steamFlowKgS = 37;
        in.steamInletK = TSAT;
        in.steamCpJKgK = 3_275;
        in.feedwaterFlowKgS = 37;
        in.feedwaterInletK = 503.15;
        in.feedwaterCpJKgK = 4_900;
        return in;
    }

    private static void run(WaterTubeBoiler b, BoilerInputs in, BoilerOutputs out, double seconds) {
        for (int k = 0, n = (int) Math.round(seconds / DT); k < n; k++) b.stepFast(DT, in, out);
    }

    /** Продувка на номинальном воздухе, розжиг горелки 0 при 30 % воздуха и 0,6 кг/с в коллекторе. */
    private static void lightOff(WaterTubeBoiler b, BoilerInputs in, BoilerOutputs out) {
        BurnerManagementSystem bms = b.bmsControl();
        run(b, in, out, 1);
        assertThat(bms.requestPurge()).isEqualTo(BmsResponse.ACCEPTED);
        run(b, in, out, 301);
        assertThat(b.bms()).isEqualTo(BmsState.PURGE_COMPLETE);
        in.forcedAirKgS = 0.3 * AIR;
        assertThat(bms.requestIgniter(0)).isEqualTo(BmsResponse.ACCEPTED);
        run(b, in, out, 1);
        assertThat(b.bms()).isEqualTo(BmsState.IGNITER_ON);
        in.fuelFeedKgS = 0.6;
        assertThat(bms.requestMainFuel(0)).isEqualTo(BmsResponse.ACCEPTED);
        run(b, in, out, 1);
        assertThat(b.bms()).isEqualTo(BmsState.RUNNING);
    }

    /** Все горелки в работе на номинале. */
    private static void fullLoad(WaterTubeBoiler b, BoilerInputs in, BoilerOutputs out) {
        lightOff(b, in, out);
        for (int i = 1; i < SPEC.burners(); i++) assertThat(b.bmsControl().requestIgniter(i)).isEqualTo(BmsResponse.ACCEPTED);
        run(b, in, out, 1);
        for (int i = 1; i < SPEC.burners(); i++) assertThat(b.bmsControl().requestMainFuel(i)).isEqualTo(BmsResponse.ACCEPTED);
        in.fuelFeedKgS = SPEC.ratedFuelKgS();
        in.forcedAirKgS = AIR;
        run(b, in, out, 100);
    }

    /**
     * Пример 07 §7.9.5: газ 2,0 кг/с, η ≈ 0,92 → пар 9,8 МПа/540 °C из воды 230 °C, Δh = 2484 кДж/кг → ≈ 37 кг/с.
     * Газы за топкой 1100–1250 °C, уходящие 120–160 °C, в топке разрежение.
     */
    @Test
    void nominalLoad() {
        BoilerInputs in = inputs();
        BoilerOutputs out = new BoilerOutputs();
        WaterTubeBoiler b = boiler(new Log());
        fullLoad(b, in, out);
        assertThat(out.fuelBurnKgS).isCloseTo(2.0, within(1e-6));
        assertThat(out.combustion.excessAir).isCloseTo(1.1, within(1e-3));
        assertThat(out.efficiency).isBetween(0.90, 0.94);
        double steam = (out.heatToWaterW + out.convectiveHeatW) / (3475e3 - 991e3);
        assertThat(steam).isCloseTo(37, within(0.05 * 37));
        assertThat(out.furnaceExitK - 273.15).isBetween(1050.0, 1250.0);
        assertThat(out.fluegasExitK - 273.15).isBetween(120.0, 160.0);
        assertThat(TSAT + out.superheaterHeatW / (37 * 3_275) - 273.15).as("перегрев").isBetween(520.0, 560.0);
        assertThat(503.15 + out.economizerHeatW / (37 * 4_900)).as("экономайзер не кипит").isLessThan(TSAT);
        assertThat(out.airPreheatK - 273.15).isBetween(150.0, 350.0);
        assertThat(out.furnacePressurePa).isBetween(-50.0, -10.0);
        assertThat(out.flame).isTrue();
        assertThat(out.furnaceFuelKg).isZero();
        // экраны под водой: металл на десятки кельвинов горячее воды
        assertThat(out.screenMetalK - TSAT).isBetween(10.0, 60.0);
    }

    /** T7.24: топливо и запальник без продувки, продувка без вентиляторов — блокировка. */
    @Test
    void interlocksWithoutPurge() {
        BoilerInputs in = inputs();
        BoilerOutputs out = new BoilerOutputs();
        WaterTubeBoiler b = boiler(new Log());
        BurnerManagementSystem bms = b.bmsControl();
        assertThat(bms.requestMainFuel(0)).isEqualTo(BmsResponse.INTERLOCK_PURGE);
        assertThat(bms.requestIgniter(0)).isEqualTo(BmsResponse.INTERLOCK_PURGE);
        assertThat(bms.requestIgniter(9)).isEqualTo(BmsResponse.INTERLOCK_BURNER);
        in.forcedAirKgS = 0;
        run(b, in, out, 1);
        assertThat(bms.requestPurge()).isEqualTo(BmsResponse.INTERLOCK_FANS);
        in.forcedAirKgS = AIR;
        run(b, in, out, 1);
        assertThat(bms.requestPurge()).isEqualTo(BmsResponse.ACCEPTED);
        assertThat(bms.requestMainFuel(0)).isEqualTo(BmsResponse.INTERLOCK_PURGE);
        in.fuelFeedKgS = 2;
        run(b, in, out, 10);
        assertThat(out.fuelBurnKgS).isZero();
        assertThat(b.accumulatedFuelKg()).isZero();
    }

    /**
     * Продувка: ≥ 300 с и ≥ 5 смен объёма при воздухе ≥ 25 %. На номинале хватает 300 с, на 25 % воздуха пять смен
     * объёма набираются дольше; ниже 25 % отсчёт сбрасывается; окно на запальник — 300 с.
     */
    @Test
    void purgeConditions() {
        BoilerInputs in = inputs();
        BoilerOutputs out = new BoilerOutputs();
        WaterTubeBoiler b = boiler(new Log());
        BurnerManagementSystem bms = b.bmsControl();
        run(b, in, out, 1);
        bms.requestPurge();
        run(b, in, out, 299);
        assertThat(b.bms()).isEqualTo(BmsState.PURGE);
        run(b, in, out, 1.1);
        assertThat(b.bms()).isEqualTo(BmsState.PURGE_COMPLETE);
        assertThat(bms.purgeVolumeChanges()).isGreaterThan(5);
        run(b, in, out, 300);
        assertThat(b.bms()).as("окно на запальник истекло").isEqualTo(BmsState.OFF);

        // 25 % воздуха: смены объёма — узкое место
        in.forcedAirKgS = 0.26 * AIR;
        in.inducedDraftKgS = 0.3 * AIR;
        bms.requestPurge();
        run(b, in, out, 310);
        assertThat(b.bms()).isEqualTo(BmsState.PURGE);
        double rate = bms.purgeVolumeChanges() / bms.purgeElapsedS();
        double expected = BurnerManagementSystem.PURGE_MIN_VOLUME_CHANGES / rate;
        run(b, in, out, expected - 310 - 2);
        assertThat(b.bms()).isEqualTo(BmsState.PURGE);
        run(b, in, out, 3);
        assertThat(b.bms()).isEqualTo(BmsState.PURGE_COMPLETE);

        // провал воздуха ниже 25 % сбрасывает отсчёт
        b.bmsControl().requestShutdown();
        in.forcedAirKgS = AIR;
        in.inducedDraftKgS = 1.05 * AIR;
        run(b, in, out, 1);
        bms.requestPurge();
        run(b, in, out, 200);
        in.forcedAirKgS = 0.2 * AIR;
        run(b, in, out, 1);
        assertThat(bms.purgeElapsedS()).isZero();
    }

    /** T7.24: запальник не загорелся за 10 с — MFT; клапаны закрыты не дольше 1 с; пост-продувка 300 с → OFF. */
    @Test
    void igniterTrialFailureTrips() {
        BoilerInputs in = inputs();
        BoilerOutputs out = new BoilerOutputs();
        Log log = new Log();
        WaterTubeBoiler b = boiler(log);
        BurnerManagementSystem bms = b.bmsControl();
        b.setIgniterFault(0, true);
        run(b, in, out, 1);
        bms.requestPurge();
        run(b, in, out, 301);
        bms.requestIgniter(0);
        double t = 0;
        while (b.bms() == BmsState.IGNITER_TRIAL && t < 20) {
            b.stepFast(DT, in, out);
            t += DT;
        }
        assertThat(t).isCloseTo(BurnerManagementSystem.IGNITER_TRIAL_S, within(2 * DT));
        assertThat(b.bms()).isEqualTo(BmsState.MFT);
        assertThat(log.mft).containsExactly(MftCause.IGNITER_TRIAL_FAILED);
        double closed = 0;
        while (b.bms() == BmsState.MFT && closed < 5) {
            b.stepFast(DT, in, out);
            closed += DT;
        }
        assertThat(closed).isLessThanOrEqualTo(1.0);
        assertThat(bms.igniterOpen(0)).isFalse();
        assertThat(b.bms()).isEqualTo(BmsState.POST_PURGE);
        run(b, in, out, 299);
        assertThat(b.bms()).isEqualTo(BmsState.POST_PURGE);
        run(b, in, out, 2);
        assertThat(b.bms()).isEqualTo(BmsState.OFF);
        assertThat(b.accumulatedFuelKg()).as("запальник 10 с — газа меньше предела").isLessThan(0.1);
        assertThat(b.rupture()).isEqualTo(BoilerRupture.NONE);
    }

    /** Основной факел не появился за 5 с (газ, клапан коллектора закрыт) — MFT. */
    @Test
    void mainTrialFailureTrips() {
        BoilerInputs in = inputs();
        BoilerOutputs out = new BoilerOutputs();
        Log log = new Log();
        WaterTubeBoiler b = boiler(log);
        BurnerManagementSystem bms = b.bmsControl();
        run(b, in, out, 1);
        bms.requestPurge();
        run(b, in, out, 301);
        in.forcedAirKgS = 0.3 * AIR;
        bms.requestIgniter(0);
        run(b, in, out, 1);
        in.fuelFeedKgS = 0;
        bms.requestMainFuel(0);
        run(b, in, out, BurnerManagementSystem.MAIN_TRIAL_GAS_S - 0.2);
        assertThat(b.bms()).isEqualTo(BmsState.MAIN_TRIAL);
        run(b, in, out, 0.4);
        assertThat(log.mft).containsExactly(MftCause.MAIN_TRIAL_FAILED);
    }

    /** Погасли все факелы — MFT за тик, топливо отсечено за 1 с, накопленного газа меньше нижнего предела. */
    @Test
    void flameLossTrips() {
        BoilerInputs in = inputs();
        BoilerOutputs out = new BoilerOutputs();
        Log log = new Log();
        WaterTubeBoiler b = boiler(log);
        fullLoad(b, in, out);
        b.setRefractoryTemperature(500);
        for (int i = 0; i < SPEC.burners(); i++) b.disturbFlame(i);
        b.stepFast(DT, in, out);
        assertThat(out.flame).isFalse();
        run(b, in, out, 2 * DT);
        assertThat(log.mft).containsExactly(MftCause.FLAME_LOSS);
        run(b, in, out, 1);
        assertThat(out.fuelBurnKgS).isZero();
        assertThat(b.furnaceFuelConcentration()).isLessThan(FlammabilityLimits.ofGas(NG).lowerKgM3() / 3);
        run(b, in, out, 400);
        assertThat(b.accumulatedFuelKg()).as("пост-продувка вынесла газ").isLessThan(1e-3);
        assertThat(b.rupture()).isEqualTo(BoilerRupture.NONE);
    }

    /** Одна погасшая горелка из четырёх отключается одна, котёл в работе. */
    @Test
    void singleBurnerTrip() {
        BoilerInputs in = inputs();
        BoilerOutputs out = new BoilerOutputs();
        Log log = new Log();
        WaterTubeBoiler b = boiler(log);
        fullLoad(b, in, out);
        b.setRefractoryTemperature(500);
        b.bmsControl().setFlameDetectorFault(2, FlameDetectorFault.STUCK_DARK);
        run(b, in, out, 1);
        assertThat(log.burnerTrips).containsExactly(2);
        assertThat(b.bms()).isEqualTo(BmsState.RUNNING);
        assertThat(b.bmsControl().mainValve(2)).isZero();
        assertThat(log.mft).isEmpty();
    }

    /** Остановился дымосос — MFT. */
    @Test
    void fanLossTrips() {
        BoilerInputs in = inputs();
        BoilerOutputs out = new BoilerOutputs();
        Log log = new Log();
        WaterTubeBoiler b = boiler(log);
        fullLoad(b, in, out);
        in.inducedDraftKgS = 0;
        run(b, in, out, 0.2);
        assertThat(log.mft).containsExactly(MftCause.FANS_LOST);
        assertThat(out.furnacePressurePa).as("без дымососа топка под наддувом").isGreaterThan(0);
    }

    /**
     * T7.24: с байпасом датчика пламени неисправный запальник «проходит» пробы, газ идёт в холодную топку без
     * факела и копится; искра запальника соседней горелки поджигает облако — взрыв топки E = m·Q_н·0,1.
     */
    @Test
    void bypassedFlameDetectorLeadsToFurnaceExplosion() {
        BoilerInputs in = inputs();
        BoilerOutputs out = new BoilerOutputs();
        Log log = new Log();
        WaterTubeBoiler b = boiler(log);
        BurnerManagementSystem bms = b.bmsControl();
        b.setIgniterFault(0, true);
        bms.setFlameDetectorBypass(0, true);
        run(b, in, out, 1);
        bms.requestPurge();
        run(b, in, out, 301);
        in.forcedAirKgS = 0.3 * AIR;
        bms.requestIgniter(0);
        run(b, in, out, 1);
        assertThat(b.bms()).isEqualTo(BmsState.IGNITER_ON);
        in.fuelFeedKgS = 2.0;
        bms.requestMainFuel(0);
        run(b, in, out, 1);
        assertThat(b.bms()).as("BMS «видит» факел").isEqualTo(BmsState.RUNNING);
        assertThat(out.flame).isFalse();
        FlammabilityLimits lim = FlammabilityLimits.ofGas(NG);
        double t = 0;
        while (b.furnaceFuelConcentration() < 1.3 * lim.lowerKgM3() && t < 600) {
            b.stepFast(DT, in, out);
            t += DT;
        }
        assertThat(lim.flammable(b.furnaceFuelConcentration())).isTrue();
        double m = b.accumulatedFuelKg();
        assertThat(bms.requestIgniter(1)).isEqualTo(BmsResponse.ACCEPTED);
        b.stepFast(DT, in, out);
        b.stepFast(DT, in, out);
        assertThat(b.rupture()).isEqualTo(BoilerRupture.FURNACE_EXPLOSION);
        double e = log.explosionJ;
        assertThat(e).isCloseTo(m * GAS.lhv() * WaterTubeBoiler.FURNACE_BLAST_FRACTION, within(0.02 * e));
        assertThat(b.explosionEnergyJ()).isEqualTo(e);
        assertThat(BoilerExplosion.tntEquivalentKg(e, 1)).as("десятки кг ТНТ").isBetween(5.0, 200.0);
        assertThat(b.bms()).isIn(BmsState.MFT, BmsState.POST_PURGE);
        run(b, in, out, 1);
        assertThat(out.fuelBurnKgS).isZero();
    }

    /** Без байпаса тот же неисправный запальник не даёт довести дело до основного топлива. */
    @Test
    void workingDetectorPreventsAccumulation() {
        BoilerInputs in = inputs();
        BoilerOutputs out = new BoilerOutputs();
        WaterTubeBoiler b = boiler(new Log());
        BurnerManagementSystem bms = b.bmsControl();
        b.setIgniterFault(0, true);
        run(b, in, out, 1);
        bms.requestPurge();
        run(b, in, out, 301);
        in.forcedAirKgS = 0.3 * AIR;
        in.fuelFeedKgS = 2.0;
        bms.requestIgniter(0);
        run(b, in, out, 5);
        assertThat(bms.requestMainFuel(0)).isEqualTo(BmsResponse.INTERLOCK_STATE);
        run(b, in, out, 10);
        assertThat(b.bms()).isIn(BmsState.MFT, BmsState.POST_PURGE);
        assertThat(b.rupture()).isEqualTo(BoilerRupture.NONE);
    }

    /** Накипь (07 §7.9.3): 5 мг-экв/л за 5 физ. суток при 300 кВт/м² — 0,5 мм и +150 К. */
    @Test
    void scaleCalibration() {
        double rate = BoilerScale.growthRate(5, BoilerScale.REF_FLUX);
        assertThat(rate * 5 * 86_400).isCloseTo(0.5e-3, within(1e-9));
        assertThat(BoilerScale.temperatureRise(300e3, 0.5e-3)).isCloseTo(150, within(1e-9));
        assertThat(BoilerScale.growthRate(0, 300e3)).isZero();
    }

    /** Ларсон–Миллер: опорные точки пресетов — 10⁵ ч. */
    @Test
    void larsonMillerPresets() {
        assertThat(LarsonMiller.CARBON.ruptureTimeH(55e6, 723.15)).isCloseTo(1e5, within(0.1e5));
        assertThat(LarsonMiller.CARBON.ruptureTimeH(27e6, 773.15)).isCloseTo(1e5, within(0.1e5));
        assertThat(LarsonMiller.CR_MO_V.ruptureTimeH(90e6, 833.15)).isCloseTo(1e5, within(0.1e5));
        assertThat(LarsonMiller.AUSTENITIC.ruptureTimeH(65e6, 923.15)).isCloseTo(1e5, within(0.1e5));
        assertThat(LarsonMiller.CARBON.ruptureTimeH(55e6, 823.15)).as("на 100 К горячее — часы").isLessThan(100);
    }

    /**
     * Сырая вода в котле I1: накипь растёт, металл экранов греется, ползучесть рвёт трубу; разрыв гасит факел (MFT).
     * С чистыми экранами за то же время ползучесть ничтожна.
     */
    @Test
    void scaleOverheatsScreensUntilTubeLeak() {
        BoilerInputs in = inputs();
        BoilerOutputs out = new BoilerOutputs();
        Log log = new Log();
        WaterTubeBoiler b = boiler(log);
        fullLoad(b, in, out);
        double clean = out.screenMetalK;
        b.setScale(1.5e-3);
        run(b, in, out, 1);
        assertThat(out.screenMetalK - clean).isCloseTo(BoilerScale.temperatureRise(out.screenFluxWm2, 1.5e-3), within(1.0));
        double hours = 0;
        while (!b.tubeLeak() && hours < 1000) {
            b.stepSlow(3600, 5, 0, 0);
            hours++;
        }
        assertThat(hours).as("накипь 1,5 мм — часы до разрыва").isBetween(2.0, 200.0);
        assertThat(log.leaks).isEqualTo(1);
        run(b, in, out, 1);
        assertThat(out.flame).isFalse();
        assertThat(log.mft).contains(MftCause.FLAME_LOSS);

        WaterTubeBoiler cleanBoiler = boiler(new Log());
        BoilerInputs in2 = inputs();
        fullLoad(cleanBoiler, in2, out);
        for (int h = 0; h < 1000; h++) cleanBoiler.stepSlow(3600, 0, 0, 0);
        assertThat(cleanBoiler.creepDamage()).isLessThan(1e-3);
        assertThat(cleanBoiler.scaleM()).isZero();
        b.repairScreens();
        b.removeScale(1);
        assertThat(b.tubeLeak()).isFalse();
        assertThat(b.scaleM()).isZero();
    }

    /** Упуск воды: циркуляция срывается, экраны омываются паром и рвутся за физ. минуты. */
    @Test
    void lowDrumLevelStarvesScreens() {
        BoilerInputs in = inputs();
        BoilerOutputs out = new BoilerOutputs();
        WaterTubeBoiler b = boiler(new Log());
        fullLoad(b, in, out);
        in.waterLevelM = 0.1;
        run(b, in, out, 1);
        assertThat(out.screenMetalK - TSAT).isGreaterThan(200);
        double minutes = 0;
        while (!b.tubeLeak() && minutes < 6000) {
            b.stepSlow(60, 0, 0, 0);
            minutes++;
        }
        assertThat(minutes).isBetween(2.0, 120.0);
    }

    /** Кислородная коррозия: недеаэрированная вода 8 мг/кг — ≈ 1 мм стенки в год. */
    @Test
    void oxygenCorrosionThinsScreens() {
        BoilerInputs in = inputs();
        BoilerOutputs out = new BoilerOutputs();
        WaterTubeBoiler b = boiler(new Log());
        fullLoad(b, in, out);
        for (int d = 0; d < 365; d++) b.stepSlow(86_400 * 1.000_684_5, 0, 8e-6, 0);
        assertThat(SPEC.screenWallM() - b.screenWallM()).isCloseTo(1e-3, within(1e-5));
        assertThat(b.tubeLeak()).isFalse();
    }
}
