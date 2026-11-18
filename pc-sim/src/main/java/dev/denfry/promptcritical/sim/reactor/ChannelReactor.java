package dev.denfry.promptcritical.sim.reactor;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.neutronics.CellComposition;
import dev.denfry.promptcritical.sim.neutronics.CellFeedbackState;
import dev.denfry.promptcritical.sim.neutronics.CoolantKind;
import dev.denfry.promptcritical.sim.neutronics.DecayHeat;
import dev.denfry.promptcritical.sim.neutronics.DelayedGroups;
import dev.denfry.promptcritical.sim.neutronics.FissionPoisons;
import dev.denfry.promptcritical.sim.neutronics.LatticeDesigns;
import dev.denfry.promptcritical.sim.neutronics.LatticeModel;
import dev.denfry.promptcritical.sim.neutronics.ModeratorKind;
import dev.denfry.promptcritical.sim.neutronics.PeriodMeter;
import dev.denfry.promptcritical.sim.neutronics.PointKinetics;
import dev.denfry.promptcritical.sim.neutronics.ReactorShape;
import dev.denfry.promptcritical.sim.neutronics.SixFactor;
import dev.denfry.promptcritical.sim.neutronics.Westcott;
import dev.denfry.promptcritical.sim.nuclide.NuclideVector;

/**
 * Канальный уран-графитовый энергетический реактор с водой под давлением в каналах — по образцу АМ-1 Обнинской АЭС
 * (1954): 128 каналов с шагом 120 мм в графитовой кладке (активная зона ⌀1,5 × 1,7 м, отражатель 0,75 м), топливо 5 %,
 * 30 МВт тепловых, вода 10 МПа, 190 → 280 °C (Kotchetkov, «Obninsk number one», NEI, 2004; Доллежаль и др., «Уран-
 * графитовые канальные реакторы», 1980).
 *
 * <p><b>Нейтроника.</b> Ячейка — {@link LatticeModel} (решётка {@link LatticeDesigns#T1_AM1}) по среднему составу
 * загруженных каналов, с Xe-135 и Sm-149 и температурами топлива и графита; пересчитывается раз в секунду, между
 * пересчётами Доплер и графит — линейно по её производным. k_eff = k∞/(1 + M²B²) цилиндра с добавкой отражателя.
 * Кинетика — {@link PointKinetics} (шесть групп Кипина по долям делений решётки, L-устойчивая схема).
 *
 * <p><b>Вода в каналах.</b> Модель ячейки для этой решётки даёт положительный паровой эффект (осушение +1,7 % k), но
 * критический опыт 1954 г. — 60 каналов с водой против 101 канала без воды (Kotchetkov, там же) — показывает обратное:
 * в тесной решётке АМ-1 вода — заметная доля замедлителя, её убыль снижает реактивность. Поэтому коэффициент по
 * плотности воды взят из опыта: k на 60 и 101 канале по той же модели различается на 8 %, т. е. +8·10⁻⁵ на кг/м³ —
 * паровой и температурный коэффициенты воды отрицательные.
 *
 * <p><b>Отравление и выгорание</b> — по игровому времени (сутки — 20 минут): йодная яма приходится на ~10 игровых
 * часов, кампания 100 суток — на ~33 реальных часа. Кинетика, теплогидравлика и остаточное тепло — в реальном
 * времени. Выгорание — по каналам: доля мощности канала по J₀(2,405 r/R); на деление расходуется 1,17 ядра U-235
 * (α = 0,17) или 1,36 ядра Pu-239 (α = 0,36), Pu-239 нарабатывается с КВ 0,35 [оценка для 5 % в графите].
 *
 * <p><b>Теплосъём.</b> Топливо, графит и вода контура — сосредоточенные ёмкости; 5 % энергии деления (γ и замедление)
 * греет графит. Вода контура отдаёт тепло в парогенератор (тепло уходит в котёл второго контура). Выход канала с
 * паросодержанием выше 0,25 — кризис теплообмена (осушение стенки); через 5 с кризиса оболочка канала разрушается:
 * активность выходит, вода попадает в кладку и добавляет реактивность (+0,15 % на канал) [оценка].
 *
 * <p><b>Защита (АЗ)</b> — сама по себе, как СУЗ: период меньше 10 с, мощность выше 120 %, расход ниже 70 % при мощности
 * выше 10 %, вода на выходе выше 300 °C, внешний сигнал (ПАЗ). Стержни АЗ падают за 2 с, РР идут вниз приводом.
 */
