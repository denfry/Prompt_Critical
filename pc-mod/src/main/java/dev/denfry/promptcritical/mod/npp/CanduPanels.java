package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.npp.CanduReactor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Щит CANDU (T3): мощность, период, стержни аварийного останова и регуляторы, выход теплоносителя, расход, загрузка
 * каналов, перегрузки на ходу и тритий в замедлителе; ключи АЗ, взвода, регуляторов и перегрузочной машины.
 */
public final class CanduPanels {
    public static final int POWER = 0, PERIOD = 1, RHO_PCM = 2, SHUTOFF = 3, CONTROL = 4, OUT_C = 5, FLOW = 6, FUEL_C = 7, XE_PCM = 8,
            LOADED = 9, BURNUP = 10, REFUELLED = 11, TRITIUM_GBQ = 12, TRIP = 13, FLAGS = 14, MAGAZINE = 15, CHANNELS = 16;
    public static final int F_TRIPPED = 0, F_NO_SG = 1;
    public static final int B_SCRAM = 0, B_RESET = 1, B_UP = 2, B_STOP = 3, B_DOWN = 4, B_REFUEL = 5;
    public static final int SLOT_FRESH = 0, SLOT_SPENT = 1;

    private static final Tr PCT = PanelText.PERCENT, CELSIUS = PanelText.CELSIUS, SEC = PanelText.tr("unit.s", "s", "с"),
            N = PanelText.tr("npp.n", "Power", "Мощность"),
            T_PERIOD = PanelText.tr("npp.period", "Period", "Период"),
            OUTLET = PanelText.tr("npp.outlet", "Outlet", "Выход"),
            ROD_SHUTOFF = PanelText.tr("candu.shutoff", "Shutoff rods", "Стержни останова"),
            ROD_CONTROL = PanelText.tr("candu.control", "Regulators", "Регуляторы"),
            TRIPPED = PanelText.tr("npp.tripped", "SCRAM", "АЗ"),
            NO_SG = PanelText.tr("npp.no_sg", "No SG", "Нет ПГ"),
            SCRAM = PanelText.tr("npp.scram", "SCRAM", "АЗ"),
            RESET = PanelText.tr("npp.reset", "Cock", "Взвод"),
            RESET_TIP = PanelText.tr("candu.reset_tip", "Reset the trip (only with the cause gone and rods in)", "Сбросить АЗ (причина ушла, стержни внизу)"),
            UP = PanelText.tr("npp.rr_up", "▲", "▲"), STOP = PanelText.tr("npp.rr_stop", "■", "■"), DOWN = PanelText.tr("npp.rr_down", "▼", "▼"),
            UP_TIP = PanelText.tr("candu.up_tip", "Withdraw the regulators", "Извлекать регуляторы"),
            STOP_TIP = PanelText.tr("candu.stop_tip", "Stop the regulators", "Остановить регуляторы"),
            DOWN_TIP = PanelText.tr("candu.down_tip", "Insert the regulators", "Вводить регуляторы"),
            REFUEL = PanelText.tr("candu.refuel", "Refuel", "Перегрузка"),
            REFUEL_TIP = PanelText.tr("candu.refuel_tip",
                    "Bundles go from the slot into the machine magazine (up to 24). Shut down: fill the next empty channel with 12. At power: push 8 into the most burnt channel",
                    "Пучки из слота уходят в магазин машины (до 24). Заглушен: заполнить следующий пустой канал 12 пучками. На мощности: вдвинуть 8 в самый выгоревший канал"),
            FRESH = PanelText.tr("candu.fresh", "Fresh CANDU 37-element bundles: natural uranium or MOX up to 12 % Pu",
                    "Свежие 37-элементные пучки CANDU: природный уран или МОКС до 12 % Pu"),
            SPENT = PanelText.tr("npp.spent", "Spent fuel", "Отработанное топливо"),
            POWER_TIP = PanelText.tr("npp.power_tip", "Ion chamber %s %% · reactivity %s pcm · setpoint %s %%",
                    "Ионизационная камера %s %% · реактивность %s pcm · уставка %s %%"),
            STATUS = PanelText.tr("candu.status", "%s · flow %s %% · fuel %s °C · Xe %s pcm · channels %s · burnup up to %s MWd/kg · refuelled %s bundles · magazine %s · tritium %s GBq",
                    "%s · расход %s %% · топливо %s °C · Xe %s pcm · каналов %s · выгорание до %s МВт·сут/кг · перегружено пучков %s · в магазине %s · тритий %s ГБк");
    private static final Tr[] TRIPS = {
            PanelText.tr("npp.trip.none", "normal", "норма"),
            PanelText.tr("npp.trip.period", "SCRAM: period", "АЗ: период"),
            PanelText.tr("npp.trip.power", "SCRAM: power", "АЗ: мощность"),
            PanelText.tr("npp.trip.flow", "SCRAM: flow", "АЗ: расход"),
            PanelText.tr("npp.trip.external", "SCRAM: external (ESD)", "АЗ: внешняя (ПАЗ)"),
            PanelText.tr("npp.trip.manual", "SCRAM: manual", "АЗ: ручная")};

