package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.config.ServerConfig;

/**
 * Фильтр R4 (03a §3a.2.4): мощность на валу → импульс стресса Create {@code P / (W_PER_SU · n)} не чаще раза в
 * секунду и при изменении &gt; 5 % (переход через ноль — сразу). Пока вал стоит, импульс считается по номинальной
 * мощности при номинальной скорости — в очках инженера видна будущая нагрузка.
 */
public final class StressFilter {
    private static final long PERIOD_TICKS = 20;
    private static final double DEADBAND = 0.05;

    private float impact;
    private double declaredW = -1;
    private long lastTick = Long.MIN_VALUE / 2;

    /** Пересчитать импульс; {@code true} — изменился, сеть Create надо известить. */
    public boolean update(double powerW, double rpm, double ratedW, double nominalRpm, long gameTime) {
        double p = Math.max(0, powerW);
        boolean zeroCross = (p == 0) != (declaredW == 0);
        if (declaredW >= 0 && gameTime - lastTick < PERIOD_TICKS) return false;
        if (declaredW >= 0 && !zeroCross && Math.abs(p - declaredW) <= DEADBAND * declaredW) return false;
        double wPerSu = ServerConfig.W_PER_SU.get();
        double n = Math.abs(rpm);
        impact = (float) (n == 0 ? ratedW / (wPerSu * nominalRpm) : p / (wPerSu * Math.max(n, 0.1 * nominalRpm)));
        declaredW = p;
        lastTick = gameTime;
        return true;
    }

    /** Импульс, SU/RPM. */
    public float impact() {
        return impact;
    }
}
