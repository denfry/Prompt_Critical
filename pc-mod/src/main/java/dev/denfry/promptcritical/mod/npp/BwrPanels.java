package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.npp.BoilingWaterReactor;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Щит кипящего реактора (T2b): мощность, период, давление в корпусе, уровень, расход рециркуляции и доля пара в зоне,
 * стержни; ключи АЗ, взвода и привода регулирующих стержней, выгрузка ТВС.
 */
public final class BwrPanels {
    public static final int POWER = 0, PERIOD = 1, RHO_PCM = 2, SCRAM_ROD = 3, CONTROL_ROD = 4, PRESSURE = 5, LEVEL = 6, FLOW = 7,
            VOID = 8, STEAM = 9, FUEL_C = 10, XE_PCM = 11, LOADED = 12, BURNUP = 13, TRIP = 14, N16_GBQ = 15, FLAGS = 16, CHANNELS = 17;
    public static final int F_TRIPPED = 0, F_NO_TURBINE = 1, F_RELIEF = 2, F_UP = 3, F_DOWN = 4;
    public static final int B_SCRAM = 0, B_RESET = 1, B_UP = 2, B_STOP = 3, B_DOWN = 4, B_UNLOAD = 5;
    public static final int SLOT_FRESH = 0, SLOT_SPENT = 1;

    private static final Tr PCT = PanelText.PERCENT, SEC = PanelText.tr("unit.s", "s", "с"),
            N = PanelText.tr("npp.n", "Power", "Мощность"),
            T_PERIOD = PanelText.tr("npp.period", "Period", "Период"),
            P_VESSEL = PanelText.tr("bwr.p", "Vessel P", "Давление"),
            ROD_SCRAM = PanelText.tr("bwr.scram_rods", "Scram rods", "Стержни АЗ"),
            ROD_CTRL = PanelText.tr("bwr.control_rods", "Control rods", "Регулирующие"),
            LEVEL_L = PanelText.tr("bwr.level", "Level", "Уровень"),
            TRIPPED = PanelText.tr("npp.tripped", "SCRAM", "АЗ"),
            NO_TURBINE = PanelText.tr("bwr.no_turbine", "No steam", "Нет отбора"),
            RELIEF = PanelText.tr("bwr.relief", "SRV", "ПК"),
            SCRAM = PanelText.tr("npp.scram", "SCRAM", "АЗ"),
            RESET = PanelText.tr("npp.reset", "Cock", "Взвод"),
            RESET_TIP = PanelText.tr("bwr.reset_tip", "Reset the scram (only with the cause gone and rods in)", "Сбросить АЗ (причина ушла, стержни внизу)"),
            UP = PanelText.tr("npp.rr_up", "▲", "▲"), STOP = PanelText.tr("npp.rr_stop", "■", "■"), DOWN = PanelText.tr("npp.rr_down", "▼", "▼"),
            UP_TIP = PanelText.tr("bwr.up_tip", "Withdraw the control rods", "Извлекать регулирующие стержни"),
            STOP_TIP = PanelText.tr("bwr.stop_tip", "Stop the control rods", "Остановить регулирующие стержни"),
            DOWN_TIP = PanelText.tr("bwr.down_tip", "Insert the control rods", "Вводить регулирующие стержни"),
            UNLOAD = PanelText.tr("npp.unload", "Unload", "Выгрузка"),
            UNLOAD_TIP = PanelText.tr("pwr.unload_tip", "Unload the most burnt assembly (reactor shut down)", "Выгрузить самую выгоревшую ТВС (реактор заглушен)"),
            FRESH = PanelText.tr("pwr.fresh", "Fresh VVER/PWR assembly, uranium or MOX up to 12 % Pu: loaded from the centre on a shut-down reactor",
                    "Свежая ТВС ВВЭР/PWR, урановая или МОКС до 12 % Pu: загружается от центра в заглушенный реактор"),
            SPENT = PanelText.tr("npp.spent", "Spent fuel", "Отработанное топливо"),
            POWER_TIP = PanelText.tr("bwr.power_tip", "Power %s %% · reactivity %s pcm · recirculation %s %% · void %s %%",
                    "Мощность %s %% · реактивность %s pcm · рециркуляция %s %% · пар в зоне %s %%"),
            STATUS = PanelText.tr("bwr.status", "%s · steam %s kg/s · fuel %s °C · Xe %s pcm · assemblies %s · burnup up to %s MWd/kg · N-16 at the turbine %s GBq",
                    "%s · пар %s кг/с · топливо %s °C · Xe %s pcm · ТВС %s · выгорание до %s МВт·сут/кг · N-16 на турбине %s ГБк");
    private static final Tr[] TRIPS = {
            PanelText.tr("npp.trip.none", "normal", "норма"),
            PanelText.tr("npp.trip.period", "SCRAM: period", "АЗ: период"),
            PanelText.tr("npp.trip.power", "SCRAM: power", "АЗ: мощность"),
            PanelText.tr("bwr.trip.pressure", "SCRAM: vessel pressure", "АЗ: давление в корпусе"),
            PanelText.tr("bwr.trip.level", "SCRAM: low level", "АЗ: уровень"),
            PanelText.tr("npp.trip.external", "SCRAM: external (ESD)", "АЗ: внешняя (ПАЗ)"),
            PanelText.tr("npp.trip.manual", "SCRAM: manual", "АЗ: ручная")};
    /** Причины для сообщения в чат ({@code panel.promptcritical.bwr.reason.*}). */
    private static final Tr[] REASONS = {
            PanelText.tr("bwr.reason.none", "normal", "норма"),
            PanelText.tr("bwr.reason.period", "period", "период"),
            PanelText.tr("bwr.reason.power", "power", "мощность"),
            PanelText.tr("bwr.reason.pressure", "vessel pressure", "давление в корпусе"),
            PanelText.tr("bwr.reason.level", "low level", "уровень"),
            PanelText.tr("bwr.reason.external", "external (ESD)", "внешняя (ПАЗ)"),
            PanelText.tr("bwr.reason.manual", "manual", "ручная")};

