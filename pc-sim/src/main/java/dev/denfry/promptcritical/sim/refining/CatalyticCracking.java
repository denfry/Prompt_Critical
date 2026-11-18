package dev.denfry.promptcritical.sim.refining;

/**
 * Каталитический крекинг в лифт-реакторе с регенератором кипящего слоя (FCC, GDD 09 §9.9.5; прототип — Г-43-107 / UOP
 * side-by-side): вакуумный газойль → бензин, газ, лёгкий и тяжёлый газойли, кокс на катализаторе, который сгорает в
 * регенераторе и греет катализатор для райзера.
 *
 * <p><b>Кинетика</b> — трёхламповая модель Уикмана (Weekman &amp; Nace, AIChE J. 16, 1970): газойль y₁ → бензин y₂ →
 * газ + кокс y₃,
 * <pre>dy₁/dt = −k₀·y₁²·φ,  dy₂/dt = (s·k₀·y₁² − k₂·y₂)·φ,  φ = e^{−αt},
 * k₀ = k₀*·(C/O)/6·exp(−E₀/R·(1/T − 1/793)),  k₂ = k₂*·exp(−E₂/R·(1/T − 1/793)),</pre>
 * k₀* = 1,157 с⁻¹, s = 0,75, k₂* = 0,05 с⁻¹, α = 0,1 с⁻¹, E₀ = 57, E₂ = 49 кДж/моль (§9.9.5 [BALANCE]; тест T9.32:
 * 520 °C, C/O = 6, 3 с — конверсия 0,750, бензин 0,513; C/O = 8 — 0,800 и 0,545). Замена τ = (1 − e^{−αt})/α сводит
 * систему к dy/dτ без φ; y₁ — аналитически, y₂ — РК4 по τ. Активность равновесного катализатора умножает k₀ и k₂
 * относительно {@value #ACTIVITY_REF} — активности, на которой калибровка §9.9.5 (катализатор в работе, не свежий).
 *
 * <p><b>Кокс</b> (§9.9.5): кокс = 0,2·y₃ + 0,6·CCR сырья, остальное y₃ — газ (сухой газ 20 %, пропан-пропилен 45 %,
 * бутан-бутилен 35 % по массе). Непревращённый газойль делится на лёгкий газойль (доля {@value #LCO_SHARE}) и тяжёлый
 * ({@code decant_oil}) [BALANCE: типичные выходы при конверсии 75 %, Sadeghbeigi, «Fluid Catalytic Cracking Handbook»,
 * гл. 1: ЛГ 15–20 %, тяжёлый 5–10 %]. Пропилен — {@value #PROPYLENE_IN_PP} пропан-пропиленовой фракции (там же:
 * 70–75 %).
 *
 * <p><b>Тепловой баланс</b> (§9.9.5, тест T9.33): регенерированный катализатор несёт в райзер теплоту на нагрев,
 * испарение и эндотермический крекинг сырья
 * <pre>ṁ_кат·c_p,кат·(T_рег − T_р) = ṁ_с·[c_p,L(T_b − T_с) + Δh_исп + c_p,V(T_р − T_b) + Δh_кр],</pre>
 * а в регенераторе сгорает кокс: ṁ_к·(ΔH_с − n_возд·c_p,возд·(T_рег − T_возд)) = ṁ_кат·c_p,кат·(T_рег − T_р) + Q_пот
 * (Q_пот = 0 [допущение, как в примере §9.9.5]). Пример: 10 кг/с сырья при 250 °C, райзер 520 °C, C/O = 6 →
 * T_рег = 712 °C, кокс 5,1 % на сырьё.
 *
 * <p><b>Динамика.</b> Игрок задаёт температуру выхода райзера; шиберная задвижка регенерированного катализатора
 * подбирает C/O (до {@value #C2O_MAX}). Регенератор — тепловая ёмкость запаса катализатора; кокс на нём копится, если
 * воздуха не хватает, и тормозит крекинг: a_кокс = exp(−CRC/{@value #CRC_REF}) [BALANCE: 0,2 % кокса на
 * регенерированном катализаторе — активность ×0,37]. Тяжёлое сырьё → больше кокса → выше T_рег → ниже C/O → ниже
 * конверсия — самоограничивающая связь FCC. После потери воздуха накопленный кокс выгорает разом — T_рег выше
 * {@value #AFTERBURN_C} °C: догорание CO ({@code FCC_AFTERBURN}). Перепад на шиберной задвижке падает, когда нет
 * воздуха (регенератор теряет давление): ниже {@value #SLIDE_DP_MIN_MPA} МПа ПАЗ закрывает задвижки
 * ({@code FCC_SLIDE_VALVE_DP_LOW}).
 *
 * <p><b>Катализатор.</b> Равновесная активность падает от гидротермальной дезактивации и металлов сырья
 * da/dt = −{@value #DEACTIVATION_PER_S}·a [BALANCE: при подпитке 0,4 кг на тонну сырья равновесная активность ≈ 0,7, §9.9.5
 * «0,3–0,5 кг/т»]; подпитка свежим (a = 1) замещает равную массу равновесного.
 */
