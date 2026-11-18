package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.npp.PressurizedWaterReactor;
import dev.denfry.promptcritical.sim.protection.TripReason;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Блочный щит управления ВВЭР (T2a): мощность, период, давление первого контура, стержни АЗ и РГ, уровень КД, бор,
 * водообмен, ТЭНы и впрыск, расчётная DNBR, первопричина АЗ; ключи СУЗ, снятие пусковой уставки и перегрузка ТВС.
 */
public final class PwrPanels {
    public static final int POWER = 0, PERIOD = 1, RHO_PCM = 2, AZ = 3, RG = 4, FUEL_C = 5, HOT_C = 6, COLD_C = 7, PRESSURE = 8,
            PZR_LEVEL = 9, BORON = 10, HEATER_KW = 11, SPRAY = 12, CHARGE = 13, FLOW = 14, DNBR = 15, SG_MW = 16, XE_PCM = 17,
            BURNUP = 18, LOADED = 19, FAILED = 20, SETPOINT = 21, FIRST_OUT = 22, EXCHANGE = 23, EXCHANGE_G = 24, LEAK = 25,
            PUMP1 = 26, PUMP2 = 27, FLAGS = 28, ACCIDENT = 29, CHANNELS = ACCIDENT + AccidentPanels.COUNT;
    public static final int F_TRIPPED = 0, F_AUTO = 1, F_NO_PZR = 2, F_NO_SG = 3, F_FAILED = 4, F_RG_UP = 5, F_RG_DOWN = 6,
            F_RELIEF = 7, F_SI = 8, F_LEAK = 9, F_LOW_FLUX_OFF = 10, F_HEATERS = 11;
    public static final int B_SCRAM = 0, B_RESET = 1, B_RG_UP = 2, B_RG_STOP = 3, B_RG_DOWN = 4, B_AUTO = 5, B_SP_DOWN = 6,
            B_SP_UP = 7, B_LOW_FLUX = 8, B_XCH_DOWN = 9, B_XCH_UP = 10, B_XCH_SOURCE = 11, B_UNLOAD = 12, B_LOCA = 13;
    public static final int SLOT_FRESH = 0, SLOT_SPENT = 1;

