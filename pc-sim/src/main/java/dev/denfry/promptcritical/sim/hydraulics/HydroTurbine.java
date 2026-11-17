package dev.denfry.promptcritical.sim.hydraulics;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Гидротурбина с водоводом без уравнительного резервуара — нелинейная модель IEEE Working Group (1992), Kundur §9.1.
 * В относительных единицах расчётной точки:
 * <pre>
 * расход через направляющий аппарат  q = G·√h        (h — напор у турбины)
 * инерция воды в водоводе             dq/dt = (1 − h − f_p·q²)/T_w,  T_w = L·Q_р/(g·A·H_р)
 * мощность                            P = A_t·h·(q − q_хх),          A_t = 1/(1 − q_хх)
 * </pre>
 * Отсюда «неминимально-фазовый» отклик: открыли аппарат — напор сперва падает, и мощность на миг уменьшается,
 * пока вода в водоводе не разгонится. Быстро закрыли — напор растёт (гидроудар); выше {@link #BURST_HEAD} водовод
 * рвёт.
 *
 * <p>Регулятор — ПИ с постоянным R_p и временным R_t статизмом (выбор R_t и T_R по Kundur, ф. 9.25–9.26: при обычном
 * пропорциональном регуляторе ГЭС неустойчива). Сервомотор аппарата — инерционное звено с ограничением скорости
 * закрытия {@link #GATE_RATE} — от гидроудара.
 */
public final class HydroTurbine {
    /** Расход холостого хода, потери в водоводе, о.е. */
    public static final double Q_NO_LOAD = 0.08, FRICTION = 0.02;
    /**
     * Скорость хода направляющего аппарата, доля в секунду (полный ход 5 с); постоянная сервомотора, с. Время
     * закрытия — компромисс: быстрее — сильнее гидроудар, медленнее — выше заброс оборотов при сбросе нагрузки
     * (у ГЭС обычно 30–60 %).
     */
    public static final double GATE_RATE = 0.2, SERVO_S = 0.5;
    /** Напор, о.е., при котором рвёт водовод (расчётный запас ≈ 1,5). */
    public static final double BURST_HEAD = 1.5;
    public static final double RHO = 1000, G = 9.80665;

    /**
     * Паспорт под место.
     *
     * @param headM       расчётный напор, м
     * @param flowM3s     расчётный расход, м³/с
     * @param penstockM   длина водовода, м
     * @param diameterM   диаметр водовода, м
     * @param efficiency  КПД турбины в расчётной точке (Френсис ≈ 0,9)
     * @param inertiaS    постоянная инерции агрегата H, с
     * @param droop       постоянный статизм R_p
     */
    public record Spec(double headM, double flowM3s, double penstockM, double diameterM, double efficiency,
            double nominalOmega, double inertiaS, double droop) {
        public Spec {
            if (!(headM > 0 && flowM3s > 0 && penstockM > 0 && diameterM > 0 && efficiency > 0 && nominalOmega > 0
                    && inertiaS > 0 && droop > 0)) throw new IllegalArgumentException("hydro spec");
        }

        /** Номинальная мощность на валу η·ρ·g·Q·H, Вт. */
        public double ratedPowerW() {
            return efficiency * RHO * G * flowM3s * headM;
        }

        /** Постоянная инерции воды T_w = L·Q/(g·A·H), с. */
        public double waterStartingTimeS() {
            double area = Math.PI * diameterM * diameterM / 4;
            return penstockM * flowM3s / (G * area * headM);
        }

        /** Временный статизм по Kundur (ф. 9.25): R_t = [2,3 − 0,15·(T_w − 1)]·T_w/T_m, T_m = 2H. */
        public double temporaryDroop() {
            double tw = waterStartingTimeS();
            return Math.max(0.1, (2.3 - 0.15 * (tw - 1)) * tw / (2 * inertiaS));
        }

        /** Постоянная времени возврата (ф. 9.26): T_R = [5 − 0,5·(T_w − 1)]·T_w, с. */
        public double resetTimeS() {
            double tw = waterStartingTimeS();
            return Math.max(1, (5 - 0.5 * (tw - 1)) * tw);
        }

        /** Тип турбины по напору [порядок]: Каплан до 40 м, Френсис до 300 м, выше — Пельтон. */
        public String kind() {
            return headM < 40 ? "kaplan" : headM < 300 ? "francis" : "pelton";
        }
    }

    private final Spec spec;
    private double q, gate, gateIntegral, omega, head = 1, powerW, speedSet;
    private double maxGate = 1;
    private boolean burst;

    public HydroTurbine(Spec spec) {
        this.spec = spec;
        speedSet = spec.nominalOmega() * (1 + spec.droop());
    }

    /**
     * Шаг.
     *
     * @param loadTorque момент нагрузки на валу, Н·м
     * @param enabled    затвор водовода открыт (иначе аппарат закрывается)
     */
    public void step(double dt, double loadTorque, boolean enabled) {
        double w0 = spec.nominalOmega();
        // регулятор: ПИ с постоянным и временным статизмом
        double e = (speedSet - omega) / w0 - spec.droop() * gate;
        double rt = spec.temporaryDroop();
        double cmd = 0;
        if (enabled && !burst) {
            gateIntegral = clamp(gateIntegral + dt * e / (rt * spec.resetTimeS()), 0, maxGate);
            cmd = clamp(gateIntegral + e / rt, 0, maxGate);
        } else {
            gateIntegral = 0;
        }
        double move = (cmd - gate) * (1 - Math.exp(-dt / SERVO_S));
        gate += Math.max(-GATE_RATE * dt, Math.min(GATE_RATE * dt, move));

        // вода в водоводе: неявно по q (h = q²/G² жёсткое при малом G): a·q'² + q' − (q + h/T_w) = 0
        double tw = spec.waterStartingTimeS();
        int n = Math.max(1, (int) Math.ceil(dt / (0.1 * tw)));
        double h = dt / n;
        for (int k = 0; k < n; k++) {
            if (gate <= 1e-6) {
                q = 0;
                head = 1;
                continue;
            }
            double a = h * (1 / (gate * gate) + FRICTION) / tw, c = q + h / tw;
            q = (-1 + Math.sqrt(1 + 4 * a * c)) / (2 * a);
            head = (q / gate) * (q / gate);
        }
        if (head > BURST_HEAD) burst = true;
        if (burst) q = 0;

        double at = 1 / (1 - Q_NO_LOAD);
        double pPu = at * head * Math.max(0, q - Q_NO_LOAD);
        powerW = pPu * spec.ratedPowerW();
        double j = 2 * spec.inertiaS() * spec.ratedPowerW() / (w0 * w0);
        double torque = powerW / Math.max(omega, 0.05 * w0);
        omega = Math.max(0, omega + dt * (torque - loadTorque) / j);
    }

    /** Предел открытия по воде: река не даст больше {@code availableM3s} надолго. */
    public void setAvailableFlow(double availableM3s) {
        maxGate = clamp(availableM3s / spec.flowM3s(), 0, 1);
    }

    public double maxGate() {
        return maxGate;
    }

    public double gate() {
        return gate;
    }

    /** Расход через турбину, м³/с. */
    public double flowM3s() {
        return q * spec.flowM3s();
    }

    /** Напор у турбины, о.е. расчётного. */
    public double headPu() {
        return head;
    }

    public double powerW() {
        return powerW;
    }

    public double omega() {
        return omega;
    }

    public void setOmega(double w) {
        omega = Math.max(0, w);
    }

    /** Водовод порвало гидроударом. */
    public boolean burst() {
        return burst;
    }

    /** Водовод заменили. */
    public void repair() {
        burst = false;
        head = 1;
    }

    public Spec spec() {
        return spec;
    }

    /** Мощность при номинальных оборотах и открытом до предела аппарате, Вт. */
    public double capacityW() {
        double qMax = maxGate; // при h ≈ 1
        return burst ? 0 : Math.max(0, (qMax - Q_NO_LOAD) / (1 - Q_NO_LOAD)) * spec.ratedPowerW();
    }

    public void save(StateWriter out) {
        out.putDouble("q", q);
        out.putDouble("gate", gate);
        out.putDouble("gate_i", gateIntegral);
        out.putDouble("omega", omega);
        out.putDouble("speed_set", speedSet);
        out.putBoolean("burst", burst);
    }

    public void load(StateReader in) {
        q = in.getDouble("q");
        gate = in.getDouble("gate");
        gateIntegral = in.getDouble("gate_i");
        omega = in.getDouble("omega");
        speedSet = in.getDouble("speed_set");
        burst = in.getBoolean("burst");
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
