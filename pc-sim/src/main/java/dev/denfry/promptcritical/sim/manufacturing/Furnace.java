package dev.denfry.promptcritical.sim.manufacturing;

/**
 * Камерная печь механизированной ступени (18 §18.6.2, §18.7): нагревательная перед молотом и закалочная. Камера —
 * сосредоточенная теплоёмкость {@code C}, теряет {@code UA·(T − T₀)} через футеровку; регулятор по уставке берёт
 * мощность до {@code maxPowerW} (уголь, газ или сеть 07). Заготовку внутри греет {@link ForgeHeating} к температуре
 * камеры, со своей окалиной.
 *
 * @param tempK температура камеры, К
 * @param setpointK уставка, К
 */
public record Furnace(double tempK, double setpointK) {
    /** Теплоёмкость камеры, Дж/К (≈ 40 кг лёгкой футеровки: разогрев до ковочной за 10–15 минут на 50 кВт) [BALANCE]. */
    public static final double HEAT_CAPACITY_J_K = 4.0e4;
    /** Теплопотери через футеровку, Вт/К (≈ 10 кВт при 1200 °C) [BALANCE]. */
    public static final double LOSS_W_K = 8.5;
    /** Коэффициент регулятора: мощность на кельвин рассогласования, Вт/К [моё]. */
    public static final double GAIN_W_K = 2000;
    /** Наибольшая уставка, К (1250 °C: выше — пережог в камере) [моё]. */
    public static final double MAX_SETPOINT_K = 1523.15;

    public Furnace {
        if (!(tempK > 0) || !Double.isFinite(tempK) || !Double.isFinite(setpointK)) {
            throw new IllegalArgumentException("temperature must be positive and finite");
        }
        setpointK = Math.max(0, Math.min(MAX_SETPOINT_K, setpointK));
    }

    public static Furnace cold(double ambientK) {
        return new Furnace(ambientK, ambientK);
    }

    public Furnace withSetpoint(double k) {
        return new Furnace(tempK, k);
    }

    /** Мощность, которую регулятор просит у источника сейчас, Вт: удержание плюс разгон, не больше {@code maxW}. */
    public double demandW(double ambientK, double maxW) {
        double hold = LOSS_W_K * Math.max(0, tempK - ambientK);
        double want = hold + GAIN_W_K * (setpointK - tempK);
        return Math.max(0, Math.min(maxW, want));
    }

    /** Шаг явным Эйлером с подстановкой по секундам: камера получает {@code powerW}, теряет через футеровку. */
    public Furnace step(double powerW, double ambientK, double seconds) {
        if (!(seconds >= 0) || !(powerW >= 0)) throw new IllegalArgumentException("power and seconds must be >= 0");
        double t = tempK;
        for (double left = seconds; left > 0; left -= 1) {
            double dt = Math.min(1, left);
            t += (powerW - LOSS_W_K * (t - ambientK)) / HEAT_CAPACITY_J_K * dt;
        }
        return new Furnace(Math.max(1, t), setpointK);
    }
}
