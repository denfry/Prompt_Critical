package dev.denfry.promptcritical.sim.npp;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.nuclide.PlutoniumCharge;
import dev.denfry.promptcritical.sim.neutronics.CellFeedbackState;
import dev.denfry.promptcritical.sim.reactor.pwr.CoolantPump;
import dev.denfry.promptcritical.sim.thermo.SaturationState;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;

/**
 * Кипящий корпусной реактор T2b (00 §0.5): 100 МВт(т), та же зона {@link LightWaterCore} на 19 ТВС, что у ВВЭР, но
 * вода кипит прямо в зоне при 7 МПа (T_s = 285,8 °C) и пар из корпуса идёт в турбину — без парогенератора и
 * компенсатора давления (прямой цикл; Lahey, Moody, «The Thermal-Hydraulics of a Boiling Water Nuclear Reactor», 1993).
 *
 * <p><b>Пустоты.</b> Вода входит в зону с недогревом от смешения питательной воды (215 °C) с отсепарированной, кипит по
 * высоте; энтальпия — по косинусному профилю мощности, паросодержание x(z), доля пара — модель дрейфа Зубера–Финдлея
 * α = x/[C₀(x + (1 − x)ρ_g/ρ_f) + ρ_g·V_gj/G], C₀ = 1,13, V_gj = 0,24 м/с (Lahey, Moody, гл. 5). Средняя плотность
 * воды в зоне — обратная связь зоны: пар вытесняет замедлитель, паровой коэффициент отрицательный.
 *
 * <p><b>Регулирование расходом.</b> Больше расход рециркуляции — пар быстрее выносится, пустот меньше, реактивность
 * растёт и мощность поднимается по «стержневой линии» карты мощность–расход; меньше — наоборот. Давление держит
 * регулятор турбины (клапан по отклонению давления), уровень — регулятор питания по пару и уровню.
 *
 * <p><b>Защита</b> — как у СУЗ T1, своя: период меньше 10 с, мощность выше 118 % (APRM), давление выше 7,6 МПа, уровень
 * ниже 30 %, ручная кнопка и внешний сигнал. Гидравлическая АЗ вводит все стержни снизу за 3 с. Потеря рециркуляции — не
 * АЗ: реактор уходит на естественную циркуляцию с меньшей мощностью. ПК корпуса — 7,9 МПа в бассейн-барботёр.
 */
public final class BoilingWaterReactor {
    public static final double DT = 0.05;
    public static final int POSITIONS = LightWaterCore.POSITIONS, PUMPS = 2;
    public static final double NOMINAL_W = 100e6, P_NOMINAL = 7.0e6, FEED_K = 488.15;
    /** Номинальный расход через зону, кг/с: кратность циркуляции ≈ 7,5 к пару (паросодержание на выходе ≈ 13 %). */
    public static final double NOMINAL_FLOW_KG_S = 400;
    /** Естественная циркуляция без насосов, доля номинала расхода на номинальной мощности (∝ Q^(1/3)). */
    public static final double NATURAL_FLOW = 0.3;
    public static final double C0 = 1.13, VGJ = 0.24;
    /** Вода в корпусе на нормальном уровне, кг; металл корпуса и внутрикорпусных, Дж/К [масштаб на 100 МВт]. */
    public static final double WATER_KG = 25_000, METAL_J_PER_K = 6e7;
    /** Ценность стержней, Δk/k: аварийные и регулирующие; бора нет, и они держат и выгорание, и схлопывание пустот при АЗ. */
    public static final double WORTH_SCRAM = 0.15, WORTH_CONTROL = 0.20, SCRAM_S = 3.0, DRIVE_FRAC_S = 1.0 / 120;
    public static final double TRIP_PERIOD_S = 10, TRIP_POWER = 1.18, TRIP_PRESSURE_PA = 7.6e6, TRIP_LEVEL = 0.3,
            SAFETY_PA = 7.9e6, SAFETY_KG_S = 60;
    /** N-16 в паре на выходе из корпуса, Бк/кг на номинале [≈ 2 ГБк/кг, порядок данных BWR: 50–200 мкКи/г]. */
    public static final double N16_BQ_PER_KG = 2e9;
    /** Пар в паропроводе и турбине, кг на кг/с расхода (≈ 1 с пути); распад N-16 по дороге ≈ 2 с. */
    public static final double STEAM_HOLDUP_S = 1.0, TRANSIT_S = 2.0;
    public static final double FUEL_R_K_PER_KW_M = 17.5, FUEL_C_J_PER_KG_HM = 400;

