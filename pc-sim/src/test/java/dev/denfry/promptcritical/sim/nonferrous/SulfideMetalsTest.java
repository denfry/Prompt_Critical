package dev.denfry.promptcritical.sim.nonferrous;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.nonferrous.Electrorefining.Mode;
import dev.denfry.promptcritical.sim.nonferrous.PeirceSmithConverter.Phase;
import dev.denfry.promptcritical.sim.nonferrous.SulfideSmelting.Metal;
import org.junit.jupiter.api.Test;

/** Медь, никель, кобальт, платиноиды и олово против 08 §8.8.3–8.8.5 и T8.15. */
class SulfideMetalsTest {
    @Test
    void mineralsAndMattesFollowStoichiometry() {
        // халькопирит CuFeS₂: Cu 34,6 %; пентландит (Fe,Ni)₉S₈ 1:1: Ni 34,2 %
        Metal cu = Metal.COPPER, ni = Metal.NICKEL;
        assertThat(1 / (1 + cu.feInMineral + cu.sInMineral)).isCloseTo(0.3463, within(1e-3));
        assertThat(1 / (1 + ni.feInMineral + ni.sInMineral)).isCloseTo(0.342, within(1e-3));
        // штейн: сульфид металла + FeS дают ровно 1 / содержание
        for (Metal m : Metal.values())
            assertThat(1 + m.feInMatte() + m.sInMatte()).isCloseTo(1 / m.matteGrade, within(1e-9));
        assertThat(cu.matteGrade).isBetween(0.45, 0.65);
        assertThat(cu.concentrateGrade).isBetween(0.20, 0.30);
    }

    @Test
    void copperMakesAboutTwoTonnesOfSo2PerTonne() {
        // §8.8.3: SO₂ ≈ 2 т/т Cu
        assertThat(SulfideSmelting.totalSo2(Metal.COPPER)).isCloseTo(2.0, within(0.05));
        // сера сходится: вся сера минерала уходит газом
        double s = SulfideSmelting.totalSo2(Metal.COPPER) * SulfideSmelting.M_S / SulfideSmelting.M_SO2;
        assertThat(s).isCloseTo(Metal.COPPER.sInMineral, within(1e-9));
        assertThat(SulfideSmelting.convertingFlux(Metal.COPPER)).isPositive();
    }

    @Test
    void converterBlowsSlagThenCopperThenRefines() {
        PeirceSmithConverter c = new PeirceSmithConverter();
        c.chargeMatte(Metal.COPPER, 1000);
        assertThat(c.phase()).isEqualTo(Phase.SLAG_BLOW);
        double fe0 = c.feKg(), flux = 0, so2 = 0;
        // без кварца шлаковый период стоит
        c.step(10, 0, 0);
        assertThat(c.feKg()).isEqualTo(fe0);
        int n = 0;
        while (c.phase() != Phase.BLISTER && n++ < 100_000) {
            flux += c.step(1, 1e9, 0);
            so2 += c.lastSo2KgS();
        }
        assertThat(c.phase()).isEqualTo(Phase.BLISTER);
        assertThat(flux).isCloseTo(fe0 * SulfideSmelting.SIO2_PER_FE, within(1e-6));
        assertThat(so2).isCloseTo(1000 * SulfideSmelting.convertingSo2(Metal.COPPER), within(1e-6));
        // без газа на дразнение — черновая медь; с газом — анодная
        c.step(1, 0, 0);
        assertThat(c.phase()).isEqualTo(Phase.BLISTER);
        c.step(1, 0, 1e9);
        assertThat(c.phase()).isEqualTo(Phase.REFINING);
        assertThat(c.gasUse()).isCloseTo(1000 * PeirceSmithConverter.POLING_NM3_PER_KG, within(1e-9));
        while (c.phase() == Phase.REFINING) c.step(10, 0, 0);
        assertThat(c.phase()).isEqualTo(Phase.ANODE);
        assertThat(c.tapCu()).isCloseTo(1000, within(1e-9));
        assertThat(c.phase()).isEqualTo(Phase.EMPTY);
    }

