package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.sim.grid.WindTurbineUnit;
import dev.denfry.promptcritical.sim.wind.WindTurbine;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Местный щит A0 ветроэлектростанции: анемометр (мгновенный ветер у оси колеса и 10-минутное среднее в подсказке),
 * тахометр колеса, ваттметр генератора; лампы «сеть», «автомат», «шторм», «авария», «тормоз»; ключи пуска,
 * останова и сброса. Каналы — целые, масштаб в имени.
 */
public final class WindPanels {
    public static final int WIND_MS10 = 0, MEAN_MS10 = 1, ROTOR_RPM10 = 2, KW10 = 3, KVAR10 = 4, PITCH10 = 5, THRUST_KN10 = 6,
            YAW_ERR = 7, CP_PERMIL = 8, ENERGY_KWH = 9, STATE = 10, FAULT = 11, FLAGS = 12, CHANNELS = 13;
    public static final int F_GRID = 0, F_AUTO = 1, F_STORM = 2, F_FAULT = 3, F_BRAKE = 4;
    public static final int BUTTON_START = 0, BUTTON_STOP = 1, BUTTON_RESET = 2;

    private static final Tr MS = PanelText.tr("unit.ms", "m/s", "м/с"),
            KW = PanelText.tr("unit.kw", "kW", "кВт"),
            WIND_LABEL = PanelText.tr("wind.wind", "Wind", "Ветер"),
            ROTOR = PanelText.tr("wind.rotor", "Rotor", "Колесо"),
            POWER = PanelText.tr("wind.power", "Output", "Мощность"),
            GRID = PanelText.tr("wind.grid", "Grid", "Сеть"),
            AUTO = PanelText.tr("wind.auto", "Auto", "Автомат"),
            STORM = PanelText.tr("wind.storm", "Storm", "Шторм"),
            FAULT_LAMP = PanelText.tr("wind.fault", "Trip", "Авария"),
            BRAKE = PanelText.tr("wind.brake", "Brake", "Тормоз"),
            START = PanelText.tr("wind.start", "Start", "Пуск"),
            START_TIP = PanelText.tr("wind.start_tip", "Switch the automatics on: the rotor starts by itself once the one-minute wind is above %s m/s",
                    "Включить автоматику: колесо пустится само, когда минутный ветер будет выше %s м/с"),
            STOP = PanelText.tr("wind.stop", "Stop", "Стоп"),
            STOP_TIP = PanelText.tr("wind.stop_tip", "Normal stop: blades to feather, generator off, brake on",
                    "Штатный останов: лопасти во флюгер, генератор отключить, тормоз"),
            RESET = PanelText.tr("wind.reset", "Reset", "Сброс"),
            RESET_TIP = PanelText.tr("wind.reset_tip", "Reset the overspeed or tower overload trip on a stopped rotor",
                    "Сбросить аварию по разносу или перегрузке башни на стоящем колесе"),
            WIND_TIP = PanelText.tr("wind.wind_tip", "Wind at the hub %s m/s · 10-minute mean %s m/s · storm stop above %s",
                    "Ветер у оси колеса %s м/с · среднее за 10 мин %s м/с · штормовой останов выше %s"),
            ROTOR_TIP = PanelText.tr("wind.rotor_tip", "Rotor %s rpm · synchronous %s · overspeed trip %s",
                    "Колесо %s об/мин · синхронно %s · разнос %s"),
            POWER_TIP = PanelText.tr("wind.power_tip", "Into the grid %s kW · magnetising %s kvar · rotor Cp %s",
                    "В сеть %s кВт · намагничивание %s квар · Cp колеса %s"),
            VALUES = PanelText.tr("wind.values", "%s · pitch %s° · yaw error %s° · thrust %s kN · %s kWh",
                    "%s · шаг %s° · рассогласование %s° · осевая %s кН · %s кВт·ч");
    private static final Tr[] STATES = {
            PanelText.tr("wind.state.parked", "Parked", "Стоит на тормозе"),
            PanelText.tr("wind.state.starting", "Running up", "Разгон"),
            PanelText.tr("wind.state.generating", "Generating", "Генерация"),
            PanelText.tr("wind.state.stopping", "Stopping", "Останов"),
            PanelText.tr("wind.state.storm", "Storm stop", "Штормовой останов"),
            PanelText.tr("wind.state.fault", "Tripped", "Авария"),
    };
    private static final Tr[] FAULTS = {
            PanelText.tr("wind.fault.none", "", ""),
            PanelText.tr("wind.fault.overspeed", "overspeed", "разнос"),
            PanelText.tr("wind.fault.overload", "tower overload", "перегрузка башни"),
    };

