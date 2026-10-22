package dev.denfry.promptcritical.sim.combustion;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.thermal.HeatTransferCorrelations;
import java.util.Objects;

/**
 * Жаротрубно-дымогарный котёл I0 (07 §7.9.4) — газовая сторона. Вода и пар — узел {@code SEPARATED} гидросети, ПК —
 * связь {@code VALVE_RELIEF} с уставкой {@link FireTubeBoilerSpec#reliefSetPressure()}; биндинг кладёт
 * {@link BoilerOutputs#heatToWaterW} в узел и передаёт его состояние в {@link BoilerInputs}.
 *
 * <p>Путь газов за тик:
 * <ol>
 *   <li>Решётка: слой угля M_слоя горит со скоростью M_слоя/τ, воздух — от естественной тяги трубы через шибер,
 *       ṁ_возд = шибер·ṁ_расч·√(Δp/Δp_расч). Тонкий слой даёт большой избыток воздуха, толстый при прикрытом
 *       шибере — недожог (CO, {@link Combustor}).</li>
 *   <li>Топка (жаровые трубы): формула Гурвича {@link FurnaceHeatTransfer}; средняя теплоёмкость газов
 *       V̄c = (I(T_a) − I(T''))/(T_a − T'') уточняется итерациями.</li>
 *   <li>Дымогарные трубы: ε-NTU с кипящей водой (C_r = 0): ε = 1 − e^(−NTU), UA ∝ (ṁ_газ/ṁ_расч)^0,8.</li>
 *   <li>Труба: тяга Δp = gH(ρ_возд − ρ_газ) по температуре газов в трубе, которая догоняет температуру уходящих
 *       газов с постоянной {@link #STACK_TIME_S} (кладка прогревается не сразу).</li>
 * </ol>
 * Потеря q₅ — обечайкой по UA·(T_воды − T_нар), она есть и у остывающего котла.
 *
 * <p>Розжиг: горящий слой прогревает холодную трубу свободной конвекцией — в расчёт тяги идёт не ниже
 * T_нар + {@link #KINDLING_DT}. Без этого у модели тяги было бы только решение «нет тяги — нет огня».
 *
 * <p>Потолок жаровой трубы — сосредоточенная стенка: ρcδ·dT_м/dt = q − (T_м − T_в)/R, R = 1/h + δ/(2λ), h — кипение
 * в большом объёме по Куперу, пока потолок под водой, и ≈ 50 Вт/(м²·К) у оголённого (пар). Легкоплавкая пробка
 * имеет температуру водяной стороны стенки и выплавляется при 232 °C (олово): струя пара гасит топку, биндинг
 * открывает течь {@link #FUSIBLE_PLUG_AREA_M2} из водяного узла в топку. Без пробки оголённый потолок греется до
 * {@link #CROWN_FAILURE_K} и сминается — взрыв котла ({@link BoilerExplosion}).
 *
 * <p>Накипь (07 §7.9.3, {@link BoilerScale}) растёт в {@link #stepSlow} по жёсткости питательной воды и тепловому
 * потоку жаровых труб (один слой на всю водяную сторону): добавляет δ/λ_н в стенку потолка — тот перегревается,
 * вплоть до смятия — и в UA дымогарных труб (площадь — UA/{@link #TUBE_U}) — растут уходящие газы, падает КПД.
 * Пробка греется водяной плёнкой q/h и от накипи не зависит.
 */
public final class FireTubeBoiler implements Boiler {
    /** Уставка ПК в долях рабочего давления. */
    public static final double RELIEF_FACTOR = 1.1;
    /** Температура плавления олова пробки, К (231,93 °C). */
    public static final double FUSIBLE_PLUG_MELT_K = 505.08;
    /** Сечение канала выплавленной пробки (∅ 1/2″), м². */
    public static final double FUSIBLE_PLUG_AREA_M2 = Math.PI / 4 * 0.0127 * 0.0127;
    /** Смятие потолка жаровой трубы под давлением, К (550 °C: сталь теряет половину прочности) [BALANCE]. */
    public static final double CROWN_FAILURE_K = 823.15;
    /** Перегрев трубы горящим слоем при розжиге, К. */
    public static final double KINDLING_DT = 40;
    /** Постоянная прогрева трубы, с. */
    public static final double STACK_TIME_S = 120;
    /** Коэффициент теплопередачи дымогарных труб в расчётном режиме, Вт/(м²·К): площадь пучка = UA/U [BALANCE]. */
    public static final double TUBE_U = 80;
    /** Слой тоньше этого гаснет, кг. */
    public static final double MIN_BED_KG = 1;

