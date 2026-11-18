package dev.denfry.promptcritical.sim.chlor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.Test;

/**
 * Хлор-щелочной электролиз против литературы: напряжение диафрагменной ванны 3,2–3,6 В при 2 кА/м² и 90–95 °C,
 * удельный расход 2,5–3,0 МВт·ч постоянного тока на тонну хлора (O'Brien, Bommaraju, Hine, «Handbook of Chlor-Alkali
 * Technology», т. I, табл. 4.x; Florea), стехиометрия Фарадея, очистка рассола, сжижение по упругости пара хлора.
 */
class ChlorAlkaliTest {
    @Test
    void reversibleVoltageFallsWithTemperature() {
        assertThat(DiaphragmCellRoom.reversibleV(298.15)).isCloseTo(2.25, within(0.01));
        assertThat(DiaphragmCellRoom.reversibleV(363.15)).isCloseTo(2.19, within(1e-9));
    }

    @Test
    void cellVoltageMatchesDiaphragmCellsAtTwoKiloampsPerSquareMetre() {
        double u = DiaphragmCellRoom.cellVoltage(2000, 363.15, 0);
        assertThat(u).isBetween(3.2, 3.6);
        // омика доминирует: наклон U(j) ≈ 0,4–0,5 В на кА/м² (O'Brien: k-фактор диафрагменных ванн)
        double k = DiaphragmCellRoom.cellVoltage(2500, 363.15, 0) - DiaphragmCellRoom.cellVoltage(1500, 363.15, 0);
        assertThat(k).isBetween(0.40, 0.55);
        // холодная ванна и забитая диафрагма — выше напряжение
        assertThat(DiaphragmCellRoom.cellVoltage(2000, 323.15, 0)).isGreaterThan(u + 0.15);
        assertThat(DiaphragmCellRoom.cellVoltage(2000, 363.15, 0.5)).isGreaterThan(u + 0.35);
    }

    @Test
    void specificEnergyIsInTheDiaphragmRange() {
        double u = DiaphragmCellRoom.cellVoltage(2000, 363.15, 0);
        double ce = DiaphragmCellRoom.currentEfficiency(0.11, 0, 0);
        assertThat(ce).isBetween(0.94, 0.97);
        double mwhPerT = DiaphragmCellRoom.specificEnergyJPerKg(u, ce) * 1000 / 3.6e9;
        assertThat(mwhPerT).isBetween(2.5, 3.0);
        // с выпрямителем по переменному току — в пределах 2,5–3,5 из задания
        assertThat(mwhPerT / 0.965).isBetween(2.5, 3.5);
    }

    @Test
    void faradayStoichiometryAndHeating() {
        DiaphragmCellRoom c = new DiaphragmCellRoom();
        c.setOn(true);
        c.setCurrentKa(30);
        double brineL = 0;
        for (int i = 0; i < 6 * 60; i++) brineL = c.step(60, 1e9, 1e9, BrineTreatment.TREATED_HARDNESS_MG_L);
        double amps = 30e3 * DiaphragmCellRoom.CELLS;
        assertThat(c.cl2KgS()).isCloseTo(amps * c.efficiency() / (2 * DiaphragmCellRoom.F) * DiaphragmCellRoom.M_CL2, within(1e-9));
        // 1,128 т NaOH и 0,028 т H₂ на тонну хлора при одинаковом выходе
        assertThat(c.naohKgS() / c.cl2KgS()).isCloseTo(2 * 39.997 / 70.906, within(1e-6));
        assertThat(c.h2KgS() / c.cl2KgS()).isCloseTo(2.016 / 70.906 / c.efficiency(), within(1e-6));
        assertThat(c.naohFraction()).isCloseTo(0.11, within(1e-6));
        assertThat(c.tempK() - 273.15).isBetween(80.0, 98.0);
        assertThat(c.dcW()).isBetween(1.8e6, 2.2e6);
        assertThat(brineL / 60).isCloseTo(DiaphragmCellRoom.brineDemandLs(30), within(1e-9));
    }