public final class ChannelReactor {
    public static final double DT = 0.05;
    public static final int CHANNELS = 128;
    public static final double PITCH_M = 0.12, HEIGHT_M = 1.7, REFLECTOR_M = 0.75, NOMINAL_W = 30e6, CHANNELS_PER_M2 = 1 / (PITCH_M * PITCH_M);
    public static final double INLET_K = 463.15, OUTLET_K = 553.15;
    /** Номинальный расход: 30 МВт на подогрев 190 → 280 °C. */
    public static final double NOMINAL_FLOW_KG_S = NOMINAL_W / (Water10MPa.enthalpy(OUTLET_K) - Water10MPa.enthalpy(INLET_K));
    /** Естественная циркуляция, доля номинала; выбег ГЦН (маховик), с. */
    public static final double NATURAL_FLOW = 0.05, COASTDOWN_S = 15;
    /** Коэффициент реактивности по плотности воды, 1/(кг/м³), и опорная плотность (горячее состояние). */
    public static final double ALPHA_WATER = 8.0e-5, WATER_REF = 830;
    public static final double GRAPHITE_SHARE = 0.05;
    /** Теплоёмкости, Дж/К; теплопередача, Вт/К [калибровка на номинал: топливо ≈ 700 К, графит ≈ 800 К]. */
    public static final double C_FUEL = 2.5e5, C_GRAPHITE = 9e7, C_LOOP = 1.5e7, UA_FUEL = 1.5e5, UA_GRAPHITE = 5.2e3, UA_SG = 6.7e5;
    public static final double CRITICAL_QUALITY = 0.25, DRYOUT_FAIL_S = 5, INGRESS_RHO = 0.0015;
    /** Ценности групп стержней (полное погружение), Δk/k. */
    public static final double WORTH_AZ = 0.04, WORTH_RR = 0.12, WORTH_AR = 0.006;
    /** Скорости стержней, доля хода в секунду: АЗ падает за 2 с, РР — 60 с, АР — 20 с. */
    public static final double SPEED_AZ = 0.5, SPEED_RR = 1.0 / 60, SPEED_AR = 1.0 / 20;
    public static final double TRIP_PERIOD_S = 10, TRIP_POWER = 1.2, TRIP_FLOW = 0.7, TRIP_OUTLET_K = 573.15;
    /** Источник нейтронов (Po–Be), нейтр./с. */
    public static final double SOURCE_N_S = 1e7;
    public static final double ALPHA_U5 = FuelDepletion.ALPHA_U5, ALPHA_PU9 = FuelDepletion.ALPHA_PU9, CONVERSION = 0.35,
            SIGMA_F5 = FuelDepletion.SIGMA_F5, SIGMA_F9 = FuelDepletion.SIGMA_F9;

    public enum Trip { NONE, PERIOD, POWER, FLOW, OUTLET, EXTERNAL, MANUAL }

