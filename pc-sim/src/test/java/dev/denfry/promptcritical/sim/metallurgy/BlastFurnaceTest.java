package dev.denfry.promptcritical.sim.metallurgy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.combustion.BurnerKind;
import dev.denfry.promptcritical.sim.combustion.BurnerSpec;
import dev.denfry.promptcritical.sim.combustion.Combustor;
import dev.denfry.promptcritical.sim.combustion.FuelGas;
import dev.denfry.promptcritical.sim.combustion.FuelSpec;
import dev.denfry.promptcritical.sim.mining.CoalAssay;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Домна (08 §8.5): баланс углерода T8.9, ход, режимы, выпуск; воздухонагреватели. */
class BlastFurnaceTest {
    private static final double DT = 72;
    /** Кокс: A^d 11 %, S 0,6 %, M₂₅ 85 — C ≈ 87 %. */
    private static final CoalAssay COKE = CokeBattery.cokeAssay(0.11, 0.006, 85);
    private static final double HOT_BLAST_K = 1373.15;

    /** Колоша на ≈ 1 т чугуна: агломерат 58 % Fe, основность 1,25. */
    private static BurdenLayer burden(double cokeKg) {
        return new BurdenLayer(1637, 0.58, 0.077, 0.096, cokeKg, COKE, 0);
    }

    private static final class Plant {
        final BlastFurnace bf = new BlastFurnace(BlastFurnaceSpec.of(54, 8), IronmakingConfig.defaults());
        final List<String> log = new ArrayList<>();
        BurdenLayer layer = burden(475);
        boolean tapping = true;
        long steps;
        double tappedIron;

        Plant() {
            bf.setEventSink(new IronmakingEvents() {
                @Override
                public void slip(double severity) {
                    log.add("slip");
                }

                @Override
                public void tuyereBurnt(int burntTotal) {
                    log.add("tuyere");
                }
            });
            for (int k = 0; k < 6; k++) bf.charge(BurdenLayer.cokeBlank(2000, COKE));
        }

        double rated() {
            return bf.spec().ratedBlastNm3S();
        }

        void run(double hours, BlastConditions b) {
            for (int k = 0; k < hours * 3600 / DT; k++) {
                while (bf.charge(layer)) {}
                bf.stepSlow(DT, b);
                if (tapping && ++steps % 120 == 0) tapOut();
            }
        }

        void run(double hours) {
            run(hours, BlastConditions.air(rated(), HOT_BLAST_K));
        }

        TapResult tapOut() {
            TapResult first = null;
            if (!bf.drillTaphole()) return null;
            for (int r = 0; r < 900; r++) {
                TapResult t = bf.tap(1);
                if (first == null) first = t;
                tappedIron += t.ironKg();
                if (t.blowing() || t == TapResult.NONE) break;
            }
            bf.plugTaphole();
            return first;
        }
    }

    private static Plant running() {
        Plant p = new Plant();
        p.run(72);
        assertThat(p.bf.state()).isEqualTo(BlastFurnaceState.RUNNING);
        return p;
    }

    /** T8.9: агломерат 58 %, 1100 нм³/т, r_d 0,4 → кокс 475 кг/т; газ 1520 нм³/т, CO 21 %. */
    @Test
    void t89CarbonBalance() {
        CarbonBalance cb = CarbonBalance.of(0.58, 1100, 0.4, 0.21, 0, 0.47, COKE);
        assertThat(cb.tuyereC()).isCloseTo(247.5, within(2.5));
        assertThat(cb.directC()).isCloseTo(81, within(1.5));
        assertThat(cb.hotMetalC()).isEqualTo(45);
        assertThat(cb.siMnC() + cb.dustC()).isCloseTo(40, within(4.0));
        assertThat(cb.cokeKg()).isCloseTo(475, within(475 * 0.05));
        assertThat(cb.n2Nm3()).isCloseTo(869, within(1.0));
        assertThat(cb.topGasNm3()).isCloseTo(1520, within(1520 * 0.05));
        assertThat(cb.coFraction() * 100).isCloseTo(21, within(1.0));
        assertThat(cb.co2Nm3() / (cb.coNm3() + cb.co2Nm3())).isCloseTo(0.47, within(1e-9));
        assertThat(cb.burdenKg()).isCloseTo(1637, within(5.0));
    }

