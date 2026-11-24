package dev.denfry.promptcritical.mod.production;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import java.util.List;

/**
 * Местные щиты транспорта нефти (09 §9.8) на фонах art/blockbench/oil_production/oilfield_panels.author.js:
 * магистральный насос и путевой подогреватель — на общем фоне {@code mainline_pump.png} (два манометра или
 * термометра, полоса, строка, лампа и две кнопки), резервуар РВС-1000 — на {@code oil_tank.png}.
 */
public final class TransportPanels {
    // насос
    public static final int P_SUCTION_KPA = 0, P_DISCHARGE_KPA = 1, P_FLOW_M3H = 2, P_HEAD_M = 3, P_POWER_KW = 4, P_EFF_PERMIL = 5,
            P_SPEED_PERMIL = 6, P_OIL_C10 = 7, P_VISC_CP10 = 8, P_FLAGS = 9, P_CHANNELS = 10;
    public static final int PF_RUN = 0, PF_CAVITATION = 1;
    // подогреватель
    public static final int H_IN_C10 = 0, H_OUT_C10 = 1, H_DUTY_PERMIL = 2, H_GAS_M3H10 = 3, H_FLOW_TH10 = 4, H_VISC_CP10 = 5,
            H_FUEL_PERMIL = 6, H_FLAGS = 7, H_CHANNELS = 8;
    public static final int HF_LIT = 0;
    // резервуар
    public static final int T_LEVEL_PERMIL = 0, T_LEVEL_CM = 1, T_VOLUME_M3 = 2, T_LOSS_KGH10 = 3, T_TEMP_C10 = 4, T_FLAGS = 5,
            T_CHANNELS = 6;
    public static final int TF_VENT = 0, TF_HIGH = 1, TF_HH = 2;