    private final LatticeModel lattice = new LatticeModel();
    private final CellComposition cell = LatticeDesigns.referenceCell(LatticeDesigns.T1_AM1);
    private final CellComposition reflectorCell = CellComposition.nonFuel(ModeratorKind.GRAPHITE, 1.0, CoolantKind.NONE, 0,
            LatticeDesigns.NUCLEAR_GRAPHITE_IMPURITY_PPM);
    private final FissionPoisons poisons = new FissionPoisons();
    private final DecayHeat decay = new DecayHeat();
    private final PeriodMeter period = new PeriodMeter(DT);
    private PointKinetics kinetics;
    // каналы: начальная масса ТМ, U-235, U-238, Pu-239, ПД (кг), выгорание (МВт·сут/кг), кризис (с), разрушен
    private final double[] hm0 = new double[CHANNELS], u5 = new double[CHANNELS], u8 = new double[CHANNELS], pu9 = new double[CHANNELS],
            fp = new double[CHANNELS], burnup = new double[CHANNELS], dryout = new double[CHANNELS], share = new double[CHANNELS],
            x234 = new double[CHANNELS], u6 = new double[CHANNELS], pu0 = new double[CHANNELS];
    private final boolean[] failed = new boolean[CHANNELS];
    private final double[] radius = new double[CHANNELS];
    // стержни: погружение 0…1
    private double az = 1, rr = 1, ar = 0.5, arTarget = 0.5;
    private int rrDrive;
    private boolean autoRegulator;
    private double setpoint = 1.0;
    // состояние
    private double fuelK = 293.15, graphiteK = 293.15, loopK = 293.15, flowFraction, flowActual, inletK = 293.15, outletK = 293.15;
    private double waterDensity = Water10MPa.density(293.15), exitQuality, qSgW;
    private Trip trip = Trip.MANUAL;
    private boolean externalTrip, protection = true;
    private DelayedGroups groups = dev.denfry.promptcritical.sim.neutronics.DelayedNeutronData.keepin(0);
    private double staticRho, evalFuelK, evalGraphiteK, alphaDoppler, alphaGraphite, betaEff, generation, keff, sigmaA, sigmaF;
    private double totalRho, fissionW, slowTimer = 1e9;
    private int loaded, newlyFailed;

    public ChannelReactor() {
        // каналы — кольцами по квадратной решётке с шагом 120 мм, ближайшие к оси первыми
        int n = 0;
        int half = 7;
        java.util.List<double[]> pts = new java.util.ArrayList<>();
        for (int i = -half; i <= half; i++)
            for (int j = -half; j <= half; j++) pts.add(new double[] {Math.hypot(i + 0.5, j + 0.5) * PITCH_M, i, j});
        pts.sort((a, b) -> Double.compare(a[0], b[0]));
        for (double[] p : pts) {
            if (n >= CHANNELS) break;
            radius[n++] = p[0];
        }
        kinetics = new PointKinetics(dev.denfry.promptcritical.sim.neutronics.DelayedNeutronData.keepin(0), 3e-4, PointKinetics.MIN_POWER);
    }

    // ------------------------------------------------------------------ топливо

    /** Загрузка канала свежим топливом: ТМ кг, доли U-235 и U-234. Только на заглушенном реакторе. */
    public boolean load(int ch, double hmKg, double u235, double u234) {
        if (!shutDown() || hm0[ch] > 0 || hmKg <= 0) return false;
        hm0[ch] = hmKg;
        u5[ch] = hmKg * u235;
        u8[ch] = hmKg * (1 - u235 - u234);
        x234[ch] = u234;
        pu9[ch] = fp[ch] = burnup[ch] = dryout[ch] = u6[ch] = pu0[ch] = 0;
        failed[ch] = false;
        slowTimer = 1e9;
        return true;
    }

    /**
     * Топливо канала: начальный ТМ, U-235, U-238, U-236 (захват на U-235), Pu-239, Pu-240 (захват на Pu-239), продукты
     * деления, кг; доля U-234 в исходном уране; выгорание. Pu-241 и Pu-242 при выгорании АМ-1 (≈ 5 МВт·сут/кг) — доли
     * процента плутония, не ведутся.
     */
    public record ChannelFuel(double hm0Kg, double u235Kg, double u238Kg, double u236Kg, double pu239Kg, double pu240Kg,
            double fpKg, double u234, double burnupMwdKg, boolean failed) {
        /** Уран сейчас, кг (U-234 — как в исходном). */
        public double uraniumKg() {
            return u235Kg + u238Kg + u236Kg + hm0Kg * u234;
        }

        public double plutoniumKg() {
            return pu239Kg + pu240Kg;
        }

        public double heavyMetalKg() {
            return uraniumKg() + plutoniumKg();
        }
    }

    public ChannelFuel fuel(int ch) {
        return new ChannelFuel(hm0[ch], u5[ch], u8[ch], u6[ch], pu9[ch], pu0[ch], fp[ch], x234[ch], burnup[ch], failed[ch]);
    }

    /** Выгрузка канала: только на заглушенном реакторе. */
    public ChannelFuel unload(int ch) {
        if (!shutDown() || hm0[ch] <= 0) return null;
        ChannelFuel f = fuel(ch);
        hm0[ch] = u5[ch] = u8[ch] = pu9[ch] = fp[ch] = burnup[ch] = dryout[ch] = u6[ch] = pu0[ch] = 0;
        failed[ch] = false;
        slowTimer = 1e9;
        return f;
    }

