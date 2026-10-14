package dev.denfry.promptcritical.sim.thermo;

import static dev.denfry.promptcritical.sim.thermo.GasSpecies.R_U;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.thermo.if97.If97;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Идеальная газовая смесь (01 §1.3.10). */
class IdealGasMixtureTest {
    private static final WaterProperties W = WaterProperties.tables();

    private record Ref(GasSpecies species, double t, double cp, double dh) {}

    /** Идеально-газовые части уравнений состояния CoolProp ({@code pc-sim/tools/ideal_gas_reference.py}). */
    private static List<Ref> reference() throws IOException {
        List<Ref> rows = new ArrayList<>();
        try (var in = Objects.requireNonNull(
                        IdealGasMixtureTest.class.getResourceAsStream("/validation/ideal_gas_reference.csv"));
                var r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            r.readLine();
            for (String line; (line = r.readLine()) != null; ) {
                String[] c = line.split(",");
                rows.add(new Ref(GasSpecies.valueOf(c[0]), Double.parseDouble(c[1]), Double.parseDouble(c[2]),
                        Double.parseDouble(c[3])));
            }
        }
        return rows;
    }

    /**
     * До 1000 К — 0,5 %. Выше один полином NASA-7 покрывает 1000–6000 К, и у пара его
     * ошибка доходит до 1 % при 2000 К (c_p 51,7 против 51,2 Дж/(моль·К) по JANAF и IAPWS-95).
     */
    @Test
    void nasaPolynomialsMatchIndependentReference() throws IOException {
        List<Ref> rows = reference();
        assertThat(rows).hasSize(232);
        for (Ref r : rows) {
            GasSpecies s = r.species();
            double tol = r.t() <= 1000 ? 0.005 : 0.01;
            assertThat(s.cp(r.t())).as("c_p %s at %s K", s, r.t()).isCloseTo(r.cp(), within(tol * r.cp()));
            double dh = s.enthalpy(r.t()) - s.enthalpy(298.15);
            assertThat(dh).as("Δh %s at %s K", s, r.t()).isCloseTo(r.dh(), within(Math.max(0.005 * Math.abs(r.dh()), 20)));
        }
    }

    @Test
    void polynomialsAreContinuousAt1000K() {
        for (GasSpecies s : GasSpecies.values()) {
            assertThat(s.cp(1000 + 1e-9)).as("c_p %s", s).isCloseTo(s.cp(1000 - 1e-9), within(1e-3 * s.cp(1000)));
            assertThat(s.enthalpy(1000 + 1e-9)).as("h %s", s).isCloseTo(s.enthalpy(1000 - 1e-9), within(5.0));
        }
    }

    @Test
    void idealGasLawAndComposition() {
        var air = new IdealGasMixture(4500);
        double p = 101325, t = 300, n = p * 4500 / (R_U * t);
        air.setMoles(GasSpecies.N2, 0.79 * n);
        air.setMoles(GasSpecies.O2, 0.21 * n);
        air.setTemperature(t);
        assertThat(air.temperature()).isCloseTo(t, within(1e-9));
        assertThat(air.pressure()).isCloseTo(p, within(1e-9 * p));
        assertThat(air.partialPressure(GasSpecies.O2)).isCloseTo(0.21 * p, within(1e-9 * p));
        assertThat(air.moleFraction(GasSpecies.N2)).isCloseTo(0.79, within(1e-12));
        assertThat(air.moleFraction(GasSpecies.HE)).isZero();
        // добавление компонента не меняет температуру
        air.setMoles(GasSpecies.H2, 0.04 * n);
        assertThat(air.temperature()).isCloseTo(t, within(1e-9));
        assertThat(air.pressure()).isCloseTo(1.04 * p, within(1e-9 * p));
    }

    /** Подведённая теплота равна ∫C_v dT; обратная операция возвращает температуру. */
    @Test
    void energyBalance() {
        var m = new IdealGasMixture(1);
        m.setMoles(GasSpecies.N2, 30);
        m.setMoles(GasSpecies.CO2, 5);
        m.setMoles(GasSpecies.H2O_VAPOR, 2);
        m.setTemperature(320);
        double q = 2.5e6;
        m.addEnergy(q);
        double t1 = m.temperature();
        assertThat(t1).isGreaterThan(1000); // через стык диапазонов полиномов
        int n = 2000;
        double step = (t1 - 320) / n, integral = m.heatCapacityV(320) + m.heatCapacityV(t1);
        for (int i = 1; i < n; i++) integral += (i % 2 == 1 ? 4 : 2) * m.heatCapacityV(320 + i * step);
        assertThat(integral * step / 3).isCloseTo(q, within(1e-4 * q));
        m.addEnergy(-q);
        assertThat(m.temperature()).isCloseTo(320, within(1e-7));
    }

    /** Энергия пара — в отсчёте IF97: энтальпия идеального пара совпадает с IF97 при малом давлении. */
    @Test
    void steamEnergyIsInIf97Frame() {
        double mu = GasSpecies.H2O_VAPOR.molarMass();
        for (double t = 280; t <= 1000; t += 40) {
            var m = new IdealGasMixture(1);
            m.setMoles(GasSpecies.H2O_VAPOR, 1);
            m.setTemperature(t);
            double h = (m.internalEnergy() + R_U * t) / mu;
            assertThat(h).as("h at %s K", t).isCloseTo(If97.inRegion(2, 1.0, t).h(), within(2e3));
        }
    }

    /** Пересыщенный пар конденсируется до p_H₂O = p_sat(T'), масса и энергия сохраняются. */
    @Test
    void condensationConservesMassAndEnergy() {
        double v = 10, t0 = 380;
        var m = new IdealGasMixture(v);
        m.setMoles(GasSpecies.N2, 1e5 * v / (R_U * t0));
        m.setMoles(GasSpecies.H2O_VAPOR, 2 * W.saturationPressure(t0) * v / (R_U * t0));
        m.setTemperature(t0);
        double nw0 = m.moles(GasSpecies.H2O_VAPOR), u0 = m.internalEnergy();

        double condensate = m.condenseExcessSteam(W);

        double t1 = m.temperature();
        assertThat(condensate).isPositive();
        assertThat(t1).isGreaterThan(t0); // теплота конденсации осталась в газе
        assertThat(m.partialPressure(GasSpecies.H2O_VAPOR)).isCloseTo(W.saturationPressure(t1),
                within(1e-6 * W.saturationPressure(t1)));
        double mu = GasSpecies.H2O_VAPOR.molarMass();
        assertThat(m.moles(GasSpecies.H2O_VAPOR) * mu + condensate).isCloseTo(nw0 * mu, within(1e-12 * nw0 * mu));
        double uf = W.saturationP(W.saturationPressure(t1)).uF();
        assertThat(m.internalEnergy() + condensate * uf).isCloseTo(u0, within(1e-9 * Math.abs(u0)));

        assertThat(m.condenseExcessSteam(W)).isZero();
    }

    @Test
    void noCondensationWhenUnsaturatedOrOutOfRange() {
        var m = new IdealGasMixture(1);
        assertThat(m.condenseExcessSteam(W)).isZero();
        m.setMoles(GasSpecies.H2O_VAPOR, 0.5 * W.saturationPressure(350) / (R_U * 350));
        m.setTemperature(350);
        assertThat(m.condenseExcessSteam(W)).isZero();
        m.setTemperature(700); // выше T_c
        m.setMoles(GasSpecies.H2O_VAPOR, 5000);
        assertThat(m.condenseExcessSteam(W)).isZero();
    }
}
