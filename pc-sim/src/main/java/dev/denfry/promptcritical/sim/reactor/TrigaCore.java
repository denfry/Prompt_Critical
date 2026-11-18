package dev.denfry.promptcritical.sim.reactor;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.neutronics.DelayedNeutronData;
import dev.denfry.promptcritical.sim.neutronics.PointKinetics;

/**
 * Импульсный исследовательский реактор типа TRIGA (12 §12.6.1): бассейн с активной зоной из твэлов U-ZrH. При нагреве
 * водород гидрида отдаёт энергию нейтронам, спектр ужесточается, и мгновенный отрицательный коэффициент α_prompt гасит
 * разгон без запаздывания теплопередачи. Поэтому реактор можно намеренно перевести на мгновенные нейтроны выбросом
 * импульсного стержня — вспышка в сотни мегаватт на десятки миллисекунд — и он остановится сам.
 *
 * <p>Кинетика — {@link PointKinetics} с подшагами 1 мс на время импульса (12 §12.6.1), обратная связь
 * ρ = ρ_стержней + α_prompt (T_f − T_бассейна) и адиабатическая поправка внутри подшага. Топливо — одна ёмкость C,
 * отдающая тепло воде бассейна. Оракул для тестов — формула Фукса–Нордхейма.
 *
 * <p>Режимы (12 §12.6.1, 03c §3c.2.6): в стационарном защита срабатывает по периоду короче {@link #PERIOD_TRIP_S} и
 * по мощности выше {@link #POWER_TRIP_W}; импульсный режим — осознанный ключ-байпас обеих уставок, допускается только
 * с мощности ниже {@link #PULSE_PERMISSIVE_W}, взводит пневматику стержня, и через {@link #PULSE_SCRAM_S} после выброса
 * таймер импульса сбрасывает все стержни. Предел температуры топлива — {@link #FUEL_LIMIT_K}: выше — твэлы повреждены
 * (водород выходит из гидрида, оболочка рвётся давлением).
 *
 * <p>Чего нет: пространственной формы потока, выгорания и отравления, тепловой задержки в оболочке и воде
 * (стационарная мощность в бассейне — одна теплопередача), подкритической сборки 12 §12.6.2.
 */
public final class TrigaCore {
    /** β_eff U-235 в TRIGA (12 §12.6.1, пример T12.8). */
    public static final double BETA = 0.007;
    /** Λ, с: время генерации мгновенных нейтронов в U-ZrH (12 §12.6.1). */
    public static final double GENERATION_TIME_S = 40e-6;
    /** C, Дж/К: теплоёмкость топлива зоны (12 §12.6.1, пример T12.8). */
    public static final double FUEL_HEAT_CAPACITY_J_K = 7e4;
    /** α_prompt, 1/К (12 §12.6.1: −1,0·10⁻⁴, порядок для TRIGA). */
    public static final double ALPHA_PROMPT = -1e-4;
    /** Нормировка точечной кинетики и лицензированная стационарная мощность, Вт (TRIGA Mark II, 1 МВт). */
    public static final double NOMINAL_W = 1e6;
    /**
     * Теплопередача топлива воде бассейна, Вт/К [BALANCE]: на 1 МВт средняя температура топлива около 450 °C, как у
     * TRIGA Mark II на полной мощности.
     */
    public static final double FUEL_TO_POOL_W_K = 2400;
    /** Вода бассейна, К. */
    public static final double POOL_K = 300;
    /**
     * Предел температуры топлива U-ZrH₁.₆ в стальной оболочке при импульсе, К: 1000 °C (технические условия TRIGA
     * General Atomics; выше давление водорода диссоциации рвёт оболочку).
     */
    public static final double FUEL_LIMIT_K = 1273.15;
    /** Вес импульсного стержня, Δk/k [BALANCE]: $3,00 — типичный импульсный стержень Mark II. */
    public static final double TRANSIENT_ROD_WORTH = 3 * BETA;
    /** Время выброса импульсного стержня пневматикой, с (12 §12.6.1: ≈ 0,1 с). */
    public static final double EJECT_S = 0.1;
    /**
     * Запас реактивности зоны без стержней и вес регулирующих, Δk/k [BALANCE]: запас $5 перекрыт регулирующими на $8 —
     * без импульсного стержня зона подкритична на $3 и критична при регулирующих, поднятых на ~60 %.
     */
    public static final double CORE_EXCESS = 5 * BETA, CONTROL_ROD_WORTH = 8 * BETA;
    /** Скорость привода регулирующих, доля хода в секунду. */
    public static final double CONTROL_SPEED = 0.02;
    /** Уставки стационарного режима: период, с, и мощность, Вт. */
    public static final double PERIOD_TRIP_S = 3, POWER_TRIP_W = 1.1e6;
    /** Импульсный режим допускается только с мощности ниже, Вт. */
    public static final double PULSE_PERMISSIVE_W = 1e3;
    /** Таймер импульса: через столько секунд после выброса — сброс всех стержней (консоль TRIGA). */
    public static final double PULSE_SCRAM_S = 15;
    /** Am-Be источник в зоне, нейтр./с. */
    public static final double SOURCE_N_S = 1e7;
    /** Подшаг кинетики на время импульса и при ρ выше половины β, с (12 §12.6.1). */
    public static final double FINE_DT = 1e-3;
    private static final double NU = 2.43;

