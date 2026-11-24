package dev.denfry.promptcritical.sim.hydrogen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import org.junit.jupiter.api.Test;

/**
 * Водородная цепочка против справочных чисел: плотность H₂ по NIST (14,9 кг/м³ при 20 МПа и 20 °C, 17 §17.3.8),
 * щелочной электролиз 4,5–5 кВт·ч/Нм³ постоянного тока (Nel A485, Ulleberg 2003), отключение по 2 % H₂ в O₂ на малой
 * нагрузке, сжатие до 200 бар 2–3 кВт·ч/кг, PEM около 50 % по низшей теплоте (Larminie, Dicks).
 */
class HydrogenChainTest {
    private static final double AMB = 293.15;

    @Test
    void realGasDensityMatchesNistAt200Bar() {
        assertThat(HydrogenGas.density(20e6, 293.15)).isCloseTo(14.9, within(0.2));
        assertThat(HydrogenGas.z(20e6, 293.15)).isCloseTo(1.12, within(0.02));
        assertThat(HydrogenGas.density(101_325, 273.15)).isCloseTo(0.0899, within(0.001));
        double c = HydrogenGas.molarDensity(5e6, 300);
        assertThat(HydrogenGas.pressure(c, 300)).isCloseTo(5e6, within(1.0));
    }

    /** Прогреть батарею на номинале до 80 °C. */
    private static AlkalineElectrolyser warmElectrolyser() {
        AlkalineElectrolyser e = new AlkalineElectrolyser();
        e.setOn(true);
        for (int s = 0; s < 4 * 3600 && e.tempK() < 352.5; s++) e.step(1, 3e6, 100, true, 1.1e5, AMB);
        assertThat(e.tempK()).isGreaterThan(352);
        for (int s = 0; s < 600; s++) e.step(1, 3e6, 100, true, 1.1e5, AMB);
        return e;
    }

    @Test
    void electrolyserAtNominalMakesA485OutputAtTextbookEnergy() {
        AlkalineElectrolyser e = warmElectrolyser();
        assertThat(e.tempK()).isCloseTo(353.15, within(1.5));
        assertThat(e.cellVoltage()).isBetween(1.75, 1.95);
        assertThat(e.faraday()).isBetween(0.94, 0.97);
        assertThat(e.h2Nm3h()).isBetween(460.0, 510.0);
        assertThat(e.dcW()).isBetween(2.0e6, 2.4e6);
        assertThat(e.specificKwhPerNm3()).isBetween(4.4, 4.9);
        assertThat(e.hto()).isBetween(0.002, 0.005);
        // вода: 8,94 кг на кг водорода
        assertThat(e.waterUseKgS() / (e.h2MolS() * HydrogenGas.M)).isCloseTo(8.94, within(0.05));
        assertThat(e.trip()).isEqualTo(AlkalineElectrolyser.Trip.NONE);
    }

    @Test
    void coldStackNeedsMoreVoltage() {
        double cold = AlkalineElectrolyser.cellVoltage(4000, 293.15, 0.30), hot = AlkalineElectrolyser.cellVoltage(4000, 353.15, 0.30);
        assertThat(cold - hot).isGreaterThan(0.2);
        // щёлочь не той крепости — выше омика
        assertThat(AlkalineElectrolyser.cellVoltage(4000, 353.15, 0.40)).isGreaterThan(hot);
    }

    @Test
    void lowLoadDrivesHydrogenIntoOxygenAndTripsUntilPurged() {
        AlkalineElectrolyser e = warmElectrolyser();
        e.setLoad(0.10);
        int s = 0;
        while (e.trip() == AlkalineElectrolyser.Trip.NONE && s < 1800) {
            e.step(1, 3e6, 100, true, 1.1e5, AMB);
            s++;
        }
        assertThat(e.trip()).isEqualTo(AlkalineElectrolyser.Trip.HTO);
        assertThat(e.hto()).isGreaterThanOrEqualTo(AlkalineElectrolyser.HTO_TRIP);
        assertThat(e.on()).isFalse();
        e.setOn(true);
        assertThat(e.on()).as("без продувки не пускается").isFalse();
        e.purge();
        for (int k = 0; k < 130; k++) e.step(1, 3e6, 100, true, 1.1e5, AMB);
        assertThat(e.trip()).isEqualTo(AlkalineElectrolyser.Trip.NONE);
        e.setLoad(0.5);
        e.setOn(true);
        for (int k = 0; k < 900; k++) e.step(1, 3e6, 100, true, 1.1e5, AMB);
        assertThat(e.trip()).isEqualTo(AlkalineElectrolyser.Trip.NONE);
        assertThat(e.hto()).isLessThan(AlkalineElectrolyser.HTO_ALARM);
    }

