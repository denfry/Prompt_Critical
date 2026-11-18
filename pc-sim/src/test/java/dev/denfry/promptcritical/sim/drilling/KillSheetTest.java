package dev.denfry.promptcritical.sim.drilling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class KillSheetTest {
    private static final double FT = 0.3048, PPG = 119.826_427, PSI = 6894.757, BBL = 0.158_987;

    /**
     * Учебный лист глушения IADC: TVD 10 000 футов, раствор 10,0 фунт/галлон, SIDPP 520 psi, SCR 800 psi на скорости
     * глушения. Ответы: KMW = 10 + 520/(0,052·10 000) = 11,0 фунт/галлон, ICP = 1320 psi, FCP = 800·11/10 = 880 psi.
     * Константа 0,052 в промысловой формуле — округление 0,051 948, отсюда допуск 0,01 фунт/галлон.
     */
    @Test
    void iadcWorkedExample() {
        KillSheet k = KillSheet.of(10_000 * FT, 10 * PPG, 520 * PSI, 650 * PSI, 800 * PSI, 177.7 * BBL, 520 * BBL,
                0.1195 * BBL, 3000 * FT, 15 * PPG);
        assertThat(k.killMudKgM3() / PPG).isCloseTo(11.0, within(0.01));
        assertThat(k.icpPa() / PSI).isCloseTo(1320, within(1e-6));
        assertThat(k.fcpPa() / PSI).isCloseTo(880, within(1.0));
        // ходы: объём колонны и затрубья делить на подачу за ход
        assertThat(k.strokesToBit()).isCloseTo(177.7 / 0.1195, within(1e-6));
        assertThat(k.strokesBitToSurface()).isCloseTo(520 / 0.1195, within(1e-6));
        // МДДК = (15 − 10)·0,052·3000 = 780 psi
        assertThat(k.maaspPa() / PSI).isCloseTo(780, within(1.0));
        // график давления в трубах: середина пути к долоту — среднее ICP и FCP, дальше FCP
        assertThat(k.drillPipePressurePa(k.strokesToBit() / 2) / PSI).isCloseTo((1320 + k.fcpPa() / PSI) / 2, within(1e-6));
        assertThat(k.drillPipePressurePa(k.strokesToBit() * 2)).isEqualTo(k.fcpPa());
        assertThat(k.drillPipePressurePa(0)).isEqualTo(k.icpPa());
    }

    /** Барит на утяжеление 1000 баррелей с 10 до 12 фунт/галлон: 1470·2/(35 − 12) = 127,8 фунта на баррель. */
    @Test
    void bariteForWeightUp() {
        double kg = KillSheet.bariteKg(1000 * BBL, 10 * PPG, 12 * PPG);
        double fieldLb = 1000 * 1470 * 2 / (35.0 - 12);
        // барит 4,2 г/см³ = 35,05 фунт/галлон против круглых 35 у книги — расхождение 0,3 %
        assertThat(kg / 0.453_592).isCloseTo(fieldLb, within(fieldLb * 0.005));
        assertThat(KillSheet.bariteKg(10, 1300, 1200)).isZero();
    }
}