    public enum Trip { NONE, PERIOD, POWER, PRESSURE, LEVEL, EXTERNAL, MANUAL }

    private static final WaterProperties W = WaterProperties.tables();

    private final LightWaterCore core = new LightWaterCore(NOMINAL_W, DT);
    private final CoolantPump[] pumps = new CoolantPump[PUMPS];
    private final double[] pumpDrive = new double[PUMPS];
    private final double[] satBuf = new double[17];
    private final SaturationState sat = new SaturationState();
    private double scram = 1, control = 1, scramPos;
    private int controlDrive;
    private Trip trip = Trip.MANUAL;
    private boolean externalTrip;
    private double pressure = P_NOMINAL, waterKg = WATER_KG, fuelK = 553.15, valve, steamKgS, feedKgS, reliefKgS;
    private double flowKgS, qCoreW, exitQuality, voidFraction, coreDensity, inletK;
    private double pressureSetpoint = P_NOMINAL, valveI;
    private boolean turbineAvailable = true;

    public BoilingWaterReactor() {
        for (int i = 0; i < PUMPS; i++) pumps[i] = new CoolantPump(CoolantPump.INERTIA_KGM2, 1500, 617e3);
        properties(0, 0);
    }

    private SaturationState sat(double p) {
        W.saturationP(p, satBuf);
        return sat.set(p, satBuf);
    }

    // ------------------------------------------------------------------ топливо и управление

    public boolean load(int pos, FuelDesign d, double hmKg, double u235, double u234) {
        return core.load(shutDown(), pos, d, hmKg, u235, u234);
    }

    /**
     * Загрузка ТВС МОКС: уран {@code uKg} (доли U-235 и U-234 в уране) и плутоний {@code pu}. В BWR МОКС — штатное
     * топливо (Kernkraftwerk Gundremmingen, Fukushima Daiichi-3; IAEA TRS-415, 2003): меньшая β_eff и более жёсткий
     * спектр делают паровой коэффициент сильнее по модулю, стержни — слабее ({@link LightWaterCore#rodWorthFactor()}).
     */
    public boolean load(int pos, FuelDesign d, double uKg, double u235, double u234, PlutoniumCharge pu) {
        return core.load(shutDown(), pos, d, uKg, u235, u234, pu);
    }

    public LightWaterCore.AssemblyFuel unload(int pos) {
        return core.unload(shutDown(), pos);
    }

    public LightWaterCore core() {
        return core;
    }

    public boolean shutDown() {
        return core.power() < 1e-3 && scram > 0.99;
    }

    /** Привод регулирующих стержней: −1 — извлекать, 0 — стоп, +1 — вводить. */
    public void driveControl(int dir) {
        controlDrive = Integer.signum(dir);
    }

    public int controlDrive() {
        return controlDrive;
    }

    /** Привод насоса рециркуляции {@code i}: обороты, которые держит двигатель, доля номинала. */
    public void setPumpDrive(int i, double drive) {
        pumpDrive[i] = Math.max(0, Math.min(1.1, drive));
    }

    /** Есть ли куда отдать пар (турбины подключены и принимают): иначе клапан закрыт. */
    public void setTurbineAvailable(boolean ok) {
        turbineAvailable = ok;
    }

    public void setExternalTrip(boolean on) {
        externalTrip = on;
    }

    public void scramButton() {
        if (trip == Trip.NONE) trip = Trip.MANUAL;
    }

