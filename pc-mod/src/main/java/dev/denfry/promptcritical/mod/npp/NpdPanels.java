package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.npp.NpdFuellingMachine;
import dev.denfry.promptcritical.sim.npp.NpdReactor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Щит реактивности NPD (art: textures/gui/panel/candu_npd.png, art/blockbench/core/reactor_panels.md). Регулирующих
 * стержней и зонных регуляторов у NPD не было, поэтому шесть стёкол показывают мощность, бак сброса, контур
 * теплоносителя, выход, топливо и D₂O головок машин, высокое стекло — уровень в каландре, полоса — ход перегрузки.
 * Кнопки: уровень вручную (подъём насосами, стоп, спуск клапаном), заданная мощность автомата, перегрузка, размер
 * сдвига, сброс защиты, автомат, слив бака сброса и сброс замедлителя под колпаком.
 */
public final class NpdPanels {
    public static final int POWER = 0, PERIOD = 1, RHO_PCM = 2, LEVEL = 3, DUMP_TANK = 4, COOL_FILL = 5, OUT_C = 6, FUEL_C = 7,
            HEAD_D2O = 8, SETPOINT = 9, MODE = 10, TRIP = 11, FLOW = 12, XE_PCM = 13, LOADED = 14, BURNUP = 15, REFUELLED = 16,
            BAY = 17, MAG_FRESH = 18, MAG_SPENT = 19, FM_PHASE = 20, FM_CH = 21, FM_PROGRESS = 22, SHIFT = 23, MAG_C = 24,
            MOD_PURITY = 25, COOL_PURITY = 26, TRITIUM_GBQ = 27, FLAGS = 28, LEVEL_RATE = 29, MACHINES = 30, CHANNELS = 31;
    public static final int F_TRIPPED = 0, F_NO_SG = 1, F_FM_FAULT = 2, F_FM_HOLD = 3, F_LOW_D2O = 4;
    public static final int B_SDS = 0, B_RESET = 1, B_UP = 2, B_STOP = 3, B_DOWN = 4, B_SET_DOWN = 5, B_SET_UP = 6, B_FUEL = 7,
            B_SHIFT = 8, B_AUTO = 9, B_DRAIN = 10;
    public static final int SLOT_FRESH = 0, SLOT_SPENT = 1;
    public static final int[] SHIFTS = {2, 4, 8};