    @Test
    void electrolyserWithoutCoolingTripsOnTemperatureAndOnBackPressure() {
        AlkalineElectrolyser e = warmElectrolyser();
        int s = 0;
        while (e.on() && s < 3600) {
            e.step(1, 3e6, 100, false, 1.1e5, AMB);
            s++;
        }
        assertThat(e.trip()).isEqualTo(AlkalineElectrolyser.Trip.OVERTEMP);
        assertThat(s).isBetween(300, 3000);

        AlkalineElectrolyser b = warmElectrolyser();
        b.step(1, 3e6, 100, true, 1.4e5, AMB);
        assertThat(b.trip()).isEqualTo(AlkalineElectrolyser.Trip.OUTLET_PRESSURE);
    }

    @Test
    void weakRectifierLowersTheCurrent() {
        AlkalineElectrolyser e = warmElectrolyser();
        e.step(1, 1.0e6, 100, true, 1.1e5, AMB);
        assertThat(e.dcW()).isCloseTo(1.0e6, within(5e3));
        assertThat(e.amps()).isLessThan(AlkalineElectrolyser.NOMINAL_A * 0.6);
    }

    @Test
    void compressorFillsTubeBankAt2To3KwhPerKgAndStopsAt200Bar() {
        HydrogenStorage tubes = new HydrogenStorage(HydrogenStorage.TUBE_BANK);
        tubes.take(tubes.mol(), 0); // не опустит ниже атмосферного
        DiaphragmCompressor c = new DiaphragmCompressor();
        c.setOn(true);
        // газгольдер держим на 1,2 бар — его подпитывает электролизёр
        double energyJ = 0, molIn = 0;
        int s = 0;
        while (s < 6 * 3600) {
            double mol = c.step(1, 1.2e5, AMB, tubes.pressurePa(), 1, 288.15);
            tubes.add(mol, Math.min(c.stage2OutK(), 288.15 + 15)); // доохладитель
            tubes.step(1, AMB);
            energyJ += c.powerW();
            molIn += mol;
            s++;
            if (!c.loaded() && tubes.pressurePa() > 190e5) break;
        }
        assertThat(c.trip()).isEqualTo(DiaphragmCompressor.Trip.NONE);
        assertThat(tubes.pressurePa()).isCloseTo(200e5, within(1.5e5));
        // заполнена горячим газом: на 200 бар меньше, чем 174 кг холодного
        double hotKg = tubes.massKg();
        assertThat(hotKg).isBetween(140.0, 170.0);
        assertThat(tubes.tempK()).isGreaterThan(AMB + 10);
        double kwhPerKg = energyJ / 3.6e6 / (molIn * HydrogenGas.M);
        assertThat(kwhPerKg).isBetween(1.6, 3.0);
        // газ остывает, давление падает ниже 190 бар — реле досжимает
        for (int k = 0; k < 10 * 3600; k++) {
            tubes.add(c.step(1, 1.2e5, AMB, tubes.pressurePa(), 1, 288.15), 288.15 + 15);
            tubes.step(1, AMB);
        }
        assertThat(tubes.massKg()).isGreaterThan(hotKg + 5).isBetween(160.0, 180.0);
        // у верхнего давления
        c.step(1, 1.2e5, AMB, 195e5, 1, 288.15); // гистерезис реле: пуск только ниже 190 бар
        assertThat(c.nm3h()).isEqualTo(0);
    }

    @Test
    void compressorAtFullRatioIsSizedForTheElectrolyserAndTripsWithoutCooling() {
        DiaphragmCompressor c = new DiaphragmCompressor();
        c.setOn(true);
        c.step(1, 1.2e5, AMB, 150e5, 1, 288.15);
        assertThat(c.nm3h()).isBetween(450.0, 560.0);
        assertThat(c.powerW()).isLessThan(DiaphragmCompressor.MOTOR_W);
        assertThat(c.specificKwhPerKg()).isBetween(2.0, 3.0);
        assertThat(c.stage2OutK()).isLessThan(DiaphragmCompressor.TRIP_K);
        c.step(1, 1.2e5, AMB, 150e5, 1, Double.NaN);
        assertThat(c.trip()).isEqualTo(DiaphragmCompressor.Trip.DISCHARGE_TEMP);
        assertThat(c.on()).isFalse();
    }

