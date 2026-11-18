package dev.denfry.promptcritical.sim.hazard;

import dev.denfry.promptcritical.sim.consequence.GasDispersion;
import dev.denfry.promptcritical.sim.consequence.GasDispersion.Stability;

/**
 * Облако горючего газа на открытом воздухе (09 §9.11.2): гауссов шлейф приземного источника (модель 04b §4b.9.5,
 * {@link GasDispersion}) с вертикальным профилем exp(−z²/2σz²); для тяжёлых газов σz вдвое меньше и источник у земли.
 * Шлейф устанавливается со скоростью ветра: через t секунд облако тянется на u·t по ветру. Горючая масса — масса между
 * изолиниями НКПР и ВКПР, интегрированием по сетке {@value #CELL_M}×{@value #CELL_M}×{@value #CELL_M} м в радиусе
 * {@code hazard.cloud_max_radius_m} = {@value #MAX_RADIUS_M} м.
 */
public final class FlammableCloud {
    public static final double CELL_M = 2, MAX_RADIUS_M = 128, MAX_HEIGHT_M = 32;

    /** Доля непустых блоков в кубе 5×5×5 вокруг точки (смещение от источника: восток, вверх, юг; м). */
    @FunctionalInterface
    public interface Congestion {
        double at(double eastM, double upM, double southM);
    }

    /**
     * @param flammableKg   горючая масса
     * @param flammableM3   объём между пределами
     * @param congestedM3   из него — в загромождённой зоне
     * @param meanCongestion средняя доля загромождения по загромождённой части (0 — её нет)
     * @param centroidEast  центр горючей части, м к востоку от источника
     * @param centroidUp    м вверх
     * @param centroidSouth м к югу
     */
    public record Result(double flammableKg, double flammableM3, double congestedM3, double meanCongestion,
            double centroidEast, double centroidUp, double centroidSouth) {
        public static final Result EMPTY = new Result(0, 0, 0, 0, 0, 0, 0);
    }

    private FlammableCloud() {
    }

    private static double sigmaZ(Stability s, double x, boolean dense) {
        return Math.max(0.5, s.sigmaZ(x) * (dense ? 0.5 : 1));
    }

    /** Концентрация в точке системы ветра (x по ветру, y поперёк, z над землёй), кг/м³. */
    public static double concentration(Flammable gas, double qKgS, double windMs, Stability s, double ageS, double x, double y, double z) {
        if (qKgS <= 0 || x <= 0) return 0;
        double u = Math.max(1, windMs);
        if (x > u * Math.max(0, ageS) + CELL_M) return 0;
        x = Math.max(x, 1);
        double sy = Math.max(0.5, s.sigmaY(x)), sz = sigmaZ(s, x, gas.dense());
        return qKgS / (Math.PI * sy * sz * u) * Math.exp(-y * y / (2 * sy * sy) - z * z / (2 * sz * sz));
    }

    /** Концентрация в точке со смещением от источника (восток, вверх, юг), кг/м³; ветер — откуда дует. */
    public static double concentrationAt(Flammable gas, double qKgS, double windMs, Stability s, double fromRad, double ageS,
            double east, double up, double south) {
        double[] w = GasDispersion.windFrame(east, south, fromRad);
        return concentration(gas, qKgS, windMs, s, ageS, w[0], w[1], Math.max(0, up));
    }

    /** Горючая часть облака непрерывной утечки {@code qKgS} через {@code ageS} секунд после начала. */
    public static Result integrate(Flammable gas, double qKgS, double windMs, Stability s, double fromRad, double ageS, Congestion cong) {
        if (qKgS <= 0 || ageS <= 0) return Result.EMPTY;
        double u = Math.max(1, windMs);
        double xMax = Math.min(MAX_RADIUS_M, u * ageS);
        double ux = -Math.sin(fromRad), uz = Math.cos(fromRad);
        double cell3 = CELL_M * CELL_M * CELL_M, lo = gas.lflKgM3(), hi = gas.uflKgM3();
        double mass = 0, vol = 0, congVol = 0, congSum = 0, cx = 0, cy = 0, cz = 0;
        for (double x = CELL_M / 2; x <= xMax; x += CELL_M) {
            double sy = Math.max(0.5, s.sigmaY(Math.max(1, x))), sz = sigmaZ(s, Math.max(1, x), gas.dense());
            // ось шлейфа ниже НКПР — дальше по ветру концентрация только падает
            if (concentration(gas, qKgS, windMs, s, ageS, x, 0, 0) < lo) break;
            double yMax = Math.min(Math.sqrt(Math.max(0, MAX_RADIUS_M * MAX_RADIUS_M - x * x)), 3 * sy);
            double zMax = Math.min(MAX_HEIGHT_M, 3 * sz);
            for (double y = -Math.floor(yMax / CELL_M) * CELL_M; y <= yMax; y += CELL_M) {
                for (double z = CELL_M / 2; z <= zMax; z += CELL_M) {
                    double c = concentration(gas, qKgS, windMs, s, ageS, x, y, z);
                    if (c < lo) break; // по высоте концентрация убывает
                    if (c > hi) continue;
                    double east = x * ux - y * uz, south = x * uz + y * ux;
                    mass += c * cell3;
                    vol += cell3;
                    cx += east * cell3;
                    cy += z * cell3;
                    cz += south * cell3;
                    double k = cong == null ? 0 : cong.at(east, z, south);
                    if (k >= VapourCloudExplosion.CONGESTION_THRESHOLD) {
                        congVol += cell3;
                        congSum += k * cell3;
                    }
                }
            }
        }
        if (vol <= 0) return Result.EMPTY;
        return new Result(mass, vol, congVol, congVol > 0 ? congSum / congVol : 0, cx / vol, cy / vol, cz / vol);
    }
}
