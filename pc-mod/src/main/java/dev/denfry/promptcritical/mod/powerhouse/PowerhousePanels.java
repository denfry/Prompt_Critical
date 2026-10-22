package dev.denfry.promptcritical.mod.powerhouse;

import static dev.denfry.promptcritical.mod.panel.PanelText.CELSIUS;
import static dev.denfry.promptcritical.mod.panel.PanelText.MPA;
import static dev.denfry.promptcritical.mod.panel.PanelText.PA;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.combustion.BmsResponse;
import dev.denfry.promptcritical.sim.combustion.BmsState;
import dev.denfry.promptcritical.sim.plant.WaterTubeUnit;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Щит котла I1 (07 §7.9.5–7.9.6): приборы барабана и топки, лампы BMS и ПАЗ, команды BMS (продувка, запальник,
 * основное топливо, останов, ключ байпаса датчиков пламени), регуляторы АВТ/РУЧ и ручные задания. Каналы — целые,
 * масштаб в имени.
 */
public final class PowerhousePanels {
    public static final int P_KPA = 0, LEVEL_PERMIL = 1, STEAM_C10 = 2, DRAFT_PA10 = 3, FLUE_C10 = 4, FUEL_PERMIL = 5,
            AIR_PERMIL = 6, IDF_PERMIL = 7, STEAM_KGH = 8, FEED_KGH = 9, EFF_PERMIL = 10, FLAGS = 11, BMS = 12, TRIP = 13,
            RESPONSE = 14, PURGE_S = 15, FUEL_STOCK = 16, FEED_L = 17, SET_KPA = 18, MAN_FUEL = 19, MAN_AIR = 20,
            MAN_DRAFT = 21, MAN_FEED = 22, ALPHA100 = 23, PROBLEM = 24, CHANNELS = 25;
    public static final int F_FLAME = 0, F_RELIEF = 1, F_BYPASS = 2, F_COMB_AUTO = 3, F_FEED_AUTO = 4, F_FANS = 5,
            F_EXPLODED = 6, F_UNFORMED = 7, F_TRIP = 8, F_LOW_LEVEL = 9, F_PURGED = 10, F_COAL = 11;
    public static final int BUTTON_PURGE = 0, BUTTON_IGNITER = 1, BUTTON_MAIN_FUEL = 2, BUTTON_SHUTDOWN = 3, BUTTON_BYPASS = 4,
            BUTTON_COMB_MODE = 5, BUTTON_FEED_MODE = 6, BUTTON_FUEL_DOWN = 7, BUTTON_FUEL_UP = 8, BUTTON_AIR_DOWN = 9,
            BUTTON_AIR_UP = 10, BUTTON_DRAFT_DOWN = 11, BUTTON_DRAFT_UP = 12, BUTTON_FEED_DOWN = 13, BUTTON_FEED_UP = 14;
    public static final int SLOT_DUST = 0;
    /** Шкала водоуказательного стекла барабана: ±250 мм от нормального уровня. */
    public static final double GLASS_SPAN_M = 0.5;
    /** Причины, почему котёл не собран (канал {@link #PROBLEM} — индекс + 1). */
    public static final List<String> PROBLEMS = List.of("no_wall", "unloaded", "too_big", "too_small", "furnace_blocked",
            "wall_gap", "burner_place", "burner_count", "no_drum");