    public boolean loaded(int ch) {
        return hm0[ch] > 0;
    }

    public int loadedChannels() {
        return loaded;
    }

    /** Заглушен: АЗ взведена вниз или мощность ниже 0,1 % и РР внизу. */
    public boolean shutDown() {
        return power() < 1e-3 && (az > 0.99 || rr > 0.99);
    }

    // ------------------------------------------------------------------ управление

    public double az() {
        return az;
    }

    public double rr() {
        return rr;
    }

    public double ar() {
        return ar;
    }

    /** Привод РР: −1 — вверх (извлечение), 0 — стоп, +1 — вниз. */
    public void driveRr(int dir) {
        rrDrive = Integer.signum(dir);
    }

    public int rrDrive() {
        return rrDrive;
    }

    public void setAutoRegulator(boolean on) {
        autoRegulator = on;
        arTarget = ar;
    }

    public boolean autoRegulator() {
        return autoRegulator;
    }

    public double setpoint() {
        return setpoint;
    }

    public void setSetpoint(double fraction) {
        setpoint = Math.max(0.01, Math.min(1.1, fraction));
    }

    public void setFlowFraction(double f) {
        flowFraction = Math.max(0, Math.min(1.2, f));
    }

    public void setExternalTrip(boolean on) {
        externalTrip = on;
    }

    /**
     * Вывод технологических защит (ключ на пульте): АЗ по периоду, мощности, расходу и температуре не срабатывает,
     * остаются ручная кнопка и внешний сигнал ПАЗ.
     */
    public void setProtection(boolean on) {
        protection = on;
    }

    public boolean protection() {
        return protection;
    }

    public void scram() {
        if (trip == Trip.NONE) trip = Trip.MANUAL;
    }

    /** Взвод АЗ: только если причины срабатывания нет. */
    public boolean resetTrip() {
        if (externalTrip || power() > TRIP_POWER || periodS() > 0 && periodS() < TRIP_PERIOD_S) return false;
        trip = Trip.NONE;
        return true;
    }

    public Trip trip() {
        return trip;
    }

    // ------------------------------------------------------------------ шаг

    /**
     * Шаг {@link #DT} реального времени, {@code gameDt} — игрового (яды и выгорание). {@code secondaryK} — температура
     * воды котла второго контура ({@code NaN} — парогенератору некуда отдать тепло). Возвращает тепло в парогенератор,
     * Вт.
     */
    public double step(double gameDt, double secondaryK) {
        newlyFailed = 0;
        loaded = 0;
        for (int i = 0; i < CHANNELS; i++) if (hm0[i] > 0) loaded++;
        slowTimer += DT;
        if (slowTimer >= 1) {
            slowTimer = 0;
            solveStatic();
        }
        moveRods();
        double rho = totalReactivity();
        totalRho = rho;
        double n = loaded > 0 ? kinetics.step(DT, rho, sourceNorm()) : PointKinetics.MIN_POWER;
        fissionW = n * NOMINAL_W;
        decay.step(DT, fissionW);
        poisons(gameDt);
        burn(gameDt);
        thermal(secondaryK);
        period.sample(Math.max(n, PointKinetics.MIN_POWER));
        protect();
        return qSgW;
    }

    private double sourceNorm() {
        return PointKinetics.normalizedSource(SOURCE_N_S, 2.43, generation, NOMINAL_W);
    }

    /** Средний состав загруженных каналов на 1 т ТМ — для модели ячейки. */
    private NuclideVector averageFuel() {
        return FuelDepletion.averageFuel(hm0, u5, u8, pu9, fp, 0.05);
    }

    private CellFeedbackState state(double tf, double tg) {
        double tn = tg + LatticeModel.NEUTRON_GAS_OFFSET;
        return new CellFeedbackState(Math.max(1, tf), WATER_REF, Math.max(1, tg), 0, poisons.xenonAbs(sigmaXe(tn)), poisons.samariumAbs(sigmaSm(tn)));
    }

