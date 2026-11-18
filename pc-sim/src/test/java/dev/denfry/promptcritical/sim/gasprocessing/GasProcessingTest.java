package dev.denfry.promptcritical.sim.gasprocessing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GasProcessingTest {
    private static final double PSI = 6894.757;

    private static double fahrenheit(double f) {
        return (f - 32) / 1.8 + 273.15;
    }

    /** Букачек против номограммы Маккетты–Уэйва (GPSA, рис. 20-4): 1000 psia — ≈ 60 фунт/млн фут³ при 100 °F, ≈ 9 при 40 °F. */
    @Test
    void waterContentMatchesMcKettaWehe() {
        assertThat(WaterContent.saturatedMgPerSm3(fahrenheit(100), 1000 * PSI) / WaterContent.MG_PER_SM3_PER_LB_MMSCF)
                .isCloseTo(60, within(3.0));
        assertThat(WaterContent.saturatedMgPerSm3(fahrenheit(40), 1000 * PSI) / WaterContent.MG_PER_SM3_PER_LB_MMSCF)
                .isCloseTo(9.1, within(0.6));
        double w = WaterContent.saturatedMgPerSm3(290, 5e6);
        assertThat(WaterContent.dewPointK(w, 5e6)).isCloseTo(290, within(0.01));
        assertThat(WaterContent.saturatedMgPerSm3(290, 7e6)).isLessThan(w);
    }

    /** Регенерация ТЭГ при 204 °C и атмосферном давлении — 98,7 % масс. (GPSA, разд. 20). */
    @Test
    void leanTegFromReboilerTemperature() {
        assertThat(new GlycolDehydration(477.15, 2, 25).leanTegMassFraction()).isBetween(0.985, 0.992);
        assertThat(new GlycolDehydration(450, 2, 25).leanTegMassFraction())
                .isLessThan(new GlycolDehydration(477.15, 2, 25).leanTegMassFraction());
    }

    /** Абсорбер: точка росы падает с числом тарелок и циркуляцией и не ниже равновесной с тощим гликолем. */
    @Test
    void contactorFollowsKremser() {
        double wet = WaterContent.saturatedMgPerSm3(308, 7.1e6);
        var one = new GlycolDehydration(477, 1, 25).dry(wet, 308, 7.1e6);
        var two = new GlycolDehydration(477, 2, 25).dry(wet, 308, 7.1e6);
        var lean = new GlycolDehydration(477, 2, 15).dry(wet, 308, 7.1e6);
        assertThat(two.dewPointK()).isLessThan(one.dewPointK());
        assertThat(two.dewPointK()).isLessThan(lean.dewPointK());
        assertThat(two.dryMgSm3()).isLessThan(wet).isPositive();
        // осушка ТЭГ до точки росы −10…−15 °C при контакте 35 °C — обычная для промыслов (GPSA, разд. 20)
        assertThat(two.dewPointK() - 273.15).isBetween(-20.0, -5.0);
    }

    /**
     * Хаммершмидт: при 1000 psia гидраты газа плотностью 0,6 — при 63,7 °F, график Катца — около 62 °F; метанол 20 %
     * в водной фазе понижает температуру на 1297·20/(32,04·80) = 10,1 K.
     */
    @Test
    void hydratesAndMethanol() {
        double t = Hydrates.formationK(1000 * PSI, 0.6);
        assertThat((t - 273.15) * 1.8 + 32).isCloseTo(62, within(2.5));
        assertThat(Hydrates.formationK(1000 * PSI, 0.8)).isGreaterThan(t);
        assertThat(Hydrates.methanolDepressionK(20)).isCloseTo(10.12, within(0.01));
        assertThat(Hydrates.methanolForDepression(10.12)).isCloseTo(20, within(0.01));
    }

    /**
     * Джоуль–Томсон метана при 300 K и 1 бар — около 0,44 К/бар (Perry's Chemical Engineers' Handbook); Пенг–Робинсон с
     * теплоёмкостью идеального газа даёт на 10–15 % больше. Дросселирование метана с 70 до 35 бар от 20 °C — 15–16 K.
     */
    @Test
    void jouleThomsonOfMethane() {
        double[] m = GasComponent.pure(GasComponent.C1);
        assertThat(PengRobinson.jouleThomson(m, 300, 1e5) * 1e5).isBetween(0.40, 0.55);
        assertThat(293 - PengRobinson.throttle(m, 293, 7e6, 3.5e6)).isBetween(13.0, 19.0);
        assertThat(PengRobinson.zVapor(m, 300, 1e5)).isCloseTo(1, within(0.01));
    }

    @Test
    void flashConservesMoles() {
        double[] z = GasComponent.associatedGas();
        Flash warm = Flash.of(z, 300, 3.5e6), cold = Flash.of(z, 240, 3.5e6);
        assertThat(cold.vapor()).isLessThan(warm.vapor());
        for (int i = 0; i < z.length; i++)
            assertThat(cold.vapor() * cold.y()[i] + (1 - cold.vapor()) * cold.x()[i]).isCloseTo(z[i], within(1e-6));
        assertThat(cold.x()[GasComponent.C3.ordinal()]).isGreaterThan(cold.y()[GasComponent.C3.ordinal()]);
    }

    /** Две ступени со степенью ≤ 4; работа с промежуточным охлаждением меньше одноступенчатой. */
    @Test
    void compressorStagesSaveWork() {
        double[] x = GasComponent.associatedGas();
        assertThat(GasCompressor.stages(0.7e6, 7.1e6)).isEqualTo(2);
        double z = PengRobinson.zVapor(x, 310, 0.7e6), k = GasCompressor.K;
        double one = z * PengRobinson.R * 310 * k / (k - 1) * (Math.pow(7.1 / 0.7, (k - 1) / k) - 1) / GasCompressor.EFFICIENCY
                * WaterContent.MOL_PER_SM3;
        double two = GasCompressor.workJPerSm3(x, 310, 0.7e6, 7.1e6);
        assertThat(two).isLessThan(one).isGreaterThan(0.7 * one);
    }

    /** НТС: глубже холод — больше ШФЛУ и мощности; без метанола при свободной воде растёт гидратная пробка. */
    @Test
    void ltsRecoversLiquidsAndPlugsWithoutMethanol() {
        LowTemperatureSeparation warm = new LowTemperatureSeparation(GlycolDehydration.STANDARD);
        warm.setChillK(283);
        LowTemperatureSeparation cold = new LowTemperatureSeparation(GlycolDehydration.STANDARD);
        cold.setChillK(253);
        var rw = warm.process(1, 0, 1);
        var rc = cold.process(1, 0, 1);
        assertThat(rc.nglM3s()).isGreaterThan(rw.nglM3s());
        assertThat(rc.compressorW()).isGreaterThan(rw.compressorW());
        assertThat(rc.ltsK()).isLessThan(253);
        assertThat(rc.salesSm3s() + rc.nglMolS() / WaterContent.MOL_PER_SM3).isCloseTo(1, within(1e-6));
        // −20 °C ниже точки росы после гликоля: свободная вода и гидраты
        assertThat(rc.freeWaterKgS()).isPositive();
        assertThat(rc.ltsK()).isLessThan(rc.hydrateK());
        double t = 0;
        while (!cold.blocked() && t < 30 * 86_400) {
            cold.process(1, 0, 60);
            t += 60;
        }
        assertThat(cold.blocked()).isTrue();
        // перекрытый штуцер: потока нет, но холодный гидрат не тает — пробка держится
        for (int i = 0; i < 100; i++) assertThat(cold.process(1, 0, 60).salesSm3s()).isZero();
        assertThat(cold.blocked()).isTrue();
        // метанол на нужную концентрацию предотвращает пробку
        LowTemperatureSeparation inhibited = new LowTemperatureSeparation(GlycolDehydration.STANDARD);
        inhibited.setChillK(253);
        double need = Hydrates.methanolForDepression(rc.hydrateK() - rc.ltsK() + 3);
        double meoh = rc.freeWaterKgS() * need / (100 - need);
        var ri = inhibited.process(1, meoh, 60);
        assertThat(ri.inhibitedK()).isLessThan(ri.ltsK());
        for (int i = 0; i < 1000; i++) inhibited.process(1, meoh, 60);
        assertThat(inhibited.plug()).isZero();
        // метанол в пробку при остановленном потоке её растапливает
        LowTemperatureSeparation methanolKill = new LowTemperatureSeparation(GlycolDehydration.STANDARD);
        methanolKill.setChillK(253);
        methanolKill.setPlug(1);
        for (int i = 0; i < 20; i++) methanolKill.process(1, 0.01, 60);
        assertThat(methanolKill.blocked()).isFalse();
        // прогрев: холодильник выключен (35 °C), штуцер почти прикрыт до 6 МПа — газ выше гидратной температуры, пробка тает
        cold.setChillK(LowTemperatureSeparation.COOLER_K);
        cold.setLtsPressurePa(6e6);
        for (int i = 0; i < 20; i++) cold.process(1, 0, 60);
        assertThat(cold.plug()).isLessThan(1);
    }

    /** ГФУ: больше тарелок — чище пропан; на 15 тарелках при флегме 2 — технический пропан выше 95 %; баланс. */
    @Test
    void nglFractionation() {
        LowTemperatureSeparation lts = new LowTemperatureSeparation(GlycolDehydration.STANDARD);
        lts.setChillK(253);
        double[] ngl = lts.process(1, 0, 1).nglComposition();
        var few = new NglFractionator(5, 1).split(ngl);
        var many = new NglFractionator(15, 2).split(ngl);
        assertThat(many.propanePurity()).isGreaterThan(few.propanePurity()).isGreaterThan(0.95);
        assertThat(many.butanePurity()).isGreaterThan(0.9);
        assertThat(many.gasolinePurity()).isGreaterThan(0.9);
        double total = NglFractionator.Result.total(many.fuelGas()) + NglFractionator.Result.total(many.propane())
                + NglFractionator.Result.total(many.butane()) + NglFractionator.Result.total(many.gasoline());
        assertThat(total).isCloseTo(1, within(1e-9));
    }
}
