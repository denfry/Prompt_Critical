package dev.denfry.promptcritical.sim.hydraulics;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.Objects;

/**
 * Приливная электростанция с бассейном: плотина отделяет залив от моря, в плотине — капсульный (прямоточный)
 * гидроагрегат и водопропускные отверстия. Напор — разность уровней бассейна и моря; бассейн — вертикальные берега
 * площадью A: dz_b/dt = −(Q_т + Q_в)/A.
 *
 * <p>Агрегат: расход при полном открытии Q = Q_р·√(H/H_р), мощность P = η·ρ·g·Q·H, КПД по напору
 * η = η_max·(1 − 0,4·(H/H_р − 1)²), на обратном ходу (из моря в бассейн) — 0,85 прямого: колесо и отсасывающая
 * труба спрофилированы под отлив (Bernshtein L.B. «Tidal Energy for Electric Power Plants», 1965; La Rance — 40 лет
 * эксплуатации, EDF/PNNL: агрегаты Ø5,35 м, 10 МВт при напоре 5,65 м и 275 м³/с, 93,75 об/мин, генерация и
 * насосный режим в обе стороны). Расход через агрегат ограничен нагрузкой: Q = min(Q(H), P_нагр/(η·ρ·g·H)).
 * Водопропускные отверстия — истечение под уровень Q_в = μ·S·√(2g·|ΔH|), μ = 0,9.
 *
 * <p>Режимы (Бернштейн): {@link Mode#EBB} — одностороннее на отливе: бассейн наполняется через отверстия на приливе,
 * держится закрытым, пока море не отступит на пусковой напор, и срабатывается через агрегат до наименьшего напора;
 * {@link Mode#TWO_WAY} — двустороннее: генерация и на отливе, и на приливе, между ними отверстия выравнивают уровни;
 * {@link Mode#OFF} — затворы открыты, бассейн ходит за морем.
 */
public final class TidalPlant {
    public enum Mode { OFF, EBB, TWO_WAY }

    public enum State { SLUICING, HOLDING, GENERATING_EBB, GENERATING_FLOOD }

    static final double RHO = 1025, G = 9.80665, SLUICE_MU = 0.9;
    /** КПД обратного хода относительно прямого [оценка по La Rance]. */
    static final double REVERSE_EFFICIENCY = 0.85;
    /** Разность уровней, при которой отверстия считаются выровнявшими уровни, м. */
    static final double EQUAL_M = 0.05;

    /**
     * Паспорт станции.
     *
     * @param basinAreaM2   площадь зеркала бассейна, м²
     * @param ratedHeadM    расчётный напор агрегата H_р, м
     * @param ratedFlowM3s  расчётный расход Q_р, м³/с
     * @param maxEfficiency η_max агрегата
     * @param sluiceAreaM2  площадь водопропускных отверстий, м²
     * @param startHeadM    пусковой напор, м
     * @param minHeadM      наименьший рабочий напор, м
     */
    public record Spec(double basinAreaM2, double ratedHeadM, double ratedFlowM3s, double maxEfficiency, double sluiceAreaM2,
            double startHeadM, double minHeadM) {
        public Spec {
            if (!(basinAreaM2 > 0 && ratedHeadM > 0 && ratedFlowM3s > 0 && sluiceAreaM2 >= 0)) throw new IllegalArgumentException("size");
            if (!(maxEfficiency > 0 && maxEfficiency <= 1)) throw new IllegalArgumentException("efficiency");
            if (!(minHeadM > 0 && startHeadM > minHeadM)) throw new IllegalArgumentException("heads");
        }

        /** Номинальная мощность агрегата: η·ρ·g·Q_р·H_р, Вт. */
        public double ratedPowerW() {
            return maxEfficiency * RHO * G * ratedFlowM3s * ratedHeadM;
        }

        /**
         * Агрегат под бассейн и место: расчётный напор 0,7 средней величины прилива (у La Rance 5,65 м при средней
         * величине 8 м), расчётный расход срабатывает 60 % призмы прилива A·R за 4 ч генерации на отливе, отверстия
         * пропускают вдвое больше агрегата при перепаде 1 м; пуск при 0,7 H_р, останов при 0,25 H_р [оценки по
         * Бернштейну: генерация на отливе 4–6 ч из 12,4].
         */
        public static Spec sizedFor(double basinAreaM2, double meanRangeM) {
            double hr = 0.7 * meanRangeM;
            double q = 0.6 * basinAreaM2 * meanRangeM / (4 * 3600);
            double sluice = 2 * q / (SLUICE_MU * Math.sqrt(2 * G * 1));
            return new Spec(basinAreaM2, hr, q, 0.9, sluice, 0.7 * hr, 0.25 * hr);
        }

        /** La Rance: бассейн 22 км², 24 агрегата по 275 м³/с при 5,65 м, η ≈ 0,9 [EDF/PNNL]; отверстия 6 по ≈ 150 м². */
        public static Spec laRance() {
            return new Spec(22e6, 5.65, 24 * 275, 0.9, 6 * 150, 4.0, 1.4);
        }
    }

