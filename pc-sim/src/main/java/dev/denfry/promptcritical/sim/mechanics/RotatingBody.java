package dev.denfry.promptcritical.sim.mechanics;

import java.util.Objects;

/**
 * Общая модель вращающегося тела (03a §3a.2.1): J·dω/dt = τ_drive − τ_load − τ_fr, τ_fr = τ_f0·sgn ω + c_v·ω.
 * Машина-источник (паровая машина, турбина) задаёт свой момент через {@link #stepTorque}; потребитель на валу
 * Create — через {@link #stepDriven} с асинхронной «жёсткостью скольжения» и насыщением (§3a.2.2) по полунеявной
 * схеме §3a.2.3. Сухое трение не переводит ω через ноль. Без аллокаций.
 */
public final class RotatingBody {
    /**
     * @param inertia         J, кг·м²
     * @param dryFriction     τ_f0 — сухое трение подшипников и уплотнений, Н·м
     * @param viscousFriction c_v — вязкое трение, Н·м·с
     */
    public record Spec(double inertia, double dryFriction, double viscousFriction) {
        public Spec {
            if (!(inertia > 0)) throw new IllegalArgumentException("inertia: " + inertia);
            if (!(dryFriction >= 0 && viscousFriction >= 0)) throw new IllegalArgumentException("friction < 0");
        }
    }

    /**
     * Привод от вала Create (03a §3a.2.2).
     *
     * @param ratedPowerW   P_rated, Вт
     * @param nominalOmega  ω_nom, рад/с
     * @param slipTime      t_c = J/K_c, с (0,25 [BALANCE])
     * @param coupling      тип муфты
     */
    public record CreateDrive(double ratedPowerW, double nominalOmega, double slipTime, CouplingType coupling) {
        /** Постоянная времени скольжения t_c по умолчанию, с. */
        public static final double DEFAULT_SLIP_TIME = 0.25;
        /** Минимальная объявляемая мощность — холостой ход привода, доля P_rated (§3a.2.4). */
        public static final double FLOOR_FRACTION = 0.02;

        public CreateDrive {
            if (!(ratedPowerW > 0 && nominalOmega > 0 && slipTime > 0)) throw new IllegalArgumentException("drive");
            Objects.requireNonNull(coupling, "coupling");
        }

        public static CreateDrive of(double ratedPowerW, double nominalOmega, CouplingType coupling) {
            return new CreateDrive(ratedPowerW, nominalOmega, DEFAULT_SLIP_TIME, coupling);
        }

        /** Пусковой момент τ_stall = 2·P_rated/ω_nom. */
        public double stallTorque() {
            return 2 * ratedPowerW / nominalOmega;
        }

        /** τ_max(ω) = min(τ_stall, P_rated / max(|ω|, ω_ε)). */
        public double maxTorque(double omega) {
            return Math.min(stallTorque(), ratedPowerW / Math.max(Math.abs(omega), OMEGA_EPS));
        }
    }

    private static final double OMEGA_EPS = 1e-3;

    private final Spec spec;
    private double omega, driveTorque, shaftPower;

    public RotatingBody(Spec spec, double omega) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.omega = omega;
    }

    public Spec spec() {
        return spec;
    }

    /** ω, рад/с. */
    public double omega() {
        return omega;
    }

    public void setOmega(double omega) {
        this.omega = omega;
    }

    /** Момент привода на последнем шаге, Н·м. */
    public double driveTorque() {
        return driveTorque;
    }

    /** Мощность привода P_shaft = τ_drive·ω (≥ 0) на последнем шаге, Вт (§3a.2.4). */
    public double shaftPower() {
        return shaftPower;
    }

    /** Объявляемая в сеть Create мощность P_decl = max(P_shaft, 0,02·P_rated) (§3a.2.4). */
    public double declaredPower(CreateDrive drive) {
        return Math.max(shaftPower, CreateDrive.FLOOR_FRACTION * drive.ratedPowerW());
    }

    /** Момент трения при скорости w, Н·м. */
    public double frictionTorque(double w) {
        return spec.dryFriction() * Math.signum(w) + spec.viscousFriction() * w;
    }

    /** Явный шаг с заданными моментами (машина-источник). */
    public void stepTorque(double dt, double drive, double load) {
        driveTorque = drive;
        advance(dt, drive - load);
        shaftPower = Math.max(0, drive * omega);
    }

    /**
     * Шаг потребителя на валу Create (§3a.2.3): ω* = (Jω + Δt(K_c ω_cmd − τ_load − τ_fr)) / (J + Δt K_c); если
     * τ_drive = K_c(ω_cmd − ω*) вне [τ_min, τ_max] — явный шаг с насыщенным моментом.
     *
     * @param omegaCmd        ω_cmd = i·n_s·2π/60, рад/с
     * @param clutchEngaged   для {@link CouplingType#FRICTION_CLUTCH}: включена ли муфта
     */
    public void stepDriven(double dt, double omegaCmd, CreateDrive drive, boolean clutchEngaged, double load) {
        double j = spec.inertia(), kc = j / drive.slipTime();
        double tMax = drive.maxTorque(omega);
        double tMin = switch (drive.coupling()) {
            case FREEWHEEL -> 0;
            case FRICTION_CLUTCH -> clutchEngaged ? -tMax : 0;
            case RIGID -> -tMax;
        };
        if (drive.coupling() == CouplingType.FRICTION_CLUTCH && !clutchEngaged) tMax = 0;
        double star = (j * omega + dt * (kc * omegaCmd - load - frictionTorque(omega))) / (j + dt * kc);
        double t = kc * (omegaCmd - star);
        boolean crosses = omega > 0 ? star < 0 : omega < 0 ? star > 0 : Math.abs(t - load) <= spec.dryFriction();
        if (t >= tMin && t <= tMax && !crosses) {
            driveTorque = t;
            omega = star;
        } else {
            // насыщение или трение перевело бы вал через ноль: явный шаг, в нём трение останавливает вал на нуле
            driveTorque = Math.max(tMin, Math.min(tMax, crosses ? kc * (omegaCmd - omega) : t));
            advance(dt, driveTorque - load);
        }
        shaftPower = Math.max(0, driveTorque * omega);
    }

    /** Явный шаг с чистым моментом net (без трения): трение не разгоняет и не переводит ω через ноль. */
    private void advance(double dt, double net) {
        double tf0 = spec.dryFriction();
        if (omega == 0 && Math.abs(net) <= tf0) return;
        double fr = tf0 * Math.signum(omega != 0 ? omega : net) + spec.viscousFriction() * omega;
        double next = omega + dt * (net - fr) / spec.inertia();
        if (omega != 0 && Math.signum(next) != Math.signum(omega) && Math.abs(net) <= tf0) next = 0;
        omega = next;
    }
}