    private static final Tr SUCTION = PanelText.tr("oil.suction", "Suction", "Всас"),
            DISCHARGE = PanelText.tr("oil.discharge", "Discharge", "Нагнетание"),
            SUCTION_TIP = PanelText.tr("oil.suction_tip", "Suction pressure %s MPa: feed head from the tank farm or booster pump",
                    "Давление на всасе %s МПа: подпор от резервуарного парка или подпорного насоса"),
            DISCHARGE_TIP = PanelText.tr("oil.discharge_tip", "Discharge pressure %s MPa: head %s m on the NM 1250-260 curve",
                    "Давление нагнетания %s МПа: напор %s м по характеристике НМ 1250-260"),
            FLOW = PanelText.tr("oil.flow", "Flow", "Подача"),
            FLOW_TIP = PanelText.tr("oil.flow_tip", "Flow %s m³/h of 1250 rated · shaft %s kW · efficiency %s %% · speed %s %%",
                    "Подача %s м³/ч из 1250 номинальных · на валу %s кВт · КПД %s %% · частота %s %%"),
            OIL_LINE = PanelText.tr("oil.oil_line", "%s °C · %s cP", "%s °C · %s сП"),
            OIL_TIP = PanelText.tr("oil.oil_tip", "Crude at the suction and its dead-oil viscosity (Beggs–Robinson): warm crude runs the line faster",
                    "Нефть на всасе и её вязкость (Беггс — Робинсон): тёплая нефть идёт по участку быстрее"),
            RUN = PanelText.tr("oil.run", "Run", "Работа"),
            START = PanelText.tr("oil.start", "Start", "Пуск"),
            STOP = PanelText.tr("oil.stop", "Stop", "Стоп"),
            START_TIP = PanelText.tr("oil.start_tip", "Start the pump: it takes the Create shaft at the motor tail",
                    "Пустить насос: он берёт вал Create на хвостовике двигателя"),
            STOP_TIP = PanelText.tr("oil.stop_tip", "Stop the pump", "Остановить насос"),
            CAVITATION = PanelText.tr("oil.cavitation", "Cavitation: the suction is empty", "Кавитация: на всасе нет нефти"),
            INLET = PanelText.tr("oil.inlet", "Crude in", "Вход"),
            OUTLET = PanelText.tr("oil.outlet", "Crude out", "Выход"),
            INLET_TIP = PanelText.tr("oil.inlet_tip", "Crude at the coil inlet %s °C: flowlines lie at air temperature",
                    "Нефть на входе змеевика %s °C: выкидные линии — при температуре воздуха"),
            OUTLET_TIP = PanelText.tr("oil.outlet_tip", "Crude at the coil outlet %s °C, %s cP: at most the 95 °C bath less 10 K",
                    "Нефть на выходе змеевика %s °C, %s сП: не выше бани 95 °C без 10 K недогрева"),
            DUTY = PanelText.tr("oil.duty", "Heat to the crude", "Тепло нефти"),
            DUTY_TIP = PanelText.tr("oil.duty_tip", "%s %% of the 0.63 MW duty · crude %s t/h · fuel gas tank %s %%",
                    "%s %% от 0,63 МВт · нефть %s т/ч · газ в баке %s %%"),
            GAS_LINE = PanelText.tr("oil.gas_line", "Gas %s nm³/h", "Газ %s нм³/ч"),
            GAS_TIP = PanelText.tr("oil.gas_tip", "Associated gas to the burner: heat to the crude and bath losses over 0.75 efficiency",
                    "Попутный газ на горелку: тепло нефти и потери бани при КПД 0,75"),
            FLAME = PanelText.tr("oil.flame", "Flame", "Пламя"),
            IGNITE = PanelText.tr("oil.ignite", "Ignite", "Розжиг"),
            SHUT = PanelText.tr("oil.shut", "Shut off", "Погасить"),
            IGNITE_TIP = PanelText.tr("oil.ignite_tip", "Light the burner on associated gas from the west head", "Зажечь горелку на попутном газе с западного днища"),
            SHUT_TIP = PanelText.tr("oil.shut_tip", "Shut the burner gas valve", "Закрыть газ на горелку"),
            LEVEL = PanelText.tr("oil.level", "Product level", "Уровень продукта"),
            LEVEL_TIP = PanelText.tr("oil.level_tip", "Level %s m of 11.92 m · %s m³ · high level at 90 %%, high-high at 95 %%",
                    "Взлив %s м из 11,92 м · %s м³ · верхний уровень 90 %%, аварийный 95 %%"),
            ROOF = PanelText.tr("oil.roof", "Floating roof", "Плавающая крыша"),
            ROOF_TIP = PanelText.tr("oil.roof_tip", "The pontoon deck rides on the product at %s m", "Понтон лежит на продукте на высоте %s м"),
            LOSS = PanelText.tr("oil.loss", "Breathing losses", "Потери от дыханий"),
            LOSS_TIP = PanelText.tr("oil.loss_tip", "Vapour out of the breather valve %s kg/h: filling breathes out, the daily cycle breathes in and out",
                    "Пары из дыхательного клапана %s кг/ч: налив — большое дыхание, суточный цикл — малые"),
            TANK_LINE = PanelText.tr("oil.tank_line", "%s °C · losses %s kg/h", "%s °C · потери %s кг/ч"),
            VAPOUR = PanelText.tr("oil.vapour", "Vapour", "Пары");

