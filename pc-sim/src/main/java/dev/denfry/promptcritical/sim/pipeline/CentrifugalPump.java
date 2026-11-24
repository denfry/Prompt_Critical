package dev.denfry.promptcritical.sim.pipeline;

/**
 * Центробежный насос по характеристике «напор — подача» (09 §9.8): парабола H(Q) = H₀·r² − k·Q² через номинальную
 * точку, где r — доля номинальной частоты вращения (законы подобия: Q ∝ n, H ∝ n², P ∝ n³; Karassik et al., Pump
 * Handbook, гл. 2.3). Напор при закрытой задвижке H₀ = {@value #SHUTOFF_RATIO}·Hн — типичный для насосов с
 * коэффициентом быстроходности 70–150. КПД — парабола с максимумом в номинальной точке, перенесённой по подобию.
 */
public final class CentrifugalPump {
    /** Отношение напора при нулевой подаче к номинальному. */
    public static final double SHUTOFF_RATIO = 1.2;
    /** Наименьший учитываемый КПД (около закрытой задвижки), чтобы мощность оставалась конечной. */
    public static final double MIN_EFFICIENCY = 0.05;
    private static final double G = 9.80665;

    /**
     * Магистральный насос НМ 1250-260: 1250 м³/ч при 260 м, КПД 0,80 (ГОСТ 12124-87, исполнение с ротором
     * на номинальную подачу).
     */
    public static final CentrifugalPump NM_1250_260 = new CentrifugalPump(1250 / 3600.0, 260, 0.80);

    private final double qNomM3s, hNomM, etaBep, kHead;

    public CentrifugalPump(double qNomM3s, double hNomM, double etaBep) {
        if (!(qNomM3s > 0) || !(hNomM > 0) || !(etaBep > 0) || etaBep > 1) throw new IllegalArgumentException("bad pump rating");
        this.qNomM3s = qNomM3s;
        this.hNomM = hNomM;
        this.etaBep = etaBep;
        this.kHead = (SHUTOFF_RATIO - 1) * hNomM / (qNomM3s * qNomM3s);
    }

    public double nominalFlowM3s() {
        return qNomM3s;
    }

    public double nominalHeadM() {
        return hNomM;
    }

    /** Напор, м, при подаче {@code qM3s} и доле номинальной частоты {@code speed}; не меньше нуля. */
    public double headM(double qM3s, double speed) {
        double r = Math.max(0, speed);
        return Math.max(0, SHUTOFF_RATIO * hNomM * r * r - kHead * qM3s * qM3s);
    }

    /** Подача при нулевом напоре (насос «на слив»), м³/с. */
    public double maxFlowM3s(double speed) {
        return Math.max(0, speed) * Math.sqrt(SHUTOFF_RATIO * hNomM / kHead);
    }

    /** КПД при подаче {@code qM3s}: парабола с вершиной в номинальной подаче, перенесённой по подобию. */
    public double efficiency(double qM3s, double speed) {
        double qBep = qNomM3s * Math.max(0, speed);
        if (qBep <= 0) return MIN_EFFICIENCY;
        double x = qM3s / qBep - 1;
        return Math.max(MIN_EFFICIENCY, etaBep * (1 - x * x));
    }

    /** Мощность на валу, Вт: ρ·g·Q·H/η; не меньше потерь при закрытой задвижке — 30 % номинала, как куб скорости [моё]. */
    public double shaftPowerW(double qM3s, double speed, double densityKgM3) {
        double r = Math.max(0, speed);
        double hydraulic = densityKgM3 * G * qM3s * headM(qM3s, r) / efficiency(qM3s, r);
        double shutoff = 0.3 * densityKgM3 * G * qNomM3s * hNomM / etaBep * r * r * r;
        return Math.max(hydraulic, shutoff);
    }

    /** Давление, развиваемое насосом, Па, при напоре {@code headM}. */
    public static double pressurePa(double headM, double densityKgM3) {
        return densityKgM3 * G * headM;
    }
}