    private final Spec spec;
    private Mode mode = Mode.EBB;
    private State state = State.SLUICING;
    /** Уровень бассейна над средним уровнем моря, м. */
    private double basinM;
    private double headM, turbineFlow, sluiceFlow, powerW, availableW, energyJ;
    /** Уровень моря прошлого шага — растёт ли вода. */
    private double lastSeaM = Double.NaN;

    public TidalPlant(Spec spec) {
        this.spec = Objects.requireNonNull(spec, "spec");
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

    public double basinLevelM() {
        return basinM;
    }

    public void setBasinLevelM(double m) {
        basinM = m;
    }

    /** Напор бассейн − море, м (положительный — бассейн выше, отлив). */
    public double headM() {
        return headM;
    }

    public double turbineFlowM3s() {
        return turbineFlow;
    }

    public double sluiceFlowM3s() {
        return sluiceFlow;
    }

    /** Мощность на валу агрегата, Вт. */
    public double powerW() {
        return powerW;
    }

    /** Мощность, которую агрегат дал бы при полном открытии на нынешнем напоре в режиме генерации, Вт. */
    public double availableW() {
        return availableW;
    }

    public double energyJ() {
        return energyJ;
    }

    /** КПД агрегата при напоре |H| в направлении {@code ebb}. */
    double efficiency(double absHead, boolean ebb) {
        double x = absHead / spec.ratedHeadM() - 1;
        double eta = spec.maxEfficiency() * Math.max(0, 1 - 0.4 * x * x);
        return ebb ? eta : eta * REVERSE_EFFICIENCY;
    }

    /**
     * Шаг станции.
     *
     * @param dtS     шаг, с
     * @param seaM    уровень моря над средним, м
     * @param loadW   мощность, которую забирает нагрузка (генератор), Вт; {@code Double.POSITIVE_INFINITY} — без
     *                ограничения (сеть берёт всё)
     */
    public void step(double dtS, double seaM, double loadW) {
        headM = basinM - seaM;
        double abs = Math.abs(headM);
        boolean rising = !Double.isNaN(lastSeaM) && seaM > lastSeaM;
        lastSeaM = seaM;
        control(rising, abs);

        turbineFlow = 0;
        sluiceFlow = 0;
        powerW = 0;
        boolean generating = state == State.GENERATING_EBB || state == State.GENERATING_FLOOD;
        boolean ebb = headM > 0;
        double eta = efficiency(abs, ebb);
        double qMax = spec.ratedFlowM3s() * Math.sqrt(abs / spec.ratedHeadM());
        availableW = eta * RHO * G * qMax * abs;
        if (generating && eta > 0) {
            double cap = Math.min(availableW, 1.1 * spec.ratedPowerW());
            double p = Math.min(cap, Math.max(0, loadW));
            double q = p / (eta * RHO * G * abs);
            turbineFlow = Math.signum(headM) * q;
            powerW = p;
        }
        if (state == State.SLUICING)
            sluiceFlow = Math.signum(headM) * SLUICE_MU * spec.sluiceAreaM2() * Math.sqrt(2 * G * abs);
        // вода уходит из бассейна при положительном напоре; уровень не перескакивает за море за один шаг
        double dz = -(turbineFlow + sluiceFlow) * dtS / spec.basinAreaM2();
        if (Math.abs(dz) > abs) dz = -headM;
        basinM += dz;
        energyJ += powerW * dtS;
    }

    private void control(boolean seaRising, double abs) {
        if (mode == Mode.OFF) {
            state = State.SLUICING;
            return;
        }
        switch (state) {
            case GENERATING_EBB, GENERATING_FLOOD -> {
                boolean right = state == State.GENERATING_EBB ? headM > 0 : headM < 0;
                if (!right || abs < spec.minHeadM()) state = mode == Mode.TWO_WAY ? State.SLUICING : State.HOLDING;
            }
            case HOLDING -> {
                if (headM >= spec.startHeadM()) state = State.GENERATING_EBB;
                else if (mode == Mode.TWO_WAY && -headM >= spec.startHeadM()) state = State.GENERATING_FLOOD;
                else if (mode == Mode.EBB && headM < 0) state = State.SLUICING; // море поднялось выше бассейна — наполнять
            }
            case SLUICING -> {
                if (mode == Mode.EBB) {
                    // наполнение идёт, пока прибывает вода; полная вода прошла — закрыть и держать
                    if (!seaRising && headM >= -EQUAL_M) state = State.HOLDING;
                } else if (abs < EQUAL_M) {
                    state = State.HOLDING;
                }
            }
        }
    }

    public void save(StateWriter out) {
        out.putString("mode", mode.name());
        out.putString("state", state.name());
        out.putDouble("basin", basinM);
        out.putDouble("energy", energyJ);
    }

    public void load(StateReader in) {
        mode = parse(Mode.class, in.optString("mode", Mode.EBB.name()), Mode.EBB);
        state = parse(State.class, in.optString("state", State.SLUICING.name()), State.SLUICING);
        basinM = in.optDouble("basin", 0);
        energyJ = in.optDouble("energy", 0);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E def) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
