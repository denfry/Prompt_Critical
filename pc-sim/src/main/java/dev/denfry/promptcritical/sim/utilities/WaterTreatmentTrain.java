package dev.denfry.promptcritical.sim.utilities;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/**
 * Цепочка водоподготовки (08 §8.12), SLOW: осветлитель (коагуляция + известкование) → песчаный фильтр →
 * катионитные колонны → декарбонизатор → деаэратор → дозирование. Качество считается по ступеням на каждый шаг
 * (вода проходит цепочку быстрее шага SLOW), состояние — загрузка смол и фильтра.
 *
 * <p>Катиониты: поток делится между H-группой (доля {@link #setHydrogenFraction}) и Na-группой, внутри группы —
 * поровну между колоннами в работе; группа без колонн в работе пропускает свою долю неочищенной. После групп потоки
 * смешиваются: щёлочность Na-фильтрата гасит минеральную кислоту H-фильтрата (параллельное H–Na-катионирование).
 * Колонна поглощает Q·ΣC: Na-форма — жёсткость, H-форма — все катионы; до рабочей ёмкости проскакивает
 * {@link #BASE_LEAK}, дальше проскок растёт линейно до полного при {@link #EXHAUSTED_LOADING}. Фильтроцикл
 * t_ц = V_см·q_раб/(Q·ΣC) (T8.16: 2 м³ × 1,0 экв/л при 20 м³/ч и 2,5 мг-экв/л — 40 ч физ.).
 *
 * <p>Свободная CO₂ ведётся внутри цепочки (в {@link WaterQuality} её нет): у входа — по pH и щёлочности, pH дальше —
 * по паре HCO₃⁻/CO₂ (pK₁ = 6,35) или по минеральной кислотности.
 */
public final class WaterTreatmentTrain {
    /** Проскок свежей колонны, доля входной концентрации [BALANCE]. */
    public static final double BASE_LEAK = 0.01;
    /** Загрузка (в рабочих ёмкостях), при которой смола полностью истощена. */
    public static final double EXHAUSTED_LOADING = 1.3;
    /** Остаточная карбонатная жёсткость после известкования, моль-экв/м³ (0,5–0,7). */
    public static final double LIMING_RESIDUAL = 0.6;
    /** pH известкованной воды. */
    public static final double LIMED_PH = 10.2;
    /** Осветлитель с коагуляцией: остаётся 10 % взвеси, но не меньше 5 мг/л; без коагулянта — отстой 50 %. */
    public static final double COAGULATION_PASS = 0.1, COAGULATION_FLOOR_KG_M3 = 0.005, SETTLING_PASS = 0.5;
    /** Песчаный фильтр: задерживает 98 % взвеси, забитый — 50 %. */
    public static final double FILTER_PASS = 0.02, CLOGGED_FILTER_PASS = 0.5;
    /** Свободная CO₂ после декарбонизатора, моль/м³ (≈ 5 мг/л); кислород там же — насыщение воздухом. */
    public static final double DECARBONIZED_CO2 = 0.11, AIR_SATURATED_O2 = 9e-6;
    /** Кислород после термического деаэратора (10–20 мкг/кг) и после гидразина, кг/кг. */
    public static final double DEAERATED_O2 = 15e-9, HYDRAZINE_O2 = 5e-9;
    /** Пар на деаэратор, доля расхода воды [BALANCE]. */
    public static final double DEAERATOR_STEAM_FRACTION = 0.02;
    /** Цель коррекционной обработки. */
    public static final double DOSED_PH = 9.1;
    /** Реагенты: CaO на моль-экв, коагулянт Al₂(SO₄)₃ на м³, аммиак сверх нейтрализации и избыток гидразина, кг. */
    public static final double LIME_KG_PER_EQ = 0.02804, COAGULANT_KG_M3 = 0.03, NH3_KG_PER_EQ = 0.01703,
            NH3_EXCESS_KG_M3 = 0.5e-3, N2H4_EXCESS_KG_M3 = 0.05e-3;
    /** Порог pK₁ угольной кислоты. */
    private static final double PK1 = 6.35;
    private static final double SECONDS_PER_YEAR = 365.25 * 86_400;

    private static final class Column {
        final ResinType type;
        final double volumeM3;
        double loading, fouling;
        boolean online = true;

        Column(ResinType type, double volumeM3) {
            this.type = type;
            this.volumeM3 = volumeM3;
        }
    }