    /** Задувка: горн с коксом под дутьём → BLOWING_IN; не раньше 2 сут физ. и горячий горн → RUNNING. */
    @Test
    void blowInSequence() {
        BlastFurnace cold = new BlastFurnace(BlastFurnaceSpec.of(54, 8), IronmakingConfig.defaults());
        cold.charge(burden(475));
        cold.stepSlow(DT, BlastConditions.air(1, HOT_BLAST_K));
        assertThat(cold.state()).isEqualTo(BlastFurnaceState.COLD);
        assertThat(cold.drillTaphole()).isFalse();

        Plant p = new Plant();
        p.run(0.1);
        assertThat(p.bf.state()).isEqualTo(BlastFurnaceState.BLOWING_IN);
        p.run(47);
        assertThat(p.bf.state()).isEqualTo(BlastFurnaceState.BLOWING_IN);
        p.run(2);
        assertThat(p.bf.state()).isEqualTo(BlastFurnaceState.RUNNING);
    }

    /** Ровный ход при 475 кг/т: горн ≈ 1500 °C, состав чугуна §8.5.3, удельное дутьё и газ — как в балансе. */
    @Test
    void steadyRunMatchesBalance() {
        Plant p = running();
        BlastFurnace bf = p.bf;
        assertThat(bf.hearthTempK()).isCloseTo(1773, within(60.0));
        assertThat(bf.ironSiliconFrac()).isBetween(0.003, 0.010);
        assertThat(bf.ironSulfurFrac()).isBetween(0.00015, 0.0005);
        assertThat(bf.productivityTM3Day()).isBetween(1.8, 2.3);
        double specificBlast = p.rated() / (bf.hotMetalRateKgS() / 1000);
        assertThat(specificBlast).isCloseTo(1070, within(1070 * 0.05));
        assertThat(bf.topGasNm3S() / (bf.hotMetalRateKgS() / 1000)).isCloseTo(1520, within(1520 * 0.05));
        double[] x = new double[FuelGas.values().length];
        bf.topGasComposition(x);
        assertThat(x[FuelGas.CO.ordinal()]).isCloseTo(0.21, within(0.015));
        assertThat(x[FuelGas.O2.ordinal()]).isZero();
        assertThat(bf.etaCO()).isCloseTo(0.47, within(1e-9));
        FuelSpec gas = bf.topGasFuel();
        double rho = FuelGas.mixtureDensityKgPerNm3(x);
        assertThat(gas.lhv() * rho / 1e6).isBetween(2.9, 3.4);
        // пребывание шихты 6–8 ч физ.
        double layerVolumePerT = burden(475).volumeM3();
        double residenceH = bf.stockVolumeM3() / (layerVolumePerT * bf.hotMetalRateKgS() * 3.6);
        assertThat(residenceH).isBetween(5.5, 8.0);
        assertThat(p.tappedIron).isGreaterThan(0);
    }

    /** Больше кокса — горячее горн, выше Si, ниже ход; меньше — холодный горн и сернистый чугун. */
    @Test
    void cokeRateSetsHearthHeat() {
        Plant rich = new Plant();
        rich.layer = burden(520);
        rich.run(72);
        Plant normal = running();
        Plant lean = new Plant();
        lean.layer = burden(430);
        lean.run(72);
        assertThat(rich.bf.hearthTempK()).isGreaterThan(normal.bf.hearthTempK() + 30);
        assertThat(rich.bf.ironSiliconFrac()).isGreaterThan(normal.bf.ironSiliconFrac());
        assertThat(rich.bf.productivityTM3Day()).isLessThan(normal.bf.productivityTM3Day());
        assertThat(lean.bf.hearthTempK()).isLessThan(normal.bf.hearthTempK() - 60);
        assertThat(lean.bf.ironSulfurFrac()).isGreaterThan(1.5 * normal.bf.ironSulfurFrac());
        assertThat(lean.bf.state()).isNotEqualTo(BlastFurnaceState.RUNNING);
    }