    public boolean resetTrip() {
        if (trip == Trip.NONE) return true;
        if (externalTrip || causeOf() != Trip.NONE || scram < 0.99) return false;
        trip = Trip.NONE;
        return true;
    }

    public Trip trip() {
        return trip;
    }

    public void setRods(double scram, double control) {
        this.scram = scram;
        this.control = control;
    }

    // ------------------------------------------------------------------ шаг

    public void step(double gameDt) {
        Trip c = causeOf();
        if (trip == Trip.NONE && c != Trip.NONE) trip = c;
        moveRods();
        core.step(gameDt, feedbackState(), rodReactivity());
        thermal();
    }

    private Trip causeOf() {
        if (externalTrip) return Trip.EXTERNAL;
        double t = core.periodS();
        if (t > 0 && t < TRIP_PERIOD_S && core.power() > 1e-6) return Trip.PERIOD;
        if (core.power() > TRIP_POWER) return Trip.POWER;
        if (pressure > TRIP_PRESSURE_PA) return Trip.PRESSURE;
        if (level() < TRIP_LEVEL) return Trip.LEVEL;
        return Trip.NONE;
    }

    private static double sCurve(double x) {
        return x - Math.sin(2 * Math.PI * x) / (2 * Math.PI);
    }

    public double rodReactivity() {
        return -(WORTH_SCRAM * sCurve(scram) + WORTH_CONTROL * sCurve(control));
    }

    private void moveRods() {
        if (trip != Trip.NONE) {
            // гидравлическая АЗ: все стержни снизу за SCRAM_S
            scram = Math.min(1, scram + DT / SCRAM_S);
            control = Math.min(1, control + DT / SCRAM_S);
            controlDrive = 0;
            return;
        }
        if (scram > 0) scram = Math.max(0, scram - DRIVE_FRAC_S * DT * 4); // взвод аварийных
        control = Math.max(0, Math.min(1, control + controlDrive * DRIVE_FRAC_S * DT));
    }

    private CellFeedbackState feedbackState() {
        return core.state(fuelK, coreDensity, sat(pressure).tSat(), 0);
    }

    private void thermal() {
        SaturationState s = sat(pressure);
        double hf = s.hF(), hg = s.hG(), hfg = hg - hf, tSat = s.tSat();
        // рециркуляция
        double speed = 0;
        for (int i = 0; i < PUMPS; i++) {
            pumps[i].step(DT, pumpDrive[i]);
            speed += pumps[i].speed();
        }
        double heat = core.heatW();
        double natural = NATURAL_FLOW * Math.cbrt(Math.max(0, heat) / NOMINAL_W);
        flowKgS = NOMINAL_FLOW_KG_S * Math.max(Math.max(0.01, natural), speed / PUMPS);
        // топливо
        double uaFuel = Math.max(1e3, core.rodLengthM() * 1000 / FUEL_R_K_PER_KW_M);
        double cFuel = Math.max(1e5, FUEL_C_J_PER_KG_HM * core.heavyMetalKg());
        qCoreW = uaFuel * (fuelK - tSat);
        fuelK += ((core.loadedAssemblies() > 0 ? heat : 0) - qCoreW) / cFuel * DT;
        // пар и питание
        double hFeed = W.enthalpyPT(pressure, FEED_K);
        // регулятор давления турбины: ПИ, полный ход клапана на 0,3 МПа, изодром 2 МПа·с
        double err = pressure - pressureSetpoint;
        valveI = turbineAvailable ? Math.max(0, Math.min(1.15, valveI + err / 2e6 * DT)) : 0;
        valve = turbineAvailable ? Math.max(0, Math.min(1.15, valveI + err / 0.3e6)) : 0;
        steamKgS = valve * (NOMINAL_W / (hg - hFeed)) * pressure / P_NOMINAL;
        reliefKgS = pressure > SAFETY_PA ? SAFETY_KG_S * pressure / SAFETY_PA : 0;
        double lvl = level();
        feedKgS = Math.max(0, steamKgS + 0.02 * WATER_KG * (0.5 - lvl) / 10);
        // энергия насыщенного объёма: Q − пар·h_fg − питание·(h_f − h_пв)
        double capacity = waterKg * s.cpF() + METAL_J_PER_K;
        double dT = (Math.max(0, qCoreW) - (steamKgS + reliefKgS) * hfg - feedKgS * (hf - hFeed)) / capacity * DT;
        pressure = W.saturationPressure(Math.max(373.15, Math.min(620, tSat + dT)));
        waterKg = Math.max(0, waterKg + (feedKgS - steamKgS - reliefKgS) * DT);
        // недогрев на входе от питательной воды, профиль паросодержания по высоте
        double hIn = hf - feedKgS * (hf - hFeed) / Math.max(1, flowKgS);
        properties(hIn, Math.max(0, qCoreW));
    }