    public static final PanelLayout MAINLINE_PUMP = PanelLayout.builder("mainline_pump_real", 220, 130, P_CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/mainline_pump.png"))
            .dial(12, 26, false, P_SUCTION_KPA, 1000, 1, Double.NaN, 2, PanelText.MPA.get(), SUCTION.get(),
                    v -> SUCTION_TIP.get(v.num(P_SUCTION_KPA, 1000, 2)))
            .dial(70, 26, false, P_DISCHARGE_KPA, 1000, 4, 3.4, 2, PanelText.MPA.get(), DISCHARGE.get(),
                    v -> DISCHARGE_TIP.get(v.num(P_DISCHARGE_KPA, 1000, 2), v.raw(P_HEAD_M)))
            .bar(130, 40, 78, P_FLOW_M3H, 1, 1600, 0xFF8A6A30, FLOW.get(),
                    v -> FLOW_TIP.get(v.raw(P_FLOW_M3H), v.raw(P_POWER_KW), v.num(P_EFF_PERMIL, 10, 0), v.num(P_SPEED_PERMIL, 10, 0)))
            .readout(130, 54, 78, v -> v.bit(P_FLAGS, PF_CAVITATION) ? CAVITATION.get()
                    : OIL_LINE.get(v.num(P_OIL_C10, 10, 1), v.num(P_VISC_CP10, 10, 1)), v -> OIL_TIP.get())
            .lamp(130, 74, P_FLAGS, PF_RUN, LampColor.GREEN, RUN.get())
            .button(12, 106, 96, MainlinePumpBlockEntity.B_START, START.get(), START_TIP.get())
            .button(112, 106, 96, MainlinePumpBlockEntity.B_STOP, STOP.get(), STOP_TIP.get())
            .noInventory().build();

    public static final PanelLayout LINE_HEATER = PanelLayout.builder("line_heater_real", 220, 130, H_CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/mainline_pump.png"))
            .dial(12, 26, false, H_IN_C10, 10, 120, Double.NaN, 0, PanelText.CELSIUS.get(), INLET.get(),
                    v -> INLET_TIP.get(v.num(H_IN_C10, 10, 1)))
            .dial(70, 26, false, H_OUT_C10, 10, 120, 90, 0, PanelText.CELSIUS.get(), OUTLET.get(),
                    v -> OUTLET_TIP.get(v.num(H_OUT_C10, 10, 1), v.num(H_VISC_CP10, 10, 1)))
            .bar(130, 40, 78, H_DUTY_PERMIL, 10, 100, 0xFFC06030, DUTY.get(),
                    v -> DUTY_TIP.get(v.num(H_DUTY_PERMIL, 10, 0), v.num(H_FLOW_TH10, 10, 1), v.num(H_FUEL_PERMIL, 10, 0)))
            .readout(130, 54, 78, v -> GAS_LINE.get(v.num(H_GAS_M3H10, 10, 1)), v -> GAS_TIP.get())
            .lamp(130, 74, H_FLAGS, HF_LIT, LampColor.AMBER, FLAME.get())
            .button(12, 106, 96, LineHeaterBlockEntity.B_IGNITE, IGNITE.get(), IGNITE_TIP.get())
            .button(112, 106, 96, LineHeaterBlockEntity.B_SHUT, SHUT.get(), SHUT_TIP.get())
            .noInventory().build();

    public static final PanelLayout OIL_TANK = tank("oil_tank_real", false);
    public static final PanelLayout OIL_TANK_FLOATING = tank("oil_tank_floating_real", true);

    private TransportPanels() {
    }

    /** Резервуар: уровень, вторая полоса — плавающая крыша или потери, строка температуры и лампа паров у клапана. */
    private static PanelLayout tank(String id, boolean floating) {
        PanelLayout.Builder b = PanelLayout.builder(id, 220, 100, T_CHANNELS)
                .art(PromptCritical.id("textures/gui/panel/oil_tank.png"))
                .bar(12, 36, 196, T_LEVEL_PERMIL, 10, 100, 0xFF5A4020, LEVEL.get(),
                        v -> LEVEL_TIP.get(v.num(T_LEVEL_CM, 100, 2), v.raw(T_VOLUME_M3)));
        if (floating) b.bar(12, 58, 196, T_LEVEL_PERMIL, 10, 100, 0xFF9AA0A8, ROOF.get(), v -> ROOF_TIP.get(v.num(T_LEVEL_CM, 100, 2)));
        else b.bar(12, 58, 196, T_LOSS_KGH10, 10, 50, 0xFF8A8A50, LOSS.get(), v -> LOSS_TIP.get(v.num(T_LOSS_KGH10, 10, 1)));
        return b.readout(12, 74, 120, v -> TANK_LINE.get(v.num(T_TEMP_C10, 10, 1), v.num(T_LOSS_KGH10, 10, 1)), null)
                .lamp(146, 73, T_FLAGS, TF_VENT, LampColor.RED, VAPOUR.get())
                .noInventory().build();
    }

    public static List<PanelLayout> all() {
        return List.of(MAINLINE_PUMP, LINE_HEATER, OIL_TANK, OIL_TANK_FLOATING);
    }
}