    private static final Tr PCT = PanelText.PERCENT, CELSIUS = PanelText.CELSIUS, SEC = PanelText.tr("unit.s", "s", "с"),
            MPA = PanelText.MPA,
            N = PanelText.tr("npp.n", "Power", "Мощность"),
            T_PERIOD = PanelText.tr("npp.period", "Period", "Период"),
            P1 = PanelText.tr("pwr.p1", "Primary P", "Давление 1К"),
            ROD_AZ = PanelText.tr("npp.az", "SCR (AZ)", "АЗ"),
            ROD_RG = PanelText.tr("pwr.rg", "Control group", "РГ"),
            LEVEL = PanelText.tr("pwr.pzr_level", "PZR level", "Уровень КД"),
            TRIPPED = PanelText.tr("npp.tripped", "SCRAM", "АЗ"),
            AUTO = PanelText.tr("pwr.auto", "Auto", "АРМ"),
            NO_PZR = PanelText.tr("pwr.no_pzr", "No PZR", "Нет КД"),
            RELIEF = PanelText.tr("pwr.relief", "Relief", "ИПУ"),
            SI = PanelText.tr("pwr.si", "SI", "САОЗ"),
            LEAK_L = PanelText.tr("pwr.leak", "Leak", "Течь"),
            FAILED_L = PanelText.tr("pwr.failed", "Clad", "ТВС"),
            SCRAM = PanelText.tr("npp.scram", "SCRAM", "АЗ"),
            RESET = PanelText.tr("npp.reset", "Cock", "Взвод"),
            RESET_TIP = PanelText.tr("pwr.reset_tip", "Reset the trip and cock the scram bank (only with every cause gone, 10 s after)",
                    "Сбросить АЗ и взвести аварийную группу (только когда все причины ушли, через 10 с)"),
            UP = PanelText.tr("npp.rr_up", "▲", "▲"), STOP = PanelText.tr("npp.rr_stop", "■", "■"), DOWN = PanelText.tr("npp.rr_down", "▼", "▼"),
            RG_UP_TIP = PanelText.tr("pwr.rg_up_tip", "Withdraw the control group (2 cm/s)", "Извлекать регулирующую группу (2 см/с)"),
            RG_STOP_TIP = PanelText.tr("pwr.rg_stop_tip", "Stop the control group", "Остановить регулирующую группу"),
            RG_DOWN_TIP = PanelText.tr("pwr.rg_down_tip", "Insert the control group", "Погружать регулирующую группу"),
            AUTO_BTN = PanelText.tr("pwr.auto_btn", "ARM", "АРМ"),
            AUTO_TIP = PanelText.tr("pwr.auto_tip", "Power controller drives the control group to the setpoint", "Регулятор мощности ведёт РГ к уставке"),
            SP_DOWN = PanelText.tr("npp.sp_down", "Setpoint −5 %", "Уставка −5 %"),
            SP_UP = PanelText.tr("npp.sp_up", "Setpoint +5 %", "Уставка +5 %"),
            LOW_FLUX = PanelText.tr("pwr.low_flux", "Permit", "Разреш."),
            LOW_FLUX_TIP = PanelText.tr("pwr.low_flux_tip", "Lift the 25 % start-up power trip (allowed above 10 %)",
                    "Снять пусковую уставку АЗ по мощности 25 % (разрешено выше 10 %)"),
            XCH_DOWN_TIP = PanelText.tr("pwr.xch_down_tip", "Water exchange −0.5 kg/s", "Водообмен −0,5 кг/с"),
            XCH_UP_TIP = PanelText.tr("pwr.xch_up_tip", "Water exchange +0.5 kg/s (equal letdown)", "Водообмен +0,5 кг/с (с равной продувкой)"),
            XCH_SRC = PanelText.tr("pwr.xch_src", "Source", "Источник"),
            XCH_SRC_TIP = PanelText.tr("pwr.xch_src_tip", "Exchange water: clean condensate (dilution) or 40 g/kg boric acid (boration)",
                    "Вода водообмена: чистый конденсат (разбавление) или концентрат 40 г/кг H₃BO₃ (борирование)"),
            UNLOAD = PanelText.tr("npp.unload", "Unload", "Выгрузка"),
            UNLOAD_TIP = PanelText.tr("pwr.unload_tip", "Unload the most burnt assembly (reactor shut down)", "Выгрузить самую выгоревшую ТВС (реактор заглушен)"),
            FRESH = PanelText.tr("pwr.fresh", "Fresh VVER/PWR assembly: loaded from the centre on a shut-down reactor",
                    "Свежая ТВС ВВЭР/PWR: загружается от центра в заглушенный реактор"),
            SPENT = PanelText.tr("npp.spent", "Spent fuel", "Отработанное топливо"),
            POWER_TIP = PanelText.tr("npp.power_tip", "Ion chamber %s %% · reactivity %s pcm · setpoint %s %%",
                    "Ионизационная камера %s %% · реактивность %s pcm · уставка %s %%"),
            PERIOD_TIP = PanelText.tr("npp.period_tip", "Period %s s · scram below %s s", "Период %s с · АЗ при периоде меньше %s с"),
            P_TIP = PanelText.tr("pwr.p_tip", "Pressurizer %s MPa · heaters %s kW · spray %s kg/s · trips %s / %s MPa, relief %s MPa",
                    "КД %s МПа · ТЭН %s кВт · впрыск %s кг/с · АЗ %s / %s МПа, ИПУ %s МПа"),
            LEVEL_TIP = PanelText.tr("pwr.level_tip", "Pressurizer level %s %% · charging %s kg/s · leak %s kg/s",
                    "Уровень КД %s %% · подпитка %s кг/с · течь %s кг/с"),
            CHEM = PanelText.tr("pwr.chem", "H₃BO₃ %s g/kg · exchange %s kg/s of %s g/kg · loop %s → %s °C · flow %s %% (pumps %s / %s %%) · fuel %s °C",
                    "H₃BO₃ %s г/кг · водообмен %s кг/с по %s г/кг · петля %s → %s °C · расход %s %% (ГЦН %s / %s %%) · топливо %s °C"),
            STATUS = PanelText.tr("pwr.status", "%s · DNBR %s · SG %s MW · Xe %s pcm · assemblies %s, failed %s · burnup up to %s MWd/kg",
                    "%s · DNBR %s · ПГ %s МВт · Xe %s pcm · ТВС %s, разгерметизировано %s · выгорание до %s МВт·сут/кг"),
            NORMAL = PanelText.tr("npp.trip.none", "normal", "норма"),
            TRIP_PREFIX = PanelText.tr("pwr.trip", "SCRAM: %s", "АЗ: %s");
    private static final Tr[] REASONS = {
            PanelText.tr("pwr.reason.manual", "manual", "ручная"),
            PanelText.tr("pwr.reason.high_flux", "power", "мощность"),
            PanelText.tr("pwr.reason.short_period", "period", "период"),
            PanelText.tr("pwr.reason.high_primary_pressure", "high pressure", "давление высокое"),
            PanelText.tr("pwr.reason.low_primary_pressure", "low pressure", "давление низкое"),
            PanelText.tr("pwr.reason.low_coolant_flow", "low flow", "расход"),
            PanelText.tr("pwr.reason.high_coolant_temp", "hot leg temperature", "температура горячей нитки"),
            PanelText.tr("pwr.reason.low_dnbr", "low DNBR", "запас до кризиса"),
            PanelText.tr("pwr.reason.low_sg_level", "low SG level", "уровень ПГ"),
            PanelText.tr("pwr.reason.high_containment_pressure", "containment pressure", "давление в гермообъёме"),
            PanelText.tr("pwr.reason.loss_of_drive_power", "RCP speed / drives", "обороты ГЦН / приводы"),
            PanelText.tr("pwr.reason.turbine_trip", "turbine trip", "отключение турбины"),
            PanelText.tr("pwr.reason.seismic", "seismic", "сейсмика"),
            PanelText.tr("pwr.reason.lua_request", "external (ESD)", "внешняя (ПАЗ)")};