public final class CatalyticCracking {
    public static final double K0_STAR = 1.157, SELECTIVITY = 0.75, K2_STAR = 0.05, ALPHA = 0.1, E0_J_MOL = 57e3, E2_J_MOL = 49e3;
    public static final double T_REF_K = 793, R = 8.314, CONTACT_S = 3, ACTIVITY_REF = 0.7;
    public static final double COKE_FROM_Y3 = 0.2, COKE_FROM_CCR = 0.6, DRY_GAS = 0.20, PP = 0.45, BB = 0.35;
    public static final double LCO_SHARE = 0.68, PROPYLENE_IN_PP = 0.72;
    /** Сырьё и тепло §9.9.5: жидкость 2,5 кДж/(кг·К) до T_b 380 °C, испарение 0,2 МДж/кг, пар 2,8 кДж/(кг·К), крекинг 0,35 МДж/кг. */
    public static final double CP_LIQ = 2.5e3, T_BOIL_K = 380 + 273.15, H_VAP = 0.2e6, CP_VAP = 2.8e3, H_CRACK = 0.35e6;
    /** Катализатор 1,1 кДж/(кг·К); кокс 32 МДж/кг, воздух 13 кг на кг кокса при c_p 1,1 кДж/(кг·К) от 200 °C. */
    public static final double CP_CAT = 1.1e3, H_COKE = 32e6, AIR_PER_COKE = 13, CP_AIR = 1.097e3, T_AIR_K = 200 + 273.15;
    public static final double C2O_MAX = 10, CRC_REF = 0.002, AFTERBURN_C = 760, SLIDE_DP_MIN_MPA = 0.02, SLIDE_DP_NOMINAL_MPA = 0.05;
    /** Запас катализатора: 20 т на 10 кг/с сырья (§9.9.5) — 2000 кг на кг/с. */
    public static final double INVENTORY_PER_KGS = 2000;
    public static final double DEACTIVATION_PER_S = 8.6e-8, MAX_SUBSTEP_S = 5;

    // ------------------------------------------------------------------ кинетика

    /**
     * @param conversion доля превращённого газойля
     * @param gasoline   бензин, доля по массе
     * @param gasCoke    газ + кокс (y₃)
     */
    public record Kinetics(double conversion, double gasoline, double gasCoke) {
    }

    public static Kinetics weekman(double tK, double catToOil, double contactS, double activity) {
        double a = Math.max(0, activity) / ACTIVITY_REF;
        double k0 = K0_STAR * catToOil / 6 * Math.exp(-E0_J_MOL / R * (1 / tK - 1 / T_REF_K)) * a;
        double k2 = K2_STAR * Math.exp(-E2_J_MOL / R * (1 / tK - 1 / T_REF_K)) * a;
        double tau = (1 - Math.exp(-ALPHA * contactS)) / ALPHA;
        int n = 64;
        double h = tau / n, y2 = 0, t = 0;
        for (int i = 0; i < n; i++) {
            double k1 = dy2(k0, k2, t, y2), k2b = dy2(k0, k2, t + h / 2, y2 + h / 2 * k1);
            double k3 = dy2(k0, k2, t + h / 2, y2 + h / 2 * k2b), k4 = dy2(k0, k2, t + h, y2 + h * k3);
            y2 += h / 6 * (k1 + 2 * k2b + 2 * k3 + k4);
            t += h;
        }
        double y1 = 1 / (1 + k0 * tau);
        return new Kinetics(1 - y1, y2, Math.max(0, 1 - y1 - y2));
    }

    private static double dy2(double k0, double k2, double tau, double y2) {
        double y1 = 1 / (1 + k0 * tau);
        return SELECTIVITY * k0 * y1 * y1 - k2 * y2;
    }

    /**
     * Выходы по массе на сырьё.
     */
    public record Yields(double gasoline, double dryGas, double propanePropylene, double butaneButylene, double lightCycleOil,
            double decantOil, double coke, double conversion) {
    }

