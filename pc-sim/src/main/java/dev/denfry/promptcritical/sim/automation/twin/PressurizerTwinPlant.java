package dev.denfry.promptcritical.sim.automation.twin;

import dev.denfry.promptcritical.sim.thermo.if97.If97;
import java.util.List;

/**
 * Учебная установка «первый контур с компенсатором давления» для урока {@code stuck_porv} (11 §11.3.4; индикация
 * команды против датчика положения — 03c §3c.2.8 {@code POSITION_DISAGREE}; уровень КД растёт при течи пара — 04c).
 * Модель балансовая и учебная [BALANCE], не теплогидравлика 01: давление, запас теплоносителя (доля номинального) и
 * паросодержание в контуре.
 *
 * <p>Импульсный предохранительный клапан КД ({@code porv}) и запорная задвижка перед ним ({@code block_valve}) пропускают
 * пар: запас уходит со скоростью {@value #RELIEF_PER_S}·√(p/p_ном) в секунду, давление падает на {@value #RELIEF_MPA_S}
 * МПа/с. Ниже давления насыщения горячей нитки ({@value #T_HOT_C} °C, IF97) теплоноситель вскипает: давление держится у
 * насыщения, а пар в контуре вытесняет воду в КД — уровень КД <b>растёт</b>, хотя запас падает (ловушка урока: оператор
 * видит «КД полон» и сокращает подпитку). Подпитка {@code makeup} 0,5 уравновешивает организованный отбор; ТЭНы
 * {@code heaters} 0,5 — теплопотери. Запас ниже {@value #UNCOVER} — оголение активной зоны, авария.
 *
 * <p>Сигналы пульта: давление, уровень КД, лампа клапана (без отказа — положение, с отказом {@code IndicationCommand} —
 * команда на соленоид), датчик положения клапана (концевик штока), температура сбросной трубы за клапаном, запас до
 * насыщения. Сам клапан на пульте оператора не управляется (пятый механизм — пульт показывает четыре): он импульсный.
 */
public final class PressurizerTwinPlant implements TwinPlant {
    public static final String ID = "pressurizer";
    public static final double P_NOM_MPA = 15.5, T_HOT_C = 320, RELIEF_PER_S = 0.0012, RELIEF_MPA_S = 0.15, HEATER_MPA_S = 0.03,
            SPRAY_MPA_S = 0.05, MAKEUP_PER_S = 0.0015, LETDOWN_PER_S = 0.00075, VOID_RISE_PER_S = 0.004, VOID_COLLAPSE_PER_S = 0.003,
            UNCOVER = 0.70, TAILPIPE_OPEN_C = 140, TAILPIPE_CLOSED_C = 45, TAILPIPE_TAU_S = 10, RECOVER_MPA_S = 0.02, VOID_MAX = 0.6;
    public static final String PRESSURE = "pressure_mpa", LEVEL = "pzr_level", PORV_LAMP = "porv_lamp", PORV_POSITION = "porv_position",
            TAILPIPE = "tailpipe_c", SUBCOOLING = "subcooling_k", INVENTORY = "inventory", RELIEF_FLOW = "relief_flow";
    public static final String BLOCK_VALVE = "block_valve", MAKEUP = "makeup", HEATERS = "heaters", SPRAY = "spray", PORV = "porv";
    public static final List<String> SIGNALS = List.of(PRESSURE, LEVEL, PORV_LAMP, PORV_POSITION, TAILPIPE, SUBCOOLING),
            ACTUATORS = List.of(BLOCK_VALVE, MAKEUP, HEATERS, SPRAY, PORV);
    private static final double[] UNIT = {0, 1};

    private double p = P_NOM_MPA, inventory = 1, voids, tailpipe = TAILPIPE_CLOSED_C, relief;
    private double block = 1, makeup = 0.5, heaters = 0.5, spray, porv;
    private final double pSatHot = If97.psat(T_HOT_C + 273.15) / 1e6;
    private String failure;

    @Override
    public String template() {
        return ID;
    }

    @Override
    public List<String> signals() {
        return SIGNALS;
    }

    /** Уровень КД: доля по запасу и вытесненная паром вода. */
    double level() {
        return Math.max(0, Math.min(1, 0.55 + 2.0 * (inventory - 1) + 0.9 * voids));
    }

    @Override
    public double read(String signal) {
        return switch (signal) {
            case PRESSURE -> p;
            case LEVEL -> level();
            case PORV_LAMP, PORV_POSITION -> porv;
            case TAILPIPE -> tailpipe;
            case SUBCOOLING -> If97.tsat(p * 1e6) - 273.15 - T_HOT_C;
            case INVENTORY -> inventory;
            case RELIEF_FLOW -> relief;
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
        return BLOCK_VALVE.equals(actuator) || PORV.equals(actuator);
    }

    @Override
    public double actuator(String actuator) {
        return switch (actuator) {
            case BLOCK_VALVE -> block;
            case MAKEUP -> makeup;
            case HEATERS -> heaters;
            case SPRAY -> spray;
            case PORV -> porv;
            default -> Double.NaN;
        };
    }

    @Override
    public void actuate(String actuator, double value) {
        double v = TwinPlant.clamp(value, UNIT);
        switch (actuator) {
            case BLOCK_VALVE -> block = v >= 0.5 ? 1 : 0;
            case MAKEUP -> makeup = v;
            case HEATERS -> heaters = v;
            case SPRAY -> spray = v;
            case PORV -> porv = v >= 0.5 ? 1 : 0;
            default -> throw new IllegalArgumentException("no actuator " + actuator);
        }
    }

    @Override
    public void step() {
        double dt = DT, open = porv * block;
        relief = open * RELIEF_PER_S * Math.sqrt(Math.max(0, p) / P_NOM_MPA);
        inventory += (makeup * MAKEUP_PER_S - LETDOWN_PER_S - relief) * dt;
        double dp = HEATER_MPA_S * (heaters - 0.5) - SPRAY_MPA_S * spray - RELIEF_MPA_S * open;
        // после изоляции ТЭНы КД при номинальной мощности поднимают давление обратно к номинальному
        if (open == 0 && p < P_NOM_MPA && heaters >= 0.5) dp += RECOVER_MPA_S;
        p = Math.max(0.1, Math.min(17.5, p + dp * dt));
        // ниже насыщения горячей нитки теплоноситель вскипает и держит давление; пар вытесняет воду в КД
        boolean saturated = p <= pSatHot;
        if (saturated) p = pSatHot;
        if (saturated && open > 0) voids = Math.min(VOID_MAX, voids + VOID_RISE_PER_S * dt);
        else if (open == 0) voids = Math.max(0, voids - VOID_COLLAPSE_PER_S * dt);
        double target = open > 0 ? TAILPIPE_OPEN_C : TAILPIPE_CLOSED_C;
        tailpipe += (target - tailpipe) * dt / TAILPIPE_TAU_S;
        if (failure == null && inventory < UNCOVER) failure = "core_uncovered";
    }

    @Override
    public String failure() {
        return failure;
    }
}