    private static final Tr PRESSURE = PanelText.tr("wtb.pressure", "Drum", "Барабан"),
            LEVEL = PanelText.tr("wtb.level", "Level", "Уровень"),
            STEAM = PanelText.tr("wtb.steam", "Steam", "Пар"),
            DRAFT = PanelText.tr("wtb.draft", "Draft", "Разрежение"),
            FLAME = PanelText.tr("wtb.flame", "Flame", "Факел"),
            RELIEF = PanelText.tr("wtb.relief", "Relief", "ПК"),
            BYPASS = PanelText.tr("wtb.bypass", "Bypass", "Байпас"),
            TRIP_LAMP = PanelText.tr("wtb.trip", "MFT", "Отсечка"),
            LOW = PanelText.tr("wtb.low", "Lvl", "Уровень"),
            FANS = PanelText.tr("wtb.fans", "Fans", "Тягодутьё"),
            PURGED = PanelText.tr("wtb.purged", "Purged", "Продуто"),
            COMB_AUTO = PanelText.tr("wtb.comb_auto", "Comb.", "Горение"),
            FEED_AUTO = PanelText.tr("wtb.feed_auto", "Feed", "Питание"),
            FUEL_BAR = PanelText.tr("wtb.fuel_bar", "Fuel", "Топливо"),
            AIR_BAR = PanelText.tr("wtb.air_bar", "FD fan", "Дутьё"),
            IDF_BAR = PanelText.tr("wtb.idf_bar", "ID fan", "Дымосос"),
            FEED_BAR = PanelText.tr("wtb.feed_bar", "Feed tank", "Питательный бак"),
            PRESSURE_TIP = PanelText.tr("wtb.pressure_tip", "Drum %s MPa · setpoint %s · trip above %s · relief %s",
                    "Барабан %s МПа · уставка %s · отсечка выше %s · ПК %s"),
            LEVEL_TIP = PanelText.tr("wtb.level_tip", "Drum level %s mm from normal · trip at ±200 mm",
                    "Уровень в барабане %s мм от нормального · отсечка при ±200 мм"),
            STEAM_TIP = PanelText.tr("wtb.steam_tip", "Superheated steam %s °C", "Перегретый пар %s °C"),
            DRAFT_TIP = PanelText.tr("wtb.draft_tip", "Furnace draft %s Pa (keep 20–50)", "Разрежение в топке %s Па (держать 20–50)"),
            FUEL_TIP = PanelText.tr("wtb.fuel_tip", "Fuel control valve %s%% · manual %s%%", "Регулирующий клапан топлива %s%% · ручное %s%%"),
            AIR_TIP = PanelText.tr("wtb.air_tip", "FD fan vanes %s%% · manual %s%%", "Направляющий аппарат дутья %s%% · ручное %s%%"),
            IDF_TIP = PanelText.tr("wtb.idf_tip", "ID fan vanes %s%% · manual %s%%", "Направляющий аппарат дымососа %s%% · ручное %s%%"),
            FEED_TIP = PanelText.tr("wtb.feed_tip", "Feed water %s L · manual feed %s%%", "Питательной воды %s л · ручное питание %s%%"),
            DUST_SLOT = PanelText.tr("wtb.dust_slot", "Coal dust for the burners", "Угольная пыль для горелок"),
            VALUES = PanelText.tr("wtb.values", "Steam %s kg/h · feed %s kg/h · flue %s °C · α %s · eff. %s",
                    "Пар %s кг/ч · питание %s кг/ч · газы %s °C · α %s · КПД %s"),
            STATUS = PanelText.tr("wtb.status", "BMS: %s", "BMS: %s"),
            STATUS_PURGE = PanelText.tr("wtb.status_purge", "BMS: purge %s s of 300", "BMS: продувка %s из 300 с"),
            STATUS_TRIP = PanelText.tr("wtb.status_trip", "BMS: %s — %s", "BMS: %s — %s"),
            STATUS_REFUSED = PanelText.tr("wtb.status_refused", "BMS: %s · refused: %s", "BMS: %s · отказ: %s"),
            UNFORMED = PanelText.tr("wtb.unformed", "Boiler not formed: %s", "Котёл не собран: %s"),
            EXPLODED = PanelText.tr("wtb.exploded", "WRECKED — furnace explosion", "РАЗРУШЕН — взрыв топки");

    private static final Tr B_PURGE = PanelText.tr("wtb.b_purge", "Purge", "Продувка"),
            B_PURGE_TIP = PanelText.tr("wtb.b_purge_tip", "Start the furnace purge: fans running, fuel valves shut, ≥ 25 % air for 300 s",
                    "Начать продувку топки: тягодутьё в работе, клапаны топлива закрыты, ≥ 25 % воздуха 300 с"),
            B_IGNITER = PanelText.tr("wtb.b_igniter", "Igniter", "Запальник"),
            B_IGNITER_TIP = PanelText.tr("wtb.b_igniter_tip", "Light the igniter of the next burner (10 s trial)",
                    "Зажечь запальник следующей горелки (проба 10 с)"),
            B_FUEL = PanelText.tr("wtb.b_fuel", "Fuel", "Топливо"),
            B_FUEL_TIP = PanelText.tr("wtb.b_fuel_tip", "Open main fuel to the burners with a lit igniter (trial 5 s gas / 10 s dust)",
                    "Подать основное топливо на горелки с запальником (проба 5 с газ / 10 с пыль)"),
            B_STOP = PanelText.tr("wtb.b_stop", "Stop", "Стоп"),
            B_STOP_TIP = PanelText.tr("wtb.b_stop_tip", "Master fuel trip and post-purge", "Отсечка топлива и пост-продувка"),
            B_KEY = PanelText.tr("wtb.b_key", "Key", "Ключ"),
            B_KEY_TIP = PanelText.tr("wtb.b_key_tip", "Bypass the flame detectors with the key — the BMS no longer sees a missing flame!",
                    "Байпас датчиков пламени ключом — BMS перестаёт видеть отсутствие факела!"),
            B_COMB = PanelText.tr("wtb.b_comb", "Comb.", "Горение"),
            B_COMB_TIP = PanelText.tr("wtb.b_comb_tip", "Combustion control: AUTO (pressure → fuel, air leads fuel) or MANUAL",
                    "Регулятор горения: АВТ (давление → топливо, воздух впереди топлива) или РУЧ"),
            B_FEED = PanelText.tr("wtb.b_feed", "Feed", "Питание"),
            B_FEED_TIP = PanelText.tr("wtb.b_feed_tip", "Feed control: AUTO (drum level) or MANUAL", "Регулятор питания: АВТ (уровень) или РУЧ"),
            B_FUEL_ADJ = PanelText.tr("wtb.b_fuel_adj", "Manual: fuel −/+ 5 %; AUTO: pressure setpoint −/+ 0.05 MPa",
                    "РУЧ: топливо −/+ 5 %; АВТ: уставка давления −/+ 0,05 МПа"),
            B_AIR_ADJ = PanelText.tr("wtb.b_air_adj", "Manual: FD fan vanes −/+ 5 %", "РУЧ: дутьё −/+ 5 %"),
            B_DRAFT_ADJ = PanelText.tr("wtb.b_draft_adj", "Manual: ID fan vanes −/+ 5 %", "РУЧ: дымосос −/+ 5 %"),
            B_FEED_ADJ = PanelText.tr("wtb.b_feed_adj", "Manual: feed pump −/+ 5 %", "РУЧ: питание −/+ 5 %");