    private static final Tr
            G_POWER = PanelText.tr("npd.g_power", "N", "N"),
            G_DUMP = PanelText.tr("npd.g_dump", "Dump", "Бак"),
            G_HT = PanelText.tr("npd.g_ht", "HT", "ТН"),
            G_OUT = PanelText.tr("npd.g_out", "Out", "Вых"),
            G_FUEL = PanelText.tr("npd.g_fuel", "Fuel", "Топл"),
            G_HEAD = PanelText.tr("npd.g_head", "FM", "РЗМ"),
            G_LEVEL = PanelText.tr("npd.g_level", "Level", "Уровень"),
            POWER_TIP = PanelText.tr("npd.power_tip", "Power %s %% of 92 MW · setpoint %s %% · period %s s · reactivity %s pcm",
                    "Мощность %s %% от 92 МВт · уставка %s %% · период %s с · реактивность %s pcm"),
            DUMP_TIP = PanelText.tr("npd.dump_tip", "Dump tank under the calandria: %s %% of a calandria's worth of D₂O",
                    "Бак сброса под каландром: %s %% объёма каландра D₂O"),
            HT_TIP = PanelText.tr("npd.ht_tip", "Heat transport D₂O %s %% full · purity %s %% D · flow %s %% · fill it through the headers",
                    "Теплоноситель D₂O заполнен на %s %% · чистота %s %% D · расход %s %% · заливается через коллекторы"),
            OUT_TIP = PanelText.tr("npd.out_tip", "Outlet header %s °C (277 at full power)", "Выходной коллектор %s °C (277 на номинале)"),
            FUEL_TIP = PanelText.tr("npd.fuel_tip", "Fuel %s °C · xenon %s pcm · channels %s · burnup up to %s MWd/kg",
                    "Топливо %s °C · ксенон %s pcm · каналов %s · выгорание до %s МВт·сут/кг"),
            HEAD_TIP = PanelText.tr("npd.head_tip", "Fuelling machines linked: %s (one at each face) · head D₂O supply %s %%",
                    "Перегрузочных машин: %s (по одной у торца) · подача D₂O на головки %s %%"),
            LEVEL_TIP = PanelText.tr("npd.level_tip",
                    "Calandria %s %% full · %s %%/min · purity %s %% D. Power is held by this level: pumps raise it from the dump tank, the valve lowers it. Pipe D₂O into the dump tank from below",
                    "Каландр заполнен на %s %% · %s %%/мин · чистота %s %% D. Мощность держит этот уровень: насосы поднимают его из бака сброса, клапан спускает. D₂O — трубой в бак сброса снизу"),
            PROGRESS = PanelText.tr("npd.progress", "Fuelling", "Перегрузка"),
            UP = PanelText.tr("npd.up", "▲", "▲"), STOP = PanelText.tr("npd.stop", "■", "■"), DOWN = PanelText.tr("npd.down", "▼", "▼"),
            UP_TIP = PanelText.tr("npd.up_tip", "Moderator pumps: raise the level (manual)", "Насосы замедлителя: поднимать уровень (вручную)"),
            STOP_TIP = PanelText.tr("npd.stop_tip", "Hold the level", "Держать уровень"),
            DOWN_TIP = PanelText.tr("npd.down_tip", "Control valve: lower the level into the dump tank (manual)",
                    "Регулирующий клапан: спускать уровень в бак сброса (вручную)"),
            SET_DOWN = PanelText.tr("npd.set_down", "−", "−"), SET_UP = PanelText.tr("npd.set_up", "+", "+"),
            SET_TIP = PanelText.tr("npd.set_tip", "Power setpoint of the level controller, 5 %% steps", "Уставка мощности автомата уровня, шаг 5 %%"),
            FUEL = PanelText.tr("npd.fuel", "Fuel", "Перегр"),
            FUEL_BTN_TIP = PanelText.tr("npd.fuel_btn_tip",
                    "Shut down with the moderator dumped: load the next empty channel with nine bundles. At power: send both machines to the most burnt channel to push the set number of bundles with the flow. While they work: abort",
                    "Заглушен, замедлитель сброшен: загрузить следующий пустой канал девятью пучками. На мощности: отправить обе машины к самому выгоревшему каналу и вдвинуть заданное число пучков по течению. Во время работы — прервать"),
            SHIFT_TIP = PanelText.tr("npd.shift_tip", "Bundles per visit: 2, 4 or 8", "Пучков за заход: 2, 4 или 8"),
            RESET = PanelText.tr("npd.reset", "Reset", "Взвод"),
            RESET_TIP = PanelText.tr("npd.reset_tip",
                    "Close the dump valves once the cause is gone; also clears a fuelling machine fault once its magazine is empty",
                    "Закрыть сбросные клапаны, когда причина ушла; заодно снять неисправность машины, если её магазин пуст"),
            AUTO = PanelText.tr("npd.auto", "Auto", "Авто"),
            AUTO_TIP = PanelText.tr("npd.auto_tip", "Level controller on/off: holds the power setpoint with the period above 30 s",
                    "Автомат уровня вкл/выкл: держит уставку мощности с периодом не короче 30 с"),
            SDS = PanelText.tr("npd.sds", "DUMP", "СБРОС"),
            SDS_TIP = PanelText.tr("npd.sds_tip", "Shutdown: open the helium balance valves and dump the moderator into the tank",
                    "Останов: открыть клапаны выравнивания гелия и сбросить замедлитель в бак"),
            DRAIN = PanelText.tr("npd.drain", "⇩", "⇩"),
            DRAIN_TIP = PanelText.tr("npd.drain_tip", "Drain 1 m³ from the dump tank into the drain tank of its grade (top port)",
                    "Слить 1 м³ из бака сброса в бак слива его сорта (верхний патрубок)"),
            FRESH = PanelText.tr("npd.fresh", "Fresh NPD 19-element bundles of natural uranium (to the fuel bay, up to 27)",
                    "Свежие 19-элементные пучки NPD из природного урана (в лоток, до 27)"),
            SPENT = PanelText.tr("npp.spent", "Spent fuel", "Отработанное топливо"),
            STATUS = PanelText.tr("npd.status", "%s · %s · level %s %% · N %s %%", "%s · %s · уровень %s %% · N %s %%"),
            STATUS_TIP = PanelText.tr("npd.status_tip", "Moderator %s %% D · coolant %s %% D · tritium %s GBq · refuelled %s bundles",
                    "Замедлитель %s %% D · теплоноситель %s %% D · тритий %s ГБк · перегружено пучков %s"),
            FM = PanelText.tr("npd.fm", "FM %s · ch %s · %s/%s", "РЗМ %s · канал %s · %s/%s"),
            FM_TIP = PanelText.tr("npd.fm_tip", "Bay %s fresh · magazine %s fresh, %s spent at %s °C · shift %s bundles",
                    "В лотке %s свежих · в магазине %s свежих, %s выгоревших при %s °C · сдвиг %s пучков"),
            MANUAL = PanelText.tr("npd.manual", "manual", "ручн"),
            AUTOMATIC = PanelText.tr("npd.automatic", "auto", "авто");
    private static final Tr[] TRIPS = {
            PanelText.tr("npd.trip.none", "normal", "норма"),
            PanelText.tr("npd.trip.period", "DUMP: period", "СБРОС: период"),
            PanelText.tr("npd.trip.power", "DUMP: power", "СБРОС: мощность"),
            PanelText.tr("npd.trip.flow", "DUMP: flow", "СБРОС: расход"),
            PanelText.tr("npd.trip.external", "DUMP: external", "СБРОС: внешний"),
            PanelText.tr("npd.trip.manual", "DUMP: manual", "СБРОС: ручной")};
    private static final Tr[] PHASES = {
            PanelText.tr("npd.fm.idle", "parked", "стоянка"),
            PanelText.tr("npd.fm.travel", "travel", "ход"),
            PanelText.tr("npd.fm.lock", "lock-on", "захват"),
            PanelText.tr("npd.fm.pressurise", "pressurise", "опрессовка"),
            PanelText.tr("npd.fm.unplug", "unplug", "пробки"),
            PanelText.tr("npd.fm.exchange", "push", "сдвиг"),
            PanelText.tr("npd.fm.replug", "replug", "пробки"),
            PanelText.tr("npd.fm.depressurise", "depressurise", "сброс давл."),
            PanelText.tr("npd.fm.unlock", "release", "отход"),
            PanelText.tr("npd.fm.return", "return", "возврат")};

