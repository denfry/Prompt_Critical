package dev.denfry.promptcritical.mod.mine;

import static dev.denfry.promptcritical.mod.panel.PanelText.M3H;
import static dev.denfry.promptcritical.mod.panel.PanelText.PERCENT;
import static dev.denfry.promptcritical.mod.panel.PanelText.RPM;
import static dev.denfry.promptcritical.mod.panel.PanelText.RUNNING;
import static dev.denfry.promptcritical.mod.panel.PanelText.SHAFT;
import static dev.denfry.promptcritical.mod.panel.PanelText.SHAFT_TIP;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Местные щиты шахты: вентилятор главного проветривания, насосы дегазации и водоотлива, шкаф газовой защиты. */
public final class MinePanels {
    // машины на валу: тахометр вала, флаги, свои каналы
    public static final int SHAFT_RPM10 = 0, FLAGS = 1;
    public static final int F_RUNNING = 0, F_ACID = 1, F_DRY = 2;
    /** Кнопка толчка у вентилятора и насоса (проверка вращения, 13 §13.6.6). */
    public static final int BUTTON_BUMP = 0;
    public static final int FAN_RPM = 2, FAN_KW10 = 3, FAN_PA = 4, FAN_Q10 = 5, FAN_CHANNELS = 6;
    public static final int DEGAS_CAPTURE_PERMIL = 2, DEGAS_KW10 = 3, DEGAS_CHANNELS = 4;
    public static final int PUMP_FLOW_M3H10 = 2, PUMP_HEAD_M = 3, PUMP_KW10 = 4, PUMP_CHANNELS = 5;
    /** Метров водяного столба на 1 МПа: 10⁶ / (ρg) при ρ = 1000 кг/м³. */
    private static final double M_PER_MPA = 1e6 / (1000 * 9.80665);

    // шкаф АГЗ
    public static final int D_CH4_X100 = 0, D_CO_PPM = 1, D_O2_X10 = 2, D_EMISSION_X100 = 3, D_AIR_X10 = 4, D_SUMP_PERMIL = 5,
            D_INFLOW_X10 = 6, D_PUMPED_X10 = 7, D_FLAGS = 8, D_EXPLOSIONS = 9, D_TRIP_X100 = 10, D_NODES = 11, D_CHANNELS = 12;
    public static final int DF_READY = 0, DF_ALARM = 1, DF_EXPLOSION = 2, DF_FLOODED = 3, DF_ACID = 4, DF_SUMP_SMALL = 5;

    private static final Tr IMPELLER = PanelText.tr("fan.impeller", "Impeller", "Крыльчатка"),
            FAN_STATUS = PanelText.tr("fan.status", "Flow %s m³/s · shaft %s kW · gear ×%s", "Подача %s м³/с · вал %s кВт · редуктор ×%s"),
            DEPRESSION = PanelText.tr("fan.depression", "Depression", "Депрессия"),
            DEPRESSION_TIP = PanelText.tr("fan.depression_tip", "Fan pressure %s Pa (%s mm w.g.) across the fan drift",
                    "Давление вентилятора %s Па (%s мм вод. ст.) на канале"),
            CAPTURE = PanelText.tr("degas.capture", "Capture", "Каптаж"),
            DEGAS_STATUS = PanelText.tr("degas.status", "Pump power %s kW", "Мощность насоса %s кВт"),
            FLOW = PanelText.tr("pump.flow", "Flow", "Подача"),
            ACID = PanelText.tr("pump.acid", "Acid", "Кислая"),
            DRY = PanelText.tr("pump.dry", "Dry", "Не залит"),
            DISCHARGE = PanelText.tr("pump.discharge", "Discharge", "Нагнетание"),
            DISCHARGE_TIP = PanelText.tr("pump.discharge_tip", "Discharge %s MPa: the water column of %s m in the rising main",
                    "Нагнетание %s МПа: столб воды %s м в ставе"),
            BUMP = PanelText.tr("machine.bump", "Bump", "Толчок"),
            BUMP_TIP = PanelText.tr("machine.bump_tip", "Bump test: check the rotation direction of the running shaft",
                    "Толчок: проверить направление вращения вала"),
            PUMP_STATUS = PanelText.tr("pump.status", "Head %s m · %s kW · water goes up the pipe on top",
                    "Напор %s м · %s кВт · вода — в трубу сверху");