    private static double sigmaXe(double tn) {
        return FissionPoisons.SIGMA_XE_B * Westcott.g(Westcott.Factor.XE135_A, tn) * Westcott.oneOverV(tn);
    }

    private static double sigmaSm(double tn) {
        return FissionPoisons.SIGMA_SM_B * Westcott.g(Westcott.Factor.SM149_A, tn) * Westcott.oneOverV(tn);
    }

    /** Радиус активной зоны по числу загруженных каналов (загрузка — от центра), м. */
    private double coreRadius() {
        return Math.sqrt(Math.max(loaded, 1) / (CHANNELS_PER_M2 * Math.PI));
    }

    private void solveStatic() {
        loaded = 0;
        for (int i = 0; i < CHANNELS; i++) if (hm0[i] > 0) loaded++;
        NuclideVector fuel = averageFuel();
        CellFeedbackState s = state(fuelK, graphiteK);
        SixFactor sf = lattice.evaluate(cell, fuel, s);
        SixFactor refl = lattice.evaluate(reflectorCell, fuel, s);
        double delta = lattice.reflectorSavings(sf, refl, REFLECTOR_M);
        double b2 = lattice.geometricBuckling(ReactorShape.cylinder(coreRadius(), HEIGHT_M), delta);
        keff = loaded > 0 ? lattice.estimateKeff(sf, b2) : 0;
        staticRho = loaded > 0 ? (keff - 1) / keff : -1;
        evalFuelK = fuelK;
        evalGraphiteK = graphiteK;
        alphaDoppler = sf.reactivityCoefficient(sf.dkdTf());
        alphaGraphite = sf.reactivityCoefficient(sf.dkdTm());
        sigmaA = sf.sigmaA();
        sigmaF = sf.sigmaF();
        DelayedGroups g = lattice.delayedGroups(lattice.fissionFractions(cell, fuel, s), b2, sf);
        generation = lattice.generationTime(sf, graphiteK + LatticeModel.NEUTRON_GAS_OFFSET);
        betaEff = g.betaTotal();
        groups = g;
        kinetics.setParameters(g, generation);
        // доли мощности каналов по J₀
        double r = coreRadius() + delta, sum = 0;
        for (int i = 0; i < CHANNELS; i++) {
            share[i] = hm0[i] > 0 ? Math.max(0.05, j0(2.405 * radius[i] / r)) : 0;
            sum += share[i];
        }
        for (int i = 0; i < CHANNELS; i++) share[i] = sum > 0 ? share[i] / sum : 0;
    }

    /** J₀(x) при 0 ≤ x ≤ 3 (Abramowitz, Stegun, 9.4.1, погрешность 5·10⁻⁸). */
    static double j0(double x) {
        double y = Math.min(3, Math.abs(x)) / 3, y2 = y * y;
        return 1 + y2 * (-2.2499997 + y2 * (1.2656208 + y2 * (-0.3163866 + y2 * (0.0444479 + y2 * (-0.0039444 + y2 * 0.0002100)))));
    }

    private static double sCurve(double x) {
        return x - Math.sin(2 * Math.PI * x) / (2 * Math.PI);
    }

    /** Реактивность стержней (отрицательная), Δk/k. */
    public double rodReactivity() {
        return -(WORTH_AZ * sCurve(az) + WORTH_RR * sCurve(rr) + WORTH_AR * sCurve(ar));
    }

    private double totalReactivity() {
        int broken = 0;
        for (boolean f : failed) if (f) broken++;
        return staticRho + alphaDoppler * (fuelK - evalFuelK) + alphaGraphite * (graphiteK - evalGraphiteK)
                + ALPHA_WATER * (waterDensity - WATER_REF) + rodReactivity() + INGRESS_RHO * broken;
    }

    private void moveRods() {
        boolean tripped = trip != Trip.NONE;
        az = tripped ? Math.min(1, az + SPEED_AZ * DT) : Math.max(0, az - SPEED_AZ * DT * 0.2);
        int dir = tripped ? 1 : rrDrive;
        rr = Math.max(0, Math.min(1, rr + dir * SPEED_RR * DT));
        if (autoRegulator && !tripped) {
            // АР: пропорционально-интегральный по логарифму мощности, зона нечувствительности 1 %
            double err = Math.log(Math.max(power(), 1e-9) / setpoint);
            if (Math.abs(err) > 0.01) arTarget = Math.max(0, Math.min(1, arTarget + 0.02 * err * DT * 20));
            ar += Math.max(-SPEED_AR * DT, Math.min(SPEED_AR * DT, arTarget - ar));
        } else if (tripped) {
            ar = Math.min(1, ar + SPEED_AR * DT);
            arTarget = ar;
        }
    }

