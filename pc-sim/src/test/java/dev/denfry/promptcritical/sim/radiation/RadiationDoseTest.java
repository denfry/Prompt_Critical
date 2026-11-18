package dev.denfry.promptcritical.sim.radiation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Модель дозы против справочных величин: керм-постоянные Co-60 и Cs-137 (IAEA TECDOC-1344; Козлов, 1999), 1 Ки Co-60
 * на 1 м ≈ 1,3 Р/ч, слои половинного ослабления в свинце для узкого пучка, факторы накопления ANSI/ANS-6.4.3 при
 * 1 МэВ, нейтроны Cf-252 (≈ 23 мЗв/ч на 1 мг на 1 м), калибровка вспышки СЦР.
 */
class RadiationDoseTest {
    @Test
    void kermaConstantsFromTheLines() {
        assertThat(Emitter.CO60.kermaConstant()).isCloseTo(0.306, within(0.306 * 0.03));
        assertThat(Emitter.CS137.kermaConstant()).isCloseTo(0.0779, within(0.0779 * 0.04));
        // 1 Ки Co-60 на 1 м: 37 ГБк × 0,306 = 11,3 мГр/ч (≈ 1,3 Р/ч)
        assertThat(DoseRate.gammaGyH(Emitter.CO60.source(3.7e10), 1, List.of()) * 1000).isCloseTo(11.3, within(0.4));
        // закон обратных квадратов
        PointSource s = Emitter.CS137.source(1e9);
        assertThat(DoseRate.gammaGyH(s, 2, List.of()) * 4).isCloseTo(DoseRate.gammaGyH(s, 1, List.of()), within(1e-15));
    }

    @Test
    void decayHalvesActivityEveryHalfLife() {
        double a = Emitter.CO60.activity(1e10, Emitter.CO60.halfLifeS * 3);
        assertThat(a).isCloseTo(1.25e9, within(1.0));
        assertThat(Emitter.CS137.halfLifeS / (365.25 * 86400)).isCloseTo(30.08, within(0.01));
    }

    @Test
    void narrowBeamHalfValueLayersInLead() {
        // свинец: Cs-137 — 0,55 см, Co-60 — около 1,0–1,1 см (узкий пучок, без накопления)
        double hvlCs = Math.log(2) / ShieldMaterial.LEAD.mu(0.6617, 11.35);
        double hvlCo = Math.log(2) / ShieldMaterial.LEAD.mu(1.25, 11.35);
        assertThat(hvlCs).isBetween(0.50, 0.62);
        assertThat(hvlCo).isBetween(0.95, 1.15);
        // вода хуже бетона на единицу толщины, на единицу массы — почти так же
        assertThat(ShieldMaterial.WATER.mu(1, 1) / ShieldMaterial.CONCRETE.mu(1, 2.3)).isBetween(0.45, 0.50);
    }

    @Test
    void buildupFactorsMatchAnsTablesAtOneMeV() {
        // ANSI/ANS-6.4.3, точечный изотропный источник 1 МэВ, накопление дозы: вода 7,68 и 16,2 на 4 и 7 длинах,
        // свинец 2,26 и 3,02, железо 5,30 на 4 длинах
        assertThat(ShieldMaterial.WATER.buildup(4)).isCloseTo(7.68, within(7.68 * 0.15));
        assertThat(ShieldMaterial.WATER.buildup(7)).isCloseTo(16.2, within(16.2 * 0.15));
        assertThat(ShieldMaterial.LEAD.buildup(4)).isCloseTo(2.26, within(2.26 * 0.10));
        assertThat(ShieldMaterial.LEAD.buildup(7)).isCloseTo(3.02, within(3.02 * 0.10));
        assertThat(ShieldMaterial.IRON.buildup(4)).isCloseTo(5.30, within(5.30 * 0.15));
        assertThat(ShieldMaterial.WATER.buildup(0)).isEqualTo(1);
    }

