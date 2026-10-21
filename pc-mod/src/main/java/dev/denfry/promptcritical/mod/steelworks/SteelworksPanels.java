package dev.denfry.promptcritical.mod.steelworks;

import static dev.denfry.promptcritical.mod.panel.PanelText.CELSIUS;
import static dev.denfry.promptcritical.mod.panel.PanelText.MPA;
import static dev.denfry.promptcritical.mod.panel.PanelText.RPM;
import static dev.denfry.promptcritical.mod.panel.PanelText.RUNNING;
import static dev.denfry.promptcritical.mod.panel.PanelText.SHAFT;
import static dev.denfry.promptcritical.mod.panel.PanelText.SHAFT_TIP;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.metallurgy.BatchFurnaceUnit;
import dev.denfry.promptcritical.sim.metallurgy.ConverterPhase;
import dev.denfry.promptcritical.sim.metallurgy.MillRollKind;
import dev.denfry.promptcritical.sim.metallurgy.PassResult;
import dev.denfry.promptcritical.sim.metallurgy.PipeKind;
import dev.denfry.promptcritical.sim.metallurgy.SteelFamily;
import dev.denfry.promptcritical.sim.utilities.AsuState;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * Местные щиты сталеплавильного, прокатного и трубного цехов и ВРУ. У машин на валу — тахометр
 * ({@link #SHAFT_RPM10}) и лампы ({@link #FLAGS}); остальные каналы — свои с {@link #OWN}. Кнопки — общие номера.
 */
public final class SteelworksPanels {
    public static final int SHAFT_RPM10 = 0, FLAGS = 1, OWN = 2;
    public static final int F_RUNNING = 0, F_ALARM = 1;
    public static final int BTN_O2_DOWN = 0, BTN_O2_UP = 1, BTN_LANCE_DOWN = 2, BTN_LANCE_UP = 3, BTN_LANCE_PARK = 4, BTN_ADD_1 = 5,
            BTN_ADD_2 = 6, BTN_ADD_3 = 7, BTN_STIR = 8, BTN_SET_DOWN = 9, BTN_SET_UP = 10, BTN_SET_DOWN_BIG = 11, BTN_SET_UP_BIG = 12,
            BTN_RAMP_DOWN = 13, BTN_RAMP_UP = 14, BTN_UNLOAD = 15, BTN_MODE = 16, BTN_GAP_DOWN = 17, BTN_GAP_UP = 18,
            BTN_GAP_DOWN_BIG = 19, BTN_GAP_UP_BIG = 20, BTN_KIND = 21, BTN_SIZE = 22, BTN_SPEED_DOWN = 23, BTN_SPEED_UP = 24,
            BTN_POWER_DOWN = 25, BTN_POWER_UP = 26, BTN_REGEN = 27, BTN_TRIP = 28, BTN_LIQ_DOWN = 29, BTN_LIQ_UP = 30,
            BTN_LIQ_KIND = 31, BTN_DRAIN = 32;

    // конвертер
    public static final int C_PHASE = OWN, C_TEMP_C = OWN + 1, C_O2_NM3H = OWN + 2, C_O2_SET_NM3H = OWN + 3, C_TILT = OWN + 4,
            C_C_PCT1000 = OWN + 5, C_METAL_KG = OWN + 6, C_SCRAP_KG = OWN + 7, C_SLAG_KG = OWN + 8, C_BASICITY10 = OWN + 9,
            C_FEO_PCT = OWN + 10, C_O2_BLOWN = OWN + 11, C_LANCE_CM = OWN + 12, C_LANCE_SET_CM = OWN + 13, C_O2_MB = OWN + 14,
            C_GAS_MB = OWN + 15, C_FLAGS = OWN + 16, C_CHANNELS = OWN + 17;
    public static final int CF_BLOWING = 0, CF_INTERLOCK = 1, CF_READY = 2, CF_OVERBLOWN = 3;
    // установка доводки
    public static final int L_TEMP_C = OWN, L_MASS_KG = OWN + 1, L_C_PCT1000 = OWN + 2, L_MN_PCT100 = OWN + 3, L_SI_PCT100 = OWN + 4,
            L_CR_PCT100 = OWN + 5, L_O_PPM = OWN + 6, L_FAMILY = OWN + 7, L_ARGON_MB = OWN + 8, L_STIR_MIN10 = OWN + 9,
            L_FLAGS = OWN + 10, L_CHANNELS = OWN + 11;
    public static final int LF_LADLE = 0, LF_STIR = 1, LF_KILLED = 2, LF_SPEC = 3;
    // печи с садкой
    public static final int R_TEMP_C = OWN, R_SET_C = OWN + 1, R_RAMP_KH = OWN + 2, R_LOADED = OWN + 3, R_GAS_MB = OWN + 4,
            R_COLD_C = OWN + 5, R_HOT_C = OWN + 6, R_FLAGS = OWN + 7, R_CHANNELS = OWN + 8;
    public static final int RF_FIRING = 0, RF_QUENCH = 1;
    // клеть
    public static final int M_GAP_MM = OWN, M_ROLLS = OWN + 1, M_FORCE_KN = OWN + 2, M_TORQUE_KNM = OWN + 3, M_POWER_KW = OWN + 4,
            M_OUTCOME = OWN + 5, M_PASSES = OWN + 6, M_PIECE_C = OWN + 7, M_PIECE_H_MM = OWN + 8, M_PIECE_W_MM = OWN + 9,
            M_CHANNELS = OWN + 10;
    // прошивной стан
    public static final int P_OD_MM = OWN, P_WALL_MM = OWN + 1, P_KIND = OWN + 2, P_PIPES = OWN + 3, P_PROGRESS_PERMIL = OWN + 4,
            P_BILLET_C = OWN + 5, P_CHANNELS = OWN + 6;
    // трубосварочный стан
    public static final int W_OD_MM = OWN, W_SPEED10 = OWN + 1, W_SPEED_SET = OWN + 2, W_POWER_KW = OWN + 3, W_WALL_MM = OWN + 4,
            W_OPT_KW = OWN + 5, W_KIND = OWN + 6, W_PIPES = OWN + 7, W_DEFECT_PPM = OWN + 8, W_PROGRESS_PERMIL = OWN + 9,
            W_CHANNELS = OWN + 10;
    // гидропресс
    public static final int H_PRESSURE_KPA = OWN, H_TEST_KPA = OWN + 1, H_TESTED = OWN + 2, H_BURST = OWN + 3, H_WATER_MB = OWN + 4,
            H_CHANNELS = OWN + 5;
    // ВРУ
    public static final int A_STATE = OWN, A_COLD_C = OWN + 1, A_POWER_KW = OWN + 2, A_O2_NM3H = OWN + 3, A_N2_NM3H = OWN + 4,
            A_PURITY_PERMIL = OWN + 5, A_LOX_PPM10 = OWN + 6, A_SIEVE_PCT = OWN + 7, A_LIQUID_PCT = OWN + 8, A_O2_MB = OWN + 9,
            A_CHANNELS = OWN + 10;
    public static final int AF_FORMED = 2, AF_REGEN = 3, AF_TRIP = 4, AF_LOX = 5, AF_ICED = 6;

    private static final Tr MINUS = PanelText.tr("steel.minus", "−", "−"), PLUS = PanelText.tr("steel.plus", "+", "+"),
            MINUS2 = PanelText.tr("steel.minus2", "−−", "−−"), PLUS2 = PanelText.tr("steel.plus2", "++", "++"),
            NM3H = PanelText.tr("steel.unit.nm3h", "Nm³/h", "нм³/ч"),
            KW = PanelText.tr("steel.unit.kw", "kW", "кВт"),
            MB = PanelText.tr("steel.mb", "%s mB", "%s мБ"),
            KG = PanelText.tr("steel.kg", "%s kg", "%s кг"),
            ALARM = PanelText.tr("steel.alarm", "Alarm", "Авария"),
            GAS = PanelText.tr("steel.gas", "Fuel gas", "Горючий газ"),
            CYCLE = PanelText.tr("steel.cycle", "Cycle", "Цикл"),
            KIND = PanelText.tr("steel.kind", "Kind", "Вид"),
            SIZE = PanelText.tr("steel.size", "Size", "Размер"),
            PIPE = PanelText.tr("steel.pipe", "Pipe", "Труба");

    private static final Tr BATH = PanelText.tr("steel.bof.bath", "Bath", "Ванна"),
            O2 = PanelText.tr("steel.bof.o2", "Oxygen", "Кислород"),
            PHASE = PanelText.tr("steel.bof.phase", "%s · tilt %s°", "%s · наклон %s°"),
            BLOWING = PanelText.tr("steel.bof.blowing", "Blowing", "Продувка"),
            INTERLOCK = PanelText.tr("steel.bof.interlock", "Lance interlock", "Блокировка фурмы"),
            READY = PanelText.tr("steel.bof.ready", "Ready to blow", "Готов к продувке"),
            OVERBLOWN = PanelText.tr("steel.bof.overblown", "Overblown", "Передув"),
            O2_SET = PanelText.tr("steel.bof.o2_set", "O₂ set %s Nm³/h · blown %s Nm³", "Уставка O₂ %s нм³/ч · вдуто %s нм³"),
            O2_TIP = PanelText.tr("steel.bof.o2_tip", "Oxygen flow −/+ 180 Nm³/h", "Расход кислорода −/+ 180 нм³/ч"),
            LANCE = PanelText.tr("steel.bof.lance", "Lance %s m (set %s m) · O₂ %s mB", "Фурма %s м (уставка %s м) · O₂ %s мБ"),
            NO_LANCE = PanelText.tr("steel.bof.no_lance", "No oxygen lance above the mouth", "Над горловиной нет фурмы"),
            LANCE_TIP = PanelText.tr("steel.bof.lance_tip", "Lance height −/+ 0.1 m", "Высота фурмы −/+ 0,1 м"),
            PARK = PanelText.tr("steel.bof.park", "Park", "Вверх"),
            PARK_TIP = PanelText.tr("steel.bof.park_tip", "Raise the lance out of the mouth / lower it to the blowing height",
                    "Поднять фурму из горловины / опустить на высоту продувки"),
            METAL = PanelText.tr("steel.bof.metal", "Metal %s t · C %s%% · scrap %s kg", "Металл %s т · C %s%% · лом %s кг"),
            SLAG = PanelText.tr("steel.bof.slag", "Slag %s kg · CaO/SiO₂ %s · FeO %s%% · gas %s mB",
                    "Шлак %s кг · CaO/SiO₂ %s · FeO %s%% · газ %s мБ"),
            SCRAP_SLOT = PanelText.tr("steel.bof.scrap_slot", "Scrap or pig iron", "Лом или чушковый чугун"),
            LIME_SLOT = PanelText.tr("steel.bof.lime_slot", "Burnt lime", "Известь");

    private static final Tr STEEL = PanelText.tr("steel.station.steel", "Steel", "Сталь"),
            STATION_CHEM = PanelText.tr("steel.station.chem", "%s t · C %s%% · Mn %s%% · Si %s%% · Cr %s%% · O %s ppm",
                    "%s т · C %s%% · Mn %s%% · Si %s%% · Cr %s%% · O %s ppm"),
            GRADE = PanelText.tr("steel.station.grade", "Grade: %s", "Марка: %s"),
            NO_LADLE = PanelText.tr("steel.station.no_ladle", "No steel ladle beside the station", "Рядом нет ковша со сталью"),
            LADLE = PanelText.tr("steel.station.ladle", "Ladle", "Ковш"),
            KILLED = PanelText.tr("steel.station.killed", "Killed", "Раскислена"),
            SPEC = PanelText.tr("steel.station.spec", "In spec", "В марке"),
            STIR = PanelText.tr("steel.station.stir", "Argon", "Аргон"),
            STIR_TIP = PanelText.tr("steel.station.stir_tip", "Argon stirring on/off", "Продувка аргоном вкл/выкл"),
            ARGON = PanelText.tr("steel.station.argon", "Argon %s mB · stirred %s min", "Аргон %s мБ · продуто %s мин"),
            ADD = PanelText.tr("steel.station.add", "Add", "Ввести"),
            ADD_TIP = PanelText.tr("steel.station.add_tip", "Add one ferroalloy from this bin", "Ввести один ферросплав из бункера"),
            BIN = PanelText.tr("steel.station.bin", "Ferroalloy bin", "Бункер ферросплава");

    private static final Tr FURNACE = PanelText.tr("steel.furnace", "Furnace", "Печь"),
            FIRING = PanelText.tr("steel.firing", "Firing", "Горение"),
            QUENCH = PanelText.tr("steel.quench", "Quench", "Закалка"),
            SET = PanelText.tr("steel.set", "Set %s °C · ramp %s K/h", "Уставка %s °C · %s К/ч"),
            SET_TIP = PanelText.tr("steel.set_tip", "Setpoint −/+ 10 °C (−−/++ 100 °C)", "Уставка −/+ 10 °C (−−/++ 100 °C)"),
            RAMP_TIP = PanelText.tr("steel.ramp_tip", "Heat-up rate −/+", "Скорость нагрева −/+"),
            PIECES = PanelText.tr("steel.pieces", "%s / %s pieces · %s…%s °C", "%s / %s шт. · %s…%s °C"),
            UNLOAD = PanelText.tr("steel.unload", "Unload", "Выгрузить"),
            UNLOAD_TIP = PanelText.tr("steel.unload_tip", "Unload the charge into the quench tank or onto the floor",
                    "Выгрузить садку в закалочный бак или на воздух"),
            MODE = PanelText.tr("steel.mode", "Q / air", "Закалка / воздух"),
            MODE_TIP = PanelText.tr("steel.mode_tip", "Unload into the quench tank or cool in air", "Выгружать в закалочный бак или на воздух"),
            STOCK = PanelText.tr("steel.stock", "Ingot, billet, plate, rod, beam", "Слиток, заготовка, лист, пруток, балка"),
            HEATED = PanelText.tr("steel.heated", "Heated", "Нагретые");

    private static final Tr GAP = PanelText.tr("steel.stand.gap", "Gap %s mm · %s rolls", "Зазор %s мм · валки %s"),
            GAP_TIP = PanelText.tr("steel.stand.gap_tip", "Roll gap −/+ 1 mm (−−/++ 10 mm)", "Зазор −/+ 1 мм (−−/++ 10 мм)"),
            ROLLS = PanelText.tr("steel.stand.rolls", "Rolls", "Валки"),
            ROLLS_TIP = PanelText.tr("steel.stand.rolls_tip", "Flat or section rolls", "Гладкие или калиброванные валки"),
            FORCE = PanelText.tr("steel.stand.force", "Roll force", "Усилие"),
            TORQUE = PanelText.tr("steel.stand.torque", "Torque", "Момент"),
            KN = PanelText.tr("steel.unit.kn", "kN", "кН"),
            KNM = PanelText.tr("steel.unit.knm", "kN·m", "кН·м"),
            PASS = PanelText.tr("steel.stand.pass", "Pass %s: %s · %s kW", "Пропуск %s: %s · %s кВт"),
            PIECE = PanelText.tr("steel.stand.piece", "Piece %s × %s mm at %s °C", "Раскат %s × %s мм при %s °C"),
            NO_PIECE = PanelText.tr("steel.stand.no_piece", "No piece in the rolls", "В валках пусто"),
            SHEARED = PanelText.tr("steel.stand.sheared", "Shear pin", "Срезной штифт");

    private static final Tr PIERCE = PanelText.tr("steel.piercing.size", "%s × %s mm · %s", "%s × %s мм · %s"),
            PIERCE_LAST = PanelText.tr("steel.piercing.last", "Billet %s °C · last %s m", "Заготовка %s °C · последний раз %s м"),
            SIZE_TIP = PanelText.tr("steel.size_tip", "Next pipe size", "Следующий типоразмер"),
            KIND_TIP = PanelText.tr("steel.kind_tip", "Next pipe kind", "Следующее назначение"),
            BILLET = PanelText.tr("steel.billet", "Heated billet or ingot (≥ 1150 °C)", "Нагретая заготовка или слиток (≥ 1150 °C)");

    private static final Tr WELD = PanelText.tr("steel.welder.size", "⌀ %s mm · wall %s mm · %s", "⌀ %s мм · стенка %s мм · %s"),
            SPEED = PanelText.tr("steel.welder.speed", "Line %s m/min (set %s)", "Линия %s м/мин (уставка %s)"),
            SPEED_TIP = PanelText.tr("steel.welder.speed_tip", "Line speed −/+ 1 m/min", "Скорость линии −/+ 1 м/мин"),
            POWER = PanelText.tr("steel.welder.power", "HF power %s kW (optimum %s kW)", "Мощность ТВЧ %s кВт (оптимум %s кВт)"),
            POWER_TIP = PanelText.tr("steel.welder.power_tip", "HF power −/+ 10 kW", "Мощность ТВЧ −/+ 10 кВт"),
            WELD_LAST = PanelText.tr("steel.welder.last", "Last %s m · defect risk %s ppm", "Последний раз %s м · риск дефекта %s ppm"),
            PLATE = PanelText.tr("steel.plate", "Steel plate (skelp)", "Стальной лист (штрипс)");

    private static final Tr PRESSURE = PanelText.tr("steel.hydro.pressure", "Pressure", "Давление"),
            HYDRO = PanelText.tr("steel.hydro.stats", "Test %s MPa · tested %s · burst %s", "Испытание %s МПа · испытано %s · лопнуло %s"),
            WATER = PanelText.tr("steel.hydro.water", "Water", "Вода"),
            BURST = PanelText.tr("steel.hydro.burst", "Burst", "Разрыв"),
            COLD_PIPE = PanelText.tr("steel.hydro.pipe", "Cooled pipe (below 100 °C)", "Остывшая труба (ниже 100 °C)");

    private static final Tr COLD_BOX = PanelText.tr("steel.asu.cold_box", "Cold box", "Блок"),
            COMPRESSOR = PanelText.tr("steel.asu.compressor", "Compressor", "Компрессор"),
            OXYGEN = PanelText.tr("steel.asu.o2", "O₂", "O₂"),
            ASU_STATE = PanelText.tr("steel.asu.state", "%s · purity %s%%", "%s · чистота %s%%"),
            ASU_FLOW = PanelText.tr("steel.asu.flow", "N₂ %s Nm³/h · O₂ in tank %s mB", "N₂ %s нм³/ч · O₂ в баке %s мБ"),
            LOX_HC = PanelText.tr("steel.asu.lox_hc", "C₂H₂ in LOX", "C₂H₂ в LOX"),
            SIEVE = PanelText.tr("steel.asu.sieve", "Molecular sieve", "Адсорбер"),
            FORMED = PanelText.tr("steel.asu.formed", "Assembled", "Собрана"),
            ICED = PanelText.tr("steel.asu.iced", "Ice", "Лёд"),
            REGEN = PanelText.tr("steel.asu.regen", "Regen", "Регенер."),
            REGEN_TIP = PanelText.tr("steel.asu.regen_tip", "Molecular sieve regeneration heater on/off",
                    "Нагреватель регенерации адсорберов вкл/выкл"),
            TRIP = PanelText.tr("steel.asu.trip", "HC trip", "ПАЗ"),
            TRIP_TIP = PanelText.tr("steel.asu.trip_tip", "Hydrocarbon trip in LOX on/off (off — the way to an explosion)",
                    "Защита по углеводородам в LOX вкл/выкл (без неё — путь к взрыву)"),
            LIQUID = PanelText.tr("steel.asu.liquid", "Liquid %s%% as %s", "Жидкий продукт %s%%: %s"),
            LIQUID_TIP = PanelText.tr("steel.asu.liquid_tip", "Share of power for liquid product −/+ 5 %%", "Доля мощности на жидкий продукт −/+ 5 %%"),
            LIQ_KIND = PanelText.tr("steel.asu.liq_kind", "LOX/LIN", "LOX/LIN"),
            DRAIN = PanelText.tr("steel.asu.drain", "Drain", "Слив"),
            DRAIN_TIP = PanelText.tr("steel.asu.drain_tip", "Drain LOX from the sump: hydrocarbons go with it, the trip resets",
                    "Слить LOX из куба: углеводороды уходят с ним, останов сбрасывается");

    public static final PanelLayout CONVERTER = PanelLayout.builder("bof_vessel", 230, 168, C_CHANNELS)
            .dial(8, 24, true, C_TEMP_C, 1, 1800, 1720, 0, CELSIUS.get(), BATH.get(), null)
            .dial(80, 24, false, C_O2_NM3H, 1, 6000, Double.NaN, 0, NM3H.get(), O2.get(), null)
            .lamp(140, 24, C_FLAGS, CF_BLOWING, LampColor.AMBER, BLOWING.get())
            .lamp(140, 38, C_FLAGS, CF_READY, LampColor.GREEN, READY.get())
            .lamp(140, 52, C_FLAGS, CF_INTERLOCK, LampColor.WHITE, INTERLOCK.get())
            .lamp(140, 66, C_FLAGS, CF_OVERBLOWN, LampColor.RED, OVERBLOWN.get())
            .readout(8, 94, 214, v -> PHASE.get(phase(v.raw(C_PHASE)), v.raw(C_TILT)), null)
            .readout(8, 106, 170, v -> O2_SET.get(v.raw(C_O2_SET_NM3H), v.raw(C_O2_BLOWN)), null)
            .button(180, 102, 20, BTN_O2_DOWN, MINUS.get(), O2_TIP.get())
            .button(202, 102, 20, BTN_O2_UP, PLUS.get(), O2_TIP.get())
            .readout(8, 120, 140, SteelworksPanels::lance, null)
            .button(150, 116, 20, BTN_LANCE_DOWN, MINUS.get(), LANCE_TIP.get())
            .button(172, 116, 20, BTN_LANCE_UP, PLUS.get(), LANCE_TIP.get())
            .button(194, 116, 28, BTN_LANCE_PARK, PARK.get(), PARK_TIP.get())
            .readout(8, 134, 214, v -> METAL.get(v.num(C_METAL_KG, 1000, 1), v.num(C_C_PCT1000, 1000, 3), v.raw(C_SCRAP_KG)), null)
            .readout(8, 146, 170, v -> SLAG.get(v.raw(C_SLAG_KG), v.num(C_BASICITY10, 10, 1), v.raw(C_FEO_PCT), v.raw(C_GAS_MB)), null)
            .slot(186, 146, 0, s -> s.is(PcItems.STEEL_SCRAP.get()) || s.is(PcItems.PIG_IRON.get()), SCRAP_SLOT.get())
            .slot(206, 146, 1, s -> s.is(PcItems.BURNT_LIME.get()), LIME_SLOT.get())
            .build();

    public static final PanelLayout LADLE_STATION = PanelLayout.builder("ladle_station", 220, 140, L_CHANNELS)
            .dial(8, 24, true, L_TEMP_C, 1, 1800, Double.NaN, 0, CELSIUS.get(), STEEL.get(), null)
            .lamp(84, 24, L_FLAGS, LF_LADLE, LampColor.GREEN, LADLE.get())
            .lamp(84, 38, L_FLAGS, LF_STIR, LampColor.AMBER, STIR.get())
            .lamp(150, 24, L_FLAGS, LF_KILLED, LampColor.GREEN, KILLED.get())
            .lamp(150, 38, L_FLAGS, LF_SPEC, LampColor.GREEN, SPEC.get())
            .readout(84, 56, 128, SteelworksPanels::grade, null)
            .readout(84, 70, 100, v -> ARGON.get(v.raw(L_ARGON_MB), v.num(L_STIR_MIN10, 10, 1)), null)
            .button(188, 66, 24, BTN_STIR, STIR.get(), STIR_TIP.get())
            .readout(8, 96, 204, v -> v.bit(L_FLAGS, LF_LADLE) ? STATION_CHEM.get(v.num(L_MASS_KG, 1000, 1), v.num(L_C_PCT1000, 1000, 3),
                    v.num(L_MN_PCT100, 100, 2), v.num(L_SI_PCT100, 100, 2), v.num(L_CR_PCT100, 100, 2), v.raw(L_O_PPM)) : NO_LADLE.get(), null)
            .slot(16, 116, 0, LadleStationBlockEntity::isAlloy, BIN.get())
            .button(36, 116, 28, BTN_ADD_1, ADD.get(), ADD_TIP.get())
            .slot(84, 116, 1, LadleStationBlockEntity::isAlloy, BIN.get())
            .button(104, 116, 28, BTN_ADD_2, ADD.get(), ADD_TIP.get())
            .slot(152, 116, 2, LadleStationBlockEntity::isAlloy, BIN.get())
            .button(172, 116, 28, BTN_ADD_3, ADD.get(), ADD_TIP.get())
            .build();

    public static final PanelLayout REHEATING = furnace("reheating_furnace", BatchFurnaceUnit.Spec.REHEATING, false);
    public static final PanelLayout HEAT_TREATMENT = furnace("heat_treatment_furnace", BatchFurnaceUnit.Spec.HEAT_TREATMENT, true);

    public static final PanelLayout STAND = PanelLayout.builder("rolling_mill_stand", 220, 130, M_CHANNELS)
            .dial(8, 24, false, SHAFT_RPM10, 10, 128, Double.NaN, 0, RPM.get(), SHAFT.get(), shaftTip())
            .dial(64, 24, false, M_FORCE_KN, 1, 20000, Double.NaN, 0, KN.get(), FORCE.get(), null)
            .dial(120, 24, false, M_TORQUE_KNM, 1, 400, 300, 0, KNM.get(), TORQUE.get(), null)
            .lamp(176, 24, FLAGS, F_RUNNING, LampColor.AMBER, RUNNING.get())
            .lamp(176, 38, FLAGS, F_ALARM, LampColor.RED, SHEARED.get())
            .readout(8, 84, 140, v -> GAP.get(v.raw(M_GAP_MM), rolls(v.raw(M_ROLLS))), null)
            .button(8, 96, 20, BTN_GAP_DOWN_BIG, MINUS2.get(), GAP_TIP.get())
            .button(30, 96, 20, BTN_GAP_DOWN, MINUS.get(), GAP_TIP.get())
            .button(52, 96, 20, BTN_GAP_UP, PLUS.get(), GAP_TIP.get())
            .button(74, 96, 20, BTN_GAP_UP_BIG, PLUS2.get(), GAP_TIP.get())
            .button(100, 96, 40, BTN_KIND, ROLLS.get(), ROLLS_TIP.get())
            .readout(8, 114, 204, v -> PASS.get(v.raw(M_PASSES), outcome(v.raw(M_OUTCOME)), v.raw(M_POWER_KW)),
                    v -> v.raw(M_PIECE_H_MM) > 0 ? PIECE.get(v.raw(M_PIECE_H_MM), v.raw(M_PIECE_W_MM), v.raw(M_PIECE_C)) : NO_PIECE.get())
            .noInventory()
            .build();

    public static final PanelLayout PIERCING = PanelLayout.builder("piercing_mill", 200, 140, P_CHANNELS)
            .dial(8, 24, false, SHAFT_RPM10, 10, 128, Double.NaN, 0, RPM.get(), SHAFT.get(), shaftTip())
            .lamp(8, 80, FLAGS, F_RUNNING, LampColor.AMBER, RUNNING.get())
            .bar(66, 24, 126, P_PROGRESS_PERMIL, 10, 100, 0xFFC8A050, CYCLE.get(), null)
            .readout(66, 40, 126, v -> PIERCE.get(v.raw(P_OD_MM), v.raw(P_WALL_MM), pipeKind(v.raw(P_KIND))), null)
            .button(66, 52, 40, BTN_SIZE, SIZE.get(), SIZE_TIP.get())
            .button(110, 52, 40, BTN_KIND, KIND.get(), KIND_TIP.get())
            .readout(66, 70, 126, v -> PIERCE_LAST.get(v.raw(P_BILLET_C), v.raw(P_PIPES)), null)
            .slot(16, 112, 0, PiercingMillBlockEntity::pierceable, BILLET.get())
            .caption(40, 117, Component.literal("→"))
            .output(104, 112, 1, PIPE.get()).output(122, 112, 2, PIPE.get()).output(140, 112, 3, PIPE.get())
            .build();

    public static final PanelLayout WELDER = PanelLayout.builder("pipe_welder", 220, 150, W_CHANNELS)
            .dial(8, 24, false, SHAFT_RPM10, 10, 128, Double.NaN, 0, RPM.get(), SHAFT.get(), shaftTip())
            .lamp(8, 80, FLAGS, F_RUNNING, LampColor.AMBER, RUNNING.get())
            .bar(66, 24, 146, W_PROGRESS_PERMIL, 10, 100, 0xFFC8A050, CYCLE.get(), null)
            .readout(66, 40, 146, v -> WELD.get(v.raw(W_OD_MM), v.raw(W_WALL_MM), pipeKind(v.raw(W_KIND))), null)
            .button(66, 52, 40, BTN_SIZE, SIZE.get(), SIZE_TIP.get())
            .button(110, 52, 40, BTN_KIND, KIND.get(), KIND_TIP.get())
            .readout(8, 94, 160, v -> SPEED.get(v.num(W_SPEED10, 10, 1), v.raw(W_SPEED_SET)), null)
            .button(170, 90, 20, BTN_SPEED_DOWN, MINUS.get(), SPEED_TIP.get())
            .button(192, 90, 20, BTN_SPEED_UP, PLUS.get(), SPEED_TIP.get())
            .readout(8, 108, 160, v -> POWER.get(v.raw(W_POWER_KW), v.raw(W_OPT_KW)), null)
            .button(170, 104, 20, BTN_POWER_DOWN, MINUS.get(), POWER_TIP.get())
            .button(192, 104, 20, BTN_POWER_UP, PLUS.get(), POWER_TIP.get())
            .readout(8, 120, 204, v -> WELD_LAST.get(v.raw(W_PIPES), v.raw(W_DEFECT_PPM)), null)
            .slot(16, 130, 0, PipeWelderBlockEntity::isPlate, PLATE.get())
            .caption(40, 135, Component.literal("→"))
            .output(104, 130, 1, PIPE.get()).output(122, 130, 2, PIPE.get()).output(140, 130, 3, PIPE.get())
            .build();

    public static final PanelLayout HYDROTEST = PanelLayout.builder("hydrotest_bench", 200, 130, H_CHANNELS)
            .dial(8, 24, true, H_PRESSURE_KPA, 1000, 60, Double.NaN, 1, MPA.get(), PRESSURE.get(), null)
            .lamp(84, 24, FLAGS, F_RUNNING, LampColor.AMBER, RUNNING.get())
            .lamp(84, 38, FLAGS, F_ALARM, LampColor.RED, BURST.get())
            .bar(84, 56, 108, H_WATER_MB, 1, 4000, 0xFF3060C0, WATER.get(), v -> MB.get(v.raw(H_WATER_MB)))
            .readout(8, 96, 184, v -> HYDRO.get(v.num(H_TEST_KPA, 1000, 1), v.raw(H_TESTED), v.raw(H_BURST)), null)
            .slot(16, 110, 0, HydrotestBenchBlockEntity::testable, COLD_PIPE.get())
            .caption(40, 115, Component.literal("→"))
            .output(104, 110, 1, PIPE.get()).output(122, 110, 2, PIPE.get()).output(140, 110, 3, PIPE.get())
            .output(158, 110, 4, PIPE.get())
            .build();

    public static final PanelLayout ASU = PanelLayout.builder("air_separator", 230, 150, A_CHANNELS)
            .dial(8, 24, true, A_COLD_C, 1, 60, Double.NaN, 0, CELSIUS.get(), COLD_BOX.get(), v -> Component.literal(v.raw(A_COLD_C) + " °C"))
            .dial(80, 24, false, A_POWER_KW, 1, 1200, Double.NaN, 0, KW.get(), COMPRESSOR.get(), null)
            .dial(136, 24, false, A_O2_NM3H, 1, 3000, Double.NaN, 0, NM3H.get(), OXYGEN.get(), null)
            .lamp(192, 24, FLAGS, F_RUNNING, LampColor.GREEN, RUNNING.get())
            .lamp(192, 38, FLAGS, AF_FORMED, LampColor.WHITE, FORMED.get())
            .lamp(192, 52, FLAGS, AF_ICED, LampColor.AMBER, ICED.get())
            .lamp(192, 66, FLAGS, F_ALARM, LampColor.RED, ALARM.get())
            .readout(8, 92, 214, v -> ASU_STATE.get(asuState(v.raw(A_STATE)), v.num(A_PURITY_PERMIL, 10, 1)),
                    v -> ASU_FLOW.get(v.raw(A_N2_NM3H), v.raw(A_O2_MB)))
            .bar(8, 106, 100, A_LOX_PPM10, 10, 6, 0xFFC04030, LOX_HC.get(), v -> Component.literal(v.num(A_LOX_PPM10, 10, 1) + " ppm"))
            .bar(118, 106, 104, A_SIEVE_PCT, 1, 100, 0xFFB0A070, SIEVE.get(), v -> Component.literal(v.raw(A_SIEVE_PCT) + " %"))
            .readout(8, 122, 120, v -> LIQUID.get(v.raw(A_LIQUID_PCT), Component.literal(v.bit(FLAGS, AF_LOX) ? "LOX" : "LIN")), null)
            .button(130, 118, 18, BTN_LIQ_DOWN, MINUS.get(), LIQUID_TIP.get())
            .button(150, 118, 18, BTN_LIQ_UP, PLUS.get(), LIQUID_TIP.get())
            .button(170, 118, 52, BTN_LIQ_KIND, LIQ_KIND.get(), null)
            .button(8, 134, 52, BTN_REGEN, REGEN.get(), REGEN_TIP.get())
            .button(62, 134, 52, BTN_TRIP, TRIP.get(), TRIP_TIP.get())
            .button(116, 134, 52, BTN_DRAIN, DRAIN.get(), DRAIN_TIP.get())
            .lamp(176, 136, FLAGS, AF_REGEN, LampColor.AMBER, REGEN.get())
            .lamp(206, 136, FLAGS, AF_TRIP, LampColor.GREEN, TRIP.get())
            .noInventory()
            .build();

    private SteelworksPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(CONVERTER, LADLE_STATION, REHEATING, HEAT_TREATMENT, STAND, PIERCING, WELDER, HYDROTEST, ASU);
    }

    private static PanelLayout furnace(String id, BatchFurnaceUnit.Spec spec, boolean heatTreatment) {
        PanelLayout.Builder b = PanelLayout.builder(id, 220, 150, R_CHANNELS)
                .dial(8, 24, true, R_TEMP_C, 1, 1400, spec.maxSetpointK() - 273.15, 0, CELSIUS.get(), FURNACE.get(), null)
                .lamp(84, 24, R_FLAGS, RF_FIRING, LampColor.AMBER, FIRING.get())
                .bar(84, 44, 128, R_GAS_MB, 1, 8000, 0xFFB0B060, GAS.get(), v -> MB.get(v.raw(R_GAS_MB)))
                .readout(84, 60, 128, v -> SET.get(v.raw(R_SET_C), v.raw(R_RAMP_KH)), null)
                .button(84, 72, 18, BTN_SET_DOWN_BIG, MINUS2.get(), SET_TIP.get())
                .button(104, 72, 18, BTN_SET_DOWN, MINUS.get(), SET_TIP.get())
                .button(124, 72, 18, BTN_SET_UP, PLUS.get(), SET_TIP.get())
                .button(144, 72, 18, BTN_SET_UP_BIG, PLUS2.get(), SET_TIP.get())
                .button(170, 72, 20, BTN_RAMP_DOWN, MINUS.get(), RAMP_TIP.get())
                .button(192, 72, 20, BTN_RAMP_UP, PLUS.get(), RAMP_TIP.get())
                .readout(8, 96, 204, v -> PIECES.get(v.raw(R_LOADED), spec.capacity(), v.raw(R_COLD_C), v.raw(R_HOT_C)), null);
        if (heatTreatment) {
            b.lamp(150, 24, R_FLAGS, RF_QUENCH, LampColor.WHITE, QUENCH.get())
                    .button(8, 108, 60, BTN_MODE, MODE.get(), MODE_TIP.get())
                    .button(70, 108, 52, BTN_UNLOAD, UNLOAD.get(), UNLOAD_TIP.get());
        }
        return b.slot(16, 128, 0, s -> Steelworks.isStock(s) || heatTreatment && Steelworks.isPipe(s), STOCK.get())
                .caption(40, 133, Component.literal("→"))
                .output(104, 128, 1, HEATED.get()).output(122, 128, 2, HEATED.get())
                .output(140, 128, 3, HEATED.get()).output(158, 128, 4, HEATED.get())
                .build();
    }

    private static PanelLayout.Tip shaftTip() {
        return v -> SHAFT_TIP.get(v.num(SHAFT_RPM10, 10, 0));
    }

    private static <E extends Enum<E>> E at(E[] all, int ordinal) {
        return ordinal < 0 || ordinal >= all.length ? null : all[ordinal];
    }

    private static Component phase(int ordinal) {
        ConverterPhase p = at(ConverterPhase.values(), ordinal);
        return p == null ? Component.empty() : SteelworksText.phase(p);
    }

    private static Component rolls(int ordinal) {
        MillRollKind k = at(MillRollKind.values(), ordinal);
        return k == null ? Component.empty() : SteelworksText.rolls(k);
    }

    private static Component pipeKind(int ordinal) {
        PipeKind k = at(PipeKind.values(), ordinal);
        return k == null ? Component.empty() : SteelworksText.pipeKind(k);
    }

    private static Component asuState(int ordinal) {
        AsuState s = at(AsuState.values(), ordinal);
        return s == null ? Component.empty() : SteelworksText.asuState(s);
    }

    private static Component outcome(int ordinal) {
        PassResult.Outcome o = at(PassResult.Outcome.values(), ordinal);
        return o == null ? Component.literal("—") : SteelworksText.msg("outcome." + o.name().toLowerCase(Locale.ROOT));
    }

    private static Component lance(PanelValues v) {
        if (v.raw(C_LANCE_CM) < 0) return NO_LANCE.get();
        return LANCE.get(v.num(C_LANCE_CM, 100, 1), v.num(C_LANCE_SET_CM, 100, 1), v.raw(C_O2_MB));
    }

    private static Component grade(PanelValues v) {
        SteelFamily f = at(SteelFamily.values(), v.raw(L_FAMILY));
        if (f == null || !v.bit(L_FLAGS, LF_LADLE)) return Component.empty();
        return GRADE.get(Component.literal(f.designation + " (").append(SteelworksText.msg("family." + f.name().toLowerCase(Locale.ROOT)))
                .append(")"));
    }
}