    private static final double STEEL_RHO_C = 7850 * 490, STEEL_K = 45;
    /** Теплоотдача оголённой стенки пару и нижняя граница пузырькового кипения, Вт/(м²·К). */
    private static final double STEAM_H = 50, MIN_BOILING_H = 5_000;
    private static final double GAS_FLOW_EXPONENT = 0.8;
    private static final int FURNACE_ITERATIONS = 4;

    private final FireTubeBoilerSpec spec;
    /** Давление разрыва обечайки в долях p_раб: конструкционное или k_b шва с дефектом (13 §13.6.6). */
    private double burstFactor;
    private final double designFluegasKgS;
    private Combustor combustor;
    private double bedKg, crownK, stackK, chimneyHeightM, scaleM, lastFluxWm2;
    private boolean lit, plugMelted, plugFitted = true;
    private BoilerRupture rupture = BoilerRupture.NONE;

    /**
     * @param fuel         уголь на решётке ({@code CoalAssay.toFuel()})
     * @param initialTempK начальная температура металла и трубы (котёл в равновесии с водой или воздухом), К
     */
    public FireTubeBoiler(FireTubeBoilerSpec spec, FuelSpec fuel, double initialTempK) {
        this.spec = Objects.requireNonNull(spec, "spec");
        setFuel(fuel);
        this.designFluegasKgS = spec.designAirKgS() + spec.nominalFuelKgS();
        this.crownK = initialTempK;
        this.stackK = initialTempK;
        this.chimneyHeightM = spec.chimneyHeightM();
        this.burstFactor = spec.burstPressureFactor();
    }

    /** Давление разрыва обечайки в долях p_раб: шов с {@code SEAM_WEEP} рвётся при k_b вместо конструкционного. */
    public void setBurstPressureFactor(double factor) {
        if (!(factor > RELIEF_FACTOR)) throw new IllegalArgumentException("burst factor: " + factor);
        burstFactor = factor;
    }

    public double burstPressureFactor() {
        return burstFactor;
    }

    public FireTubeBoilerSpec spec() {
        return spec;
    }

    /** Смена угля на решётке (новый предмет); не для горячего цикла. */
    public void setFuel(FuelSpec fuel) {
        Objects.requireNonNull(fuel, "fuel");
        combustor = new Combustor(fuel, BurnerSpec.of(BurnerKind.GRATE, spec.nominalFuelKgS()));
    }

    public Combustor combustor() {
        return combustor;
    }

    /** Ручная загрузка угля в топку, кг. */
    public void chargeFuel(double kg) {
        if (!(kg >= 0)) throw new IllegalArgumentException("kg: " + kg);
        bedKg += kg;
    }

    public double bedKg() {
        return bedKg;
    }

    /** Розжиг (огниво, факел): удаётся, если на решётке есть уголь и пробка цела. */
    public boolean ignite() {
        if (bedKg >= MIN_BED_KG && !plugMelted && rupture == BoilerRupture.NONE) lit = true;
        return lit;
    }

    /** Погасить топку (залить, выгрести слой). */
    public void extinguish() {
        lit = false;
    }

    public boolean isLit() {
        return lit;
    }

    public boolean plugMelted() {
        return plugMelted;
    }

    /** Замена пробки на холодном котле. */
    public void replacePlug() {
        plugMelted = false;
        plugFitted = true;
    }

    /** Пробка стоит в потолке жаровой трубы; без неё (заглушена болтом) упуск воды кончается смятием потолка. */
    public void setPlugFitted(boolean fitted) {
        plugFitted = fitted;
    }

    public boolean plugFitted() {
        return plugFitted;
    }

    public BoilerRupture rupture() {
        return rupture;
    }

    public double crownTempK() {
        return crownK;
    }

    public double stackTempK() {
        return stackK;
    }

    /** Металл потолка и кладка трубы в равновесии с водой (пусконаладка, тесты). */
    public void setMetalTemperature(double kelvin) {
        crownK = kelvin;
        stackK = kelvin;
    }

    /** Высота трубы над решёткой, м (в мире — по звеньям {@code chimney} над дымоходом котла). */
    public void setChimneyHeight(double heightM) {
        if (!(heightM > 0)) throw new IllegalArgumentException("chimney height: " + heightM);
        chimneyHeightM = heightM;
    }

