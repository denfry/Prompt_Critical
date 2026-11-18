package dev.denfry.promptcritical.sim.automation.twin;

import java.util.List;

/**
 * Учебный резервуар (11 §11.3.4, уроки {@code tank_overfill}, {@code forced_bit}): вертикальный бак с приёмной
 * задвижкой и откачивающим насосом, уровнемер и независимый сигнализатор верхнего уровня. Балансовая модель:
 * dV/dt = Q_пр − Q_отк; выше борта продукт уходит в обвалование ({@code spilled_m3}) — авария «перелив».
 *
 * <p>Размеры учебные [BALANCE]: сечение {@value #AREA_M2} м², высота {@value #HEIGHT_M} м, приём до
 * {@value #INFLOW_MAX_M3H} м³/ч при полностью открытой задвижке (подпор трубопровода постоянный), насос
 * {@value #PUMP_M3H} м³/ч, срывает подачу ниже {@value #PUMP_MIN_LEVEL_M} м. Сигнализатор — на {@value #LSH_M} м.
 */
public final class TankTwinPlant implements TwinPlant {
    public static final String ID = "tank_farm";
    public static final double AREA_M2 = 10, HEIGHT_M = 8, INFLOW_MAX_M3H = 360, PUMP_M3H = 200, PUMP_MIN_LEVEL_M = 0.2,
            LSH_M = 7.5;
    /** Перелив больше этого объёма — авария, м³. */
    public static final double SPILL_LIMIT_M3 = 0.1;
    public static final String LEVEL = "level_m", LSH = "lsh", INFLOW = "inflow_m3h", OUTFLOW = "outflow_m3h", NET = "net_m3h",
            SPILLED = "spilled_m3", INLET = "inlet_valve", PUMP = "outlet_pump";
    public static final List<String> SIGNALS = List.of(LEVEL, LSH, INFLOW, OUTFLOW, NET, SPILLED), ACTUATORS = List.of(INLET, PUMP);
    private static final double[] UNIT = {0, 1};

    private double level, inlet, pump, spilled, qIn, qOut;
    private String failure;

    public TankTwinPlant(double levelM, double inletPosition, boolean pumpOn) {
        level = Math.max(0, Math.min(HEIGHT_M, levelM));
        inlet = TwinPlant.clamp(inletPosition, UNIT);
        pump = pumpOn ? 1 : 0;
        flows();
    }

    @Override
    public String template() {
        return ID;
    }

    @Override
    public List<String> signals() {
        return SIGNALS;
    }

    @Override
    public double read(String signal) {
        return switch (signal) {
            case LEVEL -> level;
            case LSH -> level >= LSH_M ? 1 : 0;
            case INFLOW -> qIn;
            case OUTFLOW -> qOut;
            case NET -> qIn - qOut;
            case SPILLED -> spilled;
            default -> Double.NaN;
        };
    }

    @Override
    public List<String> actuators() {
        return ACTUATORS;
    }

    @Override
    public double[] range(String actuator) {
        return UNIT;
    }

    @Override
    public boolean discrete(String actuator) {
        return PUMP.equals(actuator);
    }

    @Override
    public double actuator(String actuator) {
        return switch (actuator) {
            case INLET -> inlet;
            case PUMP -> pump;
            default -> Double.NaN;
        };
    }

    @Override
    public void actuate(String actuator, double value) {
        switch (actuator) {
            case INLET -> inlet = TwinPlant.clamp(value, UNIT);
            case PUMP -> pump = value >= 0.5 ? 1 : 0;
            default -> throw new IllegalArgumentException("no actuator " + actuator);
        }
    }

    private void flows() {
        qIn = inlet * INFLOW_MAX_M3H;
        qOut = pump > 0 && level > PUMP_MIN_LEVEL_M ? PUMP_M3H : 0;
    }

    @Override
    public void step() {
        flows();
        double v = level * AREA_M2 + (qIn - qOut) / 3600 * DT;
        double cap = HEIGHT_M * AREA_M2;
        if (v > cap) {
            spilled += v - cap;
            v = cap;
        }
        level = Math.max(0, v / AREA_M2);
        if (failure == null && spilled > SPILL_LIMIT_M3) failure = "tank_overflow";
    }

    @Override
    public String failure() {
        return failure;
    }
}
