package dev.denfry.promptcritical.sim.reactor.pwr;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Главный циркуляционный насос с маховиком (01 §1.2.7, решение R2). Гидравлический момент по законам подобия растёт как
 * квадрат оборотов, T = T₀s², s = ω/ω₀; без привода J·ω₀·ds/dt = −T₀s², откуда выбег s(t) = 1/(1 + t/τ) с постоянной
 * τ = Jω₀²/P₀. У ГЦН ПР-300 (J = 300 кг·м², 1500 об/мин, P₀ = 617 кВт) τ = 12,0 с. Расход пропорционален оборотам.
 *
 * <p>С приводом насос разгоняется до заданных оборотов за {@link #RUN_UP_S} (пуск асинхронного двигателя на маховик),
 * но тормозиться быстрее выбега не может: снижение задания насос «догоняет» выбегом. Ниже {@link #STOP_SPEED} ротор
 * останавливается трением в подшипниках.
 */
public final class CoolantPump {
    /** Канон маховика ГЦН (R2): маховик 270 + ротор 30 кг·м². */
    public static final double INERTIA_KGM2 = 300;
    /** Время разгона от нуля до номинала, с [оценка: пуск ГЦН ВВЭР-440 — 10–15 с]. */
    public static final double RUN_UP_S = 12;
    public static final double STOP_SPEED = 0.02;

    private final double inertia, omegaNominal, shaftPowerNominal;
    private double speed;

    /**
     * @param inertia           момент инерции ротора с маховиком, кг·м²
     * @param rpmNominal        номинальные обороты
     * @param shaftPowerNominal мощность на валу на номинале, Вт
     */
    public CoolantPump(double inertia, double rpmNominal, double shaftPowerNominal) {
        if (!(inertia > 0 && rpmNominal > 0 && shaftPowerNominal > 0)) throw new IllegalArgumentException("pump");
        this.inertia = inertia;
        this.omegaNominal = rpmNominal * 2 * Math.PI / 60;
        this.shaftPowerNominal = shaftPowerNominal;
    }

    /** Постоянная выбега τ = Jω₀²/P₀, с. */
    public double coastdownTimeConstant() {
        return inertia * omegaNominal * omegaNominal / shaftPowerNominal;
    }

    /**
     * Шаг: {@code drive} — обороты, которые держит привод, доля номинала (0 — привод обесточен).
     */
    public void step(double dt, double drive) {
        double target = Math.max(0, Math.min(1.1, drive));
        if (target > speed) {
            speed = Math.min(target, speed + dt / RUN_UP_S);
        } else {
            // точное решение ds/dt = −s²/τ на шаге
            double tau = coastdownTimeConstant();
            double coasted = speed / (1 + speed * dt / tau);
            speed = Math.max(target, coasted);
            if (target <= 0 && speed < STOP_SPEED) speed = 0;
        }
    }

    /** Обороты, доля номинала (они же — доля номинального расхода насоса). */
    public double speed() {
        return speed;
    }

    /** Мощность на валу при текущих оборотах, Вт: P = P₀s³. */
    public double shaftPowerW() {
        return shaftPowerNominal * speed * speed * speed;
    }

    public void setSpeed(double s) {
        speed = Math.max(0, s);
    }

    public void save(StateWriter out) {
        out.putDouble("s", speed);
    }

    public void load(StateReader in) {
        speed = in.optDouble("s", 0);
    }
}
