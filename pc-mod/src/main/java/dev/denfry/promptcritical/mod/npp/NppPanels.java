package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.reactor.ChannelReactor;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Щит A0 блочного пульта АЭС (T1): мощность по ионизационной камере, период, стержни АЗ/РР/АР, температуры топлива,
 * графита и воды, расход, паросодержание, ксенон, выгорание, сигнализация АЗ; ключи СУЗ и перегрузки.
 */
public final class NppPanels {
    public static final int POWER = 0, PERIOD = 1, RHO_PCM = 2, AZ = 3, RR = 4, AR = 5, FUEL_C = 6, GRAPHITE_C = 7, IN_C = 8, OUT_C = 9,
            FLOW = 10, QUALITY = 11, SG_KW = 12, XE_PCM = 13, BURNUP = 14, LOADED = 15, FAILED = 16, SETPOINT = 17, TRIP = 18, FLAGS = 19,
            ACCIDENT = 20, ORM = ACCIDENT + AccidentPanels.COUNT, CHANNELS = ORM + 1;
    public static final int F_TRIPPED = 0, F_AUTO = 1, F_PROT_OFF = 2, F_DRYOUT = 3, F_NO_SG = 4, F_FAILED = 5, F_RR_UP = 6, F_RR_DOWN = 7,
            F_ORM_LOW = 8;
    public static final int B_SCRAM = 0, B_RESET = 1, B_RR_UP = 2, B_RR_STOP = 3, B_RR_DOWN = 4, B_AUTO = 5, B_SP_DOWN = 6, B_SP_UP = 7,
            B_PROTECTION = 8, B_UNLOAD = 9, B_LOCA = 10;
    public static final int SLOT_FRESH = 0, SLOT_SPENT = 1;

