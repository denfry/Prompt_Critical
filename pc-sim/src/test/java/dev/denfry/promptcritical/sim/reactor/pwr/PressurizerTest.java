package dev.denfry.promptcritical.sim.reactor.pwr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import org.junit.jupiter.api.Test;

/** Компенсатор давления: равновесие, сжатие подушки, ТЭНы, впрыск, ИПУ. */
class PressurizerTest {
    static final WaterProperties W = WaterProperties.tables();
    static final double P0 = 15.7e6, OFF = 99e6;

    static Pressurizer kd() {
        Pressurizer k = new Pressurizer(W, 3.0);
        k.initialize(P0, 0.6);
        return k;
    }

    @Test
    void saturatedStateHolds() {
        Pressurizer k = kd();
        for (int i = 0; i < 200; i++) k.step(0.05, 0, 0, 0, 0, 0, OFF, 0, OFF, 0);
        assertEquals(P0, k.pressure(), 0.02e6);
        assertEquals(0.6, k.level(), 0.005);
        assertEquals(W.saturationP(P0).tSat(), k.liquidTempK(), 0.5);
    }

    /**
     * Insurge 100 кг холодной (290 °C) воды в КД 3 м³: подушка 1,2 м³ сжимается почти адиабатически. Равновесная оценка
     * снизу — без конденсации давление выросло бы по изоэнтропе пара: ΔV/V_v ≈ 0,14/1,2, κ ≈ 1,1 → ≈ +2 МПа; реальный
     * прирост меньше из-за конденсации на холодной воде, но заведомо выше нуля и ниже адиабаты.
     */
    @Test
    void insurgeCompressesSteamCushion() {
        Pressurizer k = kd();
        double h = W.enthalpyPT(P0, 563.15);
        for (int i = 0; i < 20; i++) k.step(0.05, 5, h, 0, 0, 0, OFF, 0, OFF, 0);
        assertTrue(k.pressure() > P0 + 0.3e6, "pressure rose: " + k.pressure());
        assertTrue(k.pressure() < P0 + 2.5e6, "below adiabatic: " + k.pressure());
        assertTrue(k.level() > 0.63, "level rose: " + k.level());
    }

    @Test
    void outsurgeFlashesAndPressureFalls() {
        Pressurizer k = kd();
        for (int i = 0; i < 40; i++) k.step(0.05, -5, 0, 0, 0, 0, OFF, 0, OFF, 0);
        assertTrue(k.pressure() < P0 - 0.1e6, "pressure fell: " + k.pressure());
        assertTrue(k.level() < 0.6, "level fell");
        // вскипание держит давление у насыщения жидкости
        assertTrue(W.saturationPressure(k.liquidTempK()) - k.pressure() < 0.3e6);
    }

    @Test
    void heatersRaiseAndSprayLowersPressure() {
        Pressurizer k = kd();
        for (int i = 0; i < 600; i++) k.step(0.05, 0, 0, 0, 0, 150e3, OFF, 0, OFF, 0);
        double heated = k.pressure();
        assertTrue(heated > P0 + 0.05e6, "heaters: " + heated);
        double hc = W.enthalpyPT(P0, 563.15);
        for (int i = 0; i < 200; i++) k.step(0.05, 0, 0, 3, hc, 0, OFF, 0, OFF, 0);
        assertTrue(k.pressure() < heated - 0.05e6, "spray: " + k.pressure());
    }

    @Test
    void reliefValveCapsPressure() {
        Pressurizer k = kd();
        double h = W.enthalpyPT(P0, 593.15);
        double max = 0;
        for (int i = 0; i < 2000; i++) {
            k.step(0.05, 2, h, 0, 0, 300e3, 16.6e6, 10, 17.4e6, 30);
            max = Math.max(max, k.pressure());
        }
        assertTrue(max < 17.9e6, "relief holds: " + max);
        assertTrue(k.relievedKg() > 0);
    }
}