    public double chimneyHeightM() {
        return chimneyHeightM;
    }

    /** Котёл без BMS: «горит» — {@link BmsState#RUNNING}. */
    @Override
    public BmsState bms() {
        return lit ? BmsState.RUNNING : BmsState.OFF;
    }

    /** Толщина накипи, м. */
    public double scaleM() {
        return scaleM;
    }

    /** Механическая очистка ({@code boiler_scraper}) на холодном котле: снимает до {@code thicknessM} накипи. */
    public void removeScale(double thicknessM) {
        if (!(thicknessM >= 0)) throw new IllegalArgumentException("thickness: " + thicknessM);
        scaleM = Math.max(0, scaleM - thicknessM);
    }

    /** Накипь задана (пусконаладка, тесты), м. */
    public void setScale(double thicknessM) {
        if (!(thicknessM >= 0)) throw new IllegalArgumentException("thickness: " + thicknessM);
        scaleM = thicknessM;
    }

    /**
     * Шаг SLOW: рост накипи по жёсткости питательной воды при тепловом потоке жаровых труб последнего тика FAST.
     *
     * @param hardnessMolEqM3 жёсткость питательной воды, моль-экв/м³ (= мг-экв/л)
     */
    public void stepSlow(double dtPhysS, double hardnessMolEqM3) {
        scaleM += BoilerScale.growthRate(hardnessMolEqM3, lastFluxWm2) * dtPhysS;
    }

    /** Состояние металла, слоя и топки; топливо сохраняет владелец (по {@link FuelSpec#fuelId()}). */
    public void save(StateWriter out) {
        out.putDouble("bed_kg", bedKg);
        out.putDouble("crown_k", crownK);
        out.putDouble("stack_k", stackK);
        out.putDouble("chimney_m", chimneyHeightM);
        out.putBoolean("lit", lit);
        out.putBoolean("plug_melted", plugMelted);
        out.putBoolean("plug_fitted", plugFitted);
        out.putInt("rupture", rupture.ordinal());
        out.putDouble("scale_m", scaleM);
    }

    public void load(StateReader in) {
        bedKg = in.getDouble("bed_kg");
        crownK = in.getDouble("crown_k");
        stackK = in.getDouble("stack_k");
        chimneyHeightM = in.getDouble("chimney_m");
        lit = in.getBoolean("lit");
        plugMelted = in.getBoolean("plug_melted");
        plugFitted = in.getBoolean("plug_fitted");
        rupture = BoilerRupture.values()[in.getInt("rupture")];
        scaleM = in.optDouble("scale_m", 0);
    }

