package dev.denfry.promptcritical.sim.consequence;

/** Режим сервера, ключ {@code server.mode} (04d §4d.1): задаёт значения по умолчанию политик последствий. */
public enum ServerMode {
    REALISTIC(12.0),
    CONTAINED(8.0),
    CREATIVE_SAFE(0.0);

    /** {@code damage.max_explosion_power} по умолчанию, экв. мощности взрыва MC. */
    public final double defaultMaxExplosionPower;

    ServerMode(double defaultMaxExplosionPower) {
        this.defaultMaxExplosionPower = defaultMaxExplosionPower;
    }
}