    static {
        if (STATES.length != WindTurbine.State.values().length || FAULTS.length != WindTurbine.Fault.values().length)
            throw new IllegalStateException("wind panel texts out of step with the turbine states");
    }

    private static final double SYNC_RPM = WindTurbineUnit.SPEC.syncRpm(50) / WindTurbineUnit.SPEC.gearRatio();

    public static final PanelLayout WIND = PanelLayout.builder("wind_turbine", 232, 124, CHANNELS)
            .dial(8, 24, false, WIND_MS10, 10, 30, WindTurbineUnit.SPEC.cutOutMs(), 1, MS.get(), WIND_LABEL.get(),
                    v -> WIND_TIP.get(v.num(WIND_MS10, 10, 1), v.num(MEAN_MS10, 10, 1),
                            PanelText.num(WindTurbineUnit.SPEC.cutOutMs(), 0)))
            .dial(64, 24, false, ROTOR_RPM10, 10, 40, WindTurbineUnit.SPEC.overspeed() * SYNC_RPM, 1, PanelText.RPM.get(),
                    ROTOR.get(), v -> ROTOR_TIP.get(v.num(ROTOR_RPM10, 10, 1), PanelText.num(SYNC_RPM, 0),
                            PanelText.num(WindTurbineUnit.SPEC.overspeed() * SYNC_RPM, 1)))
            .dial(120, 24, false, KW10, 10, 120, 110, 0, KW.get(), POWER.get(),
                    v -> POWER_TIP.get(v.num(KW10, 10, 1), v.num(KVAR10, 10, 1), v.num(CP_PERMIL, 1000, 2)))
            .lamp(178, 22, FLAGS, F_GRID, LampColor.GREEN, GRID.get())
            .lamp(178, 36, FLAGS, F_AUTO, LampColor.GREEN, AUTO.get())
            .lamp(178, 50, FLAGS, F_STORM, LampColor.AMBER, STORM.get())
            .lamp(178, 64, FLAGS, F_FAULT, LampColor.RED, FAULT_LAMP.get())
            .lamp(178, 78, FLAGS, F_BRAKE, LampColor.AMBER, BRAKE.get())
            .button(8, 84, 52, BUTTON_START, START.get(), START_TIP.get(PanelText.num(WindTurbineUnit.SPEC.cutInMs(), 0)))
            .button(64, 84, 52, BUTTON_STOP, STOP.get(), STOP_TIP.get())
            .button(120, 84, 52, BUTTON_RESET, RESET.get(), RESET_TIP.get())
            .readout(8, 106, 216, WindPanels::values, null)
            .noInventory().build();

    private WindPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(WIND);
    }

    private static Component values(PanelValues v) {
        int s = Math.floorMod(v.raw(STATE), STATES.length);
        Component state = STATES[s].get();
        int f = Math.floorMod(v.raw(FAULT), FAULTS.length);
        if (f != 0) state = Component.literal("").append(state).append(" — ").append(FAULTS[f].get());
        Component c = VALUES.get(state, v.num(PITCH10, 10, 1), v.raw(YAW_ERR), v.num(THRUST_KN10, 10, 1),
                String.format(Locale.ROOT, "%d", v.raw(ENERGY_KWH)));
        return f != 0 ? c.copy().withStyle(ChatFormatting.RED) : c;
    }
}
