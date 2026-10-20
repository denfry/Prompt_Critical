package dev.denfry.promptcritical.sim.utilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Качество воды и водоподготовка (08 §8.12): Ланжелье, иониты (T8.16), известкование, деаэрация, дозирование. */
class WaterTreatmentTest {
    private static final UtilitiesConfig CFG = UtilitiesConfig.defaults();

    private static final class Log implements UtilitiesEvents {
        final List<Integer> breakthroughs = new ArrayList<>();
        int clogged;

        @Override public void resinBreakthrough(int column) { breakthroughs.add(column); }
        @Override public void filterClogged() { clogged++; }
    }

    /** Ланжелье: 25 °C, TDS 500, Ca 200 и щёлочность 150 мг/л CaCO₃, pH 8,0 → pH_s 7,478, LSI +0,522 (накипь). */
    @Test
    void langelierIndex() {
        WaterQuality q = new WaterQuality(200 / 50.04, 0, 0, 0, 0, 500 / WaterQuality.TDS_MG_L_PER_SM, 8.0, 0,
                150 / 50.04);
        assertThat(q.tdsMgL()).isCloseTo(500, within(1e-9));
        assertThat(q.langelier(298.15)).isCloseTo(0.5218, within(1e-3));
        assertThat(q.withPh(7.0).langelier(298.15)).as("кислая — коррозионная").isNegative();
        assertThat(q.langelier(353.15)).as("горячая вода откладывает сильнее").isGreaterThan(q.langelier(298.15));
    }

    /** Речная вода: 5 + 0,87 мг-экв/л катионов → ≈ 590 мкСм/см. */
    @Test
    void conductivityEstimate() {
        double uScm = WaterQuality.RAW_RIVER.conductivitySm() * 1e4;
        assertThat(uScm).isCloseTo(590, within(10.0));
    }

    /**
     * T8.16: катионит 2 м³ × 1,0 экв/л, 20 м³/ч, 2,5 мг-экв/л — фильтроцикл 40 ч физ. ±2 %, затем событие
     * {@code pc_resin_breakthrough}; после проскока жёсткость фильтрата растёт.
     */
    @Test
    void filterCycleT816() {
        Log log = new Log();
        WaterTreatmentTrain t = new WaterTreatmentTrain(CFG, log);
        int col = t.addColumn(ResinType.SAC_NA, 2);
        WaterQuality in = WaterQuality.of(2.5, 9e-6, 30e-6, 10e-6, 0, 7.5, 0, 2.0);
        t.setInlet(in, 20.0 / 3600);
        double dt = 60, time = 0;
        while (log.breakthroughs.isEmpty() && time < 100 * 3600) {
            t.stepSlow(dt);
            time += dt;
        }
        assertThat(time / 3600).isCloseTo(40, within(0.02 * 40));
        assertThat(log.breakthroughs).containsExactly(col);
        assertThat(t.resin(col).exhausted()).isTrue();
        double leak = t.outlet(TreatmentStage.CATION).hardnessMolEqM3();
        for (int k = 0; k < 120; k++) t.stepSlow(dt);
        assertThat(t.outlet(TreatmentStage.CATION).hardnessMolEqM3()).isGreaterThan(2 * leak);

        double salt = t.regenerate(col);
        assertThat(salt).isGreaterThan(t.resin(col).workingCapacityEq() * ResinType.SAC_NA.regenerantKgPerEq);
        assertThat(t.resin(col).loadingFrac()).isZero();
        t.stepSlow(dt);
        assertThat(t.outlet(TreatmentStage.CATION).hardnessMolEqM3())
                .isCloseTo(2.5 * WaterTreatmentTrain.BASE_LEAK, within(1e-9));
    }

    /** Na-катионирование умягчает, не меняя солесодержания: Ca/Mg уходят, Na⁺ приходит эквивалентно. */
    @Test
    void sodiumSoftening() {
        WaterTreatmentTrain t = new WaterTreatmentTrain(CFG, UtilitiesEvents.NONE);
        t.addColumn(ResinType.SAC_NA, 2);
        t.setInlet(WaterQuality.RAW_RIVER, 0.005);
        t.stepSlow(60);
        WaterQuality out = t.outlet(TreatmentStage.CATION);
        assertThat(out.hardnessMolEqM3()).isCloseTo(0.05, within(1e-9));
        assertThat(out.sodiumMolEqM3() - WaterQuality.RAW_RIVER.sodiumMolEqM3()).isCloseTo(4.95, within(1e-6));
        assertThat(out.conductivitySm()).isCloseTo(WaterQuality.RAW_RIVER.conductivitySm(), within(1e-6));
        assertThat(out.alkalinityMolEqM3()).isEqualTo(WaterQuality.RAW_RIVER.alkalinityMolEqM3());
    }