    /** Паросодержание и доля пара по высоте зоны (косинусный профиль, 10 точек), средняя плотность воды. */
    private void properties(double hIn, double q) {
        SaturationState s = sat(pressure);
        double rhoF = s.rhoF(), rhoG = s.rhoG(), hf = s.hF(), hfg = s.hG() - s.hF();
        if (q <= 0 || flowKgS <= 0) {
            voidFraction = exitQuality = 0;
            coreDensity = rhoF;
            inletK = s.tSat();
            return;
        }
        double g = flowKgS / flowArea(), sumA = 0;
        int n = 10;
        for (int i = 0; i < n; i++) {
            double z = (i + 0.5) / n;
            double h = hIn + q / flowKgS * (1 - Math.cos(Math.PI * z)) / 2;
            sumA += voidOf((h - hf) / hfg, rhoF, rhoG, g);
        }
        exitQuality = Math.max(0, (hIn + q / flowKgS - hf) / hfg);
        voidFraction = sumA / n;
        coreDensity = voidFraction * rhoG + (1 - voidFraction) * rhoF;
        inletK = W.temperaturePH(pressure, Math.min(hIn, hf));
    }

    /** Проходное сечение зоны, м²: шаг ТВС минус твэлы и направляющие трубы. */
    public double flowArea() {
        FuelDesign d = core.design() == null ? FuelDesign.VVER_1000 : core.design();
        double p = LightWaterCore.pitch(d), cell = d.hexagonal ? Math.sqrt(3) / 2 * p * p : p * p;
        double rod = Math.PI / 4 * d.cladOd * d.cladOd;
        return Math.max(1, core.loadedAssemblies()) * (cell - d.rods * rod - d.guideTubes * rod * 1.6);
    }

    /** Доля пара по модели дрейфа (Зубер–Финдлей). */
    static double voidOf(double x, double rhoF, double rhoG, double g) {
        if (x <= 0) return 0;
        double xx = Math.min(1, x);
        return xx / (C0 * (xx + (1 - xx) * rhoG / rhoF) + rhoG * VGJ / Math.max(1, g));
    }

    // ------------------------------------------------------------------ показания

    public double power() {
        return core.power();
    }

    public double pressure() {
        return pressure;
    }

    /** Уровень в корпусе, доля шкалы (0,5 — нормальный); пар в зоне поднимает уровень (набухание). */
    public double level() {
        return 0.5 * waterKg / WATER_KG + 0.15 * voidFraction;
    }

    public double voidFraction() {
        return voidFraction;
    }

    public double exitQuality() {
        return exitQuality;
    }

    public double coreWaterDensity() {
        return coreDensity;
    }

    public double flowFraction() {
        return flowKgS / NOMINAL_FLOW_KG_S;
    }

    public double pumpSpeed(int i) {
        return pumps[i].speed();
    }

    public double steamKgS() {
        return steamKgS;
    }

    public double feedKgS() {
        return feedKgS;
    }

    public double reliefKgS() {
        return reliefKgS;
    }

    public double valve() {
        return valve;
    }