    public static final PanelLayout BOILER = PanelLayout.builder("water_tube_boiler", 240, 222, CHANNELS)
            .dial(8, 24, true, P_KPA, 1000, 1.6, 1.47, 2, MPA.get(), PRESSURE.get(),
                    v -> PRESSURE_TIP.get(v.num(P_KPA, 1000, 2), v.num(SET_KPA, 1000, 2),
                            PanelText.num(1.05 * WaterTubeUnit.WORKING_PRESSURE_PA / 1e6 - 0.101, 2),
                            PanelText.num(WaterTubeUnit.RELIEF_FACTOR * WaterTubeUnit.WORKING_PRESSURE_PA / 1e6 - 0.101, 2)))
            .glass(80, 24, 74, LEVEL_PERMIL, 0xFF3F78B8, (0.25 - 0.2) / GLASS_SPAN_M, 0.5, LEVEL.get(),
                    v -> LEVEL_TIP.get(PanelText.num((v.raw(LEVEL_PERMIL) / 1000.0 - 0.5) * GLASS_SPAN_M * 1000, 0)))
            .dial(104, 24, false, STEAM_C10, 10, 400, 350, 0, CELSIUS.get(), STEAM.get(),
                    v -> STEAM_TIP.get(v.num(STEAM_C10, 10, 0)))
            .dial(162, 24, false, DRAFT_PA10, 10, 100, Double.NaN, 0, PA.get(), DRAFT.get(),
                    v -> DRAFT_TIP.get(v.num(DRAFT_PA10, 10, 0)))
            .slot(218, 26, SLOT_DUST, s -> s.is(PcItems.COAL_DUST.get()), DUST_SLOT.get())
            .lamp(104, 88, FLAGS, F_FLAME, LampColor.AMBER, FLAME.get())
            .lamp(104, 104, FLAGS, F_RELIEF, LampColor.WHITE, RELIEF.get())
            .lamp(104, 120, FLAGS, F_BYPASS, LampColor.RED, BYPASS.get())
            .lamp(150, 88, FLAGS, F_TRIP, LampColor.RED, TRIP_LAMP.get())
            .lamp(150, 104, FLAGS, F_LOW_LEVEL, LampColor.RED, LOW.get())
            .lamp(150, 120, FLAGS, F_FANS, LampColor.GREEN, FANS.get())
            .lamp(196, 88, FLAGS, F_PURGED, LampColor.WHITE, PURGED.get())
            .lamp(196, 104, FLAGS, F_COMB_AUTO, LampColor.GREEN, COMB_AUTO.get())
            .lamp(196, 120, FLAGS, F_FEED_AUTO, LampColor.GREEN, FEED_AUTO.get())
            .bar(8, 136, 108, FUEL_PERMIL, 1000, 1, 0xFFE07828, FUEL_BAR.get(),
                    v -> FUEL_TIP.get(v.num(FUEL_PERMIL, 10, 0), v.num(MAN_FUEL, 10, 0)))
            .bar(124, 136, 108, AIR_PERMIL, 1000, 1, 0xFF3F78B8, AIR_BAR.get(),
                    v -> AIR_TIP.get(v.num(AIR_PERMIL, 10, 0), v.num(MAN_AIR, 10, 0)))
            .bar(8, 148, 108, IDF_PERMIL, 1000, 1, 0xFF707070, IDF_BAR.get(),
                    v -> IDF_TIP.get(v.num(IDF_PERMIL, 10, 0), v.num(MAN_DRAFT, 10, 0)))
            .bar(124, 148, 108, FEED_L, 1, WaterTubeBoilerBlockEntity.FEED_TANK_MB, 0xFF3F78B8, FEED_BAR.get(),
                    v -> FEED_TIP.get(v.num(FEED_L, 1, 0), v.num(MAN_FEED, 10, 0)))
            .readout(8, 160, 224, PowerhousePanels::status, null)
            .readout(8, 172, 224, PowerhousePanels::values, null)
            .button(8, 186, 44, BUTTON_PURGE, B_PURGE.get(), B_PURGE_TIP.get())
            .button(54, 186, 44, BUTTON_IGNITER, B_IGNITER.get(), B_IGNITER_TIP.get())
            .button(100, 186, 44, BUTTON_MAIN_FUEL, B_FUEL.get(), B_FUEL_TIP.get())
            .button(146, 186, 44, BUTTON_SHUTDOWN, B_STOP.get(), B_STOP_TIP.get())
            .button(192, 186, 40, BUTTON_BYPASS, B_KEY.get(), B_KEY_TIP.get())
            .button(8, 202, 40, BUTTON_COMB_MODE, B_COMB.get(), B_COMB_TIP.get())
            .button(50, 202, 40, BUTTON_FEED_MODE, B_FEED.get(), B_FEED_TIP.get())
            .button(94, 202, 16, BUTTON_FUEL_DOWN, Component.literal("T−"), B_FUEL_ADJ.get())
            .button(110, 202, 16, BUTTON_FUEL_UP, Component.literal("T+"), B_FUEL_ADJ.get())
            .button(130, 202, 16, BUTTON_AIR_DOWN, Component.literal("A−"), B_AIR_ADJ.get())
            .button(146, 202, 16, BUTTON_AIR_UP, Component.literal("A+"), B_AIR_ADJ.get())
            .button(166, 202, 16, BUTTON_DRAFT_DOWN, Component.literal("D−"), B_DRAFT_ADJ.get())
            .button(182, 202, 16, BUTTON_DRAFT_UP, Component.literal("D+"), B_DRAFT_ADJ.get())
            .button(202, 202, 16, BUTTON_FEED_DOWN, Component.literal("W−"), B_FEED_ADJ.get())
            .button(218, 202, 16, BUTTON_FEED_UP, Component.literal("W+"), B_FEED_ADJ.get())
            .build();