    private static final Tr CH4 = PanelText.tr("agz.ch4", "Methane", "Метан"),
            CO = PanelText.tr("agz.co", "CO", "CO"),
            O2 = PanelText.tr("agz.o2", "Oxygen", "Кислород"),
            PPM = PanelText.tr("unit.ppm", "ppm", "ppm"),
            SUMP = PanelText.tr("agz.sump", "Sump", "Водосборник"),
            ALARM = PanelText.tr("agz.alarm", "Gas", "Газ"),
            BLAST = PanelText.tr("agz.blast", "Blast", "Взрыв"),
            FLOOD = PanelText.tr("agz.flood", "Flood", "Затоп."),
            SMALL = PanelText.tr("agz.small", "Small", "Мал"),
            LINK = PanelText.tr("agz.link", "Link", "Связь"),
            CH4_TIP = PanelText.tr("agz.ch4_tip", "Worst methane in the workings %s %% (trip %s %%)",
                    "Наибольший метан в выработках %s %% (отключение %s %%)"),
            CO_TIP = PanelText.tr("agz.co_tip", "Worst carbon monoxide %s ppm (limit 17)", "Наибольший угарный газ %s ppm (ПДК 17)"),
            O2_TIP = PanelText.tr("agz.o2_tip", "Lowest oxygen %s %% (no less than 20)", "Наименьший кислород %s %% (не ниже 20)"),
            SUMP_TIP = PanelText.tr("agz.sump_tip", "Sump %s %% · red line — flooding starts above",
                    "Водосборник %s %% · выше красной черты — затопление"),
            AIR = PanelText.tr("agz.air", "Air %s m³/s · CH4 %s m³/min · %s nodes", "Воздух %s м³/с · CH4 %s м³/мин · узлов %s"),
            WATER = PanelText.tr("agz.water", "Inflow %s m³/h · pumped %s m³/h", "Приток %s м³/ч · откачка %s м³/ч"),
            EXPLOSIONS = PanelText.tr("agz.explosions", "Explosions: %s", "Взрывов: %s"),
            NO_MINE = PanelText.tr("agz.no_mine", "No mine workings found next to the cabinet", "Рядом со шкафом нет выработок шахты");

