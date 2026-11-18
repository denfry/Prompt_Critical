package dev.denfry.promptcritical.sim.consequence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.Test;

/**
 * Рассеяние выброса (гауссова модель, Бриггс, Пасквилл) и пробиты токсичных газов: хлор (CCPS 1999), фтористый водород
 * (TNO Green Book 1992).
 */
class GasDispersionTest {
    @Test
    void stabilityClassesFollowPasquill() {
        assertThat(GasDispersion.stability(1.5, 800)).isEqualTo(GasDispersion.Stability.A);
        assertThat(GasDispersion.stability(4, 400)).isEqualTo(GasDispersion.Stability.B);
        assertThat(GasDispersion.stability(7, 200)).isEqualTo(GasDispersion.Stability.D);
        assertThat(GasDispersion.stability(2, 0)).isEqualTo(GasDispersion.Stability.F);
        assertThat(GasDispersion.stability(8, 0)).isEqualTo(GasDispersion.Stability.D);
    }

    @Test
    void briggsSigmasAtOneKilometre() {
        // Briggs, открытая местность, D: σy ≈ 76 м, σz ≈ 38 м на 1 км (Turner workbook — 68 и 32 по Пасквиллу–Гиффорду)
        assertThat(GasDispersion.Stability.D.sigmaY(1000)).isCloseTo(76, within(2.0));
        assertThat(GasDispersion.Stability.D.sigmaZ(1000)).isCloseTo(37.9, within(1.0));
        assertThat(GasDispersion.Stability.F.sigmaZ(1000)).isLessThan(GasDispersion.Stability.D.sigmaZ(1000) / 2);
    }

    @Test
    void plumeIsDangerousDownwindAndNightIsWorse() {
        // утечка 1 кг/с (разрыв трубки ½″ на жидком хлоре) при ветре 3 м/с
        double day = ToxicGas.CHLORINE.ppm(GasDispersion.plumeKgM3(1, 3, GasDispersion.Stability.C, 200, 0));
        double night = ToxicGas.CHLORINE.ppm(GasDispersion.plumeKgM3(1, 3, GasDispersion.Stability.F, 200, 0));
        assertThat(day).isGreaterThan(ToxicGas.CHLORINE.idlhPpm);
        assertThat(night).isGreaterThan(5 * day);
        // концентрация ∝ 1/u и спадает поперёк
        double side = ToxicGas.CHLORINE.ppm(GasDispersion.plumeKgM3(1, 3, GasDispersion.Stability.C, 200, 60));
        assertThat(side).isLessThan(day / 2);
        assertThat(GasDispersion.plumeKgM3(1, 6, GasDispersion.Stability.C, 200, 0))
                .isCloseTo(GasDispersion.plumeKgM3(1, 3, GasDispersion.Stability.C, 200, 0) / 2, within(1e-12));
        assertThat(GasDispersion.plumeKgM3(1, 3, GasDispersion.Stability.C, -50, 0)).isZero();
    }

    @Test
    void puffMassIsConservedOverGround() {
        // интеграл приземного клуба по площади с отражением от земли: ∫∫C dx dy = M·2/(√(2π)·σz)
        double m = 100, u = 2, t = 300;
        GasDispersion.Stability s = GasDispersion.Stability.D;
        double sum = 0, step = 2;
        for (double xx = 0; xx < 1200; xx += step)
            for (double yy = -300; yy < 300; yy += step) sum += GasDispersion.puffKgM3(m, u, s, xx, yy, t) * step * step;
        double sz = s.sigmaZ(u * t);
        assertThat(sum).isCloseTo(m * 2 / (Math.sqrt(2 * Math.PI) * sz), within(sum * 0.02));
    }

    @Test
    void windFrameUsesMeteorologicalDirection() {
        // северный ветер (с −Z) несёт на юг (+Z)
        double[] p = GasDispersion.windFrame(0, 100, 0);
        assertThat(p[0]).isCloseTo(100, within(1e-9));
        assertThat(p[1]).isCloseTo(0, within(1e-9));
        // восточный ветер (с +X) несёт на запад
        double[] q = GasDispersion.windFrame(-100, 0, Math.PI / 2);
        assertThat(q[0]).isCloseTo(100, within(1e-9));
    }

    @Test
    void probitLethalityForChlorine() {
        // 50 % летальности: ln(C²t) = (5 + 8,29)/0,92 → при 30 мин ≈ 250 ppm; AEGL-3 на 30 мин — 28 ppm, пробит
        // там даёт доли процента
        double c = Math.sqrt(Math.exp((5 + 8.29) / 0.92) / 30);
        assertThat(c).isBetween(230.0, 270.0);
        assertThat(ToxicGas.CHLORINE.lethality(28 * 28 * 30)).isLessThan(0.01);
        assertThat(ToxicGas.CHLORINE.lethality(c * c * 30)).isCloseTo(0.5, within(0.01));
        assertThat(ToxicGas.CHLORINE.lethality(10 * 10 * 30)).isLessThan(1e-4);
        assertThat(ToxicGas.CHLORINE.lethality(0)).isZero();
    }

    @Test
    void hydrogenFluorideProbitAndUf6Hydrolysis() {
        // TNO: LC50 за 30 мин около 960 ppm (крыса 1 ч — 1276 ppm, Wohlslagel 1976 — тот же порядок)
        assertThat(ToxicGas.HYDROGEN_FLUORIDE.lc50Ppm(30)).isBetween(850.0, 1100.0);
        assertThat(ToxicGas.HYDROGEN_FLUORIDE.lethality(ToxicGas.HYDROGEN_FLUORIDE.load(30, 30))).isLessThan(1e-3);
        assertThat(ToxicGas.CHLORINE.lc50Ppm(30)).isBetween(230.0, 270.0);
        // 1 кг UF₆ даёт 0,227 кг HF
        assertThat(ToxicGas.HF_PER_UF6).isCloseTo(0.2273, within(1e-4));
        // ppm на мг/м³: HF лёгкий — при той же массе ppm в 3,5 раза больше, чем у хлора
        assertThat(ToxicGas.HYDROGEN_FLUORIDE.ppm(1e-6) / ToxicGas.CHLORINE.ppm(1e-6)).isCloseTo(70.906 / 20.006, within(1e-9));
    }
}
