package dev.denfry.promptcritical.sim.hydraulics;

import java.util.Objects;

/**
 * Пассивный скаляр, переносимый с жидкой фазой (бор, растворённые газы, группы
 * активности; 01 §1.13.2). Масса скаляра в узле — кг (или Бк для активности).
 * Летучесть растворённых газов не моделируется: скаляр уходит из узла только с жидкостью.
 */
public record PassiveScalar(String id, double decayConstant /* 1/с, 0 — стабильный */) {
    public static final PassiveScalar BORON = new PassiveScalar("boron", 0);
    public static final PassiveScalar DISSOLVED_H2 = new PassiveScalar("dissolved_h2", 0);
    public static final PassiveScalar DISSOLVED_O2 = new PassiveScalar("dissolved_o2", 0);

    public PassiveScalar {
        Objects.requireNonNull(id, "id");
        if (!(decayConstant >= 0)) throw new IllegalArgumentException("decayConstant < 0: " + decayConstant);
    }
}