    /**
     * Параллельное H–Na-катионирование: минеральная кислота H-фильтрата гасится щёлочностью Na-фильтрата. Доля H
     * 0,6 оставляет ≈ 0,5 мг-экв/л щёлочности, 0,9 — кислая вода; H-колонна грузится всеми катионами.
     */
    @Test
    void hydrogenSodiumBlend() {
        WaterTreatmentTrain t = new WaterTreatmentTrain(CFG, UtilitiesEvents.NONE);
        int h = t.addColumn(ResinType.SAC_H, 2);
        int na = t.addColumn(ResinType.SAC_NA, 2);
        t.setInlet(WaterQuality.RAW_RIVER, 0.005);
        t.setHydrogenFraction(0.6);
        t.setDecarbonizer(true);
        t.stepSlow(3600);
        WaterQuality out = t.outlet(TreatmentStage.CATION);
        double cations = 5 + WaterQuality.RAW_RIVER.sodiumMolEqM3();
        double expected = 0.4 * 4 - 0.6 * (cations * (1 - WaterTreatmentTrain.BASE_LEAK) - 4);
        assertThat(out.alkalinityMolEqM3()).isCloseTo(expected, within(1e-6));
        assertThat(out.alkalinityMolEqM3()).isBetween(0.4, 0.7);
        assertThat(out.hardnessMolEqM3()).isCloseTo(0.05, within(1e-9));
        assertThat(out.ph()).as("до декарбонизатора CO2 много").isBetween(4.5, 6.5);
        assertThat(t.outlet(TreatmentStage.DECARBONIZER).ph()).isBetween(6.5, 8.0);
        double loadH = 0.6 * 0.005 * 3600 * cations * 0.99 / t.resin(h).workingCapacityEq();
        assertThat(t.resin(h).loadingFrac()).isCloseTo(loadH, within(1e-4 * loadH));
        assertThat(t.resin(na).loadingFrac()).isLessThan(t.resin(h).loadingFrac());

        t.setHydrogenFraction(0.9);
        t.stepSlow(60);
        WaterQuality acid = t.outlet(TreatmentStage.CATION);
        assertThat(acid.alkalinityMolEqM3()).isZero();
        assertThat(acid.ph()).isLessThan(4.5);
        assertThat(acid.conductivitySm()).as("H⁺ проводит сильнее солей").isGreaterThan(0.01 * 0.5);
        assertThat(t.regenerantKg(h)).isCloseTo(t.resin(h).loadingFrac() * t.resin(h).workingCapacityEq() * 0.1,
                within(1e-9));
        assertThat(ResinType.SAC_H.regenerantId()).isEqualTo("sulfuric_acid");
    }

    /** Колонна выведена — её доля потока идёт неочищенной. */
    @Test
    void offlineGroupBypasses() {
        WaterTreatmentTrain t = new WaterTreatmentTrain(CFG, UtilitiesEvents.NONE);
        int c = t.addColumn(ResinType.SAC_NA, 2);
        t.setInlet(WaterQuality.RAW_RIVER, 0.005);
        t.setOnline(c, false);
        t.stepSlow(60);
        assertThat(t.outlet(TreatmentStage.CATION).hardnessMolEqM3()).isEqualTo(5);
        assertThat(t.resin(c).loadingFrac()).isZero();
    }