    /** События реактора для мира: журнал и достижения. */
    public interface Events {
        Events NONE = new Events() {};

        default void scram(String reason, double powerW) {}

        default void fuelDamaged(double fuelK) {}

        /** Импульс окончен (сработал таймер): пик, энергия, ширина на полувысоте, наибольшая температура топлива. */
        default void pulseCompleted(double peakW, double energyJ, double fwhmS, double peakFuelK, boolean fuelDamaged) {}
    }

    private final PointKinetics kinetics = new PointKinetics(DelayedNeutronData.u235(BETA), GENERATION_TIME_S,
            PointKinetics.MIN_POWER);
    private final double sourceNorm = PointKinetics.normalizedSource(SOURCE_N_S, NU, GENERATION_TIME_S, NOMINAL_W);
    private double fuelK = POOL_K;
    private double control = 1, transientRod = 1;
    private int drive;
    private boolean pulseMode, armed, scrammed = true, fuelDamaged;
    /** Окно записи импульса после выброса, с: вспышка и её спад укладываются в него с запасом. */
    public static final double PULSE_WINDOW_S = 1;
    /** Импульс: время от выброса, заданный вес, пик, энергия, наибольшая температура топлива. */
    private double pulseT = -1, pulseReactivity = 2 * BETA, peakW, energyJ, peakFuelK;
    private boolean pulseReported;
    /** Мощность по подшагам 1 мс в окне импульса — для ширины на полувысоте. */
    private final double[] trace = new double[(int) Math.round(PULSE_WINDOW_S / FINE_DT) + 1];
    private int traceN;
    private double lastPeakW, lastEnergyJ, lastFwhmS, lastPeakFuelK;
    private double periodS = Double.POSITIVE_INFINITY;

    public TrigaCore() {
        kinetics.resetToEquilibrium(PointKinetics.MIN_POWER);
    }

    // ------------------------------------------------------------------ шаг

    /** Шаг FAST {@code dt} (0,05 с). */
    public void step(double dt, Events ev) {
        moveRods(dt);
        boolean fine = pulseT >= 0 && pulseT < PULSE_WINDOW_S || reactivity() > 0.5 * BETA;
        int n = fine ? (int) Math.ceil(dt / FINE_DT) : 1;
        double h = dt / n, p0 = powerW();
        for (int i = 0; i < n; i++) substep(h);
        double p1 = powerW();
        periodS = p1 > p0 * (1 + 1e-9) && p0 > 0 ? dt / Math.log(p1 / p0) : Double.POSITIVE_INFINITY;
        if (!fuelDamaged && fuelK > FUEL_LIMIT_K) {
            fuelDamaged = true;
            ev.fuelDamaged(fuelK);
        }
        protect(ev);
        if (pulseT >= 0) {
            if (!pulseReported && pulseT >= PULSE_WINDOW_S) {
                pulseReported = true;
                lastPeakW = peakW;
                lastEnergyJ = energyJ;
                lastFwhmS = fwhm();
                lastPeakFuelK = peakFuelK;
                ev.pulseCompleted(lastPeakW, lastEnergyJ, lastFwhmS, lastPeakFuelK, fuelDamaged);
            }
            if (pulseT >= PULSE_SCRAM_S) {
                pulseT = -1;
                doScram();
                ev.scram("pulse_timer", powerW());
            }
        }
    }

    /** Ширина на полувысоте по записи окна, с. */
    private double fwhm() {
        int first = -1, last = -1;
        for (int i = 0; i < traceN; i++) {
            if (trace[i] >= 0.5 * peakW) {
                if (first < 0) first = i;
                last = i;
            }
        }
        return first < 0 ? 0 : (last - first + 1) * FINE_DT;
    }

