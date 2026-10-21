package dev.denfry.promptcritical.sim.ops;

/**
 * Каталог неисправностей (13 §13.6.5). Значение — переменная состояния модели-владельца в единицах таблицы; пороги
 * 1/2/3 — тяжести ВНИМАНИЕ / ТРЕБУЕТ РЕМОНТА / КРИТИЧНО. Нормированный износ s ∈ [0, 1]: у растущих — x/t₃, у
 * падающих — линейно от нормы до t₃, у сопротивления изоляции — по логарифму.
 */
public enum MaintenanceFaultKind {
    /** D_b подшипника (04a §4a.7). */
    BEARING_WEAR(FaultCategory.MECHANICAL, Scale.RISING, 0.5, 0.7, 0.9, 0, 0.03, 5, Detection.MEASURED),
    /** Остаток смазки до доливки, доля интервала. */
    LUBRICATION(FaultCategory.MECHANICAL, Scale.FALLING, 0.8, 0.5, 0.2, 1, 0, 0, Detection.ALWAYS),
    /** v_rms 1×, мм/с: границы зон B/C/D ISO 10816-3 (группа 2, жёсткое основание) [сверить]. */
    IMBALANCE(FaultCategory.MECHANICAL, Scale.RISING, 1.4, 2.8, 4.5, 0, 0.01, 3, Detection.MEASURED),
    /** Расцентровка, мм. */
    MISALIGNMENT(FaultCategory.MECHANICAL, Scale.RISING, 0.05, 0.1, 0.2, 0, 0.01, 3, Detection.MEASURED),
    /** Протечка торцевого уплотнения, кг/с. */
    SEAL_LEAK(FaultCategory.SEALING, Scale.RISING, 0.005, 0.02, 0.1, 0, 0, 1, Detection.ALWAYS),
    /** Потеря затяжки фланцев, доля. */
    FLANGE_LEAK(FaultCategory.SEALING, Scale.RISING, 0.1, 0.3, 0.6, 0, 0, 1, Detection.MEASURED),
    /** Износ набивки сальника w (пороги мои: в GDD «по протечке»). */
    PACKING_LEAK(FaultCategory.SEALING, Scale.RISING, 0.3, 0.6, 0.9, 0, 0.01, 0, Detection.MEASURED),
    /** Протечка закрытой арматуры, доля ṁ_ном. */
    VALVE_SEAT_WEAR(FaultCategory.SEALING, Scale.RISING, 0.002, 0.01, 0.05, 0, 0, 0, Detection.MEASURED),
    /** R_f в долях проектного; у жаротрубного котла I0 проектное — 1 мм накипи (13 §13.6.7). */
    HX_FOULING(FaultCategory.HEAT_TRANSFER, Scale.RISING, 0.25, 0.5, 1.0, 0, 0.10, 0, Detection.PASSPORT),
    /** Превышение TMT над чистой трубой, К. */
    FURNACE_COKING(FaultCategory.HEAT_TRANSFER, Scale.RISING, 20, 40, 60, 0, 0.05, 3, Detection.ALWAYS),
    /** Остаточный ресурс стенки RL в интервалах контроля; норма — 8 (моё). */
    CORROSION_THINNING(FaultCategory.CORROSION, Scale.FALLING, 4, 2, 1, 8, 0, 5, Detection.MEASURED),
    /** Дрейф датчика в классах точности. */
    SENSOR_DRIFT(FaultCategory.INSTRUMENTATION, Scale.RISING, 0.5, 1, 2, 0, 0, 2, Detection.MEASURED),
    /** Перепад на фильтре, доля допустимого. */
    FILTER_CLOGGING(FaultCategory.MECHANICAL, Scale.RISING, 0.5, 0.8, 1.0, 0, 0.05, 1, Detection.ALWAYS),
    /** Сопротивление изоляции, МОм; норма — 1000. */
    INSULATION_AGING(FaultCategory.ELECTRICAL, Scale.FALLING_LOG, 100, 10, 1, 1000, 0, 5, Detection.MEASURED),
    /** Активность катализатора. */
    CATALYST_DEACTIVATION(FaultCategory.HEAT_TRANSFER, Scale.FALLING, 0.8, 0.6, 0.4, 1, 0.4, 0,
            Detection.ALWAYS),
    /** Потеря напора рабочего колеса, доля. */
    IMPELLER_EROSION(FaultCategory.MECHANICAL, Scale.RISING, 0.1, 0.25, 0.5, 0, 0.1, 1, Detection.PASSPORT),
    /** D_k каната (08 §8.2.8). */
    ROPE_WEAR(FaultCategory.MECHANICAL, Scale.RISING, 0.5, 0.7, 0.9, 0, 0, 5, Detection.MEASURED),
    /** Износ футеровки, доля толщины. */
    REFRACTORY_DAMAGE(FaultCategory.HEAT_TRANSFER, Scale.RISING, 0.25, 0.5, 0.75, 0, 0.05, 1, Detection.ALWAYS),
    /** Течь охлаждающей воды фурмы, кг/с. */
    TUYERE_BURNOUT(FaultCategory.HEAT_TRANSFER, Scale.RISING, 0.1, 0.5, 2, 0, 0, 3, Detection.ALWAYS),
    /** Износ валков по диаметру, мм. */
    ROLL_WEAR(FaultCategory.MECHANICAL, Scale.RISING, 0.5, 1, 2, 0, 0.02, 0, Detection.MEASURED),
    /** Рост удельного сопротивления мембраны, доля. */
    MEMBRANE_DEGRADATION(FaultCategory.ELECTRICAL, Scale.RISING, 0.1, 0.25, 0.5, 0, 0.1, 1, Detection.ALWAYS);