    @Test
    void t815CopperElectrorefiningEnergy() {
        double kwh = Electrorefining.specificEnergyJPerKg(Mode.COPPER, 0.3, 0.95) / 3.6e6;
        assertThat(kwh).isCloseTo(0.266, within(0.266 * 0.01));
        // 250–350 А/м² → 0,25–0,35 В
        assertThat(Electrorefining.cellVoltage(Mode.COPPER, 250 * Electrorefining.CATHODE_M2)).isBetween(0.25, 0.35);
        assertThat(Electrorefining.cellVoltage(Mode.COPPER, 350 * Electrorefining.CATHODE_M2)).isBetween(0.25, 0.35);
        Electrorefining e = new Electrorefining();
        double kg = e.step(3600, 20e3, 1e9);
        assertThat(kg).isCloseTo(Electrorefining.CELLS * 0.95 * 20e3 * SulfideSmelting.M_CU / (2 * Electrorefining.F) * 3600, within(1e-6));
        assertThat(e.specificKwhPerKg()).isBetween(0.25, 0.30);
        // анодов мало — ток падает
        assertThat(e.step(3600, 20e3, 10)).isCloseTo(10, within(1e-9));
        assertThat(e.lastAmps()).isLessThan(20e3);
    }

    @Test
    void nickelMatteAnodesNeedThreeVolts() {
        assertThat(Electrorefining.cellVoltage(Mode.NICKEL, Electrorefining.MIN_A * 5)).isBetween(3.0, 3.5);
        assertThat(Electrorefining.cellVoltage(Mode.NICKEL, Electrorefining.NOMINAL_A)).isBetween(3.0, 3.5);
        assertThat(Electrorefining.cellVoltage(Mode.NICKEL, Electrorefining.MAX_A)).isBetween(3.0, 3.5);
        Electrorefining e = new Electrorefining();
        e.setMode(Mode.NICKEL);
        double kg = e.step(3600, 20e3, 100);
        assertThat(kg).isCloseTo(90, within(1e-9));
        assertThat(e.anodeUse(kg)).isCloseTo(100, within(1e-9));
        assertThat(e.specificKwhPerKg()).isGreaterThan(2.5);
    }

    @Test
    void tenGramsOfIridiumNeedFiftyTonnesOfOre() {
        // §8.8.4: 10 г Ir ≈ 50 т руды при извлечении 75 %; Ir 2–4 % суммы, руда 5–10 г/т
        double oreT = 50;
        double pgmKg = oreT * 1000 * 0.017 * Flotation.PGM_PER_NI;
        double irG = pgmKg * PgmRefinery.IR_SHARE * 0.75 * 1000;
        assertThat(irG).isBetween(7.0, 13.0);
        assertThat(PgmRefinery.RECOVERY).isBetween(0.70, 0.85);
        assertThat(PgmRefinery.IR_SHARE).isBetween(0.02, 0.04);
        // кобальт 0,035·Ni (08 §8.1.3), извлечение 50–65 %
        assertThat(Electrorefining.CO_RECOVERY).isBetween(0.50, 0.65);
        assertThat(Flotation.CO_PER_NI).isEqualTo(0.035);
    }

    @Test
    void smelterIsLimitedByHeatOxygenAndFlux() {
        Metal m = Metal.COPPER;
        double full = MatteSmelter.matteKg(m, 100, 1e9, 1e15, 1e9, 1e9);
        assertThat(full).isCloseTo(MatteSmelter.CONCENTRATE_KG_S * m.concentrateGrade * 100, within(1e-9));
        assertThat(MatteSmelter.matteKg(m, 100, 1e9, 0, 1e9, 1e9)).isZero();
        assertThat(MatteSmelter.matteKg(m, 100, 1e9, 1e15, 0, 1e9)).isZero();
        double half = MatteSmelter.matteKg(m, 100, 1e9, 1e15, SulfideSmelting.smeltingO2(m) * full / 2, 1e9);
        assertThat(half).isCloseTo(full / 2, within(1e-9));
    }

    @Test
    void tinReductionNeedsCokeAndHeat() {
        // SnO₂ + 2C → Sn + 2CO: 0,202 кг C на кг Sn
        assertThat(MatteSmelter.TIN_CARBON_PER_SN).isCloseTo(0.202, within(0.001));
        double kg = MatteSmelter.tinKg(100, 1e9, 1e15, 1e9);
        assertThat(kg).isCloseTo(MatteSmelter.TIN_KG_S * 100, within(1e-9));
        assertThat(MatteSmelter.tinKg(100, 1e9, 1e15, 0)).isZero();
        assertThat(MatteSmelter.tinKg(100, 1e9, MatteSmelter.TIN_HEAT_J_PER_KG, 1e9)).isCloseTo(1, within(1e-9));
    }
}
