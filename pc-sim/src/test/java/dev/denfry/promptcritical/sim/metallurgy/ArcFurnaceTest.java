package dev.denfry.promptcritical.sim.metallurgy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class ArcFurnaceTest {
    /** R = 1,33·X: cos φ 0,8, P = 3·(230/√3)²·0,48/X ≈ 3,17 МВт; при 0,9 о.е. напряжения — 0,81 от неё. */
    @Test
    void arcPowerAndPowerFactor() {
        ArcFurnace f = new ArcFurnace();
        f.charge(6000);
        f.restore(6000, ArcFurnace.H_LIQUID + 1e4, 0); // жидкая ванна: шум ±3 %
        double p = 0, q = 0;
        for (long t = 0; t < 2000; t++) {
            f.step(0.05, 1, t, true);
            p += f.powerW();
            q += f.reactiveVar();
        }
        p /= 2000;
        q /= 2000;
        assertThat(p).isCloseTo(3.17e6, within(0.05e6));
        assertThat(p / Math.hypot(p, q)).isCloseTo(0.8, within(0.01));
        f.step(0.05, 0.9, 7, true);
        double p90 = f.powerW();
        f.step(0.05, 1, 7, true);
        assertThat(p90 / f.powerW()).isCloseTo(0.81, within(1e-9));
    }

    /** 386 кВт·ч/т теоретически, при КПД 0,65 — ≈ 594; плавка 6 т — немногим больше часа. */
    @Test
    void aHeatFromColdScrapTakesAboutAnHour() {
        assertThat(ArcFurnace.specificEnergyKwhPerT()).isCloseTo(594, within(2.0));
        ArcFurnace f = new ArcFurnace();
        assertThat(f.charge(8000)).isEqualTo(6000);
        double borePeak = 0, boreMin = 1e9, qPeak = 0, qMin = 1e9, energy = 0;
        int s = 0;
        while (!f.readyToTap() && s < 3 * 3600) {
            for (int k = 0; k < 20; k++) {
                long tick = s * 20L + k;
                f.step(0.05, 1, tick, true);
                energy += f.powerW() * 0.05;
                if (f.moltenFraction() < 0.2) {
                    borePeak = Math.max(borePeak, f.powerW());
                    boreMin = Math.min(boreMin, f.powerW());
                    qPeak = Math.max(qPeak, f.reactiveVar());
                    qMin = Math.min(qMin, f.reactiveVar());
                }
            }
            s++;
        }
        assertThat(s / 60.0).isBetween(60.0, 80.0);
        assertThat(energy / 3.6e6 / 6).isCloseTo(594, within(5.0));
        assertThat(f.electrodeKg()).isCloseTo(30, within(0.5)); // 5 кг/т
        // фликер на проплавлении: у максимума мощность по R плоская (±13 %), реактивная скачет вдвое сильнее — она и
        // качает напряжение у соседей
        assertThat((borePeak - boreMin) / borePeak).isGreaterThan(0.1);
        assertThat((qPeak - qMin) / qPeak).isGreaterThan(0.4);
        double t = f.tempK();
        assertThat(f.tap()).isEqualTo(6000);
        assertThat(t).isGreaterThanOrEqualTo(ArcFurnace.TAP_K);
        assertThat(f.massKg()).isZero();
    }

    /** Холодный лом на жидкую ванну её охлаждает: энтальпия смешения. */
    @Test
    void scrapOnALiquidBathCoolsIt() {
        ArcFurnace f = new ArcFurnace();
        f.restore(3000, ArcFurnace.H_LIQUID + 820 * 100, 0);
        double before = f.tempK();
        f.charge(1000);
        assertThat(f.tempK()).isLessThan(before);
        assertThat(f.moltenFraction()).isLessThan(1);
    }
}
