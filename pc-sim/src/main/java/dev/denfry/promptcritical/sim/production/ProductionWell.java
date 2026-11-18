package dev.denfry.promptcritical.sim.production;

import dev.denfry.promptcritical.sim.drilling.NaturalGas;
import dev.denfry.promptcritical.sim.drilling.Trap;

/**
 * Добывающая скважина: залежь ({@link ProducingReservoir}), лифт по НКТ со штуцером ({@link Tubing}) или ШГН
 * ({@link BeamPump}). Пока пласт поднимает столб, скважина фонтанирует через штуцер; когда давление упало и точки
 * работы нет — скважина «умирает», и нужен станок-качалка. Закрытая или заглохшая скважина сама снова не
 * запускается, если без потока столб жидкости тяжелее пластового давления, даже когда при потоке газ облегчил бы
 * его ({@link Tubing#kicksOff}).
 *
 * <p>При насосной эксплуатации газ отводится по затрубью (насос с газовым якорем) и в насос идёт дегазированная
 * жидкость; насос откачивает до минимального давления на приёме 0,3 МПа: если подача больше притока, заполнение
 * падает («откачка до уровня»), если меньше — уровень поднимается до равенства подачи и притока (Brown, т. 2a; Брэдли,
 * «Petroleum Engineering Handbook», гл. 10).
 *
 * <p><b>ШГН</b> (09 §9.6.5): динамограмма — колонна из восьми масс ({@link RodPumpDynamics}), 200 точек; газ, не
 * отведённый газовым якорем ({@link #GAS_ANCHOR_EFFICIENCY}), занимает камеру насоса вместо жидкости. Противовес по
 * умолчанию — C = W_ж/2 + W_ш(1 − ρ_ж/ρ_ст) (§9.6.5), игрок его меняет; пиковый момент выше паспорта редуктора API 228
 * (25,8 кН·м) ломает зубья. Штанги рвутся, когда наибольшее напряжение выше допускаемого по модифицированной
 * диаграмме Гудмана (API RP 11BR, штанги класса D: σ_в = 793 МПа, S_A = σ_в/4 + 0,5625·σ_min); глубина обрыва
 * детерминирована. Нагнетательный клапан изнашивается с числом качаний ({@link #VALVE_LIFE_STROKES}) — утечка
 * наклоняет карту и снижает подачу. Уровень в затрубье — эхолотом: z = c·t/2.
 *
 * <p><b>Газлифт</b> (09 §9.6.4, {@link GasLift}): с клапанами в НКТ и закачкой газа без качалки скважина работает
 * через рабочий клапан; газ закачки возвращается с продукцией. Точка работы пересчитывается раз в 20 шагов или при
 * смене закачки — расчёт пуска перебирает клапаны.
 *
 * <p><b>Нагнетательная</b> (09 §9.4.6): та же скважина с отрицательным дебитом — {@link #inject}: q = J_нагн·(p_заб −
 * p̄), p_заб = p_устья + ρ_в·g·H (трение в НКТ не учитывается [допущение]); выше давления гидроразрыва — трещины
 * ({@link ProducingReservoir#inject}); взвесь в воде растит скин.
 */
public final class ProductionWell {
    public enum Mode { SHUT_IN, FLOWING, DEAD, PUMPING, GAS_LIFT, INJECTING }

    public static final double PUMP_ABOVE_PERFS_M = 30, MIN_INTAKE_PA = 0.3e6;
    public static final int MIN_CHOKE = 4, MAX_CHOKE = 64;

    private final ProducingReservoir reservoir;
    private int choke64 = 16;
    private Mode mode = Mode.SHUT_IN;
    private double oilM3s, waterM3s, gasStdM3s, pwfPa, wellheadPa, fluidLoadN, fillage;
    private RodPumpDynamics.DynoCard card;
    /** Паспорт редуктора API 228 (228 000 фунт·дюйм), Н·м (09 §9.6.5). */
    public static final double GEARBOX_RATED_NM = 25.8e3;
    /** Предел прочности штанг класса D (API Spec 11B), Па. */
    public static final double ROD_TENSILE_PA = 793e6;
    /** Доля свободного газа, которую газовый якорь отводит в затрубье [BALANCE]. */
    public static final double GAS_ANCHOR_EFFICIENCY = 0.8;
    /** Качаний до полного износа нагнетательного клапана (утечка 100 %) [BALANCE]. */
    public static final double VALVE_LIFE_STROKES = 1.5e6;
    /** Показатель адиабаты газа затрубья для скорости звука. */
    public static final double GAS_GAMMA = 1.3;