    private void substep(double h) {
        if (pulseT >= 0) {
            // стержень выходит линейно за EJECT_S до заданного веса; время импульса идёт по подшагам
            pulseT += h;
            double frac = Math.min(1, pulseT / EJECT_S);
            transientRod = 1 - frac * pulseReactivity / TRANSIENT_ROD_WORTH;
        }
        double rho = reactivity();
        // адиабатическая обратная связь внутри подшага: dρ/d(∫n dt) = α P_nom / C
        double kappa = ALPHA_PROMPT * NOMINAL_W / FUEL_HEAT_CAPACITY_J_K;
        double n0 = kinetics.power();
        double n1 = kinetics.step(h, rho, sourceNorm, kappa);
        double pAvg = 0.5 * (n0 + n1) * NOMINAL_W;
        // топливо: деления — мгновенно в топливо, отвод в бассейн неявно
        fuelK = (FUEL_HEAT_CAPACITY_J_K * fuelK + pAvg * h + FUEL_TO_POOL_W_K * h * POOL_K)
                / (FUEL_HEAT_CAPACITY_J_K + FUEL_TO_POOL_W_K * h);
        if (pulseT >= 0 && !pulseReported) {
            double p = n1 * NOMINAL_W;
            energyJ += pAvg * h;
            if (p > peakW) peakW = p;
            if (traceN < trace.length) trace[traceN++] = p;
            peakFuelK = Math.max(peakFuelK, fuelK);
        }
    }

    private void moveRods(double dt) {
        if (scrammed) {
            control = 1;
            transientRod = 1;
            return;
        }
        if (drive != 0) control = Math.max(0, Math.min(1, control - drive * CONTROL_SPEED * dt));
    }

    private void protect(Events ev) {
        if (scrammed || pulseMode) return;
        double p = powerW();
        if (p > POWER_TRIP_W) {
            doScram();
            ev.scram("high_power", p);
        } else if (periodS < PERIOD_TRIP_S) {
            doScram();
            ev.scram("short_period", p);
        }
    }

    private void doScram() {
        scrammed = true;
        armed = false;
        drive = 0;
        control = transientRod = 1;
    }

    // ------------------------------------------------------------------ пульт

    /** Снять АЗ: рабочий режим, регулирующие внизу. */
    public boolean reset() {
        if (!scrammed || pulseT >= 0) return false;
        scrammed = false;
        return true;
    }

    public void scram(Events ev) {
        if (scrammed) return;
        pulseT = -1;
        doScram();
        ev.scram("manual", powerW());
    }

    /** Привод регулирующих: −1 вниз, 0 стоп, +1 вверх. */
    public void drive(int dir) {
        drive = scrammed ? 0 : Integer.signum(dir);
    }

    /**
     * Ключ импульсного режима: байпас уставок по периоду и мощности. Включается только с мощности ниже
     * {@link #PULSE_PERMISSIVE_W} и без АЗ; выключается всегда (стержень, если взведён, остаётся в зоне).
     */
    public boolean setPulseMode(boolean on) {
        if (!on) {
            pulseMode = false;
            armed = false;
            return true;
        }
        if (scrammed || powerW() > PULSE_PERMISSIVE_W) return false;
        pulseMode = true;
        return true;
    }

    /** Вес импульса, Δk/k, от 0 до веса стержня. */
    public void setPulseReactivity(double rho) {
        pulseReactivity = Math.max(0, Math.min(TRANSIENT_ROD_WORTH, rho));
    }

    /** Взвести пневматику импульсного стержня. */
    public boolean arm() {
        if (!pulseMode || scrammed || pulseT >= 0) return false;
        armed = true;
        return true;
    }

    /** Выброс импульсного стержня. */
    public boolean fire() {
        if (!armed || pulseT >= 0) return false;
        armed = false;
        pulseT = 0;
        pulseReported = false;
        traceN = 0;
        peakW = energyJ = 0;
        peakFuelK = fuelK;
        return true;
    }

    // ------------------------------------------------------------------ показания

    /** Полная реактивность, Δk/k. */
    public double reactivity() {
        return CORE_EXCESS - CONTROL_ROD_WORTH * control - TRANSIENT_ROD_WORTH * transientRod
                + ALPHA_PROMPT * (fuelK - POOL_K);
    }

    public double powerW() {
        return kinetics.power() * NOMINAL_W;
    }

    public double fuelK() {
        return fuelK;
    }

    public double periodS() {
        return periodS;
    }

    public double controlInsertion() {
        return control;
    }

    public double transientInsertion() {
        return transientRod;
    }

    public boolean scrammed() {
        return scrammed;
    }

