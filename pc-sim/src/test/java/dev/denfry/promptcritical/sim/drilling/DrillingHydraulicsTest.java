package dev.denfry.promptcritical.sim.drilling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** СИ-формулы против промысловых формул Bourgoyne et al. (гл. 4) с переводом единиц. */
class DrillingHydraulicsTest {
    private static final double GPM = 6.309_02e-5, PPG = 119.826_427, IN = 0.0254, FT = 0.3048, PSI = 6894.757;
    private static final double CP = 1e-3, YP = 0.478_803; // 1 фунт/100 фут² = 0,4788 Па

    /** Ламинарный бинхамовский поток в трубе: dp/dL = μₚv/(1500d²) + τ₀/(225d), psi/фут, v в фут/с. */
    @Test
    void laminarPipeMatchesFieldUnits() {
        double q = 100 * GPM, d = 4.276 * IN, rho = 10 * PPG, pv = 40 * CP, yp = 25 * YP;
        double vFt = 100 / (2.448 * 4.276 * 4.276);
        double fieldPsiFt = 40 * vFt / (1500 * 4.276 * 4.276) + 25 / (225 * 4.276);
        double si = DrillingHydraulics.pipeGradientPaM(q, d, rho, pv, yp);
        assertThat(si / PSI * FT).isCloseTo(fieldPsiFt, within(fieldPsiFt * 0.003));
    }

    /** Ламинарный поток в кольце 8½″ × 5″: dp/dL = μₚv/(1000Δd²) + τ₀/(200Δd). */
    @Test
    void laminarAnnulusMatchesFieldUnits() {
        double q = 300 * GPM, rho = 10 * PPG, pv = 20 * CP, yp = 15 * YP;
        double vFt = 300 / (2.448 * (8.5 * 8.5 - 5 * 5));
        double gap = 3.5;
        double fieldPsiFt = 20 * vFt / (1000 * gap * gap) + 15 / (200 * gap);
        double si = DrillingHydraulics.annulusGradientPaM(q, 8.5 * IN, 5 * IN, rho, pv, yp);
        assertThat(si / PSI * FT).isCloseTo(fieldPsiFt, within(fieldPsiFt * 0.003));
    }

    /** Турбулентный поток в трубе (Блазиус): dp/dL = ρ^0,75 v^1,75 μₚ^0,25 / (1800 d^1,25), книга 4.66. */
    @Test
    void turbulentPipeMatchesFieldUnits() {
        double q = 600 * GPM, d = 4.276 * IN, rho = 10 * PPG, pv = 20 * CP, yp = 15 * YP;
        double vFt = 600 / (2.448 * 4.276 * 4.276);
        double fieldPsiFt = Math.pow(10, 0.75) * Math.pow(vFt, 1.75) * Math.pow(20, 0.25) / (1800 * Math.pow(4.276, 1.25));
        double si = DrillingHydraulics.pipeGradientPaM(q, d, rho, pv, yp);
        assertThat(si / PSI * FT).isCloseTo(fieldPsiFt, within(fieldPsiFt * 0.01));
    }

    /** Насадки 3 × 16/32″ (0,589 дюйм²), 600 галлон/мин, 10 фунт/галлон: Δp = 8,311·10⁻⁵ρq²/(C_d²A²) ≈ 956 psi. */
    @Test
    void bitPressureDropMatchesFieldUnits() {
        double field = 8.311e-5 * 10 * 600 * 600 / (0.95 * 0.95 * 0.589 * 0.589);
        double si = DrillingHydraulics.bitPressureDropPa(600 * GPM, 10 * PPG, 0.589 * IN * IN);
        assertThat(si / PSI).isCloseTo(field, within(field * 0.002));
        assertThat(field).isCloseTo(956, within(2.0));
        // сила удара струй: 0,01823·C_d·q·√(ρΔp) фунтов
        double jetField = 0.01823 * 0.95 * 600 * Math.sqrt(10 * field);
        assertThat(DrillingHydraulics.jetImpactN(600 * GPM, 10 * PPG, 0.589 * IN * IN) / 4.448_222)
                .isCloseTo(jetField, within(jetField * 0.005));
    }

    @Test
    void ecdOfStaticColumnIsMudDensity() {
        assertThat(DrillingHydraulics.equivalentDensityKgM3(1200 * 9.806_65 * 2000, 2000)).isCloseTo(1200, within(1e-9));
    }
}