    private void poisons(double gameDt) {
        double f = 0, phi = 0;
        double vCm3 = Math.PI * Math.pow(coreRadius() * 100, 2) * HEIGHT_M * 100;
        if (loaded > 0 && sigmaF > 0) {
            f = fissionW / (PointKinetics.FISSION_ENERGY * vCm3);
            phi = f / sigmaF;
        }
        double tn = graphiteK + LatticeModel.NEUTRON_GAS_OFFSET;
        poisons.step(gameDt, f, phi, sigmaXe(tn), sigmaSm(tn));
    }

    private void burn(double gameDt) {
        if (fissionW <= 0 || gameDt <= 0) return;
        for (int i = 0; i < CHANNELS; i++)
            if (share[i] > 0) FuelDepletion.deplete(i, fissionW * share[i], gameDt, CONVERSION, hm0, u5, u8, u6, pu9, pu0, fp, burnup);
    }

    private void thermal(double secondaryK) {
        double heat = fissionW + decay.powerW();
        double toFuel = heat * (1 - GRAPHITE_SHARE), toGraphite = heat * GRAPHITE_SHARE;
        // насос разгоняется и выбегает с постоянной маховика
        flowActual += (flowFraction - flowActual) * Math.min(1, DT / COASTDOWN_S);
        double w = NOMINAL_FLOW_KG_S * Math.max(NATURAL_FLOW, flowActual);
        double cp = Water10MPa.cp(loopK);
        double qFuel = UA_FUEL * (fuelK - loopK), qGraphite = UA_GRAPHITE * (graphiteK - loopK);
        fuelK += (toFuel - qFuel) / C_FUEL * DT;
        graphiteK += (toGraphite - qGraphite) / C_GRAPHITE * DT;
        double qCore = qFuel + qGraphite;
        qSgW = Double.isNaN(secondaryK) ? 0 : Math.max(0, UA_SG * (loopK - secondaryK));
        loopK += (qCore - qSgW) / C_LOOP * DT;
        double dT = qCore / Math.max(1e-9, w * cp);
        inletK = loopK - dT / 2;
        double hOut = Water10MPa.enthalpy(Math.min(inletK, Water10MPa.T_SAT)) + qCore / w;
        exitQuality = Water10MPa.quality(hOut);
        outletK = exitQuality > 0 ? Water10MPa.T_SAT : Math.min(Water10MPa.T_SAT, loopK + dT / 2);
        // средняя плотность воды в каналах: жидкость при средней температуре, при кипении — смесь на выходе пополам
        double liquid = Water10MPa.density((inletK + outletK) / 2);
        waterDensity = exitQuality > 0 ? 0.5 * (liquid + Water10MPa.mixtureDensity(exitQuality)) : liquid;
        // кризис теплообмена по каналам
        for (int i = 0; i < CHANNELS; i++) {
            if (share[i] <= 0 || failed[i]) continue;
            double wCh = w / Math.max(1, loaded), qCh = qCore * share[i];
            double x = Water10MPa.quality(Water10MPa.enthalpy(Math.min(inletK, Water10MPa.T_SAT)) + qCh / wCh);
            dryout[i] = x > CRITICAL_QUALITY ? dryout[i] + DT : 0;
            if (dryout[i] >= DRYOUT_FAIL_S) {
                failed[i] = true;
                newlyFailed++;
            }
        }
    }

    private void protect() {
        if (trip != Trip.NONE) return;
        if (externalTrip) {
            trip = Trip.EXTERNAL;
            autoRegulator = false;
            return;
        }
        if (!protection) return;
        double p = power(), t = period.period();
        if (t > 0 && t < TRIP_PERIOD_S && p > 1e-6) trip = Trip.PERIOD;
        else if (p > TRIP_POWER) trip = Trip.POWER;
        else if (p > 0.1 && flowActual < TRIP_FLOW) trip = Trip.FLOW;
        else if (outletK > TRIP_OUTLET_K) trip = Trip.OUTLET;
        if (trip != Trip.NONE) autoRegulator = false;
    }

