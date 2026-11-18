package dev.denfry.promptcritical.sim.reactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * TRIGA (12 §12.6.1): импульс против оракула Фукса–Нордхейма (тест T12.8), самогашение мгновенным коэффициентом,
 * таймер импульса, защита стационарного режима и разрешение импульсного.
 */
class TrigaCoreTest {
    private static final double DT = 0.05;

    private static final class Log implements TrigaCore.Events {
        final List<String> scrams = new ArrayList<>();
        double peakW = -1, energyJ, fwhmS, peakFuelK;
        boolean damaged;
        int pulses, fuel;

        @Override
        public void scram(String reason, double powerW) {
            scrams.add(reason);
        }

        @Override
        public void fuelDamaged(double fuelK) {
            fuel++;
        }

        @Override
        public void pulseCompleted(double peakW, double energyJ, double fwhmS, double peakFuelK, boolean fuelDamaged) {
            pulses++;
            this.peakW = peakW;
            this.energyJ = energyJ;
            this.fwhmS = fwhmS;
            this.peakFuelK = peakFuelK;
            this.damaged = fuelDamaged;
        }
    }

    private static void run(TrigaCore c, double s, TrigaCore.Events ev) {
        for (int i = 0; i < Math.round(s / DT); i++) c.step(DT, ev);
    }

    /** Критичная зона на ваттах: импульсный стержень в зоне, регулирующие на критическом положении. */
    private static TrigaCore critical(double w) {
        TrigaCore c = new TrigaCore();
        assertThat(c.reset()).isTrue();
        // ρ = запас − регулирующие·x − импульсный = 0
        c.setControlInsertion((TrigaCore.CORE_EXCESS - TrigaCore.TRANSIENT_ROD_WORTH) / TrigaCore.CONTROL_ROD_WORTH);
        c.setSteady(w);
        assertThat(c.reactivity()).isCloseTo(0, within(1e-5));
        return c;
    }

    private static Log pulse(TrigaCore c, double dollars) {
        Log ev = new Log();
        assertThat(c.setPulseMode(true)).isTrue();
        c.setPulseReactivity(dollars * TrigaCore.BETA);
        assertThat(c.arm()).isTrue();
        assertThat(c.fire()).isTrue();
        run(c, 1.2, ev);
        return ev;
    }

    /** T12.8: $2, Λ = 40 мкс, C = 7·10⁴ Дж/К, |α| = 10⁻⁴: 429 МВт, 9,8 МДж, 20 мс, +140 К (выброс за 0,1 с). */
    @Test
    void twoDollarPulseMatchesFuchsNordheim() {
        double rho0 = 2 * TrigaCore.BETA;
        assertThat(TrigaCore.fuchsNordheimPeakW(rho0)).isCloseTo(429e6, within(2e6));
        assertThat(TrigaCore.fuchsNordheimEnergyJ(rho0)).isCloseTo(9.8e6, within(1e4));
        assertThat(TrigaCore.fuchsNordheimFwhmS(rho0)).isCloseTo(0.020, within(2e-4));
        TrigaCore c = critical(50);
        Log ev = pulse(c, 2);
        assertThat(ev.pulses).isEqualTo(1);
        // стержень выходит не мгновенно: вспышка чуть ниже и шире ступенчатой, порядок — как у оракула
        assertThat(ev.peakW).isBetween(0.5 * 429e6, 1.2 * 429e6);
        assertThat(ev.energyJ).isBetween(0.6 * 9.8e6, 1.5 * 9.8e6);
        assertThat(ev.fwhmS).isBetween(0.010, 0.045);
        assertThat(ev.peakFuelK - TrigaCore.POOL_K).isBetween(80.0, 220.0);
        assertThat(ev.damaged).isFalse();
        // самогашение: через секунду мощность на порядки ниже пика без всякой защиты
        assertThat(c.powerW()).isLessThan(0.05 * ev.peakW);
        assertThat(ev.scrams).isEmpty();
    }

    /** Полный стержень $3: больше 100 МВт («Импульс», 11) и топливо далеко от 1000 °C. */
    @Test
    void fullRodPulseIsAboveAHundredMegawattsAndSafe() {
        TrigaCore c = critical(50);
        Log ev = pulse(c, 3);
        assertThat(ev.peakW).isGreaterThan(100e6);
        assertThat(ev.peakW).isGreaterThan(pulse2Peak());
        assertThat(ev.peakFuelK).isLessThan(TrigaCore.FUEL_LIMIT_K);
        assertThat(c.fuelDamaged()).isFalse();
        // таймер импульса сбрасывает стержни
        Log after = new Log();
        run(c, TrigaCore.PULSE_SCRAM_S, after);
        assertThat(after.scrams).containsExactly("pulse_timer");
        assertThat(c.scrammed()).isTrue();
        assertThat(c.transientInsertion()).isEqualTo(1);
    }

    private static double pulse2Peak() {
        return pulse(critical(50), 2).peakW;
    }

    /** Ниже доллара мгновенной критичности нет: никакой вспышки, медленный подъём на периоде. */
    @Test
    void halfDollarIsNoPulse() {
        Log ev = pulse(critical(50), 0.5);
        assertThat(ev.peakW).isLessThan(1e5);
    }

    @Test
    void steadyModeTripsOnShortPeriodAndPulseNeedsLowPower() {
        TrigaCore c = critical(50);
        Log ev = new Log();
        c.drive(1); // регулирующие вверх без остановки
        run(c, 60, ev);
        assertThat(ev.scrams).containsExactly("short_period");
        assertThat(c.scrammed()).isTrue();

        TrigaCore hot = new TrigaCore();
        hot.reset();
        hot.setSteady(0.5e6);
        assertThat(hot.setPulseMode(true)).isFalse(); // с мегаватта импульс не разрешён
        assertThat(hot.arm()).isFalse();
    }

    /** На лицензированном мегаватте топливо около 450 °C. */
    @Test
    void megawattSteadyFuelTemperature() {
        TrigaCore c = new TrigaCore();
        c.setSteady(1e6);
        assertThat(c.fuelK() - 273.15).isCloseTo(443, within(10.0));
    }

    @Test
    void saveAndLoadRoundTrip() {
        TrigaCore c = critical(50);
        pulse(c, 2.5);
        MutableStateTree t = new MutableStateTree();
        c.save(new TreeStateWriter(t));
        TrigaCore q = new TrigaCore();
        q.load(new TreeStateReader(t));
        assertThat(q.pulsePeakW()).isEqualTo(c.pulsePeakW());
        assertThat(q.fuelK()).isEqualTo(c.fuelK());
        assertThat(q.powerW()).isCloseTo(c.powerW(), within(1e-6 * c.powerW() + 1e-9));
        assertThat(q.pulseReactivity()).isEqualTo(c.pulseReactivity());
    }
}
