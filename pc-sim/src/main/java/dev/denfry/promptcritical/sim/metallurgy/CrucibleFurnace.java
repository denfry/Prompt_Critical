package dev.denfry.promptcritical.sim.metallurgy;

import java.util.Objects;

/**
 * Тигельная печь {@code crucible_furnace} (08 §8.9, I0): Cu, бронза, Al. Тепло — от {@code Combustor} (07)
 * привязкой; здесь — энтальпийный баланс шихты m·dh/dt = Q̇ − UA(T − T₀), плавление на площадке ликвидуса.
 */
public final class CrucibleFurnace {
    private final double capacityKg, lossWPerK;
    private CastAlloy alloy;
    private double mass, h;

    /**
     * @param capacityKg ёмкость тигля, кг
     * @param lossWPerK  UA теплопотерь, Вт/К
     */
    public CrucibleFurnace(double capacityKg, double lossWPerK) {
        if (!(capacityKg > 0 && lossWPerK >= 0)) throw new IllegalArgumentException("crucible");
        this.capacityKg = capacityKg;
        this.lossWPerK = lossWPerK;
    }

    /**
     * Загрузить холодную шихту (293 К). Тигель держит один сплав; возвращает принятую массу.
     */
    public double charge(CastAlloy a, double kg) {
        Objects.requireNonNull(a, "alloy");
        if (a == CastAlloy.CARBON_STEEL || a == CastAlloy.GRAY_IRON)
            throw new IllegalArgumentException("crucible melts Cu, bronze, Al: " + a);
        if (mass > 0 && a != alloy) return 0;
        double add = Math.min(Math.max(0, kg), capacityKg - mass);
        if (add <= 0) return 0;
        alloy = a;
        h = h * mass / (mass + add);
        mass += add;
        return add;
    }

    /** Шаг SLOW: подведённое тепло, Вт. */
    public void stepSlow(double dt, double heatW) {
        if (mass <= 0) return;
        double t = alloy.temperature(h);
        h += (Math.max(0, heatW) - lossWPerK * (t - 293)) * dt / mass;
        h = Math.max(0, h);
    }

    /** Разлить до {@code kg}; льётся только полностью жидкий металл. Возвращает массу, кг. */
    public double pour(double kg) {
        if (mass <= 0 || alloy.liquidFraction(h) < 1) return 0;
        double out = Math.min(mass, Math.max(0, kg));
        mass -= out;
        if (mass <= 0) {
            mass = 0;
            h = 0;
            alloy = null;
        }
        return out;
    }

    public double temperatureK() {
        return mass > 0 ? alloy.temperature(h) : 293;
    }

    /** Перегрев над ликвидусом, К (отрицателен, пока не расплавлено). */
    public double superheatK() {
        return mass > 0 ? temperatureK() - alloy.liquidusK : Double.NaN;
    }

    public double liquidFraction() {
        return mass > 0 ? alloy.liquidFraction(h) : 0;
    }

    public double massKg() {
        return mass;
    }

    public CastAlloy alloy() {
        return alloy;
    }
}