    // ------------------------------------------------------------------ показания

    /** Мощность делений, доля номинала. */
    public double power() {
        return kinetics.power();
    }

    public double fissionW() {
        return fissionW;
    }

    public double decayHeatW() {
        return decay.powerW();
    }

    public double periodS() {
        return period.period();
    }

    public double reactivity() {
        return totalRho;
    }

    public DelayedGroups delayedGroups() {
        return groups;
    }

    /** Средняя плотность делений, дел./(см³·с), и поток, н/(см²·с), при доле мощности {@code n}. */
    public double fissionDensity(double n) {
        double vCm3 = Math.PI * Math.pow(coreRadius() * 100, 2) * HEIGHT_M * 100;
        return n * NOMINAL_W / (PointKinetics.FISSION_ENERGY * vCm3);
    }

    public double flux(double n) {
        return sigmaF > 0 ? fissionDensity(n) / sigmaF : 0;
    }

    /** Равновесная реактивность ксенона при доле мощности {@code n}, Δk/k. */
    public double equilibriumXenonReactivity(double n) {
        double tn = graphiteK + LatticeModel.NEUTRON_GAS_OFFSET, sx = sigmaXe(tn);
        double x = FissionPoisons.equilibriumXenon(fissionDensity(n), flux(n), sx);
        return sigmaA > 0 ? -x * sx * 1e-24 / sigmaA : 0;
    }

    public double xenonSigmaB() {
        return sigmaXe(graphiteK + LatticeModel.NEUTRON_GAS_OFFSET);
    }

    public double betaEff() {
        return betaEff;
    }

    public double generationTime() {
        return generation;
    }

    public double keffStatic() {
        return keff;
    }

    /** Реактивность ксенона и самария, Δk/k (отрицательные): их поглощение к поглощению ячейки. */
    public double xenonReactivity() {
        return sigmaA > 0 ? -poisons.xenonAbs(sigmaXe(graphiteK + LatticeModel.NEUTRON_GAS_OFFSET)) / sigmaA : 0;
    }

    public double samariumReactivity() {
        return sigmaA > 0 ? -poisons.samariumAbs(sigmaSm(graphiteK + LatticeModel.NEUTRON_GAS_OFFSET)) / sigmaA : 0;
    }

    public FissionPoisons poisons() {
        return poisons;
    }

    public double fuelK() {
        return fuelK;
    }

    public double graphiteK() {
        return graphiteK;
    }

    public double inletK() {
        return inletK;
    }

    public double outletK() {
        return outletK;
    }

    public double loopK() {
        return loopK;
    }

    /** Фактический расход (с выбегом насоса), доля номинала. */
    public double flowFraction() {
        return flowActual;
    }

    /** Задание расхода (насос), доля номинала. */
    public double flowCommand() {
        return flowFraction;
    }

    public double exitQuality() {
        return exitQuality;
    }

    public double waterDensity() {
        return waterDensity;
    }

    public double steamGeneratorW() {
        return qSgW;
    }

    public double burnup(int ch) {
        return burnup[ch];
    }

    public boolean failed(int ch) {
        return failed[ch];
    }

    /** Каналов, разрушенных на последнем шаге. */
    public int newlyFailed() {
        return newlyFailed;
    }

    public int failedChannels() {
        int n = 0;
        for (boolean f : failed) if (f) n++;
        return n;
    }

    public double maxBurnup() {
        double m = 0;
        for (double b : burnup) m = Math.max(m, b);
        return m;
    }

    /** Наибольшее паросодержание на выходе канала (самый напряжённый канал). */
    public double hottestChannelQuality() {
        double w = NOMINAL_FLOW_KG_S * Math.max(NATURAL_FLOW, flowActual), q = UA_FUEL * (fuelK - loopK) + UA_GRAPHITE * (graphiteK - loopK);
        double m = 0;
        for (int i = 0; i < CHANNELS; i++) {
            if (share[i] <= 0) continue;
            m = Math.max(m, Water10MPa.quality(Water10MPa.enthalpy(Math.min(inletK, Water10MPa.T_SAT)) + q * share[i] / (w / Math.max(1, loaded))));
        }
        return m;
    }