    /** Как значение растёт к отказу. */
    public enum Scale { RISING, FALLING, FALLING_LOG }

    /**
     * Как панель узнаёт значение (P2): {@code ALWAYS} — счётчик или прибор на месте; {@code PASSPORT} — сравнение
     * трендов с паспортом, без паспорта не видно; {@code MEASURED} — только замером ({@link Probe}).
     */
    public enum Detection { ALWAYS, PASSPORT, MEASURED }

    private final FaultCategory category;
    private final Scale scale;
    private final double t1, t2, t3, normal;
    private final FaultCoefficients coefficients;
    private final Detection detection;

    MaintenanceFaultKind(FaultCategory category, Scale scale, double t1, double t2, double t3, double normal,
            double eff, double trip, Detection detection) {
        this.category = category;
        this.scale = scale;
        this.t1 = t1;
        this.t2 = t2;
        this.t3 = t3;
        this.normal = normal;
        this.coefficients = new FaultCoefficients(eff, trip);
        this.detection = detection;
    }

    public FaultCategory category() {
        return category;
    }

    public Scale scale() {
        return scale;
    }

    public Detection detection() {
        return detection;
    }

    public FaultCoefficients defaultCoefficients() {
        return coefficients;
    }

    /** Порог тяжести 1–3. */
    public double threshold(int severity) {
        return switch (severity) {
            case 1 -> t1;
            case 2 -> t2;
            case 3 -> t3;
            default -> throw new IllegalArgumentException("severity " + severity);
        };
    }

    /** Тяжесть 0–3; NaN — 0. */
    public int severity(double x) {
        if (Double.isNaN(x)) return 0;
        boolean rising = scale == Scale.RISING;
        int s = 0;
        for (double t : new double[] {t1, t2, t3}) if (rising ? x >= t : x <= t) s++;
        return s;
    }

    /** Нормированный износ s ∈ [0, 1]: 1 — на пороге КРИТИЧНО. */
    public double normalized(double x) {
        if (Double.isNaN(x)) return 0;
        double s = switch (scale) {
            case RISING -> x / t3;
            case FALLING -> (normal - x) / (normal - t3);
            case FALLING_LOG -> x <= 0 ? 1 : Math.log(normal / x) / Math.log(normal / t3);
        };
        return Math.clamp(s, 0, 1);
    }
}