    @Test
    void brineShortageRaisesCausticAndThenHydrogenTrips() {
        DiaphragmCellRoom c = new DiaphragmCellRoom();
        c.setOn(true);
        double need = DiaphragmCellRoom.brineDemandLs(c.currentKa()) * 1;
        c.step(1, 1e9, need * 0.8, 2);
        double ceShort = c.efficiency();
        assertThat(c.naohFraction()).isGreaterThan(0.13);
        c.step(1, 1e9, need, 2);
        assertThat(c.efficiency()).isGreaterThan(ceShort);
        assertThat(c.tripped()).isFalse();
        c.step(1, 1e9, need * 0.3, 2);
        assertThat(c.h2InCl2()).isGreaterThan(DiaphragmCellRoom.H2_IN_CL2_LIMIT);
        assertThat(c.tripped()).isTrue();
        c.step(1, 1e9, need, 2);
        assertThat(c.cl2KgS()).isZero();
    }

    @Test
    void hardBrineFoulsTheDiaphragm() {
        DiaphragmCellRoom soft = new DiaphragmCellRoom(), hard = new DiaphragmCellRoom();
        soft.setOn(true);
        hard.setOn(true);
        for (int i = 0; i < 24 * 30; i++) {
            soft.step(3600, 1e9, 1e9, BrineTreatment.hardnessMgL(1));
            hard.step(3600, 1e9, 1e9, BrineTreatment.hardnessMgL(0));
        }
        assertThat(hard.fouling()).isGreaterThan(20 * soft.fouling());
        assertThat(hard.voltageV()).isGreaterThan(soft.voltageV() + 0.2);
        assertThat(hard.efficiency()).isLessThan(soft.efficiency());
        hard.rebuildDiaphragms();
        assertThat(hard.fouling()).isZero();
    }

    @Test
    void brineTreatmentNumbers() {
        assertThat(BrineTreatment.RAW_HARDNESS_MG_L).isBetween(250.0, 400.0);
        assertThat(BrineTreatment.hardnessMgL(1)).isLessThanOrEqualTo(5);
        // щёлочь на очистку — около 1 % выработки (≈ 106 г NaOH на литр рассола при разложении 57 %)
        assertThat(BrineTreatment.NAOH_KG_PER_L * 1000).isBetween(0.5, 1.5);
        assertThat(BrineTreatment.brineFromSaltL(310)).isCloseTo(1000, within(1e-6));
    }

    @Test
    void chlorineVapourPressureAndLiquefactionLimitedByHydrogen() {
        assertThat(ChlorineLiquefier.vapourPressurePa(243.15) / 1e5).isCloseTo(1.2, within(0.08));
        assertThat(ChlorineLiquefier.vapourPressurePa(293.15) / 1e5).isCloseTo(6.8, within(0.4));
        ChlorineLiquefier l = new ChlorineLiquefier();
        l.setOn(true);
        l.setPressureMpa(0.8);
        l.setCondK(253.15);
        l.step(0.2, ChlorineLiquefier.H2_NORMAL);
        assertThat(l.degree()).isBetween(0.98, 0.999);
        assertThat(l.tailH2Fraction()).isLessThan(ChlorineLiquefier.H2_LIMIT);
        assertThat(l.liquidKgS() + l.tailCl2KgS()).isCloseTo(0.2, within(1e-9));
        // глубже — больше жидкости, но водород в абгазе выше 4 %: защита
        l.setCondK(238.15);
        l.step(0.2, ChlorineLiquefier.H2_NORMAL);
        assertThat(l.tripped()).isTrue();
        l.step(0.2, ChlorineLiquefier.H2_NORMAL);
        assertThat(l.tailCl2KgS()).isCloseTo(0.2, within(1e-12));
    }
}
