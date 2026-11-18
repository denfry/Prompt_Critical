package dev.denfry.promptcritical.sim.hydraulics;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.Objects;

/**
 * Гидроаккумулирующая электростанция с одним обратимым агрегатом ({@link PumpTurbine}): верхний бассейн с
 * вертикальными берегами площадью A_в и рабочей сработкой от 0 до {@link Spec#drawdownM}, нижний бассейн — площадью
 * A_н (0 — река или водохранилище, уровень не меняется), напорный водовод длиной L и диаметром D с потерями по
 * Дарси–Вейсбаху h_тр = λ·(L/D)·v²/(2g), λ — по Хааланду ({@link Friction#darcy}) при расчётном расходе.
 *
 * <p>Насосный режим: подача — пересечение характеристики насоса с H_ст + h_тр(Q); мощность из сети
 * P = ρgQH_н/(η_н·η_д). Турбинный: мощность по заданию, расход — по ней при напоре нетто H_ст − h_тр(Q), не больше
 * полного открытия. Время — сетевое (секунды), чтобы энергия, закачанная из сети, и энергия, отданная в сеть,
 * считались одним счётом.
 *
 * <p>Автоматика ({@link Mode#AUTO}): частота выше {@link #PUMP_ON_HZ_ABOVE} над номиналом — избыток в системе,
 * агрегат пускается насосом; ниже {@link #GENERATE_BELOW_HZ} под номиналом — дефицит, турбина со статизмом 4 %. Пороги
 * — как у ограниченного частотного регулирования LFSM-O/LFSM-U (Регламент ЕС 2016/631 «Requirements for Generators»,
 * ст. 13, 15: 50,2 и 49,8 Гц при 50 Гц). Насос отключается, когда частота вернулась к номиналу +0,05 Гц, турбина — к
 * номиналу −0,05 Гц; верхний бассейн полон — насос стоит, сработан — турбина стоит.
 */
public final class PumpedStorage {
    public enum Mode { IDLE, PUMP, GENERATE, AUTO }

    public enum State { STANDSTILL, PUMPING, GENERATING }

    public static final double PUMP_ON_HZ_ABOVE = 0.2, GENERATE_BELOW_HZ = 0.2, RETURN_HZ = 0.05, DROOP = 0.04;

    /**
     * @param unit          насос-турбина
     * @param staticHeadM   статический напор при верхнем бассейне на нижней отметке сработки, м
     * @param drawdownM     рабочая сработка верхнего бассейна, м
     * @param upperAreaM2   площадь верхнего бассейна, м²
     * @param lowerAreaM2   площадь нижнего бассейна, м² (0 — неограничен)
     * @param penstockM     длина водовода, м
     * @param penstockDiaM  диаметр водовода, м
     */
    public record Spec(PumpTurbine unit, double staticHeadM, double drawdownM, double upperAreaM2, double lowerAreaM2,
            double penstockM, double penstockDiaM) {
        public Spec {
            Objects.requireNonNull(unit, "unit");
            if (!(staticHeadM > 0 && drawdownM > 0 && upperAreaM2 > 0 && lowerAreaM2 >= 0 && penstockM > 0 && penstockDiaM > 0))
                throw new IllegalArgumentException("geometry");
        }

        /** Коэффициент потерь водовода k: h_тр = k·Q², с²/м⁵ (λ — при расчётном расходе турбины). */
        public double lossCoefficient() {
            double area = Math.PI * penstockDiaM * penstockDiaM / 4;
            double v = unit.turbineFlowM3s() / area;
            double re = v * penstockDiaM / 1.0e-6;
            double lambda = Friction.darcy(re, Friction.ROUGHNESS_STEEL / penstockDiaM);
            return lambda * penstockM / penstockDiaM / (2 * PumpTurbine.G * area * area);
        }

        /** Энергоёмкость верхнего бассейна при среднем статическом напоре, Дж (до КПД). */
        public double storageJ() {
            return PumpTurbine.RHO * PumpTurbine.G * upperAreaM2 * drawdownM * (staticHeadM + drawdownM / 2);
        }

        /**
         * Один агрегат Загорской ГАЭС на своём водоводе 700 м × 7,5 м (РусГидро: шесть водоводов 700 м Ø7,5 м, напор
         * 100 м) и верхний бассейн на 8 ч его полной турбинной мощности при сработке 9 м [бассейн — оценка].
         */
        public static Spec zagorskUnit() {
            PumpTurbine u = PumpTurbine.zagorsk();
            double draw = 9, head = u.ratedHeadM() - draw / 2;
            double volume = u.turbineFlowM3s() * 8 * 3600;
            return new Spec(u, head, draw, volume / draw, 0, 700, 7.5);
        }
    }