    /** Фон — art/blockbench/mining/main_mine_fan.author.js ({@code guiFan}): кольца за приборами, ниша тягомера. */
    public static final PanelLayout FAN = PanelLayout.builder("main_mine_fan", 208, 108, FAN_CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/main_mine_fan.png"))
            .dial(8, 24, false, SHAFT_RPM10, 10, 256, Double.NaN, 0, RPM.get(), SHAFT.get(), shaftTip())
            .dial(64, 24, false, FAN_RPM, 1, 400, Double.NaN, 0, RPM.get(), IMPELLER.get(), null)
            .utube(122, 24, 56, FAN_PA, 1, 1.1 * MineScanner.MAIN_FAN.h0Pa(), DEPRESSION.get(),
                    v -> DEPRESSION_TIP.get(v.raw(FAN_PA), PanelText.num(v.raw(FAN_PA) / 9.80665, 0)))
            .lamp(156, 26, FLAGS, F_RUNNING, LampColor.GREEN, RUNNING.get())
            .button(156, 60, 44, BUTTON_BUMP, BUMP.get(), BUMP_TIP.get())
            .readout(8, 92, 192, v -> FAN_STATUS.get(v.num(FAN_Q10, 10, 1), v.num(FAN_KW10, 10, 1),
                    PanelText.num(MainMineFanBlockEntity.GEAR, 2)), null)
            .noInventory().build();

    /** Фон — art/blockbench/mining/degasification_pump.author.js ({@code guiPump}). */
    public static final PanelLayout DEGAS = PanelLayout.builder("degasification_pump", 176, 100, DEGAS_CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/degasification_pump.png"))
            .dial(8, 24, false, SHAFT_RPM10, 10, 256, Double.NaN, 0, RPM.get(), SHAFT.get(), shaftTip())
            .dial(64, 24, false, DEGAS_CAPTURE_PERMIL, 10, 100, Double.NaN, 0, PERCENT.get(), CAPTURE.get(), null)
            .lamp(122, 26, FLAGS, F_RUNNING, LampColor.GREEN, RUNNING.get())
            .readout(8, 84, 160, v -> DEGAS_STATUS.get(v.num(DEGAS_KW10, 10, 1)), null)
            .noInventory().build();

    /**
     * Фон — art/blockbench/mining/mine_drainage_pump.author.js ({@code guiPump}). Манометр нагнетания показывает
     * статический напор става ρgH: обратный клапан держит столб воды и на остановленном насосе.
     */
    public static final PanelLayout DRAINAGE = PanelLayout.builder("mine_drainage_pump", 232, 108, PUMP_CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/mine_drainage_pump.png"))
            .dial(8, 24, false, SHAFT_RPM10, 10, 256, Double.NaN, 0, RPM.get(), SHAFT.get(), shaftTip())
            .dial(64, 24, false, PUMP_FLOW_M3H10, 10, 160, Double.NaN, 0, M3H.get(), FLOW.get(), null)
            .dial(120, 24, false, PUMP_HEAD_M, M_PER_MPA, 2.5, 2.0, 1, PanelText.MPA.get(), DISCHARGE.get(),
                    v -> DISCHARGE_TIP.get(PanelText.num(v.raw(PUMP_HEAD_M) / M_PER_MPA, 2), v.raw(PUMP_HEAD_M)))
            .lamp(178, 26, FLAGS, F_RUNNING, LampColor.GREEN, RUNNING.get())
            .lamp(178, 42, FLAGS, F_ACID, LampColor.AMBER, ACID.get())
            .lamp(178, 58, FLAGS, F_DRY, LampColor.RED, DRY.get())
            .button(178, 74, 44, BUTTON_BUMP, BUMP.get(), BUMP_TIP.get())
            .readout(8, 92, 216, v -> PUMP_STATUS.get(v.raw(PUMP_HEAD_M), v.num(PUMP_KW10, 10, 1)), null)
            .noInventory().build();

    public static final PanelLayout CABINET = PanelLayout.builder("mine_dispatch_cabinet", 230, 156, D_CHANNELS)
            .dial(8, 24, true, D_CH4_X100, 100, 2, 1, 2, PERCENT.get(), CH4.get(),
                    v -> CH4_TIP.get(v.num(D_CH4_X100, 100, 2), v.num(D_TRIP_X100, 100, 2)))
            .dial(80, 24, false, D_CO_PPM, 1, 100, 17, 0, PPM.get(), CO.get(), v -> CO_TIP.get(v.raw(D_CO_PPM)))
            .dial(136, 24, false, D_O2_X10, 10, 25, Double.NaN, 1, PERCENT.get(), O2.get(), v -> O2_TIP.get(v.num(D_O2_X10, 10, 1)))
            .glass(202, 24, 74, D_SUMP_PERMIL, 0xFF8A6A3A, 0.8, Double.NaN, SUMP.get(), v -> SUMP_TIP.get(v.num(D_SUMP_PERMIL, 10, 0)))
            .lamp(80, 88, D_FLAGS, DF_ALARM, LampColor.RED, ALARM.get())
            .lamp(136, 88, D_FLAGS, DF_EXPLOSION, LampColor.RED, BLAST.get())
            .lamp(80, 104, D_FLAGS, DF_FLOODED, LampColor.AMBER, FLOOD.get())
            .lamp(136, 104, D_FLAGS, DF_SUMP_SMALL, LampColor.AMBER, SMALL.get())
            .lamp(8, 104, D_FLAGS, DF_READY, LampColor.GREEN, LINK.get())
            .readout(8, 122, 214, MinePanels::air, v -> EXPLOSIONS.get(v.raw(D_EXPLOSIONS)))
            .readout(8, 137, 214, MinePanels::water, null)
            .noInventory().build();

    private MinePanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(FAN, DEGAS, DRAINAGE, CABINET);
    }

    private static PanelLayout.Tip shaftTip() {
        return v -> SHAFT_TIP.get(v.num(SHAFT_RPM10, 10, 0));
    }

    private static Component air(PanelValues v) {
        if (!v.bit(D_FLAGS, DF_READY)) return NO_MINE.get().withStyle(ChatFormatting.GOLD);
        return AIR.get(v.num(D_AIR_X10, 10, 1), v.num(D_EMISSION_X100, 100, 2), v.raw(D_NODES));
    }

    private static Component water(PanelValues v) {
        Component c = WATER.get(v.num(D_INFLOW_X10, 10, 1), v.num(D_PUMPED_X10, 10, 1));
        return v.raw(D_SUMP_PERMIL) >= 800 ? c.copy().withStyle(ChatFormatting.RED) : c;
    }
}