    public static Yields yields(Kinetics k, double ccr) {
        double y3 = k.gasCoke();
        double coke = Math.min(y3 + (1 - k.conversion()), COKE_FROM_Y3 * y3 + COKE_FROM_CCR * ccr);
        double gas = Math.max(0, y3 - coke);
        // кокс сверх y₃ (из смол Конрадсона) — за счёт непревращённого газойля
        double unconverted = Math.max(0, 1 - k.conversion() - Math.max(0, coke - y3));
        return new Yields(k.gasoline(), DRY_GAS * gas, PP * gas, BB * gas, LCO_SHARE * unconverted, (1 - LCO_SHARE) * unconverted,
                coke, k.conversion());
    }

    // ------------------------------------------------------------------ тепловой баланс

    /** Тепло на кг сырья от температуры подачи до выхода райзера, Дж/кг (§9.9.5: 1,27 МДж/кг в примере). */
    public static double feedDutyJPerKg(double feedK, double riserK) {
        return CP_LIQ * Math.max(0, T_BOIL_K - feedK) + H_VAP + CP_VAP * (riserK - T_BOIL_K) + H_CRACK;
    }

    /** Чистая теплота сгорания кокса с нагревом воздуха до T_рег, Дж/кг. */
    public static double netCokeHeat(double regenK) {
        return H_COKE - AIR_PER_COKE * CP_AIR * (regenK - T_AIR_K);
    }

    /**
     * @param regenK       температура регенератора, К
     * @param cokeKgPerKg  кокс, который надо сжечь, на кг сырья
     * @param deltaCoke    прирост кокса на катализаторе за проход, доля по массе
     */
    public record HeatBalance(double regenK, double cokeKgPerKg, double deltaCoke) {
    }

    /** Установившийся тепловой баланс при заданном C/O (пример §9.9.5, тест T9.33). */
    public static HeatBalance steadyHeatBalance(double feedK, double riserK, double catToOil) {
        double duty = feedDutyJPerKg(feedK, riserK);
        double regenK = riserK + duty / (catToOil * CP_CAT);
        double coke = catToOil * CP_CAT * (regenK - riserK) / netCokeHeat(regenK);
        return new HeatBalance(regenK, coke, coke / catToOil);
    }

    // ------------------------------------------------------------------ динамика установки

    private double regenK = 700 + 273.15, cokeOnCatKg, inventoryKg, activity = 1;
    private boolean tripped, circulating;

    /**
     * Шаг установки.
     *
     * @param cokeMadeKgS кокс за шаг, кг/с
     * @param cokeBurnKgS сожжённый кокс, кг/с
     * @param catKgS      циркуляция катализатора, кг/с
     * @param slideDpMpa  перепад на задвижке регенерированного катализатора
     */
    public record Step(Yields yields, double riserK, double regenK, double catToOil, double cokeMadeKgS, double cokeBurnKgS,
            double catKgS, double slideDpMpa, double crc, boolean afterburn, boolean trip) {
    }

    public double regenK() {
        return regenK;
    }

    public double activity() {
        return activity;
    }

    public double inventoryKg() {
        return inventoryKg;
    }

    public double cokeOnCatKg() {
        return cokeOnCatKg;
    }

    /** Кокс на регенерированном катализаторе, доля по массе. */
    public double crc() {
        return inventoryKg > 0 ? cokeOnCatKg / inventoryKg : 0;
    }

    public boolean tripped() {
        return tripped;
    }

    /** Сброс ПАЗ: задвижки снова открываются (только при достаточном перепаде — проверит следующий шаг). */
    public void resetTrip() {
        tripped = false;
    }

    public void restore(double regenK, double cokeOnCatKg, double inventoryKg, double activity, boolean tripped) {
        this.regenK = regenK;
        this.cokeOnCatKg = Math.max(0, cokeOnCatKg);
        this.inventoryKg = Math.max(0, inventoryKg);
        this.activity = Math.clamp(activity, 0, 1);
        this.tripped = tripped;
        this.circulating = !tripped;
    }

    /** Загрузить {@code kg} свежего катализатора (a = 1); см. {@link #addCatalyst}. */
    public double addFreshCatalyst(double kg, double fullKg) {
        return addCatalyst(kg, 1, fullKg);
    }