    public boolean pulseMode() {
        return pulseMode;
    }

    public boolean armed() {
        return armed;
    }

    /** Импульс идёт (с выброса до таймера). */
    public boolean pulsing() {
        return pulseT >= 0;
    }

    public double pulseReactivity() {
        return pulseReactivity;
    }

    public boolean fuelDamaged() {
        return fuelDamaged;
    }

    /** Пик последнего записанного импульса, Вт (пока окно идёт — нарастающий). */
    public double pulsePeakW() {
        return pulseT >= 0 && !pulseReported ? peakW : lastPeakW;
    }

    public double pulseEnergyJ() {
        return pulseT >= 0 && !pulseReported ? energyJ : lastEnergyJ;
    }

    /** Ширина импульса на полувысоте, с. */
    public double pulseFwhmS() {
        return pulseT >= 0 && !pulseReported ? fwhm() : lastFwhmS;
    }

    public double pulsePeakFuelK() {
        return pulseT >= 0 && !pulseReported ? peakFuelK : lastPeakFuelK;
    }

    /** Для тестов: регулирующие на заданном вводе (0 — подняты). */
    public void setControlInsertion(double x) {
        control = Math.max(0, Math.min(1, x));
    }

    /** Для тестов: стационар на мощности {@code w} с нагретым топливом. */
    public void setSteady(double w) {
        kinetics.resetToEquilibrium(Math.max(PointKinetics.MIN_POWER, w / NOMINAL_W));
        fuelK = POOL_K + w / FUEL_TO_POOL_W_K;
    }

    // ------------------------------------------------------------------ Фукс–Нордхейм (оракул 12 §12.6.1)

    /** P_max = (ρ₀ − β)² C / (2|α|Λ), Вт. */
    public static double fuchsNordheimPeakW(double rho0) {
        double d = rho0 - BETA;
        return d * d * FUEL_HEAT_CAPACITY_J_K / (2 * Math.abs(ALPHA_PROMPT) * GENERATION_TIME_S);
    }

    /** E = 2(ρ₀ − β) C/|α|, Дж. */
    public static double fuchsNordheimEnergyJ(double rho0) {
        return 2 * (rho0 - BETA) * FUEL_HEAT_CAPACITY_J_K / Math.abs(ALPHA_PROMPT);
    }

    /** ПШПВ = 3,53 Λ/(ρ₀ − β), с. */
    public static double fuchsNordheimFwhmS(double rho0) {
        return 3.53 * GENERATION_TIME_S / (rho0 - BETA);
    }

    // ------------------------------------------------------------------ сохранение

    public void save(StateWriter out) {
        out.putDouble("power", kinetics.power());
        double[] c = new double[kinetics.groupCount()];
        for (int i = 0; i < c.length; i++) c[i] = kinetics.precursor(i);
        out.putDoubles("precursors", c, c.length);
        out.putDouble("fuel_k", fuelK);
        out.putDouble("control", control);
        out.putBoolean("pulse_mode", pulseMode);
        out.putBoolean("scrammed", scrammed);
        out.putBoolean("fuel_damaged", fuelDamaged);
        out.putDouble("pulse_rho", pulseReactivity);
        out.putDouble("last_peak_w", lastPeakW);
        out.putDouble("last_energy_j", lastEnergyJ);
        out.putDouble("last_fwhm_s", lastFwhmS);
        out.putDouble("last_peak_fuel_k", lastPeakFuelK);
    }

    /** Загрузка; идущий импульс не сохраняется — реактор после загрузки в АЗ, импульсный стержень в зоне. */
    public void load(StateReader in) {
        double n = Math.max(PointKinetics.MIN_POWER, in.getDouble("power"));
        kinetics.restore(n, in.has("precursors") ? in.getDoubles("precursors") : new double[0]);
        fuelK = in.has("fuel_k") ? in.getDouble("fuel_k") : POOL_K;
        control = in.getDouble("control");
        pulseMode = in.getBoolean("pulse_mode");
        scrammed = !in.has("scrammed") || in.getBoolean("scrammed");
        fuelDamaged = in.getBoolean("fuel_damaged");
        pulseReactivity = in.has("pulse_rho") ? in.getDouble("pulse_rho") : 2 * BETA;
        lastPeakW = in.getDouble("last_peak_w");
        lastEnergyJ = in.getDouble("last_energy_j");
        lastFwhmS = in.getDouble("last_fwhm_s");
        lastPeakFuelK = in.getDouble("last_peak_fuel_k");
        armed = false;
        pulseT = -1;
        transientRod = 1;
        drive = 0;
    }
}