    @Test
    void fastFillHeatsTheGasAndTheWallCoolsItBack() {
        HydrogenStorage tubes = new HydrogenStorage(HydrogenStorage.TUBE_BANK);
        double target = HydrogenGas.molarDensity(150e5, AMB) * tubes.spec().volumeM3();
        tubes.add(target - tubes.mol(), AMB);
        assertThat(tubes.tempK() - AMB).isGreaterThan(80);
        double hot = tubes.pressurePa();
        for (int s = 0; s < 5 * 3600; s++) tubes.step(1, AMB);
        assertThat(tubes.tempK()).isCloseTo(AMB, within(2.0));
        assertThat(tubes.pressurePa()).isLessThan(hot);
        assertThat(tubes.pressurePa()).isCloseTo(150e5, within(1e5));
    }

    @Test
    void gasholderReliefVentsToTheStackAndTubeDiscBursts() {
        HydrogenStorage g = new HydrogenStorage(HydrogenStorage.GASHOLDER);
        g.add(HydrogenGas.molarDensity(1.55e5, AMB) * 70 - g.mol(), AMB);
        g.step(1, AMB);
        assertThat(g.reliefOpen()).isTrue();
        for (int s = 0; s < 600 && g.reliefOpen(); s++) g.step(1, AMB);
        assertThat(g.reliefOpen()).isFalse();
        assertThat(g.pressurePa()).isLessThan(1.5e5).isGreaterThan(1.3e5);
        assertThat(g.ventedMol()).isPositive();
        assertThat(g.burst()).isFalse();

        HydrogenStorage t = new HydrogenStorage(HydrogenStorage.TUBE_BANK);
        t.add(HydrogenGas.molarDensity(24.5e6, AMB) * 11.7 - t.mol(), AMB);
        t.step(1, AMB);
        assertThat(t.burst()).isTrue();
        for (int s = 0; s < 3 * 3600; s++) t.step(1, AMB);
        assertThat(t.pressurePa()).isLessThan(2e5);
        t.replaceDisc();
        assertThat(t.burst()).isFalse();
    }

    private static PemFuelCell warmCell() {
        PemFuelCell f = new PemFuelCell();
        f.setOn(true);
        for (int s = 0; s < 3600; s++) f.step(1, 100e5, 10, 200e3, AMB, true);
        return f;
    }

    @Test
    void fuelCellDeliversRatedPowerAtAboutHalfLhvEfficiency() {
        PemFuelCell f = warmCell();
        assertThat(f.tempK()).isCloseTo(PemFuelCell.SETPOINT_K, within(3.0));
        assertThat(f.acW()).isCloseTo(200e3, within(1e3));
        assertThat(f.cellVoltage()).isBetween(0.68, 0.78);
        assertThat(f.efficiencyLhv()).isBetween(0.45, 0.55);
        // ≈ 12 кг/ч водорода, вода — 9 кг на кг
        assertThat(f.h2MolS() * HydrogenGas.M * 3600).isBetween(11.0, 13.0);
        assertThat(f.waterKgS() / (f.h2MolS() * HydrogenGas.M)).isCloseTo(8.9, within(0.2));
        // холостой ход около 1 В, а не 1,2: проникание водорода
        assertThat(PemFuelCell.cellVoltage(0, PemFuelCell.SETPOINT_K)).isBetween(0.95, 1.07);
        // частичная нагрузка — выше КПД
        f.step(1, 100e5, 10, 60e3, AMB, true);
        assertThat(f.efficiencyLhv()).isGreaterThan(0.55);
    }

    @Test
    void coldFuelCellCannotReachRatedPowerAndOverheatsWithoutFans() {
        PemFuelCell cold = new PemFuelCell();
        cold.setOn(true);
        assertThat(cold.availableW()).isLessThan(190e3).isGreaterThan(120e3);

        PemFuelCell f = warmCell();
        int s = 0;
        while (f.on() && s < 3600) {
            f.step(1, 100e5, 10, 200e3, AMB, false);
            s++;
        }
        assertThat(f.trip()).isEqualTo(PemFuelCell.Trip.OVERTEMP);

        PemFuelCell dry = warmCell();
        dry.step(1, 2e5, 10, 200e3, AMB, true);
        assertThat(dry.trip()).isEqualTo(PemFuelCell.Trip.LOW_H2);
        assertThat(dry.acW()).isZero();
    }

    @Test
    void fuelCellIsLimitedByTheHydrogenItGets() {
        PemFuelCell f = warmCell();
        double half = PemFuelCell.h2MolS(0.5);
        f.step(1, 100e5, half, 200e3, AMB, true);
        assertThat(f.currentDensity()).isCloseTo(0.5, within(1e-6));
        assertThat(f.acW()).isLessThan(150e3);
    }

