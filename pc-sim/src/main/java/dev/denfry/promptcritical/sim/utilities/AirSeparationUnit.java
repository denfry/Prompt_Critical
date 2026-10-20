package dev.denfry.promptcritical.sim.utilities;

import java.util.Objects;

/**
 * Воздухоразделительная установка (08 §8.11), SLOW: лумпед «бинарная + аргоновая фракция» и тепловой баланс
 * холодного блока.
 *
 * <p>Блок — одна масса C с теплопритоком UA·(T_нар − T); детандер даёт холод η·W_эфф. Пока блок теплее
 * {@link #RUNNING_K}, весь холод идёт на захолаживание; дальше температура держится, а холод уходит в продукт.
 * UA таково, что холода хватает на удержание {@link #RUNNING_K} ровно от нагрузки {@link #MIN_LOAD}, C — чтобы
 * при номинальном компрессоре блок захолаживался от {@link #REFERENCE_AMBIENT_K} за
 * {@code asu.cooldown_hours_phys}. Компрессор без
 * охлаждающей воды (концевые холодильники, ΔT {@link #COOLING_WATER_DT}) работает с долей мощности cw/cw_треб.
 *
 * <p>Продукт по мощности: O₂ = W_эфф/e_O₂ (2 МВт / 0,42 кВт·ч/нм³ = 4760 нм³/ч), воздух = O₂/(0,2095·извлечение),
 * N₂ и Ar — попутно. Чистота O₂ падает при нагрузке ниже 70 %, ниже {@link #MIN_LOAD} колонна не держит флегму.
 * Доля жидкого продукта {@link #setLiquidFraction} забирает мощность по {@link #LIQUID_KWH_PER_KG}.
 *
 * <p>Адсорберы (два, цикл {@link #MOLSIEVE_CYCLE_S}): рабочий копит H₂O + CO₂ воздуха, второй регенерируется
 * нагревом, пока включён нагреватель. Если к концу цикла второй не готов, рабочий остаётся и после ёмкости
 * пропускает CO₂ и влагу — лёд в теплообменнике ({@link AsuState#ICED}) — и ацетилен. Ацетилен копится в LOX
 * куба и уходит с продувкой LOX; выше {@code asu.lox_hydrocarbon_trip_ppm} — останов (если защита не выведена),
 * выше предела растворимости {@link #HYDROCARBON_SOLUBILITY_PPM} — взрыв.
 */
public final class AirSeparationUnit {
    public static final double REFERENCE_AMBIENT_K = 300, RUNNING_K = 95;
    /** Доля мощности компрессора, которую детандер отдаёт холодом [BALANCE]. */
    public static final double REFRIGERATION_FRACTION = 0.05;
    /** Нагрев воды концевых холодильников, К. */
    public static final double COOLING_WATER_DT = 10;
    public static final double MIN_LOAD = 0.3, FULL_PURITY_LOAD = 0.7;
    public static final double O2_PURITY = 0.995, LOW_LOAD_PURITY = 0.95;
    public static final double AIR_O2 = 0.2095, AIR_N2 = 0.7808, AIR_AR = 0.00934;
    /** Извлечение O₂, доля N₂ воздуха в продукт, извлечение Ar (50–80 %). */
    public static final double O2_RECOVERY = 0.9, N2_PRODUCT_FRACTION = 0.5, AR_RECOVERY = 0.65;
    /** Плотности при нормальных условиях, кг/нм³. */
    public static final double RHO_O2 = 1.429, RHO_N2 = 1.2506, RHO_AR = 1.784, RHO_AIR = 1.293;
    /** Энергия ожижения, кВт·ч/кг (0,6–1,0) [сверить]. */
    public static final double LIQUID_KWH_PER_KG = 0.8;
    /** H₂O + CO₂ в воздухе после предохлаждения, кг/кг [BALANCE]. */
    public static final double CONTAMINANT_KG_PER_KG = 0.003;
    public static final double MOLSIEVE_CYCLE_S = 4 * 3600, MOLSIEVE_REGEN_S = 3 * 3600, MOLSIEVE_MARGIN = 1.25;
    /** Улавливание ацетилена исправным адсорбером. */
    public static final double MOLSIEVE_C2H2_REMOVAL = 0.999;
    /** Лёд, при котором теплообменник забит, кг [BALANCE]. */
    public static final double ICE_LIMIT_KG = 200;
    /** Продувка LOX из куба, доля продукционного O₂ по массе [BALANCE]. */
    public static final double LOX_PURGE_FRACTION = 0.005;
    /** Предел растворимости C₂H₂ в LOX (4–6 ppm). */
    public static final double HYDROCARBON_SOLUBILITY_PPM = 5;
    /**
     * Взрыв: горит ацетилен и алюминиевая насадка в кислороде (≈ 35 МДж на кг O₂); доля LOX куба, которая успевает
     * прореагировать, и доля энергии в ударную волну [BALANCE].
     */
    public static final double C2H2_LHV = 48.2e6, ALUMINIUM_J_PER_KG_O2 = 35e6, LOX_REACTING_FRACTION = 0.05,
            BLAST_FRACTION = 0.1;
    private static final double M_C2H2 = 0.02604, M_O2 = 0.032, NORMAL_MOLAR_VOLUME = 0.022414;

