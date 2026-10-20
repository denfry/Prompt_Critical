package dev.denfry.promptcritical.sim.automation;

/** Режим интегратора A2 (03d §3d.4.3). */
public enum IntegratorMode {
    /** Выход принудительно равен входу IC (сброс, слежение). */
    IC,
    OPERATE,
    /** Выход заморожен, утечка остаётся. */
    HOLD
}