    /** Установить тепловое состояние (горячий старт, тесты). */
    public void setTemperatures(double fuel, double graphite, double loop) {
        flowActual = flowFraction;
        fuelK = fuel;
        graphiteK = graphite;
        loopK = loop;
        slowTimer = 1e9;
    }

    /** Привести кинетику к равновесию на мощности {@code n} (тесты, горячий старт). */
    public void equilibrate(double n) {
        solveStatic();
        kinetics.resetToEquilibrium(n);
    }

    public void setRods(double az, double rr, double ar) {
        this.az = az;
        this.rr = rr;
        this.ar = ar;
        this.arTarget = ar;
    }

    // ------------------------------------------------------------------ сохранение

    public void save(StateWriter out) {
        out.putDoubles("hm0", hm0, CHANNELS);
        out.putDoubles("u5", u5, CHANNELS);
        out.putDoubles("u8", u8, CHANNELS);
        out.putDoubles("pu9", pu9, CHANNELS);
        out.putDoubles("fp", fp, CHANNELS);
        out.putDoubles("bu", burnup, CHANNELS);
        out.putDoubles("x234", x234, CHANNELS);
        out.putDoubles("u6", u6, CHANNELS);
        out.putDoubles("pu0", pu0, CHANNELS);
        int[] f = new int[CHANNELS];
        for (int i = 0; i < CHANNELS; i++) f[i] = failed[i] ? 1 : 0;
        out.putInts("failed", f, CHANNELS);
        out.putDouble("az", az);
        out.putDouble("rr", rr);
        out.putDouble("ar", ar);
        out.putDouble("tf", fuelK);
        out.putDouble("tg", graphiteK);
        out.putDouble("tl", loopK);
        out.putDouble("p", power());
        out.putDouble("sp", setpoint);
        out.putDouble("flow", flowFraction);
        out.putDouble("flow_act", flowActual);
        out.putString("trip", trip.name());
        out.beginObject("poisons");
        poisons.save(out);
        out.endObject();
        out.beginObject("decay");
        decay.save(out);
        out.endObject();
    }

    public void load(StateReader in) {
        copy(in, "hm0", hm0);
        copy(in, "u5", u5);
        copy(in, "u8", u8);
        copy(in, "pu9", pu9);
        copy(in, "fp", fp);
        copy(in, "bu", burnup);
        copy(in, "x234", x234);
        copy(in, "u6", u6);
        copy(in, "pu0", pu0);
        if (in.has("failed")) {
            int[] f = in.getInts("failed");
            for (int i = 0; i < Math.min(CHANNELS, f.length); i++) failed[i] = f[i] != 0;
        }
        az = in.optDouble("az", 1);
        rr = in.optDouble("rr", 1);
        ar = in.optDouble("ar", 0.5);
        arTarget = ar;
        fuelK = in.optDouble("tf", 293.15);
        graphiteK = in.optDouble("tg", 293.15);
        loopK = in.optDouble("tl", 293.15);
        setpoint = in.optDouble("sp", 1);
        flowFraction = in.optDouble("flow", 0);
        flowActual = in.optDouble("flow_act", 0);
        try {
            trip = Trip.valueOf(in.optString("trip", Trip.MANUAL.name()));
        } catch (IllegalArgumentException e) {
            trip = Trip.MANUAL;
        }
        if (in.has("poisons")) {
            in.beginObject("poisons");
            poisons.load(in);
            in.endObject();
        }
        if (in.has("decay")) {
            in.beginObject("decay");
            decay.load(in);
            in.endObject();
        }
        loaded = 0;
        for (int i = 0; i < CHANNELS; i++) if (hm0[i] > 0) loaded++;
        solveStatic();
        kinetics.resetToEquilibrium(Math.max(PointKinetics.MIN_POWER, in.optDouble("p", 0)));
    }

    private static void copy(StateReader in, String key, double[] to) {
        if (!in.has(key)) return;
        double[] a = in.getDoubles(key);
        System.arraycopy(a, 0, to, 0, Math.min(a.length, to.length));
    }
}