    private double counterbalanceN = Double.NaN, strokes, partDepthM = Double.NaN, peakTorqueNm, goodman, intakePa, gasFraction;
    private boolean gearboxBroken, rodPartEvent;
    private double cacheSpm = -1, cacheLoad = -1, cacheFill = -1, cacheLeak = -1, cachePart = -1, cacheIntake = -1;
    private int cacheAge;
    private final java.util.List<GasLift.Valve> valves = new java.util.ArrayList<>();
    private double injStdM3s, liftInjStdM3s = -1;
    private GasLift.Point lift = GasLift.Point.NONE;
    private int liftAge;
    private double injSkin = ProducingReservoir.SKIN, injM3s;
    private boolean fracturing;

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
        if (gearboxBroken || !Double.isNaN(partDepthM)) spm = 0;
        Inflow ipr = reservoir.inflow();
        double wc = reservoir.waterCut(), gor = reservoir.gasOilRatio(), depth = reservoir.datumM();
        if (spm <= 0) card = null;
        fluidLoadN = 0;
        fillage = 0;
        peakTorqueNm = goodman = 0;
        if (!open) {
            mode = Mode.SHUT_IN;
            oilM3s = waterM3s = gasStdM3s = 0;
            pwfPa = ipr.reservoirPa();
            wellheadPa = Math.max(0, pwfPa - (Tubing.bottomholePa(0, 0, gor, wc, depth, ipr.bubblePointPa())));
            return;
        }
        if (spm <= 0 && injStdM3s > 0 && !valves.isEmpty() && gasLift(ipr, wc, gor, depth, separatorPa)) {
            waterM3s = oilM3s * wc / (1 - wc);
            gasStdM3s = oilM3s * gor + injStdM3s;
            reservoir.produce(oilM3s, dt);
            return;
        }
        if (spm > 0) {
            pump(ipr, wc, gor, spm, separatorPa);
            mechanics(dt, spm, separatorPa);
        } else {
            // закрытая или заглохшая скважина фонтан не начнёт, если пласт не поднимает столб без потока
            boolean running = mode == Mode.FLOWING;
            double q = running || Tubing.kicksOff(ipr, gor, wc, depth, separatorPa)
                    ? Tubing.flowingRateM3s(ipr, gor, wc, depth, choke64, separatorPa) : 0;
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
        double intake = MIN_INTAKE_PA, liquid = 0, leak = valveLeak();
        for (int it = 0; it < 3; it++) {
            fluidLoadN = BeamPump.PLUNGER_AREA_M2 * Math.max(0, discharge - intake);
            double sp = BeamPump.strokeM() - BeamPump.rodStretchM(fluidLoadN, l);
            gasFraction = intakeGasFraction(gor, wc, intake);
            double qd = BeamPump.displacementM3s(spm, sp) * (1 - gasFraction) * (1 - leak / 2);
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
        intakePa = intake;
        oilM3s = liquid * (1 - wc);
        pwfPa = intake + rho * Tubing.G * below;
        wellheadPa = separatorPa;
        double chamberLiquid = fillage * (1 - gasFraction);
        card = cachedCard(spm, l, rho, discharge, intake, chamberLiquid, leak);
    }

    /** Доля свободного газа в камере насоса на приёме: газ сверх растворённого при давлении приёма, за вычетом якоря. */
    private double intakeGasFraction(double gor, double wc, double intake) {
        double rs = Tubing.solutionGor(intake, reservoir.bubblePointPa(), Math.min(gor, ProducingReservoir.RS_M3M3));
        double freeStd = Math.max(0, gor - rs) * (1 - wc) * (1 - GAS_ANCHOR_EFFICIENCY);
        double t = reservoir.temperatureK();
        double gasAtIntake = freeStd * NaturalGas.STD_PRESSURE_PA / (intake + NaturalGas.STD_PRESSURE_PA) * t / NaturalGas.STD_TEMPERATURE_K
                * NaturalGas.Z;
        return gasAtIntake / (gasAtIntake + 1);
    }

    /**
     * Шаг нагнетательной скважины: устьевое давление закачки {@code wellheadPa}, плотность воды, взвесь, мг/л, и запас
     * воды на устье, м³. Возвращает закачанный объём, м³.
     */
    public double inject(double dt, boolean open, double wellheadPa, double waterKgM3, double tssMgL, double availableM3) {
        card = null;
        oilM3s = waterM3s = gasStdM3s = 0;
        injM3s = 0;
        fracturing = false;
        double pr = reservoir.pressurePa(), depth = reservoir.datumM();
        if (!open) {
            mode = Mode.SHUT_IN;
            pwfPa = pr;
            this.wellheadPa = Math.max(0, pr - waterKgM3 * Tubing.G * depth);
            return 0;
        }
        mode = Mode.INJECTING;
        double pwf = Math.max(0, wellheadPa) + waterKgM3 * Tubing.G * depth;
        fracturing = pwf > reservoir.fracturePa();
        double j = reservoir.injectivity(injSkin) * (fracturing ? ProducingReservoir.FRACTURE_INJECTIVITY_FACTOR : 1);
        double q = Math.max(0, j * (pwf - pr));
        if (dt > 0) q = Math.min(q, Math.max(0, availableM3) / dt);
        reservoir.inject(q, dt, fracturing && q > 0);
        if (q > 0) injSkin += ProducingReservoir.SKIN_PER_MGL_S * Math.max(0, tssMgL - ProducingReservoir.TSS_LIMIT_MGL) * dt;
        injM3s = q;
        pwfPa = pwf;
        this.wellheadPa = wellheadPa;
        return q * dt;
    }

    /** Закачка воды, м³/с. */
    public double injectionM3s() {
        return injM3s;
    }

    /** Скин нагнетательной скважины (растёт со взвесью в воде). */
    public double injectionSkin() {
        return injSkin;
    }

    public void setInjectionSkin(double s) {
        injSkin = Double.isFinite(s) ? s : ProducingReservoir.SKIN;
    }

    /** Закачка идёт выше давления гидроразрыва. */
    public boolean fracturing() {
        return fracturing;
    }

    /** Газлифт: {@code true}, если скважина работает через клапан. */
    private boolean gasLift(Inflow ipr, double wc, double gor, double depth, double separatorPa) {
        if (lift == GasLift.Point.NONE || liftAge++ >= 20 || Math.abs(liftInjStdM3s - injStdM3s) > 1e-9) {
            lift = GasLift.operate(ipr, gor, wc, depth, choke64, separatorPa, injStdM3s, valves);
            liftInjStdM3s = injStdM3s;
            liftAge = 0;
        }
        if (lift.oilM3s() <= 0) return false;
        mode = Mode.GAS_LIFT;
        oilM3s = lift.oilM3s();
        pwfPa = ipr.pwfAtPa(oilM3s);
        double ql = oilM3s / (1 - wc);
        wellheadPa = Math.max(separatorPa, Tubing.gilbertWellheadPa(ql, gor * (1 - wc) + injStdM3s / ql, choke64));
        return true;
    }

    /** Газлифтные клапаны в НКТ (глубина и давление открытия). */
    public void setGasLiftValves(java.util.List<GasLift.Valve> v) {
        valves.clear();
        valves.addAll(v);
        lift = GasLift.Point.NONE;
    }

    public java.util.List<GasLift.Valve> gasLiftValves() {
        return java.util.List.copyOf(valves);
    }

    /** Закачка газлифтного газа, нм³/с (не больше {@link GasLift#MAX_INJECTION_STD_M3S}). */
    public void setGasInjectionStdM3s(double q) {
        injStdM3s = Math.clamp(q, 0, GasLift.MAX_INJECTION_STD_M3S);
    }

    public double gasInjectionStdM3s() {
        return injStdM3s;
    }

    /** Последняя точка работы газлифта. */
    public GasLift.Point gasLiftPoint() {
        return lift;
    }

    /** Карта считается заново, когда входы заметно сменились или раз в 200 шагов: она стоит сотни тысяч операций. */
    private RodPumpDynamics.DynoCard cachedCard(double spm, double l, double rho, double discharge, double intake, double fill, double leak) {
        double part = Double.isNaN(partDepthM) ? -1 : partDepthM;
        boolean same = card != null && cacheAge < 200 && Math.abs(spm - cacheSpm) < 0.01 * spm
                && Math.abs(fluidLoadN - cacheLoad) <= 0.01 * Math.max(1, fluidLoadN) && Math.abs(fill - cacheFill) < 0.01
                && Math.abs(leak - cacheLeak) < 0.01 && part == cachePart && Math.abs(intake - cacheIntake) < 0.02 * Math.max(intake, 1e5);
        if (same) {
            cacheAge++;
            return card;
        }
        cacheSpm = spm;
        cacheLoad = fluidLoadN;
        cacheFill = fill;
        cacheLeak = leak;
        cachePart = part;
        cacheIntake = intake;
        cacheAge = 0;
        return RodPumpDynamics.card(spm, l, rho, discharge, intake, fill, leak, partDepthM);
    }

    /** Противовес, редуктор, штанги и клапаны после шага насоса. */
    private void mechanics(double dt, double spm, double separatorPa) {
        if (card == null) return;
        if (Double.isNaN(counterbalanceN)) counterbalanceN = idealCounterbalanceN();
        peakTorqueNm = card.peakTorqueNm(counterbalanceN);
        double sMax = card.peakN() / BeamPump.ROD_AREA_M2, sMin = Math.max(0, card.minN()) / BeamPump.ROD_AREA_M2;
        goodman = sMax / (ROD_TENSILE_PA / 4 + 0.5625 * sMin);
        strokes += spm / 60 * dt;
        if (peakTorqueNm > GEARBOX_RATED_NM) gearboxBroken = true;
        if (goodman > 1 && Double.isNaN(partDepthM)) {
            // обрыв на муфте: глубина — детерминированная доля колонны по числу качаний
            double frac = 0.2 + 0.6 * new java.util.SplittableRandom(Double.doubleToLongBits(strokes) ^ 0x5DEECE66DL).nextDouble();
            partDepthM = Math.floor(frac * pumpDepthM());
            rodPartEvent = true;
        }
    }

    /** Противовес для уравновешенной качалки C = W_ж/2 + W_ш(1 − ρ_ж/ρ_ст), Н (09 §9.6.5). */
    public double idealCounterbalanceN() {
        if (card == null) return Double.isNaN(counterbalanceN) ? 0 : counterbalanceN;
        return card.fluidLoadN() / 2 + card.rodWeightInFluidN();
    }

    public double counterbalanceN() {
        return Double.isNaN(counterbalanceN) ? idealCounterbalanceN() : counterbalanceN;
    }

    public void setCounterbalanceN(double n) {
        counterbalanceN = Math.max(0, n);
    }

    /** Пиковый момент на редукторе за последний цикл, Н·м. */
    public double peakTorqueNm() {
        return peakTorqueNm;
    }

    /** Отношение наибольшего напряжения в штангах к допускаемому по Гудману. */
    public double goodmanRatio() {
        return goodman;
    }

    /** Утечка нагнетательного клапана, 0..1. */
    public double valveLeak() {
        return Math.min(1, strokes / VALVE_LIFE_STROKES);
    }

    public double strokes() {
        return strokes;
    }

    public boolean gearboxBroken() {
        return gearboxBroken;
    }

    public void repairGearbox() {
        gearboxBroken = false;
    }

    /** Глубина обрыва штанг, м; NaN — колонна цела. */
    public double rodPartDepthM() {
        return partDepthM;
    }

    /** Ловильные работы и замена оборванной штанги. */
    public void repairRods() {
        partDepthM = Double.NaN;
        card = null;
    }

    /** Смена насоса: новый нагнетательный клапан. */
    public void replacePump() {
        strokes = 0;
        card = null;
    }

    /** Обрыв штанг случился с прошлого вызова (для события {@code pc_well_rod_part}). */
    public boolean takeRodPartEvent() {
        boolean e = rodPartEvent;
        rodPartEvent = false;
        return e;
    }

    public double intakePa() {
        return intakePa;
    }

    public double gasFraction() {
        return gasFraction;
    }

    /** Глубина уровня жидкости в затрубье, м: столб над приёмом держит давление приёма сверх давления в затрубье. */
    public double fluidLevelM(double casingPa) {
        double rho = (1 - reservoir.waterCut()) * ProducingReservoir.STOCK_OIL_KGM3 + reservoir.waterCut() * ProducingReservoir.WATER_KGM3;
        double h = Math.max(0, intakePa - casingPa) / (rho * Tubing.G);
        return Math.clamp(pumpDepthM() - h, 0, pumpDepthM());
    }

    /** Скорость звука в газе затрубья c = √(γZRT/M), м/с, при средней температуре ствола. */
    public double annulusSoundSpeedMs() {
        double t = 0.5 * (Trap.SURFACE_K + reservoir.temperatureK());
        return Math.sqrt(GAS_GAMMA * NaturalGas.Z * NaturalGas.R * t / NaturalGas.MOLAR_MASS_KG_MOL);
    }

    /** Время эха до уровня, с (§9.6.5: z = c·t/2). */
    public double echoTimeS(double casingPa) {
        return 2 * fluidLevelM(casingPa) / annulusSoundSpeedMs();
    }

    /** Состояние механики качалки для сохранения: противовес, качания, обрыв, редуктор. */
    public double[] exportPumpState() {
        return new double[] {counterbalanceN, strokes, partDepthM, gearboxBroken ? 1 : 0};
    }

    public void restorePumpState(double[] s) {
        if (s == null || s.length != 4) return;
        counterbalanceN = s[0];
        strokes = Math.max(0, s[1]);
        partDepthM = s[2];
        gearboxBroken = s[3] > 0.5;
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
    public RodPumpDynamics.DynoCard card() {
        return card;
    }
}