    /**
     * Загрузить {@code kg} катализатора активности {@code activityIn}: сначала до запаса {@code fullKg}, затем — подпитка,
     * замещающая столько же равновесного (его выводят из регенератора). Возвращает выведенную массу, кг, — она уходит
     * с активностью и коксом, какие были у равновесного до подпитки.
     */
    public double addCatalyst(double kg, double activityIn, double fullKg) {
        if (kg <= 0) return 0;
        double a = Math.clamp(activityIn, 0, 1);
        double fill = Math.min(kg, Math.max(0, fullKg - inventoryKg));
        if (fill > 0) {
            activity = (activity * inventoryKg + a * fill) / (inventoryKg + fill);
            inventoryKg += fill;
        }
        double makeup = kg - fill;
        if (makeup > 0 && inventoryKg > 0) {
            double x = Math.min(1, makeup / inventoryKg);
            activity = activity * (1 - x) + a * x;
            cokeOnCatKg *= 1 - x;
            return makeup;
        }
        return 0;
    }

    /**
     * Шаг {@code dt} физических секунд.
     *
     * @param feedKgS     газойль, кг/с (0 — сырья нет)
     * @param feedK       температура подачи сырья
     * @param ccr         коксуемость сырья, доля
     * @param riserSetK   уставка температуры выхода райзера
     * @param airKgS      воздух в регенератор, кг/с
     * @param airDesignKgS расчётный воздух (для перепада на задвижке)
     * @param fullKg      расчётный запас катализатора, кг
     */
    public Step step(double dt, double feedKgS, double feedK, double ccr, double riserSetK, double airKgS, double airDesignKgS,
            double fullKg) {
        // запас регенератора — явный Эйлер: шаг не длиннее MAX_SUBSTEP_S, иначе тепловой баланс расходится
        int n = Math.max(1, (int) Math.ceil(dt / MAX_SUBSTEP_S));
        Step s = null;
        for (int i = 0; i < n; i++) s = stepOnce(dt / n, feedKgS, feedK, ccr, riserSetK, airKgS, airDesignKgS, fullKg);
        return s;
    }

    private Step stepOnce(double dt, double feedKgS, double feedK, double ccr, double riserSetK, double airKgS, double airDesignKgS,
            double fullKg) {
        double dp = airDesignKgS > 0 ? SLIDE_DP_NOMINAL_MPA * Math.min(1.2, airKgS / airDesignKgS) : 0;
        boolean charged = inventoryKg >= 0.5 * fullKg;
        // ПАЗ срабатывает на работающей установке; без перепада она просто не пускается (разрешающее условие пуска)
        if (circulating && dp < SLIDE_DP_MIN_MPA) tripped = true;
        boolean circulate = feedKgS > 0 && charged && !tripped && dp >= SLIDE_DP_MIN_MPA;
        circulating = circulate;
        double c2o = 0, riserK = riserSetK, catKgS = 0;
        Yields y = new Yields(0, 0, 0, 0, 0, 0, 0, 0);
        double made = 0;
        if (circulate) {
            double duty = feedDutyJPerKg(feedK, riserSetK);
            c2o = regenK - riserSetK > 1 ? duty / (CP_CAT * (regenK - riserSetK)) : C2O_MAX;
            if (c2o > C2O_MAX) {
                c2o = C2O_MAX;
                // задвижка открыта полностью: выход райзера ниже уставки — решаем баланс относительно T_р
                double fixed = CP_LIQ * Math.max(0, T_BOIL_K - feedK) + H_VAP - CP_VAP * T_BOIL_K + H_CRACK;
                riserK = (c2o * CP_CAT * regenK - fixed) / (c2o * CP_CAT + CP_VAP);
            }
            catKgS = c2o * feedKgS;
            double a = activity * Math.exp(-crc() / CRC_REF);
            y = yields(weekman(riserK, c2o, CONTACT_S, a), ccr);
            made = y.coke() * feedKgS;
            activity -= DEACTIVATION_PER_S * activity * dt;
        }
        cokeOnCatKg += made * dt;
        double burn = Math.min(cokeOnCatKg / Math.max(dt, 1e-9), Math.max(0, airKgS) / AIR_PER_COKE);
        cokeOnCatKg -= burn * dt;
        double heatCap = Math.max(inventoryKg, 1) * CP_CAT;
        double q = burn * H_COKE - Math.max(0, airKgS) * CP_AIR * (regenK - T_AIR_K) - catKgS * CP_CAT * (regenK - riserK);
        regenK += q * dt / heatCap;
        regenK = Math.max(T_AIR_K - 200, regenK);
        boolean afterburn = regenK > AFTERBURN_C + 273.15;
        return new Step(y, riserK, regenK, c2o, made, burn, catKgS, dp, crc(), afterburn, tripped);
    }
}
