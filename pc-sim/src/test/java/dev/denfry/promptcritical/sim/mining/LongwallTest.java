package dev.denfry.promptcritical.sim.mining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Очистной комбайн (08 §8.2.3): H_w по крепости, мощность от вала, предел подачи, выемка шнеком. */
class LongwallTest {
    private static final LongwallConfig CFG = LongwallConfig.defaults();

    /** Крепость угля 0,8–1,5 даёт H_w 0,3–0,8 кВт·ч/т; газовый уголь (V^daf 38 %) — f 0,84, H_w 0,33. */
    @Test
    void specificEnergyFollowsProtodyakonov() {
        assertThat(Longwall.specificEnergyKwhPerT(Longwall.F_SOFT)).isCloseTo(0.3, within(1e-12));
        assertThat(Longwall.specificEnergyKwhPerT(Longwall.F_HARD)).isCloseTo(0.8, within(1e-12));
        assertThat(Longwall.coalProtodyakonov(0.45)).isEqualTo(Longwall.F_SOFT);
        assertThat(Longwall.coalProtodyakonov(0.05)).isEqualTo(Longwall.F_HARD);
        double f = Longwall.coalProtodyakonov(0.38);
        assertThat(f).isCloseTo(0.84375, within(1e-12));
        assertThat(Longwall.specificEnergyKwhPerT(f)).isCloseTo(0.33125, within(1e-12));
        assertThat(Longwall.blockEnergyJ(f, MethaneField.COAL_DENSITY)).isCloseTo(1.55025e6, within(1.0));
        assertThat(Longwall.specificEnergyKwhPerT(0.1)).isEqualTo(Longwall.HW_MIN);
    }

    /** Привод даёт номинал пропорционально скорости вала, но не больше свободной мощности сети. */
    @Test
    void cuttingPowerLimitedByShaftAndNetwork() {
        assertThat(Longwall.cuttingPowerW(CFG, 64, 1e9)).isEqualTo(220e3);
        assertThat(Longwall.cuttingPowerW(CFG, -32, 1e9)).isCloseTo(110e3, within(1e-6));
        assertThat(Longwall.cuttingPowerW(CFG, 128, 1e9)).isEqualTo(220e3);
        assertThat(Longwall.cuttingPowerW(CFG, 64, 50e3)).isEqualTo(50e3);
        assertThat(Longwall.cuttingPowerW(CFG, 64, -5)).isZero();
        assertThat(Longwall.minColumnTimeS(CFG)).isEqualTo(10);
        assertThat(Longwall.sprayWaterM3(CFG, 1.3)).isCloseTo(0.039, within(1e-12));
    }

    /**
     * Лава мощностью 3 блока в газовом угле при 220 кВт: столбец 3 × 1,55 МДж вынимается за 21 с — подача 2,8 м/мин,
     * в диапазоне GDD 2–6; при избытке мощности подача упирается в 6 м/мин.
     */
    @Test
    void feedRateWithinGddRange() {
        double e = Longwall.blockEnergyJ(Longwall.coalProtodyakonov(0.38), MethaneField.COAL_DENSITY);
        double t = 3 * e / 220e3;
        assertThat(Longwall.feedMPerMin(Math.max(t, Longwall.minColumnTimeS(CFG)))).isBetween(2.0, 6.0);
        double fast = 3 * e / 10e6;
        assertThat(Longwall.feedMPerMin(Math.max(fast, Longwall.minColumnTimeS(CFG)))).isEqualTo(6.0);
    }

    /** Шнек набирает энергию мощностью, но не короче наименьшего времени; спрос растягивает остаток на время. */
    @Test
    void cutTakesEnergyAndMinimumTime() {
        ShearerCut c = new ShearerCut();
        assertThat(c.advance(1e6, 1)).isFalse();
        c.start(1e6, 10);
        assertThat(c.demandW(1e6)).isCloseTo(1e5, within(1e-6));
        assertThat(c.advance(1e6, 1)).isFalse();
        assertThat(c.remainingJ()).isZero();
        assertThat(c.demandW(1e6)).isZero();
        assertThat(c.progress()).isCloseTo(0.1, within(1e-12));
        for (int i = 0; i < 8; i++) assertThat(c.advance(0, 1)).isFalse();
        assertThat(c.advance(0, 1)).isTrue();

        c.start(1e6, 10);
        int s = 0;
        while (!c.advance(50e3, 1)) s++;
        assertThat(s + 1).isEqualTo(20);
        c.clear();
        assertThat(c.active()).isFalse();
        assertThat(c.progress()).isZero();
    }

    /** Орошение оставляет пятую часть пыли; распёртая мехкрепь даёт больше арочной. */
    @Test
    void sprayCutsDustAndPoweredSupportOutranksArch() {
        assertThat(CFG.sprayDustFactor()).isEqualTo(0.2);
        assertThat(MineSupport.POWERED.capacityN).isBetween(4e6, 6e6);
        assertThat(MineSupport.POWERED.spanBonus).isGreaterThan(MineSupport.ARCH.spanBonus);
    }
}