    @Test
    void leadWallAgainstCobalt() {
        // 5 см свинца ослабляют Co-60 в 13–30 раз (слой десятикратного ослабления в широком пучке ≈ 4 см)
        PointSource s = Emitter.CO60.source(3.7e10);
        double bare = DoseRate.gammaGyH(s, 1, List.of());
        double behind = DoseRate.gammaGyH(s, 1, List.of(new DoseRate.Layer(ShieldMaterial.LEAD, 5)));
        assertThat(behind / bare).isBetween(0.03, 0.08);
        // накопление делает широкий пучок слабее ослаблен, чем узкий
        double narrow = Math.exp(-ShieldMaterial.LEAD.mu(1.25, 11.35) * 5);
        assertThat(behind / bare).isGreaterThan(narrow);
        // правило последнего слоя: свинец снаружи и вода снаружи дают разное накопление
        double pbOut = DoseRate.gammaGyH(s, 1, List.of(new DoseRate.Layer(ShieldMaterial.WATER, 20), new DoseRate.Layer(ShieldMaterial.LEAD, 3)));
        double waterOut = DoseRate.gammaGyH(s, 1, List.of(new DoseRate.Layer(ShieldMaterial.LEAD, 3), new DoseRate.Layer(ShieldMaterial.WATER, 20)));
        assertThat(waterOut).isGreaterThan(pbOut);
    }

    @Test
    void fissionNeutronsFromCalifornium() {
        // 1 мг Cf-252 — 2,31·10⁹ н/с; на 1 м около 23–26 мЗв/ч
        double h = DoseRate.neutronSvH(PointSource.line(1, 0, 2.31e9), 1, List.of()) * 1000;
        assertThat(h).isBetween(20.0, 28.0);
        // метр воды ослабляет быстрые нейтроны в e^{10,3} раз
        double water = DoseRate.neutronSvH(PointSource.line(1, 0, 2.31e9), 1, List.of(new DoseRate.Layer(ShieldMaterial.WATER, 100)));
        assertThat(water / (h / 1000)).isCloseTo(Math.exp(-10.3), within(1e-6));
    }

    @Test
    void criticalityBurstKeepsTheSolutionCalibration() {
        PointSource b = FissionSource.solutionBurst(1e17);
        assertThat(DoseRate.burstSv(b, 1, List.of())).isCloseTo(0.4, within(0.004));
        assertThat(DoseRate.neutronSvH(b, 1, List.of()) / 3600).isCloseTo(0.3, within(0.003));
        // бетонная стена 30 см срезает дозу вспышки больше чем вдвое
        double behind = DoseRate.burstSv(b, 3, List.of(new DoseRate.Layer(ShieldMaterial.CONCRETE, 30)));
        assertThat(behind).isLessThan(DoseRate.burstSv(b, 3, List.of()) / 2);
    }

    @Test
    void reactorSourceScalesWithPowerAndKeepsDecayGamma() {
        PointSource running = FissionSource.reactor(200, 0);
        assertThat(running.neutrons()).isCloseTo(FissionSource.fissionRate(200) * 2.43, within(1.0));
        assertThat(FissionSource.fissionRate(1)).isCloseTo(3.12e10, within(0.01e10));
        PointSource off = FissionSource.reactor(0, 10);
        assertThat(off.neutrons()).isZero();
        assertThat(off.photons()[0]).isCloseTo(10 * 0.5 / 1.602176634e-13, within(1e6));
    }

    @Test
    void acuteRadiationSyndromeStages() {
        assertThat(RadiationSickness.stage(0.1)).isEqualTo(RadiationSickness.Stage.NONE);
        assertThat(RadiationSickness.stage(1.5)).isEqualTo(RadiationSickness.Stage.MILD);
        assertThat(RadiationSickness.stage(4.5)).isEqualTo(RadiationSickness.Stage.SEVERE);
        assertThat(RadiationSickness.lethality(4)).isCloseTo(0.5, within(1e-9));
        assertThat(RadiationSickness.lethality(8)).isGreaterThan(0.99);
        assertThat(RadiationSickness.lethality(0.9)).isZero();
        assertThat(RadiationSickness.recover(2, 30)).isCloseTo(1, within(1e-12));
    }
}