    private static final Tr PCT = PanelText.PERCENT, CELSIUS = PanelText.CELSIUS, SEC = PanelText.tr("unit.s", "s", "с"),
            N = PanelText.tr("npp.n", "Power", "Мощность"),
            T_PERIOD = PanelText.tr("npp.period", "Period", "Период"),
            ROD_AZ = PanelText.tr("npp.az", "SCR (AZ)", "АЗ"),
            ROD_RR = PanelText.tr("npp.rr", "Shim (RR)", "РР"),
            ROD_AR = PanelText.tr("npp.ar", "Auto (AR)", "АР"),
            OUTLET = PanelText.tr("npp.outlet", "Outlet", "Выход"),
            TRIPPED = PanelText.tr("npp.tripped", "SCRAM", "АЗ"),
            AUTO = PanelText.tr("npp.auto", "AR auto", "АР авт."),
            PROT_OFF = PanelText.tr("npp.prot_off", "Prot. off", "Защ. выв."),
            DRYOUT = PanelText.tr("npp.dryout", "Dryout", "Кризис"),
            NO_SG = PanelText.tr("npp.no_sg", "No SG", "Нет ПГ"),
            FAILED_L = PanelText.tr("npp.failed", "Channel", "Канал"),
            ORM_LOW = PanelText.tr("npp.orm_low", "ORM", "ОЗР"),
            ORM_TIP = PanelText.tr("npp.orm_tip", "Operational reactivity margin %s equivalent rods · keep at least %s",
                    "Оперативный запас реактивности %s эквивалентных стержней · не меньше %s"),
            SCRAM = PanelText.tr("npp.scram", "SCRAM", "АЗ"),
            RESET = PanelText.tr("npp.reset", "Cock", "Взвод"),
            RESET_TIP = PanelText.tr("npp.reset_tip", "Cock the scram rods (only with the cause gone)", "Взвести стержни АЗ (только если причины нет)"),
            RR_UP = PanelText.tr("npp.rr_up", "▲", "▲"), RR_STOP = PanelText.tr("npp.rr_stop", "■", "■"), RR_DOWN = PanelText.tr("npp.rr_down", "▼", "▼"),
            RR_UP_TIP = PanelText.tr("npp.rr_up_tip", "Withdraw the shim rods", "Извлекать стержни РР"),
            RR_STOP_TIP = PanelText.tr("npp.rr_stop_tip", "Stop the shim rods", "Остановить стержни РР"),
            RR_DOWN_TIP = PanelText.tr("npp.rr_down_tip", "Insert the shim rods", "Погружать стержни РР"),
            AUTO_BTN = PanelText.tr("npp.auto_btn", "AR", "АР"),
            AUTO_TIP = PanelText.tr("npp.auto_tip", "Automatic regulator holds the power setpoint", "Автоматический регулятор держит уставку мощности"),
            SP_DOWN = PanelText.tr("npp.sp_down", "Setpoint −5 %", "Уставка −5 %"),
            SP_UP = PanelText.tr("npp.sp_up", "Setpoint +5 %", "Уставка +5 %"),
            PROT_BTN = PanelText.tr("npp.prot_btn", "Protections", "Защиты"),
            PROT_TIP = PanelText.tr("npp.prot_tip", "Bypass the period, power, flow and outlet trips (the button and external scram stay)",
                    "Вывести защиты по периоду, мощности, расходу и температуре (кнопка АЗ и внешний сигнал остаются)"),
            UNLOAD = PanelText.tr("npp.unload", "Unload", "Выгрузка"),
            UNLOAD_TIP = PanelText.tr("npp.unload_tip", "Unload the most burnt channel (reactor shut down)", "Выгрузить самый выгоревший канал (реактор заглушен)"),
            FRESH = PanelText.tr("npp.fresh", "Fresh AM-1 channel fuel, uranium or MOX up to 12 % Pu: loaded into empty channels on a shut-down reactor",
                    "Свежее топливо канала АМ-1, урановое или МОКС до 12 % Pu: загружается в пустые каналы заглушенного реактора"),
            SPENT = PanelText.tr("npp.spent", "Spent fuel", "Отработанное топливо"),
            POWER_TIP = PanelText.tr("npp.power_tip", "Ion chamber %s %% · reactivity %s pcm · setpoint %s %%",
                    "Ионизационная камера %s %% · реактивность %s pcm · уставка %s %%"),
            PERIOD_TIP = PanelText.tr("npp.period_tip", "Period %s s · scram below %s s", "Период %s с · АЗ при периоде меньше %s с"),
            OUT_TIP = PanelText.tr("npp.out_tip", "Water %s → %s °C at 10 MPa · flow %s %% · exit quality %s %%",
                    "Вода %s → %s °C при 10 МПа · расход %s %% · паросодержание %s %%"),
            STATUS = PanelText.tr("npp.status", "%s · fuel %s °C · graphite %s °C · SG %s MW · Xe %s pcm · channels %s, failed %s · max burnup %s MWd/kg",
                    "%s · топливо %s °C · графит %s °C · ПГ %s МВт · Xe %s pcm · каналов %s, разрушено %s · выгорание до %s МВт·сут/кг");
    private static final Tr[] TRIPS = {
            PanelText.tr("npp.trip.none", "normal", "норма"),
            PanelText.tr("npp.trip.period", "SCRAM: period", "АЗ: период"),
            PanelText.tr("npp.trip.power", "SCRAM: power", "АЗ: мощность"),
            PanelText.tr("npp.trip.flow", "SCRAM: flow", "АЗ: расход"),
            PanelText.tr("npp.trip.outlet", "SCRAM: outlet temperature", "АЗ: температура на выходе"),
            PanelText.tr("npp.trip.external", "SCRAM: external (ESD)", "АЗ: внешняя (ПАЗ)"),
            PanelText.tr("npp.trip.manual", "SCRAM: manual", "АЗ: ручная")};