    private final Spec spec;
    private final double k;
    private Mode mode = Mode.AUTO;
    private State state = State.STANDSTILL;
    /** Уровень верхнего бассейна над нижней отметкой сработки, м (0…drawdown). */
    private double upperM;
    /** Подъём нижнего бассейна над начальным уровнем, м. */
    private double lowerM;
    /** Задание мощности турбины в ручном режиме, доля номинальной. */
    private double turbineSetpoint = 1;
    private double flowM3s, powerW, energyInJ, energyOutJ, grossHeadM;

    public PumpedStorage(Spec spec) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.k = spec.lossCoefficient();
    }

    public Spec spec() {
        return spec;
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode m) {
        mode = Objects.requireNonNull(m, "mode");
    }

    public State state() {
        return state;
    }

    /** Задание мощности турбины в режиме {@link Mode#GENERATE}, доля номинальной 0…1. */
    public void setTurbineSetpoint(double fraction) {
        turbineSetpoint = Math.max(0, Math.min(1, fraction));
    }

    public double turbineSetpoint() {
        return turbineSetpoint;
    }

    public double upperLevelM() {
        return upperM;
    }

    public void setUpperLevelM(double m) {
        upperM = Math.max(0, Math.min(spec.drawdownM(), m));
    }

    /** Заполнение верхнего бассейна, доля рабочего объёма. */
    public double fill() {
        return upperM / spec.drawdownM();
    }

    /** Расход через агрегат, м³/с: положительный — вверх (насос), отрицательный — вниз (турбина). */
    public double flowM3s() {
        return flowM3s;
    }

    /** Электрическая мощность, Вт: положительная — агрегат берёт из сети (насос), отрицательная — отдаёт. */
    public double powerW() {
        return powerW;
    }

    public double grossHeadM() {
        return grossHeadM;
    }

    public double energyInJ() {
        return energyInJ;
    }

    public double energyOutJ() {
        return energyOutJ;
    }

    /** Статический напор при нынешних уровнях, м. */
    public double staticHeadM() {
        return spec.staticHeadM() + upperM - lowerM;
    }

    /**
     * Шаг станции.
     *
     * @param dtS          шаг, с
     * @param frequencyHz  частота сети у агрегата (0 — сети нет)
     * @param nominalHz    номинальная частота
     */
    public void step(double dtS, double frequencyHz, double nominalHz) {
        grossHeadM = staticHeadM();
        boolean live = frequencyHz > 0;
        State want = !live ? State.STANDSTILL : switch (mode) {
            case IDLE -> State.STANDSTILL;
            case PUMP -> State.PUMPING;
            case GENERATE -> State.GENERATING;
            case AUTO -> auto(frequencyHz, nominalHz);
        };
        if (want == State.PUMPING && upperM >= spec.drawdownM()) want = State.STANDSTILL;
        if (want == State.GENERATING && upperM <= 0) want = State.STANDSTILL;
        state = want;

        flowM3s = 0;
        powerW = 0;
        if (state == State.PUMPING) pump();
        else if (state == State.GENERATING) generate(mode == Mode.AUTO ? droopSetpoint(frequencyHz, nominalHz) : turbineSetpoint);

        double dv = flowM3s * dtS;
        upperM = Math.max(0, Math.min(spec.drawdownM(), upperM + dv / spec.upperAreaM2()));
        if (spec.lowerAreaM2() > 0) lowerM -= dv / spec.lowerAreaM2();
        if (powerW > 0) energyInJ += powerW * dtS;
        else energyOutJ -= powerW * dtS;
    }

    private State auto(double f, double f0) {
        double df = f - f0;
        return switch (state) {
            case STANDSTILL -> df > PUMP_ON_HZ_ABOVE ? State.PUMPING : -df > GENERATE_BELOW_HZ ? State.GENERATING : State.STANDSTILL;
            case PUMPING -> df > RETURN_HZ ? State.PUMPING : State.STANDSTILL;
            case GENERATING -> -df > RETURN_HZ ? State.GENERATING : State.STANDSTILL;
        };
    }

    /** Статизм 4 %: полная мощность при снижении частоты на 4 % ниже порога включения. */
    static double droopSetpoint(double f, double f0) {
        double start = f0 - RETURN_HZ;
        return Math.max(0.1, Math.min(1, (start - f) / (DROOP * f0)));
    }

    /** Насос: H_н(Q) = H_ст + k·Q² → (0,35·H_р/Q_н² + k)·Q² = 1,35·H_р − H_ст. */
    private void pump() {
        PumpTurbine u = spec.unit();
        double a = (PumpTurbine.SHUT_OFF_HEAD - 1) * u.ratedHeadM() / (u.pumpFlowM3s() * u.pumpFlowM3s()) + k;
        double rhs = PumpTurbine.SHUT_OFF_HEAD * u.ratedHeadM() - grossHeadM;
        if (rhs <= 0) { // напор выше напора на закрытую задвижку: насос воду не поднимет
            state = State.STANDSTILL;
            return;
        }
        double q = Math.sqrt(rhs / a);
        double h = u.pumpHeadM(q);
        flowM3s = q;
        powerW = PumpTurbine.RHO * PumpTurbine.G * q * h / (u.pumpEfficiencyAt(q) * u.machineEfficiency());
    }

    /** Турбина: расход под заданную мощность (бисекция), не больше полного открытия. */
    private void generate(double fraction) {
        PumpTurbine u = spec.unit();
        double target = fraction * u.turbinePowerW();
        // полное открытие при напоре нетто: Q² = Q_т²·H_ст/(H_р + k·Q_т²)
        double qFull = u.turbineFlowM3s() * Math.sqrt(Math.max(0, grossHeadM) / (u.ratedHeadM() + k * u.turbineFlowM3s() * u.turbineFlowM3s()));
        double lo = 0, hi = qFull;
        for (int i = 0; i < 50; i++) {
            double q = 0.5 * (lo + hi);
            if (turbinePower(q, qFull) < target) lo = q;
            else hi = q;
        }
        double q = Math.min(qFull, hi);
        double p = turbinePower(q, qFull);
        if (p <= 0) {
            state = State.STANDSTILL;
            return;
        }
        flowM3s = -q;
        powerW = -p;
    }

    /** Электрическая мощность турбины при расходе Q, Вт. */
    private double turbinePower(double q, double qFull) {
        PumpTurbine u = spec.unit();
        double net = grossHeadM - k * q * q;
        if (net <= 0 || qFull <= 0) return 0;
        return u.turbineEfficiencyAt(q / qFull, net) * u.machineEfficiency() * PumpTurbine.RHO * PumpTurbine.G * q * net;
    }

    public void save(StateWriter out) {
        out.putString("mode", mode.name());
        out.putString("state", state.name());
        out.putDouble("upper", upperM);
        out.putDouble("lower", lowerM);
        out.putDouble("setpoint", turbineSetpoint);
        out.putDouble("e_in", energyInJ);
        out.putDouble("e_out", energyOutJ);
    }

    public void load(StateReader in) {
        mode = parse(Mode.class, in.optString("mode", Mode.AUTO.name()), Mode.AUTO);
        state = parse(State.class, in.optString("state", State.STANDSTILL.name()), State.STANDSTILL);
        upperM = in.optDouble("upper", 0);
        lowerM = in.optDouble("lower", 0);
        turbineSetpoint = in.optDouble("setpoint", 1);
        energyInJ = in.optDouble("e_in", 0);
        energyOutJ = in.optDouble("e_out", 0);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E def) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