    private PowerhousePanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(BOILER);
    }

    private static Component status(PanelValues v) {
        if (v.bit(FLAGS, F_EXPLODED)) return EXPLODED.get().withStyle(ChatFormatting.RED);
        if (v.bit(FLAGS, F_UNFORMED)) {
            int p = v.raw(PROBLEM) - 1;
            Component why = p >= 0 && p < PROBLEMS.size() ? PowerhouseText.problem(PROBLEMS.get(p)) : Component.literal("…");
            return UNFORMED.get(why).withStyle(ChatFormatting.RED);
        }
        BmsState s = BmsState.values()[Math.max(0, Math.min(BmsState.values().length - 1, v.raw(BMS)))];
        Component state = PowerhouseText.bmsState(s);
        int trip = v.raw(TRIP) - 1;
        if ((s == BmsState.MFT || s == BmsState.POST_PURGE) && trip >= 0 && trip < WaterTubeUnit.Protection.values().length)
            return STATUS_TRIP.get(state, PowerhouseText.protection(WaterTubeUnit.Protection.values()[trip])).withStyle(ChatFormatting.RED);
        int r = v.raw(RESPONSE) - 1;
        if (r > 0 && r < BmsResponse.values().length)
            return STATUS_REFUSED.get(state, PowerhouseText.response(BmsResponse.values()[r])).withStyle(ChatFormatting.GOLD);
        if (s == BmsState.PURGE) return STATUS_PURGE.get(v.raw(PURGE_S));
        return STATUS.get(state);
    }

    private static Component values(PanelValues v) {
        String eff = v.raw(EFF_PERMIL) < 0 ? "—" : v.num(EFF_PERMIL, 10, 0) + " %";
        String alpha = v.raw(ALPHA100) <= 0 ? "—" : String.format(Locale.ROOT, "%.2f", v.raw(ALPHA100) / 100.0);
        return VALUES.get(v.num(STEAM_KGH, 1, 0), v.num(FEED_KGH, 1, 0), v.num(FLUE_C10, 10, 0), alpha, eff);
    }
}