    @Test
    void roundTripEfficiencyIsAThirdAsInGdd() {
        AlkalineElectrolyser e = warmElectrolyser();
        double kwhPerKgElectrolysis = e.dcW() / 3.6e6 / (e.h2MolS() * HydrogenGas.M);
        DiaphragmCompressor c = new DiaphragmCompressor();
        c.setOn(true);
        c.step(1, 1.2e5, AMB, 150e5, 1, 288.15);
        PemFuelCell f = warmCell();
        double kwhOutPerKg = f.acW() / (f.h2MolS() * HydrogenGas.M) / 3.6e6;
        double cycle = kwhOutPerKg / (kwhPerKgElectrolysis + c.specificKwhPerKg());
        // GDD T17.12: 0,37 со щелочным элементом 0,58; с PEM 0,5 — около 0,3; ГАЭС 0,73–0,80
        assertThat(cycle).isBetween(0.27, 0.40);
    }

    /**
     * Разрыв линии DN 15 у рампы на 160 бар: критическое истечение ≈ 1,1 кг/с (струя, как в 09 §9.11); в закрытом зале
     * 4000 м³ водород доходит до НКПР (4 %) за десяток секунд, а без подпитки рампа опустошается за минуты.
     */
    @Test
    void lineBreakAtTheTubeBankFillsASealedHallToTheLowerFlammableLimit() {
        HydrogenStorage tubes = new HydrogenStorage(HydrogenStorage.TUBE_BANK);
        // разом до 160 бар: горячий газ даёт ≈ 225 бар, ниже мембраны 240; остывает до 160
        tubes.add(HydrogenGas.molarDensity(160e5, AMB) * 11.7 - tubes.mol(), AMB);
        for (int s = 0; s < 5 * 3600; s++) tubes.step(1, AMB);
        assertThat(tubes.burst()).isFalse();
        double kgS = HydrogenGas.orificeMolS(1.8e-4, tubes.pressurePa(), HydrogenStorage.ATM_PA, tubes.tempK()) * HydrogenGas.M;
        assertThat(kgS).isBetween(1.0, 2.0);
        dev.denfry.promptcritical.sim.storage.BatteryRoom hall = new dev.denfry.promptcritical.sim.storage.BatteryRoom(4000,
                dev.denfry.promptcritical.sim.storage.BatteryRoom.ACH_SEALED);
        int s = 0;
        while (!hall.explosive() && s < 600) {
            double mol = tubes.take(HydrogenGas.orificeMolS(1.8e-4, tubes.pressurePa(), HydrogenStorage.ATM_PA, tubes.tempK()), HydrogenStorage.ATM_PA);
            hall.step(1, mol * HydrogenGas.M);
            s++;
        }
        assertThat(s).isBetween(5, 30);
        // дальше рампа пустеет: за 10 минут — ниже 10 бар
        for (int k = 0; k < 600; k++) tubes.take(HydrogenGas.orificeMolS(1.8e-4, tubes.pressurePa(), HydrogenStorage.ATM_PA, tubes.tempK()), HydrogenStorage.ATM_PA);
        assertThat(tubes.pressurePa()).isLessThan(10e5);
    }

    @Test
    void stateSurvivesSaveAndLoad() {
        AlkalineElectrolyser e = warmElectrolyser();
        e.setLoad(0.7);
        TreeStateWriter w = new TreeStateWriter();
        e.save(w);
        AlkalineElectrolyser e2 = new AlkalineElectrolyser();
        e2.load(new TreeStateReader(w.root()));
        assertThat(e2.tempK()).isEqualTo(e.tempK());
        assertThat(e2.load()).isEqualTo(0.7);
        assertThat(e2.on()).isTrue();

        HydrogenStorage t = new HydrogenStorage(HydrogenStorage.TUBE_BANK);
        t.add(1000, AMB);
        TreeStateWriter w2 = new TreeStateWriter();
        t.save(w2);
        HydrogenStorage t2 = new HydrogenStorage(HydrogenStorage.TUBE_BANK);
        t2.load(new TreeStateReader(w2.root()));
        assertThat(t2.pressurePa()).isEqualTo(t.pressurePa());

        PemFuelCell f = warmCell();
        TreeStateWriter w3 = new TreeStateWriter();
        f.save(w3);
        PemFuelCell f2 = new PemFuelCell();
        f2.load(new TreeStateReader(w3.root()));
        assertThat(f2.tempK()).isEqualTo(f.tempK());
        assertThat(f2.on()).isTrue();
    }
}