    /** P2: смена шихты доходит до горна через время пребывания. */
    @Test
    void burdenChangeIsDelayed() {
        Plant p = running();
        double t0 = p.bf.hearthTempK();
        p.layer = burden(380);
        p.run(3);
        assertThat(p.bf.hearthTempK()).isCloseTo(t0, within(5.0));
        p.run(9);
        assertThat(p.bf.hearthTempK()).isLessThan(t0 - 60);
    }

    /** T_ф (§8.5.4): 2000–2300 °C; +1 % O₂ ≈ +50 К; +100 К дутья ≈ +80 К. */
    @Test
    void flameTemperature() {
        double base = BlastFurnace.raftK(HOT_BLAST_K, 0.21, 0.010);
        assertThat(base - 273.15).isBetween(2000.0, 2300.0);
        assertThat(BlastFurnace.raftK(1173.15, 0.21, 0.010) - 273.15).isBetween(2000.0, 2300.0);
        assertThat(BlastFurnace.raftK(HOT_BLAST_K, 0.22, 0.010) - base).isBetween(35.0, 65.0);
        assertThat(BlastFurnace.raftK(HOT_BLAST_K + 100, 0.21, 0.010) - base).isBetween(60.0, 100.0);
        assertThat(BlastFurnace.raftK(HOT_BLAST_K, 0.21, 0.030)).isLessThan(base - 80);
    }

    /** Перегретый очаг и обогащение — подвисание; снизили дутьё — осадка (событие) и снова ровный ход. */
    @Test
    void hangingAndSlip() {
        Plant p = running();
        BlastConditions hot = new BlastConditions(p.rated(), 1473.15, 0.27, 0.010, true);
        p.run(1, hot);
        assertThat(p.bf.state()).isEqualTo(BlastFurnaceState.HANGING);
        assertThat(p.bf.hotMetalRateKgS()).isZero();
        assertThat(p.bf.blastPressurePa()).isGreaterThan(BlastFurnace.TOP_PRESSURE_PA + BlastFurnace.COLUMN_DP_PA);
        p.run(0.2, BlastConditions.air(p.rated() * 0.8, 1173.15));
        assertThat(p.log).contains("slip");
        assertThat(p.bf.state()).isIn(BlastFurnaceState.RUNNING, BlastFurnaceState.SLIP);
    }

    /** Тихий ход: дутьё 20 % дольше часа — BANKED, горн остывает медленно; дутьё вернули — RUNNING. */
    @Test
    void bankingAndRestart() {
        Plant p = running();
        p.run(1.1, BlastConditions.air(p.rated() * 0.2, HOT_BLAST_K));
        assertThat(p.bf.state()).isEqualTo(BlastFurnaceState.BANKED);
        double t = p.bf.hearthTempK();
        p.run(6, BlastConditions.air(0, HOT_BLAST_K));
        assertThat(p.bf.state()).isEqualTo(BlastFurnaceState.BANKED);
        assertThat(p.bf.hearthTempK()).isGreaterThan(t - 60);
        p.run(0.1);
        assertThat(p.bf.state()).isEqualTo(BlastFurnaceState.RUNNING);
    }

