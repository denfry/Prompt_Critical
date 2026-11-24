package dev.denfry.promptcritical.sim.npp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class SpentFuelPoolTest {
    private static final SpentFuelPool.Spec S = SpentFuelPool.VVER70;

    private static void run(SpentFuelPool p, double seconds, double decayW, int n, boolean powered) {
        for (double t = 0; t < seconds; t += 10) p.step(10, decayW, n, powered, powered, 20);
    }

    @Test
    void cooledPoolSettlesJustAboveTheServiceWater() {
        SpentFuelPool p = new SpentFuelPool(S);
        run(p, 3 * 24 * 3600, 300e3, 100, true);
        // равновесие: Q = UA·ΔT + h·A·(T − воздух) → ≈ 25 + 300 кВт / 50 кВт/К
        assertThat(p.waterC()).isBetween(30.0, 31.5);
        assertThat(p.levelM()).isCloseTo(S.nominalLevelM(), within(1e-6));
        assertThat(p.shieldWaterCm()).isCloseTo(400, within(1e-3));
        assertThat(p.uncovered()).isZero();
    }

    @Test
    void withoutPowerItHeatsForADayThenBoilsAndLosesSuctionForGood() {
        SpentFuelPool p = new SpentFuelPool(S);
        double t = 0;
        while (p.boilKgS() == 0 && t < 10 * 24 * 3600) {
            p.step(10, 500e3, 150, false, false, 20);
            t += 10;
        }
        // ≈ 182 т воды от 25 до 100 °C на 500 кВт — около суток
        assertThat(t / 3600).isBetween(15.0, 40.0);
        // на первом шаге кипения часть тепла ещё догревала воду: расход пара — со следующего шага
        p.step(10, 500e3, 150, false, false, 20);
        assertThat(p.boilKgS()).isCloseTo(500e3 / SpentFuelPool.H_FG, within(0.05));
        run(p, 2.5 * 24 * 3600, 500e3, 150, false);
        assertThat(p.levelM()).isLessThan(S.suctionM());
        // питание вернулось, а всаса нет: охлаждение не берёт воду, пока подпитка не поднимет уровень
        p.step(10, 500e3, 150, true, false, 20);
        assertThat(p.coolingW()).isZero();
        run(p, 3 * 3600, 500e3, 150, true);
        assertThat(p.coolingW()).isPositive();
    }

    @Test
    void uncoveredFreshFuelBurstsItsCladdingAgedFuelDoesNot() {
        SpentFuelPool fresh = new SpentFuelPool(S), aged = new SpentFuelPool(S);
        fresh.restore(S.waterKgAt(0.3), 100, 100, false);
        aged.restore(S.waterKgAt(0.3), 100, 100, false);
        assertThat(fresh.uncovered()).isEqualTo(1.0);
        // 20 кВт на ТВС через неделю после выгрузки против 1 кВт через годы
        for (int i = 0; i < 720; i++) {
            fresh.step(10, 50 * 20e3, 50, false, false, 20);
            aged.step(10, 50 * 1e3, 50, false, false, 20);
        }
        assertThat(fresh.cladFailed()).isTrue();
        assertThat(aged.cladFailed()).isFalse();
        assertThat(aged.cladC()).isLessThan(SpentFuelPool.CLAD_FAIL_C);
    }

    @Test
    void sameInputsGiveTheSameState() {
        SpentFuelPool a = new SpentFuelPool(S), b = new SpentFuelPool(S);
        run(a, 36_000, 700e3, 200, false);
        run(b, 36_000, 700e3, 200, false);
        assertThat(a.waterKg()).isEqualTo(b.waterKg());
        assertThat(a.waterC()).isEqualTo(b.waterC());
    }
}
