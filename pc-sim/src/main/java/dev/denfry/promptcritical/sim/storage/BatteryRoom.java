package dev.denfry.promptcritical.sim.storage;

/**
 * Воздух аккумуляторного помещения (15 §15.7.1: газовыделение H₂ при заряде, вентиляция). Водород из батарей
 * перемешивается в объёме V и уходит с воздухообменом n (кратность, 1/ч):
 * dV_H₂/dt = q − n/3600·V_H₂, решается точно на шаге при постоянном q. Нижний концентрационный предел
 * воспламенения водорода в воздухе {@value #LEL} (4 % об.); тревога газоанализатора на {@value #ALARM_LEL} НКПР.
 * Плотность H₂ {@value #H2_KG_M3} кг/м³ (0 °C, 1 атм).
 */
public final class BatteryRoom {
    public static final double LEL = 0.04, ALARM_LEL = 0.25, H2_KG_M3 = 0.0899;
    /** Кратность воздухообмена закрытого помещения (инфильтрация) и помещения с проёмом наружу, 1/ч. */
    public static final double ACH_SEALED = 0.5, ACH_OPEN = 6;

    private double volumeM3;
    private double airChangesPerHour;
    private double h2M3;

    public BatteryRoom(double volumeM3, double airChangesPerHour) {
        resize(volumeM3, airChangesPerHour);
    }

    /** Новый объём и воздухообмен; накопленный водород сохраняется. */
    public void resize(double volumeM3, double airChangesPerHour) {
        if (!(volumeM3 > 0)) throw new IllegalArgumentException("volume must be positive");
        if (!(airChangesPerHour >= 0)) throw new IllegalArgumentException("air changes must be non-negative");
        this.volumeM3 = volumeM3;
        this.airChangesPerHour = airChangesPerHour;
        h2M3 = Math.min(h2M3, volumeM3);
    }

    /** Шаг {@code dt} с, за который в помещение вышло {@code h2Kg} кг водорода. */
    public void step(double dt, double h2Kg) {
        if (!(dt > 0)) throw new IllegalArgumentException("dt must be positive");
        double q = Math.max(0, h2Kg) / H2_KG_M3 / dt;
        double k = airChangesPerHour / 3600;
        if (k <= 0) h2M3 += q * dt;
        else {
            double eq = q / k;
            h2M3 = eq + (h2M3 - eq) * Math.exp(-k * dt);
        }
        h2M3 = Math.min(Math.max(0, h2M3), volumeM3);
    }

    /** Объёмная доля водорода. */
    public double h2Fraction() {
        return h2M3 / volumeM3;
    }

    /** Доля нижнего предела воспламенения (1 — взрывоопасная смесь). */
    public double lelFraction() {
        return h2Fraction() / LEL;
    }

    public boolean alarm() {
        return lelFraction() >= ALARM_LEL;
    }

    public boolean explosive() {
        return lelFraction() >= 1;
    }

    /** Сгорание смеси (вспышка): водород израсходован. */
    public double burn() {
        double kg = h2M3 * H2_KG_M3;
        h2M3 = 0;
        return kg;
    }

    public double volumeM3() {
        return volumeM3;
    }

    public double airChangesPerHour() {
        return airChangesPerHour;
    }

    public double h2M3() {
        return h2M3;
    }

    public void setH2M3(double m3) {
        h2M3 = Double.isFinite(m3) ? Math.min(Math.max(0, m3), volumeM3) : 0;
    }
}