    public static final PanelLayout REACTOR = PanelLayout.builder("power_reactor", 248, 196, CHANNELS)
            .dial(8, 24, false, POWER, 10, 130, ChannelReactor.TRIP_POWER * 100, 0, PCT.get(), N.get(),
                    v -> POWER_TIP.get(v.num(POWER, 10, 1), v.raw(RHO_PCM), v.raw(SETPOINT)))
            .dial(64, 24, false, PERIOD, 10, 100, Double.NaN, 0, SEC.get(), T_PERIOD.get(),
                    v -> PERIOD_TIP.get(v.raw(PERIOD) <= 0 ? "∞" : v.num(PERIOD, 10, 1), Math.round(ChannelReactor.TRIP_PERIOD_S)))
            .dial(120, 24, false, OUT_C, 1, 350, ChannelReactor.TRIP_OUTLET_K - 273.15, 0, CELSIUS.get(), OUTLET.get(),
                    v -> OUT_TIP.get(v.raw(IN_C), v.raw(OUT_C), v.raw(FLOW), v.num(QUALITY, 10, 1)))
            .bar(176, 26, 64, AZ, 10, 100, 0xFFB03030, ROD_AZ.get(), null)
            .bar(176, 40, 64, RR, 10, 100, 0xFF707070, ROD_RR.get(),
                    v -> ORM_TIP.get(v.num(ORM, 10, 1), Math.round(ChannelReactor.ORM_MIN_RODS)))
            .bar(176, 54, 64, AR, 10, 100, 0xFF4060A0, ROD_AR.get(), null)
            .lamp(176, 70, FLAGS, F_TRIPPED, LampColor.RED, TRIPPED.get())
            .lamp(212, 70, FLAGS, F_AUTO, LampColor.GREEN, AUTO.get())
            .lamp(8, 82, FLAGS, F_PROT_OFF, LampColor.RED, PROT_OFF.get())
            .lamp(56, 82, FLAGS, F_DRYOUT, LampColor.RED, DRYOUT.get())
            .lamp(104, 82, FLAGS, F_NO_SG, LampColor.AMBER, NO_SG.get())
            .lamp(152, 82, FLAGS, F_FAILED, LampColor.RED, FAILED_L.get())
            .lamp(180, 122, FLAGS, F_ORM_LOW, LampColor.AMBER, ORM_LOW.get())
            .slot(200, 86, SLOT_FRESH, s -> s.is(PcItems.FUEL_ASSEMBLY.get()), FRESH.get())
            .slot(222, 86, SLOT_SPENT, s -> false, SPENT.get())
            .button(8, 100, 36, B_SCRAM, SCRAM.get(), null)
            .button(48, 100, 36, B_RESET, RESET.get(), RESET_TIP.get())
            .button(88, 100, 18, B_RR_UP, RR_UP.get(), RR_UP_TIP.get())
            .button(108, 100, 18, B_RR_STOP, RR_STOP.get(), RR_STOP_TIP.get())
            .button(128, 100, 18, B_RR_DOWN, RR_DOWN.get(), RR_DOWN_TIP.get())
            .button(152, 100, 24, B_AUTO, AUTO_BTN.get(), AUTO_TIP.get())
            .button(180, 100, 20, B_SP_DOWN, Component.literal("−"), SP_DOWN.get())
            .button(202, 100, 20, B_SP_UP, Component.literal("+"), SP_UP.get())
            .button(8, 120, 64, B_PROTECTION, PROT_BTN.get(), PROT_TIP.get())
            .button(76, 120, 52, B_UNLOAD, UNLOAD.get(), UNLOAD_TIP.get())
            .button(132, 120, 40, B_LOCA, AccidentPanels.DRILL.get(), AccidentPanels.DRILL_TIP.get())
            .readout(8, 140, 232, NppPanels::status, null)
            .readout(8, 160, 232, v -> AccidentPanels.line(v, ACCIDENT), v -> AccidentPanels.tip(v, ACCIDENT))
            .build();

    private NppPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(REACTOR, PwrPanels.REACTOR, Vver70Panels.BPU, BwrPanels.REACTOR, CanduPanels.REACTOR, SfrPanels.REACTOR);
    }

    private static Component status(PanelValues v) {
        Tr trip = TRIPS[Math.floorMod(v.raw(TRIP), TRIPS.length)];
        Component c = STATUS.get(trip.get(), v.raw(FUEL_C), v.raw(GRAPHITE_C), v.num(SG_KW, 1000, 1), v.raw(XE_PCM), v.raw(LOADED),
                v.raw(FAILED), v.num(BURNUP, 100, 2));
        return v.bit(FLAGS, F_TRIPPED) || v.bit(FLAGS, F_FAILED) || v.bit(FLAGS, F_PROT_OFF) ? c.copy().withStyle(ChatFormatting.RED) : c;
    }
}
