package dev.denfry.promptcritical.sim.thermo.if97;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Сверка с независимой реализацией IF97 (CoolProp, бэкенд IF97::Water) по
 * 1160 точкам {@code validation/if97_reference.csv}; генератор —
 * {@code pc-sim/tools/if97_reference.py}.
 */
class If97ReferenceTest {
    private record Row(String kind, double p, double t, double rho, double h, double u, double s,
            double cp, double cv, double w, double mu, double k) {}

    private static List<Row> rows;

    @BeforeAll
    static void load() throws IOException {
        rows = new ArrayList<>();
        try (var in = Objects.requireNonNull(If97ReferenceTest.class.getResourceAsStream("/validation/if97_reference.csv"));
                var r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            r.readLine();
            for (String line; (line = r.readLine()) != null; ) {
                String[] c = line.split(",", -1);
                double[] d = new double[11];
                for (int i = 0; i < 11; i++) d[i] = c[i + 1].isEmpty() ? Double.NaN : Double.parseDouble(c[i + 1]);
                rows.add(new Row(c[0], d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7], d[8], d[9], d[10]));
            }
        }
        assertThat(rows).hasSize(1160);
    }

    private static double rel(double a, double e) {
        return Math.abs(a - e) / Math.max(Math.abs(e), 1e-300);
    }

    @Test
    void pressureTemperaturePoints() {
        List<String> bad = new ArrayList<>();
        for (Row r : rows) {
            if (!r.kind.equals("PT")) continue;
            If97Point s = If97.pt(r.p, r.t);
            double e = Math.max(Math.max(rel(s.rho(), r.rho), rel(s.h(), r.h)),
                    Math.max(rel(s.s(), r.s), Math.max(rel(s.cp(), r.cp), rel(s.w(), r.w))));
            e = Math.max(e, Math.max(rel(s.u(), r.u), rel(s.cv(), r.cv)));
            if (e > 1e-6) bad.add(r + " -> " + s + " err " + e);
        }
        assertThat(bad).isEmpty();
    }

    @Test
    void transportProperties() {
        List<String> bad = new ArrayList<>();
        for (Row r : rows) {
            if (!r.kind.equals("PT") || r.t > 1173.15) continue; // область IAPWS 2008/2011
            If97Point s = If97.pt(r.p, r.t);
            if (rel(s.mu(), r.mu) > 1e-6) bad.add("mu " + r + " -> " + s.mu());
            if (rel(s.k(), r.k) > 1e-3) bad.add("k " + r + " -> " + s.k());
        }
        assertThat(bad).isEmpty();
    }

    @Test
    void saturationLine() {
        List<String> bad = new ArrayList<>();
        for (Row r : rows) {
            if (!r.kind.startsWith("SAT")) continue;
            If97Point s = r.kind.equals("SAT0") ? If97.satLiquid(r.p) : If97.satVapor(r.p);
            double e = Math.max(Math.max(rel(s.rho(), r.rho), rel(s.h(), r.h)), rel(s.s(), r.s));
            // выше 16,53 МПа CoolProp берёт ρ' и ρ'' из обратных уравнений v(p,T) (IAPWS 2005)
            // с собственной погрешностью ~1e-6; здесь — точный корень базового уравнения региона 3
            // у критической точки расхождение обратных уравнений растёт (21,5 МПа: 4e-4)
            double tol = r.p <= If97.P_S13 ? 1e-6 : r.p <= 21e6 ? 1e-5 : 1e-3;
            if (Math.abs(s.t() - r.t) > 1e-6 || e > tol) bad.add(r + " -> " + s + " err " + e);
        }
        assertThat(bad).isEmpty();
    }

    @Test
    void pressureEnthalpyPoints() {
        List<String> bad = new ArrayList<>();
        for (Row r : rows) {
            if (!r.kind.equals("PH")) continue;
            If97Point s = If97.ph(r.p, r.h);
            // CoolProp считает T(p,h) по обратным уравнениям IF97 без уточнения; их допустимое
            // отклонение от базовых — 25 мК (регионы 1, 3) и 10 мК (регион 2), у нас — точное обращение
            double tolT = switch (s.region()) {
                case 2 -> 0.010;
                case 4 -> 1e-9;
                case 5 -> 1e-6;
                default -> 0.025;
            };
            double tolRho = s.region() == 4 ? 1e-5 : 2e-4;
            if (Math.abs(s.t() - r.t) > tolT || rel(s.rho(), r.rho) > tolRho) bad.add(r + " -> " + s);
        }
        assertThat(bad).isEmpty();
    }

    /** Обращение (p, h) -> T согласовано с прямой задачей (p, T) -> h по базовым уравнениям. */
    @Test
    void pressureEnthalpyInvertsPressureTemperature() {
        List<String> bad = new ArrayList<>();
        for (Row r : rows) {
            if (!r.kind.equals("PT")) continue;
            If97Point fwd = If97.pt(r.p, r.t);
            If97Point inv = If97.ph(r.p, fwd.h());
            if (inv.region() != fwd.region() || Math.abs(inv.t() - r.t) > 1e-7 || rel(inv.rho(), fwd.rho()) > 1e-10) {
                bad.add(fwd + " -> " + inv);
            }
        }
        assertThat(bad).isEmpty();
    }
}