    public static final PanelLayout REACTOR = PanelLayout.builder("bwr_reactor", 248, 176, CHANNELS)
            .dial(8, 24, false, POWER, 10, 130, BoilingWaterReactor.TRIP_POWER * 100, 0, PCT.get(), N.get(),
                    v -> POWER_TIP.get(v.num(POWER, 10, 1), v.raw(RHO_PCM), v.raw(FLOW), v.raw(VOID)))
            .dial(64, 24, false, PERIOD, 10, 100, Double.NaN, 0, SEC.get(), T_PERIOD.get(), null)
            .dial(120, 24, false, PRESSURE, 100, 10, BoilingWaterReactor.TRIP_PRESSURE_PA / 1e6, 2, PanelText.MPA.get(), P_VESSEL.get(), null)
            .bar(176, 26, 64, SCRAM_ROD, 10, 100, 0xFFB03030, ROD_SCRAM.get(), null)
            .bar(176, 40, 64, CONTROL_ROD, 10, 100, 0xFF707070, ROD_CTRL.get(), null)
            .bar(176, 54, 64, LEVEL, 10, 100, 0xFF4060A0, LEVEL_L.get(), null)
            .lamp(176, 70, FLAGS, F_TRIPPED, LampColor.RED, TRIPPED.get())
            .lamp(8, 82, FLAGS, F_NO_TURBINE, LampColor.AMBER, NO_TURBINE.get())
            .lamp(56, 82, FLAGS, F_RELIEF, LampColor.RED, RELIEF.get())
            .slot(200, 86, SLOT_FRESH, s -> s.is(PcItems.FUEL_ASSEMBLY.get()), FRESH.get())
            .slot(222, 86, SLOT_SPENT, s -> false, SPENT.get())
            .button(8, 100, 36, B_SCRAM, SCRAM.get(), null)
            .button(48, 100, 36, B_RESET, RESET.get(), RESET_TIP.get())
            .button(88, 100, 18, B_UP, UP.get(), UP_TIP.get())
            .button(108, 100, 18, B_STOP, STOP.get(), STOP_TIP.get())
            .button(128, 100, 18, B_DOWN, DOWN.get(), DOWN_TIP.get())
            .button(152, 100, 52, B_UNLOAD, UNLOAD.get(), UNLOAD_TIP.get())
            .readout(8, 140, 232, BwrPanels::status, null)
            .build();

    private BwrPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(REACTOR);
    }

    private static Component status(PanelValues v) {
        Tr trip = TRIPS[Math.floorMod(v.raw(TRIP), TRIPS.length)];
        Component c = STATUS.get(trip.get(), v.num(STEAM, 10, 1), v.raw(FUEL_C), v.raw(XE_PCM), v.raw(LOADED), v.num(BURNUP, 100, 2),
                v.num(N16_GBQ, 1, 0));
        return v.bit(FLAGS, F_TRIPPED) ? c.copy().withStyle(ChatFormatting.RED) : c;
    }
}