    public double fuelK() {
        return fuelK;
    }

    public double inletK() {
        return inletK;
    }

    public double scramPosition() {
        return scram;
    }

    public double controlPosition() {
        return control;
    }

    /** Паровой коэффициент реактивности, Δk/k на 1 % объёмной доли пара в зоне. */
    public double voidCoefficient() {
        SaturationState s = sat(pressure);
        return -core.densityCoefficient() * (s.rhoF() - s.rhoG()) / 100;
    }

    /**
     * Активность N-16 в паре у турбины, Бк: наработка ∝ потоку (мощности), вынос — паром, распад по пути к турбине;
     * в паропроводе и турбине около {@link #STEAM_HOLDUP_S} секунд расхода пара.
     */
    public double turbineN16Bq() {
        double lambda = Math.log(2) / 7.13;
        // наработка ∝ мощности, пар уносит её целиком: концентрация в паре почти постоянна, активность ∝ мощности
        double nominalSteam = NOMINAL_W / (W.saturationP(P_NOMINAL).hG() - W.enthalpyPT(P_NOMINAL, FEED_K));
        return N16_BQ_PER_KG * nominalSteam * STEAM_HOLDUP_S * Math.max(0, core.power()) * Math.exp(-lambda * TRANSIT_S);
    }

    // ------------------------------------------------------------------ горячий старт и сохранение

    /** Номинальное равновесие: насосы на ходу, клапан открыт, давление и топливо на номинале, мощность {@code n}. */
    public void hotStart(double n) {
        for (int i = 0; i < PUMPS; i++) {
            pumpDrive[i] = 1;
            pumps[i].setSpeed(1);
        }
        pressure = pressureSetpoint = P_NOMINAL;
        waterKg = WATER_KG;
        SaturationState s = sat(pressure);
        double hFeed = W.enthalpyPT(pressure, FEED_K);
        valve = valveI = n;
        steamKgS = n * NOMINAL_W / (s.hG() - hFeed);
        feedKgS = steamKgS;
        flowKgS = NOMINAL_FLOW_KG_S;
        double q = n * NOMINAL_W;
        fuelK = s.tSat() + (core.rodLengthM() > 0 ? q / (core.rodLengthM() * 1000 / FUEL_R_K_PER_KW_M) : 0);
        properties(s.hF() - feedKgS * (s.hF() - hFeed) / flowKgS, q);
        trip = Trip.NONE;
        core.equilibrate(feedbackState(), n);
    }

    /** Реактивность при текущем состоянии без шага (подбор критичности). */
    public double evaluateReactivity() {
        CellFeedbackState st = feedbackState();
        core.solveStatic(st);
        return core.reactivity(st, rodReactivity());
    }

    public void save(StateWriter out) {
        core.save(out);
        out.putDouble("scram", scram);
        out.putDouble("ctrl", control);
        out.putDouble("p", pressure);
        out.putDouble("water", waterKg);
        out.putDouble("tf", fuelK);
        out.putDouble("valve", valve);
        out.putString("trip", trip.name());
        for (int i = 0; i < PUMPS; i++) {
            out.beginObject("pump" + i);
            pumps[i].save(out);
            out.endObject();
        }
    }

    public void load(StateReader in) {
        scram = in.optDouble("scram", 1);
        control = in.optDouble("ctrl", 1);
        pressure = in.optDouble("p", P_NOMINAL);
        waterKg = in.optDouble("water", WATER_KG);
        fuelK = in.optDouble("tf", fuelK);
        valve = valveI = in.optDouble("valve", 0);
        try {
            trip = Trip.valueOf(in.optString("trip", Trip.MANUAL.name()));
        } catch (IllegalArgumentException e) {
            trip = Trip.MANUAL;
        }
        for (int i = 0; i < PUMPS; i++)
            if (in.has("pump" + i)) {
                in.beginObject("pump" + i);
                pumps[i].load(in);
                in.endObject();
            }
        core.load(in, this::feedbackState);
    }
}