    @Override
    public void stepFast(double dtS, BoilerInputs in, BoilerOutputs out) {
        CombustionResult res = out.combustion;
        bedKg += Math.max(0, in.fuelFeedKgS) * dtS;
        if (lit && (bedKg < MIN_BED_KG || plugMelted || rupture != BoilerRupture.NONE)) lit = false;
        double burn = lit ? bedKg / spec.bedBurnoutTimeS() : 0;

        double tw = in.waterTempK, ta0 = in.ambientK;
        double stackGas = lit ? Math.max(stackK, ta0 + KINDLING_DT) : stackK;
        double draft = Chimney.draftPa(chimneyHeightM, ta0, in.ambientPa, stackGas, gasMolarMass(res));
        double damper = Math.max(0, Math.min(1, in.damper));
        double air = damper * spec.designAirKgS() * Math.sqrt(Math.max(0, draft) / spec.designDraftPa());
        combustor.burn(burn, air, ta0, ta0, res);
        bedKg = Math.max(0, bedKg - burn * dtS);

        // топка: Гурвич, если факел горячее воды
        double ta = res.adiabaticFlameK, exit = ta, qFurnace = 0;
        double hTa = res.fluegasHeatRateW(ta);
        if (burn > 0 && ta > tw + 1) {
            double phi = FurnaceHeatTransfer.PHI_DEFAULT;
            exit = Math.max(tw + 1, 0.75 * ta);
            for (int it = 0; it < FURNACE_ITERATIONS; it++) {
                double vc = (hTa - res.fluegasHeatRateW(exit)) / (ta - exit);
                double bo = FurnaceHeatTransfer.boltzmannNumber(phi, vc, spec.screenEfficiency(),
                        spec.furnaceWallAreaM2(), ta);
                exit = Math.max(tw + 1, ta * FurnaceHeatTransfer.exitTempRatio(bo, spec.furnaceEmissivity(),
                        FurnaceHeatTransfer.M_DEFAULT));
            }
            qFurnace = phi * (hTa - res.fluegasHeatRateW(exit));
        }

        // дымогарные трубы: ε-NTU, вода кипит (C_r = 0)
        double gasExit = exit, qConv = 0;
        double hExit = res.fluegasHeatRateW(exit);
        if (res.fluegasKgS > 0 && Math.abs(exit - tw) > 1e-6) {
            double c = (hExit - res.fluegasHeatRateW(tw)) / (exit - tw);
            double ua = spec.convectiveUA() * Math.pow(res.fluegasKgS / designFluegasKgS, GAS_FLOW_EXPONENT);
            if (scaleM > 0 && ua > 0) ua = 1 / (1 / ua + BoilerScale.resistance(scaleM) * TUBE_U / spec.convectiveUA());
            double eps = 1 - Math.exp(-ua / c);
            qConv = eps * c * (exit - tw);
            gasExit = res.temperatureForHeatRate(hExit - qConv, exit - qConv / c);
        }
        double qShell = spec.shellLossWPerK() * (tw - ta0);
        stackK += (res.fluegasKgS > 0 ? gasExit - stackK : ta0 - stackK) * Math.min(1, dtS / STACK_TIME_S);

        stepCrown(dtS, in, out, qFurnace);
        if (in.waterPressurePa >= burstFactor * spec.workingPressurePa()
                && rupture == BoilerRupture.NONE) rupture = BoilerRupture.SHELL_OVERPRESSURE;
        if (rupture != BoilerRupture.NONE) lit = false;

        out.furnaceHeatW = qFurnace;
        out.convectiveHeatW = qConv;
        out.shellLossW = qShell;
        out.heatToWaterW = qFurnace + qConv - qShell;
        out.fuelBurnKgS = burn;
        out.airKgS = air;
        out.draftPa = draft;
        out.furnaceExitK = exit;
        out.fluegasExitK = gasExit;
        out.efficiency = burn > 0 ? (qFurnace + qConv - qShell) / (burn * combustor.lhv()) : Double.NaN;
    }

    /** Потолок жаровой трубы и пробка: неявный шаг сосредоточенной стенки. */
    private void stepCrown(double dt, BoilerInputs in, BoilerOutputs out, double qFurnace) {
        boolean uncovered = in.waterLevelM < spec.crownHeightM();
        double q = qFurnace / spec.furnaceWallAreaM2();
        lastFluxWm2 = q;
        double h = uncovered ? STEAM_H : boilingH(in.waterPressurePa, q);
        double r = 1 / h + BoilerScale.resistance(scaleM) + spec.crownWallM() / (2 * STEEL_K);
        double cap = STEEL_RHO_C * spec.crownWallM();
        double tw = in.waterTempK;
        crownK = (crownK + dt / cap * (q + tw / r)) / (1 + dt / (cap * r));
        double plug = tw + (crownK - tw) / (r * h);
        if (plugFitted && !plugMelted && plug >= FUSIBLE_PLUG_MELT_K) {
            plugMelted = true;
            lit = false;
        }
        if (crownK >= CROWN_FAILURE_K && rupture == BoilerRupture.NONE) rupture = BoilerRupture.CROWN_COLLAPSE;
        out.crownTempK = crownK;
        out.plugTempK = plug;
        out.crownUncovered = uncovered;
    }

    /** Кипение в большом объёме по Куперу при тепловом потоке q, не ниже {@link #MIN_BOILING_H}. */
    static double boilingH(double pressurePa, double q) {
        if (!(q > 0)) return MIN_BOILING_H;
        double pr = Math.min(0.9, Math.max(1e-4, pressurePa / HeatTransferCorrelations.CRITICAL_PRESSURE));
        return Math.max(MIN_BOILING_H,
                HeatTransferCorrelations.cooperPool(pr, HeatTransferCorrelations.WATER_MOLAR_MASS, q));
    }

    /** Молярная масса газов прошлого тика, кг/моль (воздух, если газов не было). */
    private static double gasMolarMass(CombustionResult res) {
        double mol = 0;
        for (double v : res.productMolS) mol += v;
        return mol > 0 && res.fluegasKgS > 0 ? res.fluegasKgS / mol : Chimney.AIR_MOLAR_MASS;
    }
}