    /** Известкование: карбонатная жёсткость до 0,6 мг-экв/л, CaO по снятой жёсткости и CO₂; коагуляция — взвесь. */
    @Test
    void limingAndCoagulation() {
        WaterTreatmentTrain t = new WaterTreatmentTrain(CFG, UtilitiesEvents.NONE);
        t.setInlet(WaterQuality.RAW_RIVER, 0.01);
        t.setClarifier(true, true);
        t.stepSlow(60);
        WaterQuality out = t.outlet(TreatmentStage.CLARIFIER);
        assertThat(out.hardnessMolEqM3()).isCloseTo(5 - 3.4, within(1e-9));
        assertThat(out.alkalinityMolEqM3()).isCloseTo(WaterTreatmentTrain.LIMING_RESIDUAL, within(1e-9));
        assertThat(out.ph()).isEqualTo(WaterTreatmentTrain.LIMED_PH);
        assertThat(out.suspendedKgM3()).isCloseTo(0.005, within(1e-12));
        double co2 = 4 * Math.pow(10, 6.35 - 7.6);
        assertThat(t.limeKgS()).isCloseTo(0.01 * (3.4 + 2 * co2) * WaterTreatmentTrain.LIME_KG_PER_EQ, within(1e-12));
        assertThat(t.coagulantKgS()).isCloseTo(0.01 * WaterTreatmentTrain.COAGULANT_KG_M3, within(1e-12));
    }

    /** Песчаный фильтр задерживает 98 %, забивается (событие), после промывки снова чистый. */
    @Test
    void sandFilterClogs() {
        Log log = new Log();
        WaterTreatmentTrain t = new WaterTreatmentTrain(CFG, log);
        t.setInlet(WaterQuality.RAW_RIVER, 0.01);
        t.setSandFilter(1);
        t.stepSlow(60);
        assertThat(t.outlet(TreatmentStage.SAND_FILTER).suspendedKgM3()).isCloseTo(0.03 * 0.02, within(1e-12));
        for (int k = 0; k < 70; k++) t.stepSlow(60);
        assertThat(log.clogged).isEqualTo(1);
        t.stepSlow(60);
        assertThat(t.outlet(TreatmentStage.SAND_FILTER).suspendedKgM3()).isCloseTo(0.015, within(1e-12));
        t.backwashFilter();
        t.stepSlow(60);
        assertThat(t.outlet(TreatmentStage.SAND_FILTER).suspendedKgM3()).isCloseTo(0.0006, within(1e-12));
        assertThat(t.filterLoadingFrac()).isLessThan(0.1);
    }

    /**
     * Питательная вода: декарбонизатор снова насыщает воду кислородом, деаэратор снимает O₂ до 15 мкг/кг,
     * гидразин — до 5, аммиак поднимает pH до 9,1.
     */
    @Test
    void deaerationAndDosing() {
        WaterTreatmentTrain t = new WaterTreatmentTrain(CFG, UtilitiesEvents.NONE);
        t.addColumn(ResinType.SAC_NA, 2);
        t.setInlet(WaterQuality.RAW_RIVER.withO2(2e-6), 0.005);
        t.setDecarbonizer(true);
        t.setDeaerator(true);
        t.setDosing(true);
        t.stepSlow(60);
        assertThat(t.outlet(TreatmentStage.DECARBONIZER).o2KgKg()).isEqualTo(WaterTreatmentTrain.AIR_SATURATED_O2);
        assertThat(t.outlet(TreatmentStage.DEAERATOR).o2KgKg()).isEqualTo(15e-9);
        WaterQuality feed = t.outlet(TreatmentStage.DOSING);
        assertThat(feed.o2KgKg()).isEqualTo(5e-9);
        assertThat(feed.ph()).isEqualTo(9.1);
        assertThat(feed.hardnessMolEqM3()).isCloseTo(0.05, within(1e-9));
        assertThat(t.deaeratorSteamKgS()).isCloseTo(0.1, within(1e-12));
        assertThat(t.ammoniaKgS()).isPositive();
        assertThat(t.hydrazineKgS()).isCloseTo(0.005 * (10e-9 * 1000 + WaterTreatmentTrain.N2H4_EXCESS_KG_M3),
                within(1e-15));
    }

    /** Смола теряет {@code water.resin_fouling_per_year} = 10 % ёмкости за физ. год. */
    @Test
    void resinFouling() {
        WaterTreatmentTrain t = new WaterTreatmentTrain(CFG, UtilitiesEvents.NONE);
        int c = t.addColumn(ResinType.SAC_H, 1);
        for (int d = 0; d < 365; d++) t.stepSlow(86_400 * 1.000_684_5);
        assertThat(t.resin(c).foulingFrac()).isCloseTo(0.1, within(1e-6));
        assertThat(t.resin(c).workingCapacityEq()).isCloseTo(900, within(1e-3));
    }
}
