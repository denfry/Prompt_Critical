package dev.denfry.promptcritical.sim.solar;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Солнце над миром (15 §15.1.3): положение — из видимого неба MC, а не из широты. Солнце MC идёт по дуге восток –
 * зенит – запад без сезонной высоты. Масса атмосферы — Kasten &amp; Young (1989), прямая нормальная облучённость в
 * ясную погоду — Meinel &amp; Meinel (1976), рассеянная — 10 % прямой. Облачность без осадков — процесс
 * Орнштейна – Уленбека с τ = {@value #CLOUD_TAU_S} с и долей ясного неба по биому; осадки множат прямую и рассеянную.
 */
public final class SolarSky {
    public static final double SOLAR_CONSTANT = 1361;
    /** Постоянная времени облачности, с (1 ч физ.). */
    public static final double CLOUD_TAU_S = 3600;
    /** Прямая сквозь облако без осадков: доля ясной [BALANCE]. */
    public static final double CLOUD_DIRECT = 0.25;

    public enum Weather {
        CLEAR(1, 1), RAIN(0.15, 1.5), THUNDER(0.05, 1.0), SNOW(0.15, 1.5);

        public final double direct, diffuse;

        Weather(double direct, double diffuse) {
            this.direct = direct;
            this.diffuse = diffuse;
        }
    }

    /** Облучённость и направление на солнце в момент. {@code sunX, sunY} — единичный вектор в плоскости восток–запад (x — на восток). */
    public record Sample(double altitudeRad, double sunX, double sunY, double dniWm2, double dhiWm2) {
        public static final Sample NIGHT = new Sample(0, 0, -1, 0, 0);
    }

    /** Доля ясного неба по облачности 0…1 (1 — ясно). */
    private double clear = 1;

    /**
     * Солнце по углу небесной сферы MC: 0 — полдень, 0,25 — закат на западе, 0,75 — восход на востоке.
     *
     * @return направление и облучённость в ясную погоду без учёта облаков
     */
    public static Sample clearSky(double celestialAngle) {
        double a = 2 * Math.PI * celestialAngle;
        double sunX = Math.sin(a), sunY = Math.cos(a); // при восходе (0,75) −sin = +1: солнце на востоке (+x)
        double alt = Math.asin(Math.max(-1, Math.min(1, sunY)));
        if (alt <= 0) return Sample.NIGHT;
        double dni = SOLAR_CONSTANT * Math.pow(0.7, Math.pow(airMass(alt), 0.678));
        return new Sample(alt, -sunX, sunY, dni, 0.1 * dni);
    }

    /** Масса атмосферы по высоте солнца (Kasten &amp; Young, 1989). */
    public static double airMass(double altitudeRad) {
        double deg = Math.toDegrees(altitudeRad);
        return 1 / (Math.sin(altitudeRad) + 0.50572 * Math.pow(deg + 6.07995, -1.6364));
    }

    /** Прямая нормальная облучённость при заданной массе атмосферы, Вт/м². */
    public static double dniAt(double airMass) {
        return SOLAR_CONSTANT * Math.pow(0.7, Math.pow(airMass, 0.678));
    }

    /**
     * Шаг облачности: процесс О–У к доле ясного неба биома {@code clearMean} (пустыня 0,9, равнины 0,6, джунгли 0,4).
     *
     * @param noise нормальный шум N(0, 1) от вызывающего (детерминированный ГСЧ мира)
     */
    public void step(double dtS, double clearMean, double noise) {
        if (!(dtS > 0)) return;
        double k = Math.min(1, dtS / CLOUD_TAU_S);
        double sigma = 0.3 * Math.sqrt(2 * k);
        clear = Math.max(0, Math.min(1, clear + k * (clearMean - clear) + sigma * noise));
    }

    public double clear() {
        return clear;
    }

    /** Облучённость с облаками и осадками. */
    public Sample sample(double celestialAngle, Weather weather) {
        Sample s = clearSky(celestialAngle);
        if (s.dniWm2() <= 0) return s;
        double cloudDirect = clear + (1 - clear) * CLOUD_DIRECT;
        double cloudDiffuse = 1 + 0.5 * (1 - clear);
        return new Sample(s.altitudeRad(), s.sunX(), s.sunY(), s.dniWm2() * cloudDirect * weather.direct,
                s.dhiWm2() * cloudDiffuse * weather.diffuse);
    }

    public void save(StateWriter out) {
        out.putDouble("clear", clear);
    }

    public void load(StateReader in) {
        double c = in.optDouble("clear", 1);
        clear = Double.isFinite(c) ? Math.max(0, Math.min(1, c)) : 1;
    }
}
