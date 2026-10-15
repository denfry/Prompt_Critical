package dev.denfry.promptcritical.sim.mining;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Водоотлив шахты (08 §8.2.9): приток — фильтрация плюс вода, вскрытая добычей (водообильность × тонны, стекает
 * экспонентой с τ {@link DrainageConfig#releaseTauS}); водосборник {@link MineSump} — ёмкость в SLOW. Подачу насосов
 * на их напорах складывает привязка; вода сверх ёмкости водосборника топит нижние выработки. Вода пласта с
 * сернистым углём или пиритом — кислая {@code mine_water}.
 */
public final class MineDrainage {
    private DrainageConfig cfg;
    private MineSump sump;
    private double pendingM3, pumpCapacityM3s, inflowM3s;
    private boolean acidic;

    public MineDrainage(DrainageConfig cfg) {
        this.cfg = cfg;
        this.sump = new MineSump(cfg.sumpCapacityM3());
    }

    void setConfig(DrainageConfig cfg) {
        this.cfg = cfg;
        double v = sump.volumeM3();
        sump = new MineSump(cfg.sumpCapacityM3());
        sump.restore(v);
    }

    /**
     * Добыто угля или руды.
     *
     * @param tonnes    масса, т
     * @param sulfurPct сера S^d, % (пирит — как сернистый уголь)
     */
    public void onMined(double tonnes, double sulfurPct) {
        pendingM3 += cfg.waterYieldM3PerT() * tonnes;
        if (sulfurPct >= cfg.acidSulfurPct()) acidic = true;
    }

    /** Прорыв воды в выработки (отрицательный объём — отбор); отладка и сценарии, м³. */
    public void injectM3(double m3) {
        sump.restore(sump.volumeM3() + m3);
    }

    /** Подача насосов шахты на следующий шаг, м³/с. */
    public void setPumpCapacityM3s(double q) {
        pumpCapacityM3s = Math.max(0, q);
    }

    public void stepSlow(double dt) {
        double released = pendingM3 * -Math.expm1(-dt / cfg.releaseTauS());
        pendingM3 -= released;
        inflowM3s = released / dt + cfg.filtrationM3h() / 3600;
        sump.stepSlowWithCapacity(dt, inflowM3s, pumpCapacityM3s);
    }

    /** Приток на последнем шаге, м³/с. */
    public double inflowM3s() {
        return inflowM3s;
    }

    /** Откачано на последнем шаге, м³/с. */
    public double pumpedM3s() {
        return sump.pumpedM3s();
    }

    /** Доля подачи, которую насосы реально качали (воды меньше, чем могут), 0…1. */
    public double pumpLoad() {
        return pumpCapacityM3s > 0 ? Math.min(1, sump.pumpedM3s() / pumpCapacityM3s) : 0;
    }

    public double volumeM3() {
        return sump.volumeM3();
    }

    /** Уровень водосборника, доля ёмкости (&gt; 1 — горизонт топит). */
    public double levelFrac() {
        return sump.levelFrac();
    }

    /** Вода сверх водосборника — в затопленных выработках, м³. */
    public double floodedM3() {
        return sump.floodedM3();
    }

    /** Водосборник вмещает ≥ 4 ч притока (ПБ). */
    public boolean capacityOk() {
        return sump.capacityOk(inflowM3s);
    }

    public boolean acidic() {
        return acidic;
    }

    public double capacityM3() {
        return sump.capacityM3();
    }

    void save(StateWriter out) {
        out.putDouble("volume", sump.volumeM3());
        out.putDouble("pending", pendingM3);
        out.putBoolean("acidic", acidic);
    }

    void load(StateReader in) {
        sump.restore(in.getDouble("volume"));
        pendingM3 = in.getDouble("pending");
        acidic = in.getBoolean("acidic");
    }
}