    /** Нет дутья 30 мин физ. или нет воды охлаждения — закозление; отогрев холостыми колошами — сутки и больше. */
    @Test
    void lossOfBlastOrCoolingChills() {
        Plant p = running();
        p.run(0.55, BlastConditions.air(0, HOT_BLAST_K));
        assertThat(p.bf.state()).isEqualTo(BlastFurnaceState.CHILLED);
        assertThat(p.bf.drillTaphole()).isTrue();
        assertThat(p.bf.tap(1)).isEqualTo(TapResult.NONE);
        p.bf.plugTaphole();
        p.run(1);
        assertThat(p.bf.state()).isEqualTo(BlastFurnaceState.CHILLED);
        // отогрев — холостыми колошами (один кокс)
        p.layer = BurdenLayer.cokeBlank(2000, COKE);
        double hours = 1;
        while (p.bf.state() == BlastFurnaceState.CHILLED && hours < 240) {
            p.run(1);
            hours++;
        }
        assertThat(p.bf.state()).isEqualTo(BlastFurnaceState.BLOWING_IN);
        assertThat(hours).isBetween(12.0, 120.0);

        Plant q = running();
        double wear = q.bf.liningWear();
        q.run(0.55, new BlastConditions(q.rated(), HOT_BLAST_K, 0.21, 0.010, false));
        assertThat(q.bf.state()).isEqualTo(BlastFurnaceState.CHILLED);
        assertThat(q.bf.liningWear() - wear).isGreaterThan(0);
    }

    /** Выпуск: истечение C_d·A·√(2gh + 2Δp/ρ), лётка размывается, в конце — газ. */
    @Test
    void tapping() {
        Plant p = running();
        p.tapping = false;
        p.run(2.4);
        BlastFurnace bf = p.bf;
        double iron = bf.ironKg(), slag = bf.slagKg();
        BlastFurnaceSpec s = bf.spec();
        double hi = iron / BlastFurnace.RHO_IRON / s.hearthAreaM2();
        double top = hi + slag / BlastFurnace.RHO_SLAG / s.hearthAreaM2();
        double ironAbove = hi - s.tapholeHeightM(), slagAbove = top - hi;
        double fs = slagAbove / (top - s.tapholeHeightM());
        double rho = fs * BlastFurnace.RHO_SLAG + (1 - fs) * BlastFurnace.RHO_IRON;
        double head = 9.81 * (BlastFurnace.RHO_IRON * ironAbove + BlastFurnace.RHO_SLAG * slagAbove);
        double q = BlastFurnace.TAP_CD * Math.PI * 0.05 * 0.05 / 4
                * Math.sqrt(2 * (head + bf.blastPressurePa()) / rho);
        double ironRate = (1 - fs) * q * BlastFurnace.RHO_IRON;
        assertThat(ironRate).isBetween(20.0, 150.0);

        assertThat(bf.drillTaphole()).isTrue();
        TapResult first = bf.tap(1);
        assertThat(first.ironKg()).isCloseTo(ironRate, within(ironRate * 1e-6));
        assertThat(first.ironTempK()).isCloseTo(bf.hearthTempK(), within(1e-9));
        assertThat(first.carbonFrac()).isBetween(0.042, 0.048);
        assertThat(bf.tapholeDiameterM()).isGreaterThan(0.05);
        double out = first.ironKg();
        TapResult t;
        do {
            t = bf.tap(1);
            out += t.ironKg();
        } while (!t.blowing());
        bf.plugTaphole();
        assertThat(out).isCloseTo(ironAbove * s.hearthAreaM2() * BlastFurnace.RHO_IRON, within(iron * 0.02));
        assertThat(bf.liquidLevelM()).isCloseTo(s.tapholeHeightM(), within(0.01));
    }

    /** Пропущенные выпуски: шлак у фурм — фурма прогорела, течёт вода. */
    @Test
    void missedTapsBurnTuyere() {
        Plant p = running();
        p.tapping = false;
        p.run(8);
        assertThat(p.log).contains("tuyere");
        assertThat(p.bf.tuyereLeak()).isTrue();
        assertThat(p.bf.burntTuyeres()).isEqualTo(1);
        p.bf.replaceTuyere();
        assertThat(p.bf.tuyereLeak()).isFalse();
    }

