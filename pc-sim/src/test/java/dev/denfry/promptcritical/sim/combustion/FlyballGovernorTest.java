package dev.denfry.promptcritical.sim.combustion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Центробежный регулятор Уатта (07 §7.9.7). */
class FlyballGovernorTest {
    private static final double DT = 0.05, W0 = 4 * Math.PI;

    /** Статизм 5 %: заслонка открыта до 0,975·ω₀ и закрыта от 1,025·ω₀. */
    @Test
    void droopBand() {
        FlyballGovernor g = FlyballGovernor.withDashpot();
        g.settle(0.975 * W0, W0);
        assertThat(g.throttle()).isCloseTo(1, within(1e-9));
        g.settle(1.025 * W0, W0);
        assertThat(g.throttle()).isCloseTo(0, within(1e-9));
        g.settle(W0, W0);
        assertThat(g.throttle()).isCloseTo(0.5, within(0.01));
        g.settle(0.5 * W0, W0);
        assertThat(g.throttle()).isEqualTo(1);
    }

    /** Скачок оборотов: без катаракта муфта колеблется (перерегулирование ≈ 70 %), с катарактом — без заброса. */
    @Test
    void dashpotDampsSleeve() {
        assertThat(overshoot(FlyballGovernor.bare())).isBetween(0.5, 0.85);
        assertThat(overshoot(FlyballGovernor.withDashpot())).isLessThan(0.06);
    }

    private static double overshoot(FlyballGovernor g) {
        g.settle(0.985 * W0, W0); // x ≈ 0,3
        double x0 = g.sleeve(), target = g.equilibriumSleeve(W0, W0), peak = x0;
        for (int k = 0; k < 1600; k++) {
            g.step(DT / 8, W0, W0);
            peak = Math.max(peak, g.sleeve());
        }
        assertThat(g.sleeve()).isCloseTo(target, within(0.02));
        return (peak - target) / (target - x0);
    }

    /**
     * Урок Максвелла: одноцилиндровая машина с маховиком на идеальном дросселе (p₁ = y·p_котла). Без катаракта контур
     * качает обороты незатухающе; с катарактом обороты успокаиваются в полосе статизма.
     */
    @Test
    void bareGovernorHuntsDashpotSettles() {
        double bare = hunting(FlyballGovernor.bare());
        double damped = hunting(FlyballGovernor.withDashpot());
        assertThat(bare).as("размах оборотов через 2 мин, без катаракта").isGreaterThan(0.01);
        assertThat(damped).as("с катарактом остаётся пульсация хода ≈ 0,25 %").isLessThan(0.005);
    }

    /** Размах ω/ω₀ на последних 20 с после сброса половины нагрузки. */
    private static double hunting(FlyballGovernor g) {
        double pSupply = 1.2e6, pb = 0.11e6, rho = 6.1;
        SteamEngineUnit u = new SteamEngineUnit(SteamEngineUnit.Spec.reference(1, SteamEngineUnit.FLYWHEEL_INERTIA), g);
        double full = 0.9 * u.engine().meanIndicatedTorqueNm(1.1e6, pb, 0.25);
        u.shaft().setOmega(u.nominalOmega());
        g.settle(u.nominalOmega(), u.nominalOmega());
        double lo = Double.MAX_VALUE, hi = 0;
        for (int k = 0; k < 20 * 140; k++) {
            double load = k < 20 * 20 ? full : 0.5 * full;
            double p1 = Math.max(pb, u.throttle() * pSupply);
            u.step(DT, p1, rho * p1 / pSupply, pb, load);
            if (k >= 20 * 120) {
                lo = Math.min(lo, u.omega());
                hi = Math.max(hi, u.omega());
            }
        }
        return (hi - lo) / u.nominalOmega();
    }
}