    public static final PanelLayout REACTOR = PanelLayout.builder("candu_reactor", 248, 176, CHANNELS)
            .dial(8, 24, false, POWER, 10, 130, CanduReactor.TRIP_POWER * 100, 0, PCT.get(), N.get(),
                    v -> POWER_TIP.get(v.num(POWER, 10, 1), v.raw(RHO_PCM), 100))
            .dial(64, 24, false, PERIOD, 10, 100, Double.NaN, 0, SEC.get(), T_PERIOD.get(), null)
            .dial(120, 24, false, OUT_C, 1, 350, 320, 0, CELSIUS.get(), OUTLET.get(), null)
            .bar(176, 26, 64, SHUTOFF, 10, 100, 0xFFB03030, ROD_SHUTOFF.get(), null)
            .bar(176, 40, 64, CONTROL, 10, 100, 0xFF707070, ROD_CONTROL.get(), null)
            .lamp(176, 70, FLAGS, F_TRIPPED, LampColor.RED, TRIPPED.get())
            .lamp(8, 82, FLAGS, F_NO_SG, LampColor.AMBER, NO_SG.get())
            .slot(200, 86, SLOT_FRESH, s -> s.is(PcItems.FUEL_ASSEMBLY.get()), FRESH.get())
            .slot(222, 86, SLOT_SPENT, s -> false, SPENT.get())
            .button(8, 100, 36, B_SCRAM, SCRAM.get(), null)
            .button(48, 100, 36, B_RESET, RESET.get(), RESET_TIP.get())
            .button(88, 100, 18, B_UP, UP.get(), UP_TIP.get())
            .button(108, 100, 18, B_STOP, STOP.get(), STOP_TIP.get())
            .button(128, 100, 18, B_DOWN, DOWN.get(), DOWN_TIP.get())
            .button(152, 100, 60, B_REFUEL, REFUEL.get(), REFUEL_TIP.get())
            .readout(8, 140, 232, CanduPanels::status, null)
            .build();

    private CanduPanels() {
    }

    private static Component status(PanelValues v) {
        Tr trip = TRIPS[Math.floorMod(v.raw(TRIP), TRIPS.length)];
        Component c = STATUS.get(trip.get(), v.raw(FLOW), v.raw(FUEL_C), v.raw(XE_PCM), v.raw(LOADED), v.num(BURNUP, 100, 2),
                v.raw(REFUELLED), v.raw(MAGAZINE), v.raw(TRITIUM_GBQ));
        return v.bit(FLAGS, F_TRIPPED) ? c.copy().withStyle(ChatFormatting.RED) : c;
    }
}
