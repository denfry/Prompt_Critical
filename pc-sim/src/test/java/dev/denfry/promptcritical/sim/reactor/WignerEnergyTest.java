package dev.denfry.promptcritical.sim.reactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import org.junit.jupiter.api.Test;

/** Энергия Вигнера (04c §4c.11.1): насыщение по флюенсу, спектр отжига, самоподдерживающийся нагрев, отжиг. */
class WignerEnergyTest {
    private static final double PHI_S = 1e21;

    @Test
    void saturationFallsLinearlyWithIrradiationTemperature() {
        assertThat(WignerEnergy.saturation(300)).isEqualTo(2000);
        assertThat(WignerEnergy.saturation(450)).isCloseTo(1000, within(1e-9));
        assertThat(WignerEnergy.saturation(600)).isZero();
        assertThat(WignerEnergy.saturation(700)).isZero();
    }

    /** dE/dΦ = k_W (1 − E/E_sat): малый флюенс — линейно с k_W = E₀/Φ_s, флюенс Φ_s — 1 − 1/e от насыщения. */
    @Test
    void storedEnergyApproachesSaturationOverTheFluenceScale() {
        WignerEnergy small = new WignerEnergy(PHI_S);
        small.irradiate(1e18, 300);
        assertThat(small.storedJg()).isCloseTo(2000 * 1e18 / PHI_S, within(0.01));
        WignerEnergy w = new WignerEnergy(PHI_S);
        for (int i = 0; i < 100; i++) w.irradiate(PHI_S / 100, 300);
        assertThat(w.storedJg()).isCloseTo(2000 * (1 - Math.exp(-1)), within(1.0));
        for (int i = 0; i < 1000; i++) w.irradiate(PHI_S / 100, 300);
        assertThat(w.storedJg()).isCloseTo(2000, within(1.0));
        WignerEnergy hot = new WignerEnergy(PHI_S);
        hot.irradiate(10 * PHI_S, 620);
        assertThat(hot.storedJg()).isZero();
    }

    /** Холодный графит держит запас; выше пика (200 °C) — отдаёт почти весь за час. */
    @Test
    void energyStaysInColdGraphiteAndLeavesAboveThePeak() {
        WignerEnergy w = new WignerEnergy(PHI_S);
        w.preload(300, 293);
        assertThat(w.storedJg()).isCloseTo(300, within(1e-6));
        double out = 0;
        for (int i = 0; i < 3600; i++) out += w.release(293, 1);
        assertThat(out).isLessThan(0.01 * 300); // час при 20 °C
        double warm = 0;
        for (int i = 0; i < 3600; i++) warm += w.release(373, 1);
        assertThat(warm).isLessThan(0.3 * 300); // 100 °C: уходит только низ спектра
        for (int i = 0; i < 3600; i++) w.release(650, 1);
        assertThat(w.storedJg()).isLessThan(0.05 * 300);
    }

    /**
     * Адиабатический нагрев (запас греет сам себя, dT = dE/c_p): малый запас поднимает графит на десятки градусов и
     * останавливается, большой уводит его выше 450 °C — пожар, как на Windscale.
     */
    @Test
    void largeStoreRunsAwayAdiabaticallySmallStoreDoesNot() {
        assertThat(adiabaticPeak(60, 380)).isLessThan(380 + 100);
        assertThat(adiabaticPeak(400, 380)).isGreaterThan(723);
        WignerEnergy w = new WignerEnergy(PHI_S);
        w.preload(400, 293);
        assertThat(w.spectrumDensity(420)).isGreaterThan(WignerEnergy.CP_J_GK);
        w.release(420, 1);
        assertThat(w.selfSustaining(420)).isTrue();
        WignerEnergy s = new WignerEnergy(PHI_S);
        s.preload(60, 293);
        assertThat(s.spectrumDensity(420)).isLessThan(WignerEnergy.CP_J_GK);
    }

    private static double adiabaticPeak(double jg, double t0) {
        WignerEnergy w = new WignerEnergy(PHI_S);
        w.preload(jg, 293);
        double t = t0, peak = t0;
        for (int i = 0; i < 20_000; i++) {
            t += w.release(t, 1) / WignerEnergy.CP_J_GK;
            peak = Math.max(peak, t);
        }
        return peak;
    }

    /** Отжиг засчитан: отдано больше половины запаса, графит ниже 400 °C; перегретый — нет. */
    @Test
    void annealCountsOnlyBelowOxidation() {
        WignerEnergy w = new WignerEnergy(PHI_S);
        w.preload(150, 293);
        w.release(560, 10);
        assertThat(w.takeCompletedAnneal()).isZero(); // запас ещё идёт
        for (int i = 0; i < 3600; i++) w.release(560, 10);
        double done = w.takeCompletedAnneal();
        assertThat(done).isGreaterThan(75);
        assertThat(w.takeCompletedAnneal()).isZero();

        WignerEnergy hot = new WignerEnergy(PHI_S);
        hot.preload(150, 293);
        for (int i = 0; i < 600; i++) hot.release(700, 10);
        assertThat(hot.takeCompletedAnneal()).isZero();
        assertThat(hot.peakJg()).isLessThan(10); // отсчёт начат заново
    }

    @Test
    void saveAndLoadRoundTrip() {
        WignerEnergy w = new WignerEnergy(PHI_S);
        w.preload(123, 293);
        w.release(450, 60);
        MutableStateTree tree = new MutableStateTree();
        w.save(new TreeStateWriter(tree));
        WignerEnergy q = new WignerEnergy(PHI_S);
        q.load(new TreeStateReader(tree));
        assertThat(q.storedJg()).isCloseTo(w.storedJg(), within(1e-9));
        assertThat(q.spectrumDensity(500)).isCloseTo(w.spectrumDensity(500), within(1e-9));
        assertThat(q.peakJg()).isEqualTo(w.peakJg());
    }
}
