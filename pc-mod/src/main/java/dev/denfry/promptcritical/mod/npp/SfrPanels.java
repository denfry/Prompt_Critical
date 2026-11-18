package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.npp.SodiumFastReactor;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Щит натриевого быстрого реактора (T4): мощность, период, температура натрия на выходе зоны, на входе и во втором
 * контуре, стержни, Pu-239 в зоне и в экране, КВ; лампы натриевого пожара, течи натрий–вода и разрывной мембраны.
 */
public final class SfrPanels {
    public static final int POWER = 0, PERIOD = 1, RHO_PCM = 2, SCRAM_ROD = 3, CONTROL_ROD = 4, INLET_C = 5, OUTLET_C = 6,
            INT_HOT_C = 7, FLOW = 8, STEAM = 9, FUEL_C = 10, CORE_PU = 11, BLANKET_PU_G = 12, BR = 13, TRIP = 14, FLAGS = 15,
            CHANNELS = 16;
    public static final int F_TRIPPED = 0, F_NO_STEAM = 1, F_FIRE = 2, F_NA_WATER = 3, F_DISC = 4, F_NO_CORE = 5;
    public static final int B_SCRAM = 0, B_RESET = 1, B_UP = 2, B_STOP = 3, B_DOWN = 4;
    public static final int SLOT_FRESH = 0;

    private static final Tr PCT = PanelText.PERCENT, SEC = PanelText.tr("unit.s", "s", "с"),
            DEG = PanelText.tr("sfr.deg_c", "°C", "°C"),
            N = PanelText.tr("npp.n", "Power", "Мощность"),
            T_PERIOD = PanelText.tr("npp.period", "Period", "Период"),
            T_OUT = PanelText.tr("sfr.outlet", "Core outlet", "Выход зоны"),
            ROD_SCRAM = PanelText.tr("bwr.scram_rods", "Scram rods", "Стержни АЗ"),
            ROD_CTRL = PanelText.tr("bwr.control_rods", "Control rods", "Регулирующие"),
            FLOW_L = PanelText.tr("sfr.flow", "Primary Na flow", "Расход Na 1 к."),
            TRIPPED = PanelText.tr("npp.tripped", "SCRAM", "АЗ"),
            NO_STEAM = PanelText.tr("sfr.no_steam", "No SG", "Нет ПГ"),
            FIRE = PanelText.tr("sfr.fire", "Na fire", "Пожар Na"),
            NA_WATER = PanelText.tr("sfr.na_water", "Na–H₂O", "Na–H₂O"),
            DISC = PanelText.tr("sfr.disc", "Disc", "Мембрана"),
            NO_CORE = PanelText.tr("sfr.no_core", "No core", "Нет зоны"),
            SCRAM = PanelText.tr("npp.scram", "SCRAM", "АЗ"),
            RESET = PanelText.tr("npp.reset", "Cock", "Взвод"),
            RESET_TIP = PanelText.tr("bwr.reset_tip", "Reset the scram (only with the cause gone and rods in)", "Сбросить АЗ (причина ушла, стержни внизу)"),
            UP = PanelText.tr("npp.rr_up", "▲", "▲"), STOP = PanelText.tr("npp.rr_stop", "■", "■"), DOWN = PanelText.tr("npp.rr_down", "▼", "▼"),
            UP_TIP = PanelText.tr("bwr.up_tip", "Withdraw the control rods", "Извлекать регулирующие стержни"),
            STOP_TIP = PanelText.tr("bwr.stop_tip", "Stop the control rods", "Остановить регулирующие стержни"),
            DOWN_TIP = PanelText.tr("bwr.down_tip", "Insert the control rods", "Вводить регулирующие стержни"),
            FRESH = PanelText.tr("sfr.fresh", "MOX assembly (15–35 % Pu): the core, loaded into a shut-down empty reactor",
                    "ТВС МОКС (15–35 % Pu): зона, загружается в пустой заглушенный реактор"),
            POWER_TIP = PanelText.tr("sfr.power_tip", "Power %s %% · reactivity %s pcm · primary flow %s %%",
                    "Мощность %s %% · реактивность %s pcm · расход 1 к. %s %%"),
            STATUS = PanelText.tr("sfr.status", "%s · in %s °C · intermediate %s °C · steam %s kg/s · fuel %s °C · Pu in core %s kg · in blanket %s g · BR %s",
                    "%s · вход %s °C · второй контур %s °C · пар %s кг/с · топливо %s °C · Pu в зоне %s кг · в экране %s г · КВ %s");
    private static final Tr[] TRIPS = {
            PanelText.tr("npp.trip.none", "normal", "норма"),
            PanelText.tr("npp.trip.power", "SCRAM: power", "АЗ: мощность"),
            PanelText.tr("npp.trip.period", "SCRAM: period", "АЗ: период"),
            PanelText.tr("sfr.trip.outlet", "SCRAM: core outlet temperature", "АЗ: температура на выходе"),
            PanelText.tr("sfr.trip.flow", "SCRAM: power to flow", "АЗ: мощность/расход"),
            PanelText.tr("sfr.trip.na_water", "SCRAM: sodium–water leak", "АЗ: течь натрий–вода"),
            PanelText.tr("npp.trip.external", "SCRAM: external (ESD)", "АЗ: внешняя (ПАЗ)"),
            PanelText.tr("npp.trip.manual", "SCRAM: manual", "АЗ: ручная")};
    /** Причины для сообщения в чат, по порядку {@link SodiumFastReactor.Trip}. */
    private static final Tr[] REASONS = {
            PanelText.tr("sfr.reason.none", "normal", "норма"),
            PanelText.tr("sfr.reason.power", "power", "мощность"),
            PanelText.tr("sfr.reason.period", "period", "период"),
            PanelText.tr("sfr.reason.outlet_temp", "core outlet temperature", "температура на выходе зоны"),
            PanelText.tr("sfr.reason.flow", "power to flow", "мощность/расход"),
            PanelText.tr("sfr.reason.sodium_water", "sodium–water leak", "течь натрий–вода"),
            PanelText.tr("sfr.reason.external", "external (ESD)", "внешняя (ПАЗ)"),
            PanelText.tr("sfr.reason.manual", "manual", "ручная")};

