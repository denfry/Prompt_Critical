package dev.denfry.promptcritical.sim.thermo.if97;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * Контрольные значения из текстов стандартов IAPWS: IF97 (таблицы 5, 15, 33,
 * 35, 36, 42 и граница B23), вязкость 2008, теплопроводность 2011,
 * поверхностное натяжение 2014. Допуск — последняя значащая цифра таблиц.
 */
class If97VerificationTest {
    private static final double REL = 1e-8;

    private static void close(double actual, double expected, double rel) {
        assertThat(actual).isCloseTo(expected, within(Math.abs(expected) * rel));
    }

    /** p МПа, T К; v м³/кг; h, u кДж/кг; s, cp кДж/(кг·К); w м/с. */
    @ParameterizedTest(name = "region {0}: p={1} MPa, T={2} K")
    @CsvSource({
        "1, 3,      300,  0.100215168e-2, 0.115331273e3, 0.112324818e3, 0.392294792,   0.417301218e1, 0.150773921e4",
        "1, 80,     300,  0.971180894e-3, 0.184142828e3, 0.106448356e3, 0.368563852,   0.401008987e1, 0.163469054e4",
        "1, 3,      500,  0.120241800e-2, 0.975542239e3, 0.971934985e3, 0.258041912e1, 0.465580682e1, 0.124071337e4",
        "2, 0.0035, 300,  0.394913866e2,  0.254991145e4, 0.241169160e4, 0.852238967e1, 0.191300162e1, 0.427920172e3",
        "2, 0.0035, 700,  0.923015898e2,  0.333568375e4, 0.301262819e4, 0.101749996e2, 0.208141274e1, 0.644289068e3",
        "2, 30,     700,  0.542946619e-2, 0.263149474e4, 0.246861076e4, 0.517540298e1, 0.103505092e2, 0.480386523e3",
        "5, 0.5,    1500, 0.138455090e1,  0.521976855e4, 0.452749310e4, 0.965408875e1, 0.261609445e1, 0.917068690e3",
        "5, 30,     1500, 0.230761299e-1, 0.516723514e4, 0.447495124e4, 0.772970133e1, 0.272724317e1, 0.928548002e3",
        "5, 30,     2000, 0.311385219e-1, 0.657122604e4, 0.563707038e4, 0.853640523e1, 0.288569882e1, 0.106736948e4",
    })
    void gibbsRegions(int region, double pMpa, double t, double v, double h, double u, double s, double cp, double w) {
        If97Point st = If97.inRegion(region, pMpa * 1e6, t);
        assertThat(If97.region(pMpa * 1e6, t)).isEqualTo(region);
        close(st.v(), v, REL);
        close(st.h(), h * 1e3, REL);
        close(st.u(), u * 1e3, REL);
        close(st.s(), s * 1e3, REL);
        close(st.cp(), cp * 1e3, REL);
        close(st.w(), w, REL);
    }

    /** ρ кг/м³, T К; p МПа; h, u кДж/кг; s, cp кДж/(кг·К); w м/с. */
    @ParameterizedTest(name = "region 3: rho={0}, T={1} K")
    @CsvSource({
        "500, 650, 0.255837018e2, 0.186343019e4, 0.181226279e4, 0.405427273e1, 0.138935717e2, 0.502005554e3",
        "200, 650, 0.222930643e2, 0.237512401e4, 0.226365868e4, 0.485438792e1, 0.446579342e2, 0.383444594e3",
        "500, 750, 0.783095639e2, 0.225868845e4, 0.210206932e4, 0.446971906e1, 0.634165359e1, 0.760696041e3",
    })
    void region3(double rho, double t, double p, double h, double u, double s, double cp, double w) {
        If97Point st = If97.fromRegion3(rho, t);
        close(st.p(), p * 1e6, REL);
        close(st.h(), h * 1e3, REL);
        close(st.u(), u * 1e3, REL);
        close(st.s(), s * 1e3, REL);
        close(st.cp(), cp * 1e3, REL);
        close(st.w(), w, REL);
        // обращение (p, T) -> ρ возвращает исходную плотность
        assertThat(If97.rho3(st.p(), t)).isCloseTo(rho, within(rho * 1e-9));
    }

    @ParameterizedTest(name = "p_s({0} K)")
    @CsvSource({"300, 0.353658941e-2", "500, 0.263889776e1", "600, 0.123443146e2"})
    void saturationPressure(double t, double ps) {
        close(If97.psat(t), ps * 1e6, REL);
    }

    @ParameterizedTest(name = "T_s({0} MPa)")
    @CsvSource({"0.1, 0.372755919e3", "1, 0.453035632e3", "10, 0.584149488e3"})
    void saturationTemperature(double pMpa, double ts) {
        close(If97.tsat(pMpa * 1e6), ts, REL);
    }

    @Test
    void boundary23() {
        close(If97.pB23(623.15), 0.165291643e2 * 1e6, REL);
        close(If97.tB23(0.165291643e2 * 1e6), 623.15, REL);
    }

    /** IAPWS 2008, таблица 4 (μ₂ = 1 для всех точек вне критической области). */
    @ParameterizedTest(name = "mu(T={0}, rho={1})")
    @CsvSource({
        "298.15, 998,  889.735100", "298.15, 1200, 1437.649467", "373.15, 1000, 307.883622",
        "433.15, 1,    14.538324",  "433.15, 1000, 217.685358",  "873.15, 1,    32.619287",
        "873.15, 100,  35.802262",  "873.15, 600,  77.430195",   "1173.15, 1,   44.217245",
        "1173.15, 100, 47.640433",  "1173.15, 400, 64.154608"})
    void viscosity(double t, double rho, double muMicro) {
        // таблица дана с шестью знаками после запятой в мкПа·с
        assertThat(Transport.viscosity(rho, t)).isCloseTo(muMicro * 1e-6, within(0.6e-12));
    }

    /** IAPWS 2011, таблица 4: λ₀λ₁ вне критической области (λ₂ пренебрежимо мало). */
    @ParameterizedTest(name = "k(T={0}, rho={1})")
    @CsvSource({"298.15, 0, 18.4341883", "298.15, 998, 607.712868", "298.15, 1200, 799.038144", "873.15, 0, 79.1034659"})
    void conductivityBackground(double t, double rho, double kMilli) {
        close(Transport.conductivity(rho, t, Double.NaN, Double.NaN, Double.NaN, Double.NaN), kMilli * 1e-3, 1e-8);
    }

    @Test
    void surfaceTension() {
        // IAPWS 2014, таблица 1: 300 К -> 71,69 мН/м; T_s(15,5 МПа) -> 4,67 мН/м (01 T-1.3-02: 0,0047)
        assertThat(Transport.surfaceTension(300.0)).isCloseTo(71.69e-3, within(0.01e-3));
        assertThat(Transport.surfaceTension(617.94155)).isCloseTo(4.669e-3, within(0.001e-3));
        assertThat(Transport.surfaceTension(If97.T_C)).isZero();
    }
}