    private final AsuSpec spec;
    private final UtilitiesConfig cfg;
    private final UtilitiesEvents events;
    private final double leakWK, heatCapacityJK, molsieveCapacityKg;
    private AsuState state = AsuState.OFF;
    private double tempK = REFERENCE_AMBIENT_K, ambientK = REFERENCE_AMBIENT_K;
    private double inletC2h2Ppm, liquidFraction;
    private boolean liquidOxygen = true, regenHeater = true, hydrocarbonTrip = true;
    private double bedLoading, spareLoading, cycleS, regenS, iceKg, hydrocarbonKg;
    private boolean breakthrough, alarmed;
    private double o2Nm3S, n2Nm3S, arNm3S, liquidKgS, purity, explosionJ;

    public AirSeparationUnit(AsuSpec spec, UtilitiesConfig cfg, UtilitiesEvents events) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        this.events = Objects.requireNonNull(events, "events");
        double refrigeration = REFRIGERATION_FRACTION * spec.ratedCompressorW();
        this.leakWK = refrigeration * MIN_LOAD / (REFERENCE_AMBIENT_K - RUNNING_K);
        double eqRated = REFERENCE_AMBIENT_K - refrigeration / leakWK;
        double ln = Math.log((REFERENCE_AMBIENT_K - eqRated) / (RUNNING_K - eqRated));
        this.heatCapacityJK = cfg.asuCooldownHoursPhys() * 3600 * leakWK / ln;
        this.molsieveCapacityKg = ratedAirKgS() * CONTAMINANT_KG_PER_KG * MOLSIEVE_CYCLE_S * MOLSIEVE_MARGIN;
    }

    public AsuSpec spec() {
        return spec;
    }

    /** Воздух при номинальном компрессоре, кг/с. */
    private double ratedAirKgS() {
        return o2For(spec.ratedCompressorW()) / (AIR_O2 * O2_RECOVERY) * RHO_AIR;
    }

    private double o2For(double compressorW) {
        return compressorW / (cfg.asuSpecificEnergyKwhNm3() * 3.6e6);
    }

    public void setAmbient(double kelvin) {
        ambientK = kelvin;
    }

    /** Ацетилен в забираемом воздухе, ppm (об.): у факелов и коксохима — десятые и больше. */
    public void setInletHydrocarbons(double c2h2Ppm) {
        if (!(c2h2Ppm >= 0)) throw new IllegalArgumentException("ppm: " + c2h2Ppm);
        inletC2h2Ppm = c2h2Ppm;
    }

    /** Доля мощности на жидкий продукт [0; 0,5] и какой продукт ожижается (LOX или LIN). */
    public void setLiquidFraction(double f, boolean oxygen) {
        if (!(f >= 0 && f <= 0.5)) throw new IllegalArgumentException("liquid fraction: " + f);
        liquidFraction = f;
        liquidOxygen = oxygen;
    }

    /** Нагреватель регенерации адсорберов. */
    public void setRegenerationHeater(boolean on) {
        regenHeater = on;
    }

    /** Защита по углеводородам в LOX (ПАЗ); выведенная — путь к взрыву. */
    public void setHydrocarbonTrip(boolean enabled) {
        hydrocarbonTrip = enabled;
    }

    /** Слив LOX из куба: углеводороды уходят; останов по ним можно сбросить. */
    public void drainLox() {
        hydrocarbonKg = 0;
        alarmed = false;
        if (state == AsuState.TRIPPED) changeState(AsuState.OFF);
    }

    /**
     * Шаг SLOW.
     *
     * @param compressorW мощность на валу основного компрессора, Вт
     * @param cwKgS       охлаждающая вода концевых холодильников, кг/с
     */
    public void stepSlow(double dt, double compressorW, double cwKgS) {
        o2Nm3S = n2Nm3S = arNm3S = liquidKgS = purity = 0;
        if (state == AsuState.DESTROYED || !(dt > 0)) return;
        double cwNeed = compressorW / (4186 * COOLING_WATER_DT);
        double w = compressorW > 0 ? compressorW * Math.min(1, cwNeed > 0 ? cwKgS / cwNeed : 1) : 0;
        boolean blocked = state == AsuState.TRIPPED || state == AsuState.ICED;
        double weff = blocked ? 0 : Math.max(0, w);

        // холодный блок: точное решение линейного уравнения на шаге, удержание на рабочей температуре
        double eq = ambientK - REFRIGERATION_FRACTION * weff / leakWK;
        double next = eq + (tempK - eq) * Math.exp(-dt * leakWK / heatCapacityJK);
        boolean cold = next <= RUNNING_K;
        tempK = cold ? RUNNING_K : next;
        if (state == AsuState.ICED && tempK > 273.15) {
            iceKg = 0;
            changeState(AsuState.OFF);
        }

        double load = weff / spec.ratedCompressorW();
        boolean producing = !blocked && cold && load >= MIN_LOAD;
        double airKgS = producing ? o2For(weff) / (AIR_O2 * O2_RECOVERY) * RHO_AIR : 0;
        if (producing) {
            double liquidW = liquidFraction * weff;
            o2Nm3S = o2For(weff - (liquidOxygen ? liquidW : 0));
            double airNm3S = airKgS / RHO_AIR;
            n2Nm3S = airNm3S * AIR_N2 * N2_PRODUCT_FRACTION - (liquidOxygen ? 0 : liquidW / (LIQUID_KWH_PER_KG * 3.6e6) / RHO_N2);
            n2Nm3S = Math.max(0, n2Nm3S);
            arNm3S = spec.argonColumn() ? airNm3S * AIR_AR * AR_RECOVERY : 0;
            liquidKgS = liquidW / (LIQUID_KWH_PER_KG * 3.6e6);
            purity = load >= FULL_PURITY_LOAD ? O2_PURITY
                    : LOW_LOAD_PURITY + (O2_PURITY - LOW_LOAD_PURITY) * (load - MIN_LOAD) / (FULL_PURITY_LOAD - MIN_LOAD);
        }

        stepMolsieve(dt, airKgS);
        stepHydrocarbons(dt, airKgS);
        if (state == AsuState.DESTROYED) {
            o2Nm3S = n2Nm3S = arNm3S = liquidKgS = purity = 0;
            return;
        }
        if (state == AsuState.TRIPPED || state == AsuState.ICED) {
            o2Nm3S = n2Nm3S = arNm3S = liquidKgS = purity = 0;
            return;
        }
        changeState(weff <= 0 ? AsuState.OFF : producing ? AsuState.RUNNING : AsuState.COOLDOWN);
    }

    private void stepMolsieve(double dt, double airKgS) {
        if (regenHeater && spareLoading > 0) {
            regenS += dt;
            if (regenS >= MOLSIEVE_REGEN_S) spareLoading = 0;
        }
        if (airKgS <= 0) return;
        bedLoading += airKgS * CONTAMINANT_KG_PER_KG * dt / molsieveCapacityKg;
        cycleS += dt;
        if (cycleS >= MOLSIEVE_CYCLE_S && spareLoading == 0) {
            spareLoading = bedLoading;
            bedLoading = 0;
            regenS = 0;
            cycleS = 0;
            breakthrough = false;
        }
        if (bedLoading >= 1) {
            if (!breakthrough) {
                breakthrough = true;
                events.asuMolsieveBreakthrough();
            }
            iceKg += airKgS * CONTAMINANT_KG_PER_KG * dt;
            if (iceKg >= ICE_LIMIT_KG) changeState(AsuState.ICED);
        }
    }

    private void stepHydrocarbons(double dt, double airKgS) {
        double removal = breakthrough ? 0 : MOLSIEVE_C2H2_REMOVAL;
        double inMolS = airKgS / RHO_AIR / NORMAL_MOLAR_VOLUME * inletC2h2Ppm * 1e-6 * (1 - removal);
        double purgeKgS = LOX_PURGE_FRACTION * o2Nm3S * RHO_O2 + (liquidOxygen ? liquidKgS : 0);
        hydrocarbonKg = (hydrocarbonKg + inMolS * M_C2H2 * dt) / (1 + dt * purgeKgS / spec.loxHoldupKg());
        double ppm = loxHydrocarbonsPpm();
        if (ppm >= HYDROCARBON_SOLUBILITY_PPM) {
            explosionJ = BLAST_FRACTION
                    * (hydrocarbonKg * C2H2_LHV + spec.loxHoldupKg() * LOX_REACTING_FRACTION * ALUMINIUM_J_PER_KG_O2);
            changeState(AsuState.DESTROYED);
            events.asuExploded(explosionJ);
            return;
        }
        if (ppm >= cfg.asuLoxHydrocarbonTripPpm()) {
            if (!alarmed) {
                alarmed = true;
                events.asuHydrocarbonHigh(ppm);
            }
            if (hydrocarbonTrip && state != AsuState.TRIPPED) changeState(AsuState.TRIPPED);
        } else {
            alarmed = false;
        }
    }

    private void changeState(AsuState to) {
        if (to == state) return;
        AsuState from = state;
        state = to;
        events.asuStateChanged(from, to);
    }

    public AsuState state() {
        return state;
    }

    public double coldBoxK() {
        return tempK;
    }

    /** Продукт за последний шаг, кг/с: {@code oxygen}, {@code nitrogen}, {@code argon}, {@code liquid_oxygen}, {@code liquid_nitrogen}. */
    public double productKgS(String fluidId) {
        return switch (fluidId) {
            case "oxygen" -> o2Nm3S * RHO_O2;
            case "nitrogen" -> n2Nm3S * RHO_N2;
            case "argon" -> arNm3S * RHO_AR;
            case "liquid_oxygen" -> liquidOxygen ? liquidKgS : 0;
            case "liquid_nitrogen" -> liquidOxygen ? 0 : liquidKgS;
            default -> 0;
        };
    }

    /** Газообразный O₂, нм³/с. */
    public double oxygenNm3S() {
        return o2Nm3S;
    }

    public double oxygenPurity() {
        return purity;
    }

    /** Ацетилен в LOX куба, ppm (мол.). */
    public double loxHydrocarbonsPpm() {
        return hydrocarbonKg / M_C2H2 / (spec.loxHoldupKg() / M_O2) * 1e6;
    }

    /** Загрузка рабочего адсорбера, доля ёмкости. */
    public double molsieveLoading() {
        return bedLoading;
    }

    public double iceKg() {
        return iceKg;
    }

    /** Энергия ударной волны взрыва конденсатора-испарителя, Дж (0 — не было). */
    public double explosionEnergyJ() {
        return explosionJ;
    }
}