    private final UtilitiesConfig cfg;
    private final UtilitiesEvents events;
    private final List<Column> columns = new ArrayList<>();
    private final EnumMap<TreatmentStage, WaterQuality> outlets = new EnumMap<>(TreatmentStage.class);
    private WaterQuality inlet = WaterQuality.RAW_RIVER;
    private double flowM3S;
    private boolean liming, coagulation, decarbonizer, deaerator, dosing;
    private double filterCapacityKg, filterLoadKg, hydrogenFraction;
    private double limeKgS, coagulantKgS, ammoniaKgS, hydrazineKgS, deaeratorSteamKgS;

    public WaterTreatmentTrain(UtilitiesConfig cfg, UtilitiesEvents events) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        this.events = Objects.requireNonNull(events, "events");
        evaluate(0);
    }

    /** Колонна с новой смолой ёмкости {@code water.resin_capacity_sac_eq_l}; номер колонны. */
    public int addColumn(ResinType type, double volumeM3) {
        Objects.requireNonNull(type, "type");
        if (!(volumeM3 > 0)) throw new IllegalArgumentException("volumeM3: " + volumeM3);
        columns.add(new Column(type, volumeM3));
        return columns.size() - 1;
    }

    public int columnCount() {
        return columns.size();
    }

    /** Колонна в работе или выведена (регенерация, ремонт). */
    public void setOnline(int col, boolean online) {
        columns.get(col).online = online;
    }

    public void setInlet(WaterQuality q, double flowM3S) {
        this.inlet = Objects.requireNonNull(q, "q");
        if (!(flowM3S >= 0)) throw new IllegalArgumentException("flowM3S: " + flowM3S);
        this.flowM3S = flowM3S;
    }

    public double flowM3S() {
        return flowM3S;
    }

    /** Осветлитель: известкование (CaO) и коагуляция (Al₂(SO₄)₃ + флокулянт). Оба выключены — ступени нет. */
    public void setClarifier(boolean liming, boolean coagulation) {
        this.liming = liming;
        this.coagulation = coagulation;
    }

    /** Песчаный фильтр с грязеёмкостью, кг взвеси до промывки; 0 — фильтра нет. */
    public void setSandFilter(double capacityKg) {
        if (!(capacityKg >= 0)) throw new IllegalArgumentException("capacityKg: " + capacityKg);
        filterCapacityKg = capacityKg;
    }

    /** Обратная промывка фильтра: грязь уходит в дренаж. */
    public void backwashFilter() {
        filterLoadKg = 0;
    }

    /** Загрузка фильтра в долях грязеёмкости (NaN — фильтра нет). */
    public double filterLoadingFrac() {
        return filterCapacityKg > 0 ? filterLoadKg / filterCapacityKg : Double.NaN;
    }

    /** Доля потока через H-группу катионитов, [0, 1]; остальное — через Na-группу. */
    public void setHydrogenFraction(double f) {
        if (!(f >= 0 && f <= 1)) throw new IllegalArgumentException("fraction: " + f);
        hydrogenFraction = f;
    }

    public void setDecarbonizer(boolean on) {
        decarbonizer = on;
    }

    /** Термический деаэратор в работе (пар 07 подан). */
    public void setDeaerator(boolean on) {
        deaerator = on;
    }

    /** Дозирование NH₃ и N₂H₄ ({@code chemical_dosing_skid}). */
    public void setDosing(boolean on) {
        dosing = on;
    }

    /** Шаг SLOW: качество по ступеням, загрузка колонн и фильтра, старение смолы. */
    public void stepSlow(double dtPhysS) {
        if (!(dtPhysS >= 0)) throw new IllegalArgumentException("dt: " + dtPhysS);
        evaluate(dtPhysS);
        double age = cfg.resinFoulingPerYear() * dtPhysS / SECONDS_PER_YEAR;
        for (Column c : columns) c.fouling = Math.min(0.9, c.fouling + age);
    }

    /** Вода после ступени (выключенная ступень повторяет предыдущую). */
    public WaterQuality outlet(TreatmentStage s) {
        return outlets.get(s);
    }

    public ResinState resin(int col) {
        Column c = columns.get(col);
        return new ResinState(c.type, capacityEqM3(), c.loading, c.fouling, c.volumeM3);
    }

    /** Реагент на полную регенерацию колонны, кг ({@link ResinType#regenerantId()}). */
    public double regenerantKg(int col) {
        Column c = columns.get(col);
        return c.loading * workingCapacityEq(c) * c.type.regenerantKgPerEq;
    }

    /** Регенерация: смола снова свежая (старение остаётся); возвращает израсходованный реагент, кг. */
    public double regenerate(int col) {
        double kg = regenerantKg(col);
        columns.get(col).loading = 0;
        return kg;
    }

    /** Расходы реагентов и пара за последний шаг, кг/с. */
    public double limeKgS() {
        return limeKgS;
    }

    public double coagulantKgS() {
        return coagulantKgS;
    }

    public double ammoniaKgS() {
        return ammoniaKgS;
    }

    public double hydrazineKgS() {
        return hydrazineKgS;
    }

    public double deaeratorSteamKgS() {
        return deaeratorSteamKgS;
    }

    /** Состояние колонн и фильтра; состав цепочки (ступени, колонны) задаёт владелец до загрузки. */
    public void save(StateWriter out) {
        int n = columns.size();
        double[] loading = new double[n], fouling = new double[n];
        int[] online = new int[n];
        for (int i = 0; i < n; i++) {
            Column c = columns.get(i);
            loading[i] = c.loading;
            fouling[i] = c.fouling;
            online[i] = c.online ? 1 : 0;
        }
        out.putDoubles("loading", loading, n);
        out.putDoubles("fouling", fouling, n);
        out.putInts("online", online, n);
        out.putDouble("filter_load_kg", filterLoadKg);
    }

    public void load(StateReader in) {
        double[] loading = in.getDoubles("loading"), fouling = in.getDoubles("fouling");
        int[] online = in.getInts("online");
        for (int i = 0; i < Math.min(columns.size(), loading.length); i++) {
            Column c = columns.get(i);
            c.loading = loading[i];
            c.fouling = fouling[i];
            c.online = online[i] != 0;
        }
        filterLoadKg = in.getDouble("filter_load_kg");
    }

    private double capacityEqM3() {
        return cfg.resinCapacitySacEqL() * 1000;
    }

    private double workingCapacityEq(Column c) {
        return capacityEqM3() * c.volumeM3 * (1 - c.fouling);
    }

    private static double leak(double loading) {
        if (loading <= 1) return BASE_LEAK;
        return Math.min(1, BASE_LEAK + (1 - BASE_LEAK) * (loading - 1) / (EXHAUSTED_LOADING - 1));
    }

    /** Качество по ступеням; при {@code dt} &gt; 0 — ещё и загрузка колонн и фильтра. */
    private void evaluate(double dt) {
        double q = flowM3S;
        double h = inlet.hardnessMolEqM3(), alk = inlet.alkalinityMolEqM3(), na = inlet.sodiumKgKg();
        double o2 = inlet.o2KgKg(), ss = inlet.suspendedKgM3(), ph = inlet.ph(), fma = 0;
        double co2 = alk * Math.pow(10, PK1 - ph);
        limeKgS = coagulantKgS = ammoniaKgS = hydrazineKgS = deaeratorSteamKgS = 0;
        outlets.put(TreatmentStage.INLET, inlet);

        if (liming || coagulation) {
            if (liming) {
                double removed = Math.max(0, Math.min(h, alk) - LIMING_RESIDUAL);
                limeKgS = q * (removed + 2 * co2) * LIME_KG_PER_EQ;
                h -= removed;
                alk -= removed;
                co2 = 0;
                ph = LIMED_PH;
            }
            if (coagulation) {
                if (ss > 0) coagulantKgS = q * COAGULANT_KG_M3;
                ss = Math.min(ss, Math.max(ss * COAGULATION_PASS, COAGULATION_FLOOR_KG_M3));
            } else {
                ss *= SETTLING_PASS;
            }
        }
        outlets.put(TreatmentStage.CLARIFIER, quality(h, o2, na, ph, ss, alk, fma));

        if (filterCapacityKg > 0) {
            boolean wasClogged = filterLoadKg >= filterCapacityKg;
            double out = ss * (wasClogged ? CLOGGED_FILTER_PASS : FILTER_PASS);
            filterLoadKg += (ss - out) * q * dt;
            ss = out;
            if (!wasClogged && filterLoadKg >= filterCapacityKg) events.filterClogged();
        }
        outlets.put(TreatmentStage.SAND_FILTER, quality(h, o2, na, ph, ss, alk, fma));

        if (!columns.isEmpty()) {
            double naEq = na * WaterQuality.WATER_DENSITY / WaterQuality.SODIUM_KG_PER_EQ;
            double fH = hydrogenFraction;
            // H-группа: все катионы на H⁺
            double hH = h, naH = naEq, acid = 0;
            int nH = countOnline(ResinType.SAC_H);
            if (fH > 0 && nH > 0) {
                hH = 0;
                naH = 0;
                for (Column c : columns) {
                    if (!c.online || c.type != ResinType.SAC_H) continue;
                    double lk = leak(c.loading);
                    hH += h * lk / nH;
                    naH += naEq * lk / nH;
                    load(c, q * fH / nH * (h + naEq) * (1 - lk), dt);
                }
                acid = (h - hH) + (naEq - naH);
            }
            double alkH = Math.max(0, alk - acid), fmaH = Math.max(0, acid - alk), co2H = co2 + Math.min(alk, acid);
            // Na-группа: жёсткость на Na⁺
            double hNa = h, naNa = naEq;
            int nNa = countOnline(ResinType.SAC_NA);
            if (fH < 1 && nNa > 0) {
                hNa = 0;
                for (Column c : columns) {
                    if (!c.online || c.type != ResinType.SAC_NA) continue;
                    double lk = leak(c.loading);
                    hNa += h * lk / nNa;
                    load(c, q * (1 - fH) / nNa * h * (1 - lk), dt);
                }
                naNa = naEq + (h - hNa);
            }
            h = fH * hH + (1 - fH) * hNa;
            naEq = fH * naH + (1 - fH) * naNa;
            double alkNa = alk;
            double net = (1 - fH) * alkNa + fH * alkH - fH * fmaH;
            co2 = fH * co2H + (1 - fH) * co2 + Math.min(fH * fmaH, (1 - fH) * alkNa);
            alk = Math.max(0, net);
            fma = Math.max(0, -net);
            na = naEq * WaterQuality.SODIUM_KG_PER_EQ / WaterQuality.WATER_DENSITY;
            ph = phOf(alk, co2, fma);
        }
        outlets.put(TreatmentStage.CATION, quality(h, o2, na, ph, ss, alk, fma));

        if (decarbonizer) {
            co2 = Math.min(co2, DECARBONIZED_CO2);
            o2 = Math.max(o2, AIR_SATURATED_O2);
            ph = phOf(alk, co2, fma);
        }
        outlets.put(TreatmentStage.DECARBONIZER, quality(h, o2, na, ph, ss, alk, fma));

        if (deaerator) {
            deaeratorSteamKgS = q * WaterQuality.WATER_DENSITY * DEAERATOR_STEAM_FRACTION;
            o2 = Math.min(o2, DEAERATED_O2);
            co2 = 0;
            ph = phOf(alk, co2, fma);
        }
        outlets.put(TreatmentStage.DEAERATOR, quality(h, o2, na, ph, ss, alk, fma));

        if (dosing) {
            if (ph < DOSED_PH) {
                ammoniaKgS = q * (fma * NH3_KG_PER_EQ + NH3_EXCESS_KG_M3);
                fma = 0;
                ph = DOSED_PH;
            }
            double target = Math.min(o2, HYDRAZINE_O2);
            hydrazineKgS = q * ((o2 - target) * WaterQuality.WATER_DENSITY + N2H4_EXCESS_KG_M3);
            o2 = target;
        }
        outlets.put(TreatmentStage.DOSING, quality(h, o2, na, ph, ss, alk, fma));
    }

    private int countOnline(ResinType t) {
        int n = 0;
        for (Column c : columns) if (c.online && c.type == t) n++;
        return n;
    }

    /** Поглощение {@code eqPerS} моль-экв/с за шаг; событие проскока при переходе рабочей ёмкости. */
    private void load(Column c, double eqPerS, double dt) {
        if (!(dt > 0) || !(eqPerS > 0)) return;
        double before = c.loading;
        c.loading = Math.min(EXHAUSTED_LOADING, c.loading + eqPerS * dt / workingCapacityEq(c));
        if (before < 1 && c.loading >= 1) events.resinBreakthrough(columns.indexOf(c));
    }

    /** pH по минеральной кислотности или по паре HCO₃⁻/CO₂; без обоих — нейтральная вода. */
    private static double phOf(double alk, double co2, double fma) {
        if (fma > 0) return Math.max(0, -Math.log10(fma / 1000));
        if (alk > 0 && co2 > 1e-9) return Math.max(4, Math.min(10.5, PK1 + Math.log10(alk / co2)));
        if (alk > 0) return 8.3;
        return 7.0;
    }

    private WaterQuality quality(double h, double o2, double na, double ph, double ss, double alk, double fma) {
        return new WaterQuality(h, o2, inlet.chlorideKgKg(), inlet.silicaKgKg(), na,
                WaterQuality.estimateConductivity(h, na, fma), ph, ss, alk);
    }
}
