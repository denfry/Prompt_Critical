package dev.denfry.promptcritical.mod.ironworks;

import static dev.denfry.promptcritical.mod.panel.PanelText.CELSIUS;
import static dev.denfry.promptcritical.mod.panel.PanelText.PA;
import static dev.denfry.promptcritical.mod.panel.PanelText.RPM;
import static dev.denfry.promptcritical.mod.panel.PanelText.RUNNING;
import static dev.denfry.promptcritical.mod.panel.PanelText.SHAFT;
import static dev.denfry.promptcritical.mod.panel.PanelText.SHAFT_TIP;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.metallurgy.BakingFurnaceUnit;
import dev.denfry.promptcritical.sim.metallurgy.BlastFurnaceState;
import dev.denfry.promptcritical.sim.metallurgy.StoveMode;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * Местные щиты коксохима и домны. У машин на валу — тахометр ({@link #SHAFT_RPM10}) и лампы ({@link #FLAGS});
 * остальные каналы — свои с {@link #OWN}. Кнопки — общие номера.
 */
public final class IronworksPanels {
    public static final int SHAFT_RPM10 = 0, FLAGS = 1, OWN = 2;
    public static final int F_RUNNING = 0, F_ALARM = 1;
    public static final int BTN_SET_DOWN = 0, BTN_SET_UP = 1, BTN_RAMP_DOWN = 2, BTN_RAMP_UP = 3, BTN_UNLOAD = 4, BTN_DOWN = 5,
            BTN_UP = 6, BTN_KIND = 7, BTN_MODE = 8, BTN_LOAD_DOWN = 9, BTN_LOAD_UP = 10, BTN_LIME_DOWN = 11, BTN_LIME_UP = 12,
            BTN_BLANKS = 13, BTN_HOT_DOWN = 14, BTN_HOT_UP = 15, BTN_AUTO = 16, BTN_COOLING = 17;

    // коксовая батарея
    public static final int B_WALL_C = OWN, B_SET_C = OWN + 1, B_RAMP_KH10 = OWN + 2, B_MAIN_PA = OWN + 3, B_GAS_NM3H = OWN + 4,
            B_MAKE_NM3H = OWN + 5, B_FIREBOX_KW = OWN + 6, B_DAMAGE_PERMIL = OWN + 7, B_OVENS = OWN + 8, B_GAS_MB = OWN + 9,
            B_EMPTY = OWN + 10, B_COKING = OWN + 11, B_READY = OWN + 12, B_FLAGS = OWN + 13, B_CHANNELS = OWN + 14;
    public static final int BF_HOT = 0, BF_BLEED = 1, BF_VACUUM = 2, BF_FAILED = 3;
    // улавливание
    public static final int P_KIND = OWN, P_CAPTURED_KG = OWN + 1, P_PRODUCT_MB = OWN + 2, P_REAGENT_MB = OWN + 3, P_CHANNELS = OWN + 4;
    // пресс
    public static final int E_BINDER_PCT = OWN, E_PITCH_KG = OWN + 1, E_PROGRESS_PERMIL = OWN + 2, E_CHANNELS = OWN + 3;
    // обжиговая печь
    public static final int K_TEMP_C = OWN, K_SET_C = OWN + 1, K_RAMP_KH10 = OWN + 2, K_RATE_KH10 = OWN + 3, K_LOADED = OWN + 4,
            K_SOAK_H10 = OWN + 5, K_GAS_MB = OWN + 6, K_CHANNELS = OWN + 7;
    // сепаратор
    public static final int S_FE_PCT10 = OWN, S_CONC_KG = OWN + 1, S_TAIL_KG = OWN + 2, S_PROGRESS_PERMIL = OWN + 3, S_CHANNELS = OWN + 4;
    // агломашина
    public static final int G_BASICITY10 = OWN, G_BREEZE_KG = OWN + 1, G_LIME_KG = OWN + 2, G_GAS_MB = OWN + 3, G_FE_PCT = OWN + 4,
            G_PROGRESS_PERMIL = OWN + 5, G_CHANNELS = OWN + 6;
    // воздухонагреватель
    public static final int H_DOME_C = OWN, H_BOTTOM_C = OWN + 1, H_BLAST_C = OWN + 2, H_MODE = OWN + 3, H_GAS_NM3H = OWN + 4,
            H_VALVE_NM3H = OWN + 5, H_GAS_MB = OWN + 6, H_CHANNELS = OWN + 7;
    // домна
    public static final int F_STATE = OWN, F_HEARTH_C = OWN + 1, F_BLAST_NM3H = OWN + 2, F_BLAST_C = OWN + 3, F_TOP_C = OWN + 4,
            F_IRON_KG = OWN + 5, F_SLAG_KG = OWN + 6, F_RAFT_C = OWN + 7, F_HANG_PERMIL = OWN + 8, F_ETA_PERMIL = OWN + 9,
            F_WEAR_PERMIL = OWN + 10, F_PROD_100 = OWN + 11, F_LAYERS = OWN + 12, F_VU = OWN + 13, F_ORE_KG = OWN + 14,
            F_COKE_KG = OWN + 15, F_LIME_KG = OWN + 16, F_ORE_LOAD100 = OWN + 17, F_LIME100 = OWN + 18, F_HOTSET_C = OWN + 19,
            F_TUYERES = OWN + 20, F_FLAGS = OWN + 21, F_CHANNELS = OWN + 22;
    public static final int FF_TAP = 0, FF_BLANKS = 1, FF_AUTO = 2, FF_BLEED = 3, FF_TUYERE = 4, FF_COOLING = 5;

    private static final Tr MINUS = PanelText.tr("iron.minus", "−", "−"), PLUS = PanelText.tr("iron.plus", "+", "+"),
            NM3H = PanelText.tr("iron.unit.nm3h", "Nm³/h", "нм³/ч"),
            MB = PanelText.tr("iron.mb", "%s mB", "%s мБ"),
            KG = PanelText.tr("iron.kg", "%s kg", "%s кг"),
            CYCLE = PanelText.tr("iron.cycle", "Cycle", "Цикл"),
            GAS = PanelText.tr("iron.gas", "Fuel gas", "Горючий газ"),
            ALARM = PanelText.tr("iron.alarm", "Alarm", "Авария");

    private static final Tr WALL = PanelText.tr("iron.battery.wall", "Flue wall", "Простенок"),
            MAIN = PanelText.tr("iron.battery.main", "Gas main", "Газосборник"),
            HEATING = PanelText.tr("iron.battery.heating", "Heating gas", "Газ обогрева"),
            HOT = PanelText.tr("iron.battery.hot", "Hot", "Разогрета"),
            BLEED = PanelText.tr("iron.battery.bleed", "Bleeders", "Свечи"),
            VACUUM = PanelText.tr("iron.battery.vacuum", "Vacuum", "Разрежение"),
            FAILED = PanelText.tr("iron.battery.failed", "Brickwork", "Кладка"),
            SET = PanelText.tr("iron.set", "Set %s °C · ramp %s K/h", "Уставка %s °C · %s К/ч"),
            OVENS = PanelText.tr("iron.battery.ovens", "Ovens %s: empty %s · coking %s · ready %s",
                    "Камер %s: пусто %s · коксуется %s · готово %s"),
            MAKE = PanelText.tr("iron.battery.make", "Gas make %s Nm³/h · firebox %s kW", "Выход газа %s нм³/ч · топка %s кВт"),
            DAMAGE = PanelText.tr("iron.battery.damage", "Brickwork damage %s%%", "Повреждение кладки %s%%"),
            FIREBOX = PanelText.tr("iron.battery.firebox", "Warm-up firebox: coal, coke", "Топка разогрева: уголь, кокс"),
            SET_TIP = PanelText.tr("iron.set_tip", "Setpoint −/+ 50 °C", "Уставка −/+ 50 °C"),
            RAMP_TIP = PanelText.tr("iron.ramp_tip", "Heat-up rate −/+", "Скорость нагрева −/+");

    private static final Tr KIND = PanelText.tr("iron.byproduct.kind", "Kind", "Вид"),
            KIND_TIP = PanelText.tr("iron.byproduct.kind_tip", "Change the apparatus (tanks must be empty)",
                    "Сменить аппарат (баки должны быть пусты)"),
            CAPTURED = PanelText.tr("iron.byproduct.captured", "%s · captured %s kg", "%s · уловлено %s кг"),
            PRODUCT = PanelText.tr("iron.byproduct.product", "Product", "Продукт"),
            REAGENT = PanelText.tr("iron.byproduct.reagent", "Reagent", "Реагент"),
            SULFATE = PanelText.tr("iron.byproduct.sulfate", "Ammonium sulfate", "Сульфат аммония");

    private static final Tr BINDER = PanelText.tr("iron.extruder.binder", "Binder %s%% · pitch %s kg", "Связующее %s%% · пек %s кг"),
            BINDER_TIP = PanelText.tr("iron.extruder.binder_tip", "Pitch share −/+ 1 %% (window 25–30 %%)", "Доля пека −/+ 1 %% (окно 25–30 %%)"),
            FILLER = PanelText.tr("iron.extruder.filler", "Filler: pitch coke", "Наполнитель: пековый кокс"),
            PITCH = PanelText.tr("iron.extruder.pitch", "Binder: coal-tar pitch", "Связующее: пек"),
            ELECTRODE = PanelText.tr("iron.electrode", "Electrode", "Электрод");

    private static final Tr KILN = PanelText.tr("iron.baking.kiln", "Kiln", "Печь"),
            RATE = PanelText.tr("iron.baking.rate", "Heating %s K/h · soak %s h", "Нагрев %s К/ч · выдержка %s ч"),
            LOADED = PanelText.tr("iron.baking.loaded", "Electrodes in the kiln", "Электродов в печи"),
            CRACKS = PanelText.tr("iron.baking.cracks", "Cracks", "Трещины"),
            UNLOAD = PanelText.tr("iron.baking.unload", "Unload", "Выгрузить"),
            UNLOAD_TIP = PanelText.tr("iron.baking.unload_tip", "Unload the charge (hot unloading cracks electrodes)",
                    "Выгрузить садку (горячая выгрузка трескает электроды)"),
            GREEN = PanelText.tr("iron.baking.green", "Green electrode", "Сырой электрод");

    private static final Tr CONC = PanelText.tr("iron.separator.conc", "Concentrate %s%% Fe", "Концентрат %s%% Fe"),
            MAGNETITE = PanelText.tr("iron.separator.feed", "Magnetite ore", "Магнетитовая руда"),
            CONC_OUT = PanelText.tr("iron.separator.out", "Concentrate", "Концентрат"),
            TAILS = PanelText.tr("iron.separator.tails", "Tailings", "Хвосты");

    private static final Tr BASICITY = PanelText.tr("iron.sinter.basicity", "Basicity CaO/SiO₂ %s", "Основность CaO/SiO₂ %s"),
            BASICITY_TIP = PanelText.tr("iron.sinter.basicity_tip", "Basicity −/+ 0.1", "Основность −/+ 0,1"),
            SINTER_STOCK = PanelText.tr("iron.sinter.stock", "Breeze %s kg · flux %s kg · last %s%% Fe",
                    "Мелочь %s кг · флюс %s кг · последний %s%% Fe"),
            WEAK = PanelText.tr("iron.sinter.weak", "Weak sinter", "Слабый агломерат"),
            ORE = PanelText.tr("iron.sinter.ore", "Concentrate or ore (10 per batch)", "Концентрат или руда (10 на спекание)"),
            BREEZE = PanelText.tr("iron.sinter.breeze", "Coke breeze", "Коксовая мелочь"),
            FLUX = PanelText.tr("iron.flux", "Flux: limestone, calcite", "Флюс: известняк, кальцит"),
            SINTER_OUT = PanelText.tr("iron.sinter.out", "Sinter", "Агломерат");

    private static final Tr DOME = PanelText.tr("iron.stove.dome", "Dome", "Купол"),
            BOTTOM = PanelText.tr("iron.stove.bottom", "Checker bottom", "Низ насадки"),
            HOT_BLAST = PanelText.tr("iron.hot_blast", "Hot blast", "Дутьё"),
            STOVE_GAS = PanelText.tr("iron.stove.gas", "Gas %s Nm³/h · valve %s Nm³/h", "Газ %s нм³/ч · клапан %s нм³/ч"),
            VALVE_TIP = PanelText.tr("iron.stove.valve_tip", "Gas valve −/+ 360 Nm³/h", "Газовый клапан −/+ 360 нм³/ч"),
            MODE = PanelText.tr("iron.stove.mode", "Mode", "Режим"),
            MODE_TIP = PanelText.tr("iron.stove.mode_tip", "Next mode (the furnace's auto cycle overrides it)",
                    "Следующий режим (автоцикл домны его переопределяет)"),
            FIRING = PanelText.tr("iron.stove.firing", "Firing", "Горение");

    private static final Tr HEARTH = PanelText.tr("iron.bf.hearth", "Hearth", "Горн"),
            BLAST_FLOW = PanelText.tr("iron.bf.blast", "Blast", "Дутьё"),
            TOP = PanelText.tr("iron.bf.top", "Top gas", "Колошник"),
            IRON = PanelText.tr("iron.bf.iron", "Hot metal in the hearth", "Чугун в горне"),
            SLAG = PanelText.tr("iron.bf.slag", "Slag in the hearth", "Шлак в горне"),
            HANG = PanelText.tr("iron.bf.hang", "Hanging risk", "Риск подвисания"),
            WEAR_BAR = PanelText.tr("iron.bf.wear", "Hearth lining wear", "Износ горна"),
            LOAD = PanelText.tr("iron.bf.load", "Ore load %s · flux %s per ore", "Рудная нагрузка %s · флюс %s на руду"),
            LOAD_TIP = PanelText.tr("iron.bf.load_tip", "Ore load −/+ 0.25 kg/kg; flux −/+ 0.05", "Рудная нагрузка −/+ 0,25 кг/кг; флюс −/+ 0,05"),
            HOT_SET = PanelText.tr("iron.bf.hot_set", "Hot blast set %s °C", "Уставка дутья %s °C"),
            HOPPER = PanelText.tr("iron.bf.hopper", "Hopper: ore %s · coke %s · flux %s kg", "Бункер: руда %s · кокс %s · флюс %s кг"),
            PROD = PanelText.tr("iron.bf.prod", "%s t/m³·day · V %s m³ · %s tuyeres · %s layers · η %s",
                    "%s т/м³·сут · V %s м³ · фурм %s · колош %s · η %s"),
            RAFT = PanelText.tr("iron.bf.raft", "Raceway %s °C", "Фурменный очаг %s °C"),
            TAP = PanelText.tr("iron.bf.tap", "Taphole", "Лётка"),
            TUYERE = PanelText.tr("iron.bf.tuyere", "Tuyere leak", "Течь фурмы"),
            AUTO = PanelText.tr("iron.bf.auto", "Auto stoves", "Автоцикл"),
            BLANKS = PanelText.tr("iron.bf.blanks", "Coke only", "Холостые"),
            COOLING = PanelText.tr("iron.bf.cooling", "Cooling", "Охлаждение"),
            BF_ORE = PanelText.tr("iron.bf.ore", "Sinter or lump ore", "Агломерат или руда"),
            BF_COKE = PanelText.tr("iron.bf.coke", "Coke (quenched)", "Кокс (потушенный)"),
            NO_FURNACE = PanelText.tr("iron.bf.none", "Furnace not formed", "Печь не собрана");

    public static final PanelLayout COKE_BATTERY = PanelLayout.builder("coke_battery", 220, 168, B_CHANNELS)
            .dial(8, 24, true, B_WALL_C, 1, 1400, 1350, 0, CELSIUS.get(), WALL.get(), null)
            .dial(80, 24, false, B_MAIN_PA, 1, 600, 300, 0, PA.get(), MAIN.get(), null)
            .dial(136, 24, false, B_GAS_NM3H, 1, 3000, Double.NaN, 0, NM3H.get(), HEATING.get(), null)
            .lamp(192, 24, B_FLAGS, BF_HOT, LampColor.GREEN, HOT.get())
            .lamp(192, 40, B_FLAGS, BF_BLEED, LampColor.AMBER, BLEED.get())
            .lamp(192, 56, B_FLAGS, BF_VACUUM, LampColor.RED, VACUUM.get())
            .lamp(192, 72, B_FLAGS, BF_FAILED, LampColor.RED, FAILED.get())
            .readout(8, 96, 204, v -> SET.get(v.raw(B_SET_C), v.num(B_RAMP_KH10, 10, 1)), null)
            .button(8, 108, 20, BTN_SET_DOWN, MINUS.get(), SET_TIP.get())
            .button(30, 108, 20, BTN_SET_UP, PLUS.get(), SET_TIP.get())
            .button(60, 108, 20, BTN_RAMP_DOWN, MINUS.get(), RAMP_TIP.get())
            .button(82, 108, 20, BTN_RAMP_UP, PLUS.get(), RAMP_TIP.get())
            .readout(8, 128, 204, v -> OVENS.get(v.raw(B_OVENS), v.raw(B_EMPTY), v.raw(B_COKING), v.raw(B_READY)), null)
            .readout(8, 140, 180, v -> MAKE.get(v.raw(B_MAKE_NM3H), v.raw(B_FIREBOX_KW)),
                    v -> DAMAGE.get(v.num(B_DAMAGE_PERMIL, 10, 1)))
            .readout(8, 152, 180, v -> gasStock(v.raw(B_GAS_MB)), null)
            .slot(196, 146, 0, s -> s.is(IronworksTags.FIREBOX_FUEL), FIREBOX.get())
            .build();

    public static final PanelLayout BYPRODUCT = PanelLayout.builder("byproduct_plant", 200, 110, P_CHANNELS)
            .readout(8, 24, 184, v -> CAPTURED.get(kind(v.raw(P_KIND)), v.raw(P_CAPTURED_KG)), null)
            .bar(8, 44, 120, P_PRODUCT_MB, 1, 8000, 0xFF2A2018, PRODUCT.get(), v -> MB.get(v.raw(P_PRODUCT_MB)))
            .bar(8, 68, 120, P_REAGENT_MB, 1, 8000, 0xFFC8B060, REAGENT.get(), v -> MB.get(v.raw(P_REAGENT_MB)))
            .button(140, 44, 52, BTN_KIND, KIND.get(), KIND_TIP.get())
            .output(160, 80, 0, SULFATE.get())
            .build();

    public static final PanelLayout EXTRUDER = PanelLayout.builder("electrode_extruder", 200, 150, E_CHANNELS)
            .dial(8, 24, false, SHAFT_RPM10, 10, 256, Double.NaN, 0, RPM.get(), SHAFT.get(), shaftTip())
            .lamp(8, 88, FLAGS, F_RUNNING, LampColor.AMBER, RUNNING.get())
            .bar(66, 34, 126, E_PROGRESS_PERMIL, 10, 100, 0xFFC8A050, CYCLE.get(), null)
            .readout(66, 50, 126, v -> BINDER.get(v.raw(E_BINDER_PCT), v.raw(E_PITCH_KG)), null)
            .button(66, 64, 20, BTN_DOWN, MINUS.get(), BINDER_TIP.get())
            .button(88, 64, 20, BTN_UP, PLUS.get(), BINDER_TIP.get())
            .slot(16, 120, 0, s -> s.is(PcItems.PITCH_COKE.get()), FILLER.get())
            .slot(40, 120, 1, s -> s.is(PcItems.COAL_TAR_PITCH.get()), PITCH.get())
            .caption(70, 125, Component.literal("→"))
            .output(104, 120, 2, ELECTRODE.get())
            .build();

    public static final PanelLayout BAKING = PanelLayout.builder("baking_furnace", 200, 150, K_CHANNELS)
            .dial(8, 24, true, K_TEMP_C, 1, 1200, Double.NaN, 0, CELSIUS.get(), KILN.get(), null)
            .lamp(84, 24, FLAGS, F_RUNNING, LampColor.AMBER, FIRING.get())
            .lamp(84, 40, FLAGS, F_ALARM, LampColor.RED, CRACKS.get())
            .readout(84, 58, 108, v -> SET.get(v.raw(K_SET_C), v.num(K_RAMP_KH10, 10, 1)), null)
            .button(84, 70, 20, BTN_SET_DOWN, MINUS.get(), SET_TIP.get())
            .button(106, 70, 20, BTN_SET_UP, PLUS.get(), SET_TIP.get())
            .button(132, 70, 20, BTN_RAMP_DOWN, MINUS.get(), RAMP_TIP.get())
            .button(154, 70, 20, BTN_RAMP_UP, PLUS.get(), RAMP_TIP.get())
            .readout(8, 92, 184, v -> RATE.get(v.num(K_RATE_KH10, 10, 1), v.num(K_SOAK_H10, 10, 1)), null)
            .bar(8, 108, 88, K_LOADED, 1, BakingFurnaceUnit.CAPACITY, 0xFF505050, LOADED.get(),
                    v -> Component.literal(v.raw(K_LOADED) + " / " + BakingFurnaceUnit.CAPACITY))
            .bar(104, 108, 88, K_GAS_MB, 1, 8000, 0xFFB0B060, GAS.get(), v -> MB.get(v.raw(K_GAS_MB)))
            .button(8, 126, 52, BTN_UNLOAD, UNLOAD.get(), UNLOAD_TIP.get())
            .slot(70, 128, 0, BakingFurnaceBlockEntity::isGreen, GREEN.get())
            .output(104, 128, 1, ELECTRODE.get()).output(122, 128, 2, ELECTRODE.get())
            .output(140, 128, 3, ELECTRODE.get()).output(158, 128, 4, ELECTRODE.get())
            .build();

    public static final PanelLayout SEPARATOR = PanelLayout.builder("magnetic_separator", 200, 150, S_CHANNELS)
            .dial(8, 24, false, SHAFT_RPM10, 10, 256, Double.NaN, 0, RPM.get(), SHAFT.get(), shaftTip())
            .lamp(8, 88, FLAGS, F_RUNNING, LampColor.AMBER, RUNNING.get())
            .bar(66, 34, 126, S_PROGRESS_PERMIL, 10, 100, 0xFFC8A050, CYCLE.get(), null)
            .readout(66, 50, 126, v -> CONC.get(v.num(S_FE_PCT10, 10, 1)), null)
            .slot(16, 120, 0, OreChem::magnetic, MAGNETITE.get())
            .caption(40, 125, Component.literal("→"))
            .output(104, 120, 1, CONC_OUT.get())
            .output(128, 120, 2, TAILS.get())
            .build();

    public static final PanelLayout SINTER = PanelLayout.builder("sinter_strand", 200, 150, G_CHANNELS)
            .dial(8, 24, false, SHAFT_RPM10, 10, 256, Double.NaN, 0, RPM.get(), SHAFT.get(), shaftTip())
            .lamp(8, 80, FLAGS, F_RUNNING, LampColor.AMBER, RUNNING.get())
            .lamp(8, 94, FLAGS, F_ALARM, LampColor.RED, WEAK.get())
            .bar(66, 24, 126, G_PROGRESS_PERMIL, 10, 100, 0xFFC8A050, CYCLE.get(), null)
            .bar(66, 48, 126, G_GAS_MB, 1, 4000, 0xFFB0B060, GAS.get(), v -> MB.get(v.raw(G_GAS_MB)))
            .readout(66, 62, 126, v -> BASICITY.get(v.num(G_BASICITY10, 10, 1)), null)
            .button(66, 74, 20, BTN_DOWN, MINUS.get(), BASICITY_TIP.get())
            .button(88, 74, 20, BTN_UP, PLUS.get(), BASICITY_TIP.get())
            .readout(8, 106, 184, v -> SINTER_STOCK.get(v.raw(G_BREEZE_KG), v.raw(G_LIME_KG), v.raw(G_FE_PCT)), null)
            .slot(16, 124, 0, s -> s.is(IronworksTags.SINTER_ORE), ORE.get())
            .slot(40, 124, 1, s -> s.is(PcItems.COKE_BREEZE.get()), BREEZE.get())
            .slot(64, 124, 2, s -> s.is(IronworksTags.FLUX), FLUX.get())
            .caption(86, 129, Component.literal("→"))
            .output(104, 124, 3, SINTER_OUT.get())
            .output(128, 124, 4, SINTER_OUT.get())
            .build();

    public static final PanelLayout STOVE = PanelLayout.builder("hot_blast_stove", 220, 130, H_CHANNELS)
            .dial(8, 24, true, H_DOME_C, 1, 1500, 1450, 0, CELSIUS.get(), DOME.get(), null)
            .dial(80, 24, false, H_BOTTOM_C, 1, 600, Double.NaN, 0, CELSIUS.get(), BOTTOM.get(), null)
            .dial(136, 24, false, H_BLAST_C, 1, 1300, Double.NaN, 0, CELSIUS.get(), HOT_BLAST.get(), null)
            .lamp(192, 24, FLAGS, F_RUNNING, LampColor.AMBER, FIRING.get())
            .lamp(192, 40, FLAGS, F_ALARM, LampColor.RED, ALARM.get())
            .readout(8, 96, 120, v -> MODE.get().copy().append(": ").append(stoveMode(v.raw(H_MODE))), null)
            .button(130, 92, 44, BTN_MODE, MODE.get(), MODE_TIP.get())
            .readout(8, 110, 150, v -> STOVE_GAS.get(v.raw(H_GAS_NM3H), v.raw(H_VALVE_NM3H)), v -> MB.get(v.raw(H_GAS_MB)))
            .button(162, 106, 20, BTN_DOWN, MINUS.get(), VALVE_TIP.get())
            .button(184, 106, 20, BTN_UP, PLUS.get(), VALVE_TIP.get())
            .noInventory()
            .build();

    public static final PanelLayout BLAST_FURNACE = PanelLayout.builder("blast_furnace", 240, 182, F_CHANNELS)
            .dial(8, 24, true, F_HEARTH_C, 1, 1800, Double.NaN, 0, CELSIUS.get(), HEARTH.get(), v -> RAFT.get(v.raw(F_RAFT_C)))
            .dial(80, 24, false, F_BLAST_NM3H, 1, 12000, Double.NaN, 0, NM3H.get(), BLAST_FLOW.get(), null)
            .dial(132, 24, false, F_BLAST_C, 1, 1300, Double.NaN, 0, CELSIUS.get(), HOT_BLAST.get(), null)
            .dial(184, 24, false, F_TOP_C, 1, 500, 350, 0, CELSIUS.get(), TOP.get(), null)
            .readout(8, 92, 110, IronworksPanels::furnaceState, null)
            .lamp(120, 84, F_FLAGS, FF_TAP, LampColor.AMBER, TAP.get())
            .lamp(120, 98, F_FLAGS, FF_BLEED, LampColor.AMBER, BLEED.get())
            .lamp(176, 84, F_FLAGS, FF_TUYERE, LampColor.RED, TUYERE.get())
            .lamp(176, 98, F_FLAGS, FF_COOLING, LampColor.GREEN, COOLING.get())
            .bar(8, 112, 72, F_IRON_KG, 1, 30000, 0xFFF08020, IRON.get(), v -> KG.get(v.raw(F_IRON_KG)))
            .bar(84, 112, 72, F_SLAG_KG, 1, 15000, 0xFFB0A070, SLAG.get(), v -> KG.get(v.raw(F_SLAG_KG)))
            .bar(160, 112, 72, F_HANG_PERMIL, 10, 100, 0xFFC04030, HANG.get(),
                    v -> Component.literal(v.num(F_HANG_PERMIL, 10, 0) + " %  · " + v.num(F_WEAR_PERMIL, 10, 0) + " %"))
            .readout(8, 126, 150, v -> LOAD.get(v.num(F_ORE_LOAD100, 100, 2), v.num(F_LIME100, 100, 2)), null)
            .button(160, 122, 16, BTN_LOAD_DOWN, MINUS.get(), LOAD_TIP.get())
            .button(178, 122, 16, BTN_LOAD_UP, PLUS.get(), LOAD_TIP.get())
            .button(200, 122, 16, BTN_LIME_DOWN, MINUS.get(), LOAD_TIP.get())
            .button(218, 122, 16, BTN_LIME_UP, PLUS.get(), LOAD_TIP.get())
            .readout(8, 140, 150, v -> HOT_SET.get(v.raw(F_HOTSET_C)), null)
            .button(160, 136, 16, BTN_HOT_DOWN, MINUS.get(), null)
            .button(178, 136, 16, BTN_HOT_UP, PLUS.get(), null)
            .readout(8, 154, 226, v -> HOPPER.get(v.raw(F_ORE_KG), v.raw(F_COKE_KG), v.raw(F_LIME_KG)),
                    v -> PROD.get(v.num(F_PROD_100, 100, 2), v.raw(F_VU), v.raw(F_TUYERES), v.raw(F_LAYERS), v.num(F_ETA_PERMIL, 1000, 2)))
            .button(8, 166, 52, BTN_AUTO, AUTO.get(), null)
            .button(62, 166, 52, BTN_BLANKS, BLANKS.get(), null)
            .button(116, 166, 52, BTN_COOLING, COOLING.get(), null)
            .slot(176, 164, 0, s -> s.is(IronworksTags.FURNACE_ORE), BF_ORE.get())
            .slot(196, 164, 1, BlastFurnaceControllerBlockEntity::isColdCoke, BF_COKE.get())
            .slot(216, 164, 2, s -> s.is(IronworksTags.FLUX), FLUX.get())
            .build();

    private IronworksPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(COKE_BATTERY, BYPRODUCT, EXTRUDER, BAKING, SEPARATOR, SINTER, STOVE, BLAST_FURNACE);
    }

    private static Component gasStock(int mb) {
        return GAS.get().copy().append(": ").append(MB.get(mb));
    }

    private static PanelLayout.Tip shaftTip() {
        return v -> SHAFT_TIP.get(v.num(SHAFT_RPM10, 10, 0));
    }

    private static Component kind(int ordinal) {
        PcProperties.ByproductKind[] all = PcProperties.ByproductKind.values();
        if (ordinal < 0 || ordinal >= all.length) return Component.empty();
        return IronworksText.msg("byproduct." + all[ordinal].getSerializedName());
    }

    private static Component stoveMode(int ordinal) {
        StoveMode[] all = StoveMode.values();
        if (ordinal < 0 || ordinal >= all.length) return Component.empty();
        return IronworksText.msg("stove_mode." + all[ordinal].name().toLowerCase(Locale.ROOT));
    }

    private static Component furnaceState(PanelValues v) {
        int s = v.raw(F_STATE);
        if (s < 0 || s >= BlastFurnaceState.values().length) return NO_FURNACE.get();
        var out = BlastFurnaceControllerBlockEntity.state(BlastFurnaceState.values()[s]).copy();
        if (v.bit(F_FLAGS, FF_AUTO)) out.append(" · ").append(AUTO.get());
        if (v.bit(F_FLAGS, FF_BLANKS)) out.append(" · ").append(BLANKS.get());
        return out;
    }
}
