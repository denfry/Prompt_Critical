package dev.denfry.promptcritical.sim.production;

/**
 * Добывающая скважина: залежь ({@link ProducingReservoir}), лифт по НКТ со штуцером ({@link Tubing}) или ШГН
 * ({@link BeamPump}). Пока пласт поднимает столб, скважина фонтанирует через штуцер; когда давление упало и точки
 * работы нет — скважина «умирает», и нужен станок-качалка.
 *
 * <p>При насосной эксплуатации газ отводится по затрубью (насос с газовым якорем) и в насос идёт дегазированная
 * жидкость; насос откачивает до минимального давления на приёме 0,3 МПа: если подача больше притока, заполнение
 * падает («откачка до уровня»), если меньше — уровень поднимается до равенства подачи и притока (Brown, т. 2a; Брэдли,
 * «Petroleum Engineering Handbook», гл. 10).
 */
public final class ProductionWell {
    public enum Mode { SHUT_IN, FLOWING, DEAD, PUMPING }

    public static final double PUMP_ABOVE_PERFS_M = 30, MIN_INTAKE_PA = 0.3e6;
    public static final int MIN_CHOKE = 4, MAX_CHOKE = 64;

    private final ProducingReservoir reservoir;
    private int choke64 = 16;
    private Mode mode = Mode.SHUT_IN;
    private double oilM3s, waterM3s, gasStdM3s, pwfPa, wellheadPa, fluidLoadN, fillage;
    private BeamPump.Card card;

    public ProductionWell(ProducingReservoir reservoir) {
        this.reservoir = reservoir;
    }

    public ProducingReservoir reservoir() {
        return reservoir;
    }

    public int choke64() {
        return choke64;
    }

    public void setChoke64(int s) {
        choke64 = Math.clamp(s, MIN_CHOKE, MAX_CHOKE);
    }

    public double pumpDepthM() {
        return reservoir.perfTopM() - PUMP_ABOVE_PERFS_M;
    }

    /**
     * Шаг: {@code open} — арматура открыта и продукции есть куда идти; {@code spm} — ходы станка-качалки (0 — нет);
     * {@code separatorPa} — давление в сепараторе.
     */
    public void step(double dt, boolean open, double spm, double separatorPa) {
        Inflow ipr = reservoir.inflow();
        double wc = reservoir.waterCut(), gor = reservoir.gasOilRatio(), depth = reservoir.datumM();
        card = null;
        fluidLoadN = 0;
        fillage = 0;
        if (!open) {
            mode = Mode.SHUT_IN;
            oilM3s = waterM3s = gasStdM3s = 0;
            pwfPa = ipr.reservoirPa();
            wellheadPa = Math.max(0, pwfPa - (Tubing.bottomholePa(0, 0, gor, wc, depth, ipr.bubblePointPa())));
            return;
        }
        if (spm > 0) {
            pump(ipr, wc, gor, spm, separatorPa);
        } else {
            double q = Tubing.flowingRateM3s(ipr, gor, wc, depth, choke64, separatorPa);
            mode = q > 0 ? Mode.FLOWING : Mode.DEAD;
            oilM3s = q;
            pwfPa = q > 0 ? ipr.pwfAtPa(q) : ipr.reservoirPa();
            double ql = q / (1 - wc);
            wellheadPa = q > 0 ? Math.max(separatorPa, Tubing.gilbertWellheadPa(ql, gor * (1 - wc), choke64)) : 0;
        }
        waterM3s = oilM3s * wc / (1 - wc);
        gasStdM3s = oilM3s * gor;
        reservoir.produce(oilM3s, dt);
    }

    private void pump(Inflow ipr, double wc, double gor, double spm, double separatorPa) {
        mode = Mode.PUMPING;
        double rho = (1 - wc) * ProducingReservoir.STOCK_OIL_KGM3 + wc * ProducingReservoir.WATER_KGM3;
        double l = pumpDepthM(), below = reservoir.datumM() - l;
        double discharge = separatorPa + rho * Tubing.G * l;
        double pwfMin = MIN_INTAKE_PA + rho * Tubing.G * below;
        double liquidMax = ipr.rateM3s(pwfMin) / (1 - wc);
        double intake = MIN_INTAKE_PA, liquid = 0;
        for (int it = 0; it < 3; it++) {
            fluidLoadN = BeamPump.PLUNGER_AREA_M2 * Math.max(0, discharge - intake);
            double sp = BeamPump.strokeM() - BeamPump.rodStretchM(fluidLoadN, l);
            double qd = BeamPump.displacementM3s(spm, sp);
            if (qd >= liquidMax) {
                liquid = liquidMax;
                intake = MIN_INTAKE_PA;
                fillage = qd > 0 ? liquidMax / qd : 0;
            } else {
                liquid = qd;
                double pwf = ipr.pwfAtPa(qd * (1 - wc));
                intake = Math.max(MIN_INTAKE_PA, pwf - rho * Tubing.G * below);
                fillage = 1;
            }
        }
        fluidLoadN = BeamPump.PLUNGER_AREA_M2 * Math.max(0, discharge - intake);
        card = BeamPump.card(spm, l, rho, fluidLoadN, fillage);
        oilM3s = liquid * (1 - wc);
        pwfPa = intake + rho * Tubing.G * below;
        wellheadPa = separatorPa;
    }

    public Mode mode() {
        return mode;
    }

    /** Дебит нефти, м³/с (н. у.). */
    public double oilM3s() {
        return oilM3s;
    }

    public double waterM3s() {
        return waterM3s;
    }

    /** Попутный газ, нм³/с. */
    public double gasStdM3s() {
        return gasStdM3s;
    }

    public double bottomholePa() {
        return pwfPa;
    }

    /** Давление на буфере (перед штуцером), Па. */
    public double wellheadPa() {
        return wellheadPa;
    }

    public double fluidLoadN() {
        return fluidLoadN;
    }

    public double fillage() {
        return fillage;
    }

    /** Динамограмма последнего шага при насосной эксплуатации; иначе {@code null}. */
    public BeamPump.Card card() {
        return card;
    }
}