    public static final PanelLayout REACTOR = PanelLayout.builder("pwr_reactor", 248, 176, CHANNELS)
            .dial(8, 24, false, POWER, 10, 130, 107, 0, PCT.get(), N.get(),
                    v -> POWER_TIP.get(v.num(POWER, 10, 1), v.raw(RHO_PCM), v.raw(SETPOINT)))
            .dial(64, 24, false, PERIOD, 10, 100, Double.NaN, 0, SEC.get(), T_PERIOD.get(),
                    v -> PERIOD_TIP.get(v.raw(PERIOD) <= 0 ? "∞" : v.num(PERIOD, 10, 1), 10))
            .dial(120, 24, false, PRESSURE, 100, 20, 16.8, 1, MPA.get(), P1.get(),
                    v -> P_TIP.get(v.num(PRESSURE, 100, 2), v.raw(HEATER_KW), v.num(SPRAY, 10, 1), "16.8", "13.7",
                            PanelText.num(PressurizedWaterReactor.RELIEF_PA / 1e6, 1)))
            .bar(176, 26, 64, AZ, 10, 100, 0xFFB03030, ROD_AZ.get(), null)
            .bar(176, 40, 64, RG, 10, 100, 0xFF707070, ROD_RG.get(), null)
            .bar(176, 54, 64, PZR_LEVEL, 10, 100, 0xFF4060A0, LEVEL.get(),
                    v -> LEVEL_TIP.get(v.num(PZR_LEVEL, 10, 1), v.num(CHARGE, 10, 1), v.num(LEAK, 10, 1)))
            .lamp(176, 70, FLAGS, F_TRIPPED, LampColor.RED, TRIPPED.get())
            .lamp(212, 70, FLAGS, F_AUTO, LampColor.GREEN, AUTO.get())
            .lamp(8, 82, FLAGS, F_NO_PZR, LampColor.AMBER, NO_PZR.get())
            .lamp(44, 82, FLAGS, F_RELIEF, LampColor.RED, RELIEF.get())
            .lamp(80, 82, FLAGS, F_SI, LampColor.AMBER, SI.get())
            .lamp(116, 82, FLAGS, F_LEAK, LampColor.RED, LEAK_L.get())
            .lamp(152, 82, FLAGS, F_FAILED, LampColor.RED, FAILED_L.get())
            .slot(200, 86, SLOT_FRESH, s -> s.is(PcItems.FUEL_ASSEMBLY.get()), FRESH.get())
            .slot(222, 86, SLOT_SPENT, s -> false, SPENT.get())
            .button(8, 100, 36, B_SCRAM, SCRAM.get(), null)
            .button(48, 100, 36, B_RESET, RESET.get(), RESET_TIP.get())
            .button(88, 100, 18, B_RG_UP, UP.get(), RG_UP_TIP.get())
            .button(108, 100, 18, B_RG_STOP, STOP.get(), RG_STOP_TIP.get())
            .button(128, 100, 18, B_RG_DOWN, DOWN.get(), RG_DOWN_TIP.get())
            .button(152, 100, 24, B_AUTO, AUTO_BTN.get(), AUTO_TIP.get())
            .button(180, 100, 20, B_SP_DOWN, Component.literal("−"), SP_DOWN.get())
            .button(202, 100, 20, B_SP_UP, Component.literal("+"), SP_UP.get())
            .button(8, 120, 48, B_LOW_FLUX, LOW_FLUX.get(), LOW_FLUX_TIP.get())
            .button(60, 120, 18, B_XCH_DOWN, Component.literal("−"), XCH_DOWN_TIP.get())
            .button(80, 120, 18, B_XCH_UP, Component.literal("+"), XCH_UP_TIP.get())
            .button(102, 120, 48, B_XCH_SOURCE, XCH_SRC.get(), XCH_SRC_TIP.get())
            .button(154, 120, 52, B_UNLOAD, UNLOAD.get(), UNLOAD_TIP.get())
            .button(210, 120, 30, B_LOCA, AccidentPanels.DRILL.get(), AccidentPanels.DRILL_TIP.get())
            .readout(8, 140, 232, PwrPanels::chemistry, null)
            .readout(8, 154, 232, PwrPanels::status, null)
            .readout(8, 166, 232, v -> AccidentPanels.line(v, ACCIDENT), v -> AccidentPanels.tip(v, ACCIDENT))
            .build();