    /** Колошник полон — колоша не принимается; известняк даёт CO₂ и обжиг. */
    @Test
    void stocklineAndFlux() {
        BlastFurnace bf = new BlastFurnace(BlastFurnaceSpec.of(54, 8), IronmakingConfig.defaults());
        int n = 0;
        while (bf.charge(burden(475))) n++;
        assertThat(bf.stockVolumeM3()).isLessThanOrEqualTo(54);
        assertThat(n).isBetween(25, 30);
        assertThat(L_S()).isCloseTo(Math.exp(0.9 + 2.0 * 1.1), within(1e-9));
    }

    private static double L_S() {
        return IronmakingConfig.defaults().sulfurPartition(1.1, 1773);
    }

    /** Работа воздуходувки (§8.5.4): π = 3, η 0,82 — ≈ 125 кДж/кг ≈ 170 МДж/т чугуна. */
    @Test
    void blowerWork() {
        double w = BlastFurnace.blowerWorkJPerKg(3, 273.15, 0.82);
        assertThat(w / 1e3).isCloseTo(125, within(125 * 0.05));
        assertThat(w * 1100 * 1.293 / 1e6).isCloseTo(170, within(170 * 0.08));
    }

    /** Три каупера: два «на газе» 2 ч, один «на дутье» 1 ч — дутьё держится у 1100 °C, купол ниже 1450 °C. */
    @Test
    void hotBlastStoveCycle() {
        double[] x = new double[FuelGas.values().length];
        x[FuelGas.CO.ordinal()] = 0.22 * 0.85 + 0.06 * 0.15;
        x[FuelGas.H2.ordinal()] = 0.03 * 0.85 + 0.58 * 0.15;
        x[FuelGas.CH4.ordinal()] = 0.26 * 0.15;
        x[FuelGas.CO2.ordinal()] = 0.22 * 0.85 + 0.02 * 0.15;
        x[FuelGas.N2.ordinal()] = 0.53 * 0.85 + 0.05 * 0.15;
        Combustor gas = new Combustor(FuelSpec.fromGasComposition("bfg_cog", x), BurnerSpec.of(BurnerKind.LOW_BTU_GAS, 1));
        HotBlastStove[] st = {new HotBlastStove(50_000, 1400), new HotBlastStove(50_000, 1400),
                new HotBlastStove(50_000, 1400)};
        double blast = BlastFurnaceSpec.of(54, 8).ratedBlastNm3S();
        double fuel = 0.75;
        double minDelivered = Double.MAX_VALUE, maxDome = 0;
        int period = (int) (3600 / DT);
        for (int k = 0; k < 24 * period; k++) {
            int onBlast = (k / period) % 3;
            for (int i = 0; i < 3; i++) {
                st[i].setMode(i == onBlast ? StoveMode.ON_BLAST : StoveMode.ON_GAS);
                st[i].setHeating(fuel, gas.airFor(fuel, 1.1));
                st[i].stepSlow(DT, gas, i == onBlast ? blast : 0, 423.15, HOT_BLAST_K);
                maxDome = Math.max(maxDome, st[i].domeK());
            }
            if (k >= 12 * period) minDelivered = Math.min(minDelivered, st[onBlast].hotBlastK());
        }
        assertThat(minDelivered).isGreaterThan(HOT_BLAST_K - 20);
        assertThat(maxDome).isLessThan(HotBlastStove.DOME_MAX_K);
        assertThat(st[0].domeOverTemp()).isFalse();

        st[0].setMode(StoveMode.ON_GAS);
        st[0].stepSlow(DT, null, 0, 423.15, HOT_BLAST_K);
        assertThat(st[0].flameFailure()).isTrue();
    }
}