    public static final PanelLayout REACTOR = PanelLayout.builder("candu_npd", 220, 172, CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/candu_npd.png"))
            .glass(14, 26, 48, POWER, 0xFFE0A040, Double.NaN, Double.NaN, G_POWER.get(),
                    v -> POWER_TIP.get(v.num(POWER, 10, 1), v.raw(SETPOINT), v.num(PERIOD, 10, 1), v.raw(RHO_PCM)))
            .glass(36, 26, 48, DUMP_TANK, 0xFF4F86B8, Double.NaN, Double.NaN, G_DUMP.get(), v -> DUMP_TIP.get(v.num(DUMP_TANK, 10, 1)))
            .glass(58, 26, 48, COOL_FILL, 0xFF7FA8C8, Double.NaN, Double.NaN, G_HT.get(),
                    v -> HT_TIP.get(v.num(COOL_FILL, 10, 1), v.num(COOL_PURITY, 100, 2), v.raw(FLOW)))
            .glass(80, 26, 48, OUT_C, 0xFFC05040, Double.NaN, Double.NaN, G_OUT.get(), v -> OUT_TIP.get(Math.round(v.raw(OUT_C) * 0.32)))
            .glass(102, 26, 48, FUEL_C, 0xFFD07030, Double.NaN, Double.NaN, G_FUEL.get(),
                    v -> FUEL_TIP.get(Math.round(v.raw(FUEL_C) * 2.0), v.raw(XE_PCM), v.raw(LOADED), v.num(BURNUP, 100, 2)))
            .glass(124, 26, 48, HEAD_D2O, 0xFF6FA0D0, Double.NaN, Double.NaN, G_HEAD.get(),
                    v -> HEAD_TIP.get(v.raw(MACHINES), v.num(HEAD_D2O, 10, 0)))
            .glass(178, 26, 60, LEVEL, 0xFF3F70B0, Double.NaN, Double.NaN, G_LEVEL.get(),
                    v -> LEVEL_TIP.get(v.num(LEVEL, 10, 1), v.num(LEVEL_RATE, 10, 1), v.num(MOD_PURITY, 100, 2)))
            .button(12, 86, 18, B_UP, UP.get(), UP_TIP.get())
            .button(32, 86, 18, B_STOP, STOP.get(), STOP_TIP.get())
            .button(52, 86, 18, B_DOWN, DOWN.get(), DOWN_TIP.get())
            .button(76, 86, 18, B_SET_DOWN, SET_DOWN.get(), SET_TIP.get())
            .button(96, 86, 18, B_SET_UP, SET_UP.get(), SET_TIP.get())
            .button(118, 86, 28, B_FUEL, FUEL.get(), FUEL_BTN_TIP.get())
            .button(148, 86, 18, B_SHIFT, Component.literal("±"), SHIFT_TIP.get())
            .bar(12, 108, 196, FM_PROGRESS, 10, 100, 0xFF5F8FC0, PROGRESS.get(), null)
            .readout(12, 122, 96, NpdPanels::status, v -> STATUS_TIP.get(v.num(MOD_PURITY, 100, 2), v.num(COOL_PURITY, 100, 2),
                    v.raw(TRITIUM_GBQ), v.raw(REFUELLED)))
            .readout(112, 122, 96, NpdPanels::machine, v -> FM_TIP.get(v.raw(BAY), v.raw(MAG_FRESH), v.raw(MAG_SPENT), v.raw(MAG_C), v.raw(SHIFT)))
            .button(12, 146, 28, B_RESET, RESET.get(), RESET_TIP.get())
            .button(42, 146, 28, B_AUTO, AUTO.get(), AUTO_TIP.get())
            .button(72, 146, 76, B_SDS, SDS.get(), SDS_TIP.get())
            .slot(152, 146, SLOT_FRESH, s -> s.is(PcItems.FUEL_ASSEMBLY.get()), FRESH.get())
            .slot(172, 146, SLOT_SPENT, s -> false, SPENT.get())
            .button(192, 146, 18, B_DRAIN, DRAIN.get(), DRAIN_TIP.get())
            .build();

    private NpdPanels() {
    }

    private static Component status(PanelValues v) {
        Tr trip = TRIPS[Math.floorMod(v.raw(TRIP), TRIPS.length)];
        Component c = STATUS.get(trip.get(), (v.raw(MODE) == NpdReactor.LevelMode.AUTO.ordinal() ? AUTOMATIC : MANUAL).get(),
                v.num(LEVEL, 10, 1), v.num(POWER, 10, 1));
        return v.bit(FLAGS, F_TRIPPED) ? c.copy().withStyle(ChatFormatting.RED) : c;
    }

    private static Component machine(PanelValues v) {
        Tr phase = PHASES[Math.floorMod(v.raw(FM_PHASE), PHASES.length)];
        int ch = v.raw(FM_CH);
        Component c = FM.get(phase.get(), ch < 0 ? "—" : dev.denfry.promptcritical.sim.npp.NpdChannels.label(ch),
                v.raw(MAG_FRESH), NpdFuellingMachine.MAGAZINE);
        if (v.bit(FLAGS, F_FM_FAULT)) return c.copy().withStyle(ChatFormatting.RED);
        return v.bit(FLAGS, F_FM_HOLD) ? c.copy().withStyle(ChatFormatting.GOLD) : c;
    }
}