    private PwrPanels() {
    }

    private static Component chemistry(PanelValues v) {
        return CHEM.get(v.num(BORON, 100, 2), v.num(EXCHANGE, 10, 1), v.num(EXCHANGE_G, 10, 0), v.raw(COLD_C), v.raw(HOT_C), v.raw(FLOW),
                v.raw(PUMP1), v.raw(PUMP2), v.raw(FUEL_C));
    }

    private static Component status(PanelValues v) {
        int first = v.raw(FIRST_OUT);
        Component state = first > 0 && first <= REASONS.length ? TRIP_PREFIX.get(REASONS[first - 1].get())
                : v.bit(FLAGS, F_TRIPPED) ? TRIPPED.get() : NORMAL.get();
        Component c = STATUS.get(state, v.num(DNBR, 100, 2), v.num(SG_MW, 10, 1), v.raw(XE_PCM), v.raw(LOADED), v.raw(FAILED),
                v.num(BURNUP, 100, 2));
        return v.bit(FLAGS, F_TRIPPED) || v.bit(FLAGS, F_FAILED) || v.bit(FLAGS, F_LEAK) ? c.copy().withStyle(ChatFormatting.RED) : c;
    }

    /** Порядковый номер причины для канала {@link #FIRST_OUT}: 0 — нет. */
    static int reasonCode(TripReason r) {
        return r == null ? 0 : r.ordinal() + 1;
    }
}
