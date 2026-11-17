package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Дуговая сталеплавильная печь ДСП-6 на 6 кВ: плавка стального лома дугой (07 §7.6.5, 08).
 *
 * <p>Электрика — схема замещения на фазу: напряжение вторичной обмотки печного трансформатора V (230 В линейное),
 * реактивность короткой сети X = 8 мОм, дуга — активное сопротивление R. P = 3·V²·R/(R² + X²), Q = 3·V²·X/(R² + X²).
 * Регулятор электродов держит R = 1,33·X (cos φ 0,8, почти максимум мощности, ≈ 3,2 МВт). Дуга неустойчива: на
 * проплавлении колодцев, пока лом не осел, R скачет на ±30 %, при расплавлении — ±12 %, на жидкой ванне — ±3 %.
 * Эти скачки — фликер: свет у соседних потребителей мигает.
 *
 * <p>Тепло — по энтальпии шихты от 25 °C: твёрдая сталь c 690 Дж/(кг·К) до солидуса 1500 °C (1,02 МДж/кг),
 * плавление 272 кДж/кг, жидкая c 820 Дж/(кг·К); выпуск при 1620 °C — 1,39 МДж/кг (386 кВт·ч/т теоретически). Тепловой
 * КПД открытой дуги без водоохлаждения и пенистого шлака — 0,65, отсюда ≈ 600 кВт·ч/т, как у ДСП первой половины
 * XX века. Графитовые электроды — 5 кг на тонну.
 */
public final class ArcFurnace {
    public static final double CAPACITY_KG = 6000, SECONDARY_V = 230, X_OHM = 0.008, R_OPT = 1.333 * X_OHM;
    public static final double THERMAL_EFF = 0.65, ELECTRODE_KG_PER_T = 5;
    public static final double C_SOLID = 690, C_LIQUID = 820, LATENT = 272e3, T0_K = 298.15, SOLIDUS_K = 1773.15,
            TAP_K = 1893.15;
    static final double H_MELT_START = C_SOLID * (SOLIDUS_K - T0_K), H_LIQUID = H_MELT_START + LATENT;
    /** Размах колебаний сопротивления дуги по стадиям. */
    static final double FLICKER_BORE = 0.30, FLICKER_MELT = 0.12, FLICKER_FLAT = 0.03;

    private double massKg, enthalpy, electrodeKg, powerW, reactiveVar;

    /** Загрузить лом {@code kg} при 25 °C (на ванну — охлаждает её); сверх ёмкости — не берёт. Сколько взяла, кг. */
    public double charge(double kg) {
        double take = Math.max(0, Math.min(kg, CAPACITY_KG - massKg));
        if (take <= 0) return 0;
        enthalpy = enthalpy * massKg / (massKg + take);
        massKg += take;
        return take;
    }

    /**
     * Шаг.
     *
     * @param voltagePu напряжение шины, о.е.
     * @param tick      номер тика — для детерминированного шума дуги
     * @param arc       дуга включена (электроды опущены)
     */
    public void step(double dtS, double voltagePu, long tick, boolean arc) {
        if (!arc || massKg <= 0 || voltagePu <= 0.05) {
            powerW = reactiveVar = 0;
            return;
        }
        double molten = moltenFraction();
        double amp = molten < 0.2 ? FLICKER_BORE : molten < 0.95 ? FLICKER_MELT : FLICKER_FLAT;
        double r = R_OPT * Math.max(0.2, 1 + amp * noise(tick));
        double vPh = SECONDARY_V / Math.sqrt(3) * voltagePu;
        double z2 = r * r + X_OHM * X_OHM;
        powerW = 3 * vPh * vPh * r / z2;
        reactiveVar = 3 * vPh * vPh * X_OHM / z2;
        double before = enthalpy;
        enthalpy += THERMAL_EFF * powerW * dtS / massKg;
        double meltedKg = massKg * Math.max(0, Math.min(1, (enthalpy - H_MELT_START) / LATENT)
                - Math.max(0, Math.min(1, (before - H_MELT_START) / LATENT)));
        electrodeKg += ELECTRODE_KG_PER_T * Math.max(0, meltedKg) / 1000;
    }

    /** Доля расплава, 0…1. */
    public double moltenFraction() {
        return massKg <= 0 ? 0 : Math.max(0, Math.min(1, (enthalpy - H_MELT_START) / LATENT));
    }

    /** Температура шихты (средняя по энтальпии), К. */
    public double tempK() {
        if (enthalpy < H_MELT_START) return T0_K + enthalpy / C_SOLID;
        if (enthalpy < H_LIQUID) return SOLIDUS_K;
        return SOLIDUS_K + (enthalpy - H_LIQUID) / C_LIQUID;
    }

    /** Готова к выпуску: всё расплавлено и перегрето до 1620 °C. */
    public boolean readyToTap() {
        return massKg > 0 && tempK() >= TAP_K;
    }

    /** Выпустить плавку; возвращает массу, кг (температура — {@link #tempK()} до вызова). */
    public double tap() {
        double m = massKg;
        massKg = 0;
        enthalpy = 0;
        powerW = reactiveVar = 0;
        return m;
    }

    public double massKg() {
        return massKg;
    }

    public double powerW() {
        return powerW;
    }

    public double reactiveVar() {
        return reactiveVar;
    }

    /** Израсходовано графитовых электродов с начала работы, кг. */
    public double electrodeKg() {
        return electrodeKg;
    }

    /** Удельный расход электроэнергии на выпуск из холодного лома, кВт·ч/т (теория / КПД). */
    public static double specificEnergyKwhPerT() {
        return (H_LIQUID + C_LIQUID * (TAP_K - SOLIDUS_K)) / THERMAL_EFF / 3.6e3;
    }

    public void restore(double mass, double h, double electrodes) {
        massKg = Math.max(0, Math.min(CAPACITY_KG, mass));
        enthalpy = Math.max(0, h);
        electrodeKg = Math.max(0, electrodes);
    }

    public double enthalpy() {
        return enthalpy;
    }

    /** Шум дуги в [−1, 1]: новое значение 10 раз в секунду, хеш номера тика (детерминированно). */
    static double noise(long tick) {
        long z = (tick / 2) * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53 * 2 - 1;
    }
}