    public static final PanelLayout REACTOR = PanelLayout.builder("sfr_reactor", 248, 176, CHANNELS)
            .dial(8, 24, false, POWER, 10, 130, SodiumFastReactor.TRIP_POWER * 100, 0, PCT.get(), N.get(),
                    v -> POWER_TIP.get(v.num(POWER, 10, 1), v.raw(RHO_PCM), v.raw(FLOW)))
            .dial(64, 24, false, PERIOD, 10, 100, Double.NaN, 0, SEC.get(), T_PERIOD.get(), null)
            .dial(120, 24, false, OUTLET_C, 1, 700, SodiumFastReactor.TRIP_OUTLET_K - 273.15, 0, DEG.get(), T_OUT.get(), null)
            .bar(176, 26, 64, SCRAM_ROD, 10, 100, 0xFFB03030, ROD_SCRAM.get(), null)
            .bar(176, 40, 64, CONTROL_ROD, 10, 100, 0xFF707070, ROD_CTRL.get(), null)
            .bar(176, 54, 64, FLOW, 1, 110, 0xFFC0A040, FLOW_L.get(), null)
            .lamp(176, 70, FLAGS, F_TRIPPED, LampColor.RED, TRIPPED.get())
            .lamp(8, 82, FLAGS, F_NO_STEAM, LampColor.AMBER, NO_STEAM.get())
            .lamp(56, 82, FLAGS, F_FIRE, LampColor.RED, FIRE.get())
            .lamp(104, 82, FLAGS, F_NA_WATER, LampColor.RED, NA_WATER.get())
            .lamp(152, 82, FLAGS, F_DISC, LampColor.AMBER, DISC.get())
            .lamp(176, 120, FLAGS, F_NO_CORE, LampColor.AMBER, NO_CORE.get())
            .slot(222, 86, SLOT_FRESH, s -> s.is(PcItems.FUEL_ASSEMBLY.get()), FRESH.get())
            .button(8, 100, 36, B_SCRAM, SCRAM.get(), null)
            .button(48, 100, 36, B_RESET, RESET.get(), RESET_TIP.get())
            .button(88, 100, 18, B_UP, UP.get(), UP_TIP.get())
            .button(108, 100, 18, B_STOP, STOP.get(), STOP_TIP.get())
            .button(128, 100, 18, B_DOWN, DOWN.get(), DOWN_TIP.get())
            .readout(8, 140, 232, SfrPanels::status, null)
            .build();

    private SfrPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(REACTOR);
    }

    static Component reason(SodiumFastReactor.Trip t) {
        return REASONS[t.ordinal()].get();
    }

    private static Component status(PanelValues v) {
        Tr trip = TRIPS[Math.floorMod(v.raw(TRIP), TRIPS.length)];
        Component c = STATUS.get(trip.get(), v.raw(INLET_C), v.raw(INT_HOT_C), v.num(STEAM, 10, 1), v.raw(FUEL_C),
                v.num(CORE_PU, 10, 1), v.raw(BLANKET_PU_G), v.num(BR, 100, 2));
        return v.bit(FLAGS, F_TRIPPED) ? c.copy().withStyle(ChatFormatting.RED) : c;
    }
}
