package dev.denfry.promptcritical.sim.hydraulics;

import dev.denfry.promptcritical.sim.thermo.VoidFraction;
import java.util.Objects;

/**
 * Насос на связи {@link LinkKind#PUMP} (01 §1.2.7): гомологический напор и динамика вала
 * J dω/dt = T_drive − T_hyd − T_fric. Момент привода задаёт биндинг Create (03a) каждый тик.
 */
public final class PumpModel {
    /**
     * @param ratedSpeed  ω_R, рад/с
     * @param ratedFlow   Q_R, м³/с
     * @param ratedHead   H_R, м
     * @param ratedTorque T_R, Н·м
     * @param inertia     J ротора с маховиком, кг·м²
     */
    public record Spec(double ratedSpeed, double ratedFlow, double ratedHead, double ratedTorque,
            double ratedDensity, double inertia, String homologousCurveId) {
        public Spec {
            if (!(ratedSpeed > 0 && ratedFlow > 0 && ratedHead > 0 && ratedTorque > 0 && ratedDensity > 0
                    && inertia > 0)) {
                throw new IllegalArgumentException("pump ratings must be positive");
            }
            Objects.requireNonNull(homologousCurveId, "homologousCurveId");
        }
    }

    /** Сухое трение подшипников T_f0 = 0,01·T_R. */
    public static final double DRY_FRICTION_FRACTION = 0.01;

    final Spec spec;
    private final HomologousCurve curve;
    private final double tf0;
    double omega, drivingTorque, head, hydraulicTorque, shaftPower;

    PumpModel(Spec spec, HomologousCurve curve, double omega) {
        this.spec = spec;
        this.curve = curve;
        this.tf0 = DRY_FRICTION_FRACTION * spec.ratedTorque();
        this.omega = omega;
    }

    public Spec spec() {
        return spec;
    }

    /** ω, рад/с. */
    public double speed() {
        return omega;
    }

    /** Момент привода, Н·м: от 03a (Create) на каждый тик; 0 — потеря привода. */
    public void setDrivingTorque(double newtonMeters) {
        if (!Double.isFinite(newtonMeters)) throw new IllegalArgumentException("torque: " + newtonMeters);
        drivingTorque = newtonMeters;
    }

    public double drivingTorque() {
        return drivingTorque;
    }

    /** Потребление с вала, Вт (гидравлика + трение) на последнем шаге. */
    public double shaftPower() {
        return shaftPower;
    }

    /** Напор на последнем шаге, Па. */
    public double head() {
        return head;
    }

    public double hydraulicTorque() {
        return hydraulicTorque;
    }

    /** Напор Δp_pump = ρ g H_R h(ν, v), Па, при текущей скорости вала. */
    double headAt(double massFlow, double rho) {
        double v = massFlow / (rho * spec.ratedFlow()), nu = omega / spec.ratedSpeed();
        return rho * VoidFraction.GRAVITY * spec.ratedHead() * curve.head(nu, v);
    }

    private double torqueAt(double w, double massFlow, double rho) {
        double v = massFlow / (rho * spec.ratedFlow()), nu = w / spec.ratedSpeed();
        return spec.ratedTorque() * curve.torque(nu, v) * rho / spec.ratedDensity();
    }

    /**
     * Шаг вала: момент гидравлики линеаризуется по ω, когда он тормозит (иначе маленький J
     * был бы жёстким), сухое трение не переводит вал через ноль.
     */
    void integrate(double dt, double massFlow, double rho) {
        double th = torqueAt(omega, massFlow, rho);
        hydraulicTorque = th;
        double net = drivingTorque - th;
        if (omega == 0 && Math.abs(net) <= tf0) {
            shaftPower = 0;
            return;
        }
        double eps = 1e-3 * spec.ratedSpeed();
        double slope = Math.max(0, (torqueAt(omega + eps, massFlow, rho) - torqueAt(omega - eps, massFlow, rho)) / (2 * eps));
        double fr = tf0 * Math.signum(omega != 0 ? omega : net);
        double next = omega + dt * (net - fr) / (spec.inertia() + dt * slope);
        if (omega != 0 && Math.signum(next) != Math.signum(omega) && Math.abs(net) <= tf0) next = 0;
        shaftPower = (th + fr) * omega;
        omega = next;
    }
}
