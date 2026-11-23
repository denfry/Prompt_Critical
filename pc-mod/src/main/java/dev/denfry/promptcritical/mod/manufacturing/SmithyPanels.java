package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.sim.manufacturing.Bloomery;
import dev.denfry.promptcritical.sim.manufacturing.ForgeEvent;
import dev.denfry.promptcritical.sim.manufacturing.HeatColors;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * Щиты ручной ступени (18 §18.7, решение «решения вместо реакции»): сыродутный горн, кузнечный горн, наковальня.
 * Температуру щит показывает так, как её видит кузнец, — цветом каления; число рядом грубое, до 50 °C.
 */
public final class SmithyPanels {
    public static final int FLAGS = 0;
    // сыродутный горн
    public static final int B_TEMP_C = 1, B_BLAST_PCT = 2, B_COAL_KG10 = 3, B_ORE_KG10 = 4, B_IRON_KG10 = 5, B_OVERHEAT_S = 6,
            B_CHANNELS = 7;
    public static final int BF_LIT = 0, BF_DONE = 1, BF_CAST = 2;
    public static final int BUTTON_BELLOWS = 0, BUTTON_TAKE = 1;
    // кузнечный горн
    public static final int H_FIRE_C = 1, H_BLAST_PCT = 2, H_FUEL_KG10 = 3, H_PIECE_C = 4, H_EDGE_C = 5, H_CHANNELS = 6;
    public static final int HF_LIT = 0, HF_PIECE = 1, HF_EDGE = 2;
    // наковальня
    public static final int A_TEMP_C = 1, A_OP = 2, A_A_MM10 = 3, A_B_MM10 = 4, A_LEN_MM = 5, A_SLAG_PCT = 6, A_CRACK = 7,
            A_EVENT = 8, A_LOWER_C = 9, A_UPPER_C = 10, A_ROUND = 11, A_FUSION = 12, A_CHANNELS = 13;
    public static final int AF_PIECE = 0, AF_IN_RANGE = 1, AF_COLD = 2, AF_WATER = 3, AF_OIL = 4, AF_SECOND = 5;
    public static final int BUTTON_DRAW = 0, BUTTON_UPSET = 1, BUTTON_BEND = 2, BUTTON_STRIKE = 3, BUTTON_QUENCH = 4,
            BUTTON_SHAFT = 5, BUTTON_CUT = 6, BUTTON_WELD = 7, BUTTON_QUENCH_OIL = 8;

    // волочильная доска
    public static final int D_DIE_UM = 1, D_OUTCOME = 2, D_FORCE_N = 3, D_LUBE = 4, D_WIRE_UM = 5, D_LENGTH_CM = 6,
            D_STRAIN_PCT = 7, D_METAL = 8, D_CHANNELS = 9;
    public static final int DF_WIRE = 0;
    public static final int BUTTON_DIE_SMALLER = 0, BUTTON_DIE_LARGER = 1, BUTTON_PULL = 2;

    // механический молот и кривошипный пресс
    public static final int P_RPM10 = 1, P_POWER_KW10 = 2, P_STROKE_J = 3, P_OP = 4, P_TARGET_MM = 5, P_ENERGY_PCT = 6,
            P_EVENT = 7, P_SERIES = 8, P_TEMP_C = 9, P_A_MM10 = 10, P_LEN_MM = 11, P_LOWER_C = 12, P_CHANNELS = 13;
    public static final int PF_ON = 0, PF_SHAFT = 1, PF_PIECE = 2, PF_IN_RANGE = 3, PF_COLD = 4, PF_DONE = 5;
    public static final int P_BUTTON_THINNER = 10, P_BUTTON_THICKER = 11, P_BUTTON_ENERGY = 12, P_BUTTON_RUN = 13;
    // волочильный стан
    public static final int W_RPM10 = 1, W_FORCE_KN10 = 2, W_TARGET_UM = 3, W_STOP = 4, W_PASSES = 5, W_LAST_N = 6,
            W_WIRE_UM = 7, W_LENGTH_CM = 8, W_STRAIN_PCT = 9, W_METAL = 10, W_CHANNELS = 11;
    public static final int WF_ON = 0, WF_SHAFT = 1, WF_WIRE = 2;
    public static final int W_BUTTON_RUN = 2;
    // камерные печи
    public static final int F_CHAMBER_C = 1, F_SETPOINT_C = 2, F_POWER_KW10 = 3, F_FUEL_MJ10 = 4, F_TANK_MB = 5, F_EVENT = 6,
            F_PIECE_C = 7, F_HRC = 8, F_CHANNELS = 9;
    public static final int FF_LIT = 0, FF_PIECE = 1, FF_WATER = 2;
    public static final int F_BUTTON_DOWN = 0, F_BUTTON_UP = 1, F_BUTTON_OFF = 2, F_BUTTON_QUENCH = 3;

    private static final Tr TEMP = PanelText.tr("smithy.temp", "Hearth", "Горн"),
            CELSIUS = PanelText.tr("smithy.celsius", "°C", "°C"),
            BLAST = PanelText.tr("smithy.blast", "Bellows blast", "Дутьё мехами"),
            COAL = PanelText.tr("smithy.coal", "Charcoal in the hearth", "Уголь в горне"),
            ORE = PanelText.tr("smithy.ore", "Ore left", "Руды осталось"),
            IRON = PanelText.tr("smithy.iron", "Iron in the bloom", "Железо в крице"),
            KG = PanelText.tr("smithy.kg", "%s kg", "%s кг"),
            PCT = PanelText.tr("smithy.pct", "%s %%", "%s %%"),
            LIT = PanelText.tr("smithy.lit", "Fire", "Огонь"),
            DONE = PanelText.tr("smithy.done", "Bloom ready", "Крица готова"),
            CAST = PanelText.tr("smithy.cast", "Overheated: cast iron", "Перегрев: чугун"),
            BELLOWS = PanelText.tr("smithy.bellows", "Pump the bellows", "Качнуть мехами"),
            TAKE = PanelText.tr("smithy.take", "Take the bloom", "Вынуть крицу"),
            ORE_SLOT = PanelText.tr("smithy.ore_slot", "Iron ore: raw iron", "Железная руда: сырое железо"),
            COAL_SLOT = PanelText.tr("smithy.coal_slot", "Charcoal", "Древесный уголь"),
            FUEL_SLOT = PanelText.tr("smithy.fuel_slot", "Fuel: charcoal, coal, coke", "Топливо: древесный уголь, уголь, кокс"),
            BLOOM_OUT = PanelText.tr("smithy.bloom_out", "Bloom", "Крица"),
            BLOOM_STATUS = PanelText.tr("smithy.bloom_status", "%s kg of ore, %s kg of iron reduced%s",
                    "Руды %s кг, железа восстановлено %s кг%s"),
            OVERHEAT = PanelText.tr("smithy.overheat", " · above 1350 °C for %s s", " · выше 1350 °C уже %s с"),
            FIRE_PIECE = PanelText.tr("smithy.fire_piece", "In the fire", "В огне"),
            EDGE_PIECE = PanelText.tr("smithy.edge_piece", "At the edge (tempering)", "На краю (отпуск)"),
            PIECE_SLOT = PanelText.tr("smithy.piece_slot", "Workpiece or steel stock", "Поковка или стальной прокат"),
            GLOW_AT = PanelText.tr("smithy.glow_at", "%s: %s, about %s °C", "%s: %s, около %s °C"),
            TEMPER_AT = PanelText.tr("smithy.temper_at", "%s: %s, about %s °C", "%s: %s, около %s °C"),
            EMPTY = PanelText.tr("smithy.empty", "%s: empty", "%s: пусто"),
            OP_DRAW = PanelText.tr("smithy.op_draw", "Draw out", "Протяжка"),
            OP_UPSET = PanelText.tr("smithy.op_upset", "Upset", "Осадка"),
            OP_BEND = PanelText.tr("smithy.op_bend", "Bend", "Гибка"),
            STRIKE = PanelText.tr("smithy.strike", "Strike", "Удар"),
            QUENCH = PanelText.tr("smithy.quench", "Quench in water", "Закалить в воде"),
            QUENCH_OIL = PanelText.tr("smithy.quench_oil", "Quench in oil", "Закалить в масле"),
            QUENCH_OIL_TIP = PanelText.tr("smithy.quench_oil_tip", "Needs oil next to the anvil: a pool of crude oil or a tank with a bucket of crude, gas oil or diesel",
                    "Нужно масло рядом с наковальней: лужа нефти или бак с ведром нефти, газойля или дизельного топлива"),
            CUT = PanelText.tr("smithy.cut", "Cut in half", "Разрубить"),
            CUT_TIP = PanelText.tr("smithy.cut_tip", "Hot cutting on the hardy: the other half goes to the second slot",
                    "Горячая рубка: вторая половина ляжет во второй слот"),
            WELD = PanelText.tr("smithy.weld", "Forge weld", "Сварить"),
            WELD_TIP = PanelText.tr("smithy.weld_tip", "Both pieces at white heat; sand from your inventory is the flux",
                    "Оба куска при белом калении; песок из инвентаря — флюс"),
            OIL = PanelText.tr("smithy.oil", "Oil", "Масло"),
            SECOND = PanelText.tr("smithy.second", "Second piece", "Второй кусок"),
            SHAFT = PanelText.tr("smithy.finish_shaft", "Finish as a shaft", "Готово: вал"),
            STRIKE_TIP = PanelText.tr("smithy.strike_tip", "A series of blows with the hammer in your inventory",
                    "Серия ударов молотом из вашего инвентаря"),
            QUENCH_TIP = PanelText.tr("smithy.quench_tip", "Needs water next to the anvil", "Нужна вода рядом с наковальней"),
            SHAFT_TIP = PanelText.tr("smithy.shaft_tip", "Cold, round or square, at least four diameters long",
                    "Холодная, круглая или квадратная, длиной не меньше четырёх диаметров"),
            PIECE = PanelText.tr("smithy.piece", "Ø%s mm, %s mm long", "Ø%s мм, длина %s мм"),
            PIECE_FLAT = PanelText.tr("smithy.piece_flat", "%s × %s mm, %s mm long", "%s × %s мм, длина %s мм"),
            OPERATION = PanelText.tr("smithy.operation", "Operation: %s", "Операция: %s"),
            RANGE = PanelText.tr("smithy.range", "Forging range %s…%s °C", "Ковочный интервал %s…%s °C"),
            IN_RANGE = PanelText.tr("smithy.in_range", "In range", "В интервале"),
            TOO_COLD = PanelText.tr("smithy.too_cold", "Too cold", "Холодно"),
            WATER = PanelText.tr("smithy.water", "Water", "Вода"),
            SLAG_CRACK = PanelText.tr("smithy.slag_crack", "Slag %s %% · cracks: level %s · lack of fusion: level %s",
                    "Шлак %s %% · трещины: уровень %s · непровар: уровень %s"),
            LAST = PanelText.tr("smithy.last", "Last: %s", "Последнее: %s"),
            NO_PIECE = PanelText.tr("smithy.no_piece", "Put a workpiece on the anvil", "Положите поковку на наковальню"),
            NO_ROD = PanelText.tr("smithy.no_rod", "Put in a copper ingot, a rod or wire", "Положите медный слиток, пруток или проволоку"),
            WIRE_LINE = PanelText.tr("smithy.wire_line", "%s: Ø%s mm, %s m", "%s: Ø%s мм, %s м"),
            DIE = PanelText.tr("smithy.die", "Die Ø%s mm", "Волока Ø%s мм"),
            HARDENING = PanelText.tr("smithy.hardening", "Work hardening to the limit", "Наклёп до предела"),
            PULL_LAST = PanelText.tr("smithy.pull_last", "Last pull: %s · %s N", "Последний проход: %s · %s Н"),
            LUBE = PanelText.tr("smithy.lube", "Wax for %s passes", "Воска на %s проходов"),
            SMALLER = PanelText.tr("smithy.die_smaller", "Smaller die", "Волока меньше"),
            LARGER = PanelText.tr("smithy.die_larger", "Larger die", "Волока больше"),
            PULL = PanelText.tr("smithy.pull", "Pull through", "Протянуть"),
            PULL_TIP = PanelText.tr("smithy.pull_tip", "Pull with the tongs in your inventory; the windlass gives 1500 N",
                    "Тянуть клещами из инвентаря; ворот даёт 1500 Н"),
            WORK_SLOT = PanelText.tr("smithy.work_slot", "Rod or wire", "Пруток или проволока"),
            LUBE_SLOT = PanelText.tr("smithy.lube_slot", "Beeswax: honeycomb", "Воск: сота"),
            SHAFT_LINE = PanelText.tr("smithy.shaft_line", "Shaft %s rpm · %s kW · stroke %s J", "Вал %s об/мин · %s кВт · ход %s Дж"),
            MODE_LINE = PanelText.tr("smithy.mode_line", "%s to %s mm · energy %s %%", "%s до %s мм · энергия %s %%"),
            SERIES_LINE = PanelText.tr("smithy.series_line", "Series %s · last: %s", "Серий %s · последнее: %s"),
            RUN = PanelText.tr("smithy.run", "Start / stop", "Пуск / стоп"),
            RUN_TIP = PanelText.tr("smithy.run_tip", "Forges by itself while the piece is in the forging range and short of the target",
                    "Куёт сам, пока заготовка в ковочном интервале и не дошла до заданной толщины"),
            THINNER = PanelText.tr("smithy.thinner", "-5 mm", "-5 мм"),
            THICKER = PanelText.tr("smithy.thicker", "+5 mm", "+5 мм"),
            ENERGY = PanelText.tr("smithy.energy", "Energy", "Энергия"),
            ENERGY_TIP = PanelText.tr("smithy.energy_tip", "Share of the stroke energy: 25, 50, 75 or 100 %%",
                    "Доля энергии хода: 25, 50, 75 или 100 %%"),
            ON = PanelText.tr("smithy.on", "Running", "В работе"),
            SHAFT_TURNS = PanelText.tr("smithy.shaft_turns", "Shaft", "Вал"),
            AT_TARGET = PanelText.tr("smithy.at_target", "At target", "Готово"),
            MILL_LINE = PanelText.tr("smithy.mill_line", "Shaft %s rpm · pull up to %s kN", "Вал %s об/мин · тяга до %s кН"),
            TARGET_DIE = PanelText.tr("smithy.target_die", "Draw down to Ø%s mm", "Тянуть до Ø%s мм"),
            MILL_STATUS = PanelText.tr("smithy.mill_status", "Passes %s · last %s N · %s", "Проходов %s · последний %s Н · %s"),
            CHAMBER = PanelText.tr("smithy.chamber", "Chamber", "Камера"),
            SETPOINT = PanelText.tr("smithy.setpoint", "Setpoint %s °C", "Уставка %s °C"),
            BURNER = PanelText.tr("smithy.burner", "Burner", "Горелка"),
            KW = PanelText.tr("smithy.kw", "%s kW", "%s кВт"),
            FUEL_HEAT = PanelText.tr("smithy.fuel_heat", "Fuel heat", "Теплота топлива"),
            MJ = PanelText.tr("smithy.mj", "%s MJ", "%s МДж"),
            IN_CHAMBER = PanelText.tr("smithy.in_chamber", "In the chamber", "В камере"),
            TANK_LINE = PanelText.tr("smithy.tank_line", "Tank %s mB of %s · %s HRC · last: %s", "Бак %s мБ, %s · %s HRC · последнее: %s"),
            TANK_WATER = PanelText.tr("smithy.tank_water", "water", "вода"),
            TANK_OIL = PanelText.tr("smithy.tank_oil", "oil", "масло"),
            DOWN = PanelText.tr("smithy.setpoint_down", "-25 °C", "-25 °C"),
            UP = PanelText.tr("smithy.setpoint_up", "+25 °C", "+25 °C"),
            OFF = PanelText.tr("smithy.furnace_off", "Off", "Выкл"),
            QUENCH_TANK = PanelText.tr("smithy.quench_tank", "Quench", "Закалить"),
            QUENCH_TANK_TIP = PanelText.tr("smithy.quench_tank_tip", "Drop the piece from the chamber into the tank: water or oil, at least a bucket",
                    "Сбросить заготовку из камеры в бак: вода или масло, не меньше ведра"),
            CHARGE_SLOT = PanelText.tr("smithy.charge_slot", "Workpiece, steel stock, wire, electrical sheet or laminations",
                    "Поковка, прокат, проволока, электротехнический лист или пластины"),
            COATING_SLOT = PanelText.tr("smithy.coating_slot",
                    "Paper or phenolic resin: baked on at 150–250 °C, one per item in the chamber",
                    "Бумага или фенольная смола: запекается при 150–250 °C, по одной на предмет в камере");
    private static final Tr[] MILL_STOPS = {PanelText.tr("smithy.mill.none", "working", "в работе"),
            PanelText.tr("smithy.mill.done", "at size", "размер получен"),
            PanelText.tr("smithy.mill.anneal", "anneal it first", "нужен отжиг"),
            PanelText.tr("smithy.mill.force", "not enough pull", "не хватает тяги")};
    private static final Tr[] PULLS = {PanelText.tr("smithy.pull.ok", "drawn", "протянута"),
            PanelText.tr("smithy.pull.broke", "broke", "оборвалась"),
            PanelText.tr("smithy.pull.too_strong", "too thick for the windlass", "ворот не тянет"),
            PanelText.tr("smithy.pull.no_reduction", "die too large", "волока велика"),
            PanelText.tr("smithy.pull.none", "—", "—")};
    private static final Tr[] METALS = {PanelText.tr("smithy.metal.copper", "Copper", "Медь"),
            PanelText.tr("smithy.metal.wrought_iron", "Wrought iron", "Кричное железо"),
            PanelText.tr("smithy.metal.mild_steel", "Mild steel", "Мягкая сталь"),
            PanelText.tr("smithy.metal.medium_carbon", "Medium carbon steel", "Среднеуглеродистая сталь")};
    private static final Tr[] EVENTS = new Tr[ForgeEvent.values().length + 1];

    static {
        String[][] ev = {{"Good blow", "Хороший удар"}, {"Cracked", "Трещина"}, {"Buckled", "Изгиб при осадке"},
                {"Too thin", "Слишком тонко"}, {"Burnt", "Пережог"}, {"Did not harden", "Не закалилась"},
                {"Hardened only at the surface", "Закалилась только снаружи"}, {"Quench crack", "Закалочная трещина"},
                {"Too cold for that", "Слишком холодно"}, {"Weld with lack of fusion", "Шов с непроваром"},
                {"Pieces do not match", "Куски не подходят друг к другу"}};
        for (ForgeEvent e : ForgeEvent.values())
            EVENTS[e.ordinal()] = PanelText.tr("smithy.event." + e.name().toLowerCase(Locale.ROOT), ev[e.ordinal()][0], ev[e.ordinal()][1]);
        EVENTS[ForgeEvent.values().length] = PanelText.tr("smithy.event.none", "—", "—");
    }

    /** Фон — art/blockbench/smithy/smithy.author.js ({@code guiBloomery}). */
    public static final PanelLayout BLOOMERY = PanelLayout.builder("bloomery", 200, 150, B_CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/bloomery.png"))
            .dial(8, 24, true, B_TEMP_C, 1, 1600, Bloomery.OVERHEAT_K - 273.15, 0, CELSIUS.get(), TEMP.get(), null)
            .lamp(84, 22, FLAGS, BF_LIT, LampColor.AMBER, LIT.get())
            .lamp(124, 22, FLAGS, BF_DONE, LampColor.GREEN, DONE.get())
            .lamp(164, 22, FLAGS, BF_CAST, LampColor.RED, CAST.get())
            .bar(84, 44, 108, B_BLAST_PCT, 1, 100, 0xFFE0A030, BLAST.get(), v -> PCT.get(v.num(B_BLAST_PCT, 1, 0)))
            .bar(84, 66, 108, B_COAL_KG10, 10, BloomeryBlockEntity.COAL_CAPACITY_KG, 0xFF5A5A5A, COAL.get(),
                    v -> KG.get(v.num(B_COAL_KG10, 10, 1)))
            .bar(84, 88, 108, B_IRON_KG10, 10, BloomeryBlockEntity.ORE_CAPACITY_KG * Bloomery.IRON_YIELD * 0.72, 0xFFA0A0B0,
                    IRON.get(), v -> KG.get(v.num(B_IRON_KG10, 10, 1)))
            .readout(8, 110, 184, SmithyPanels::bloomeryStatus, null)
            .button(84, 124, 60, BUTTON_BELLOWS, BELLOWS.get(), null)
            .button(8, 124, 70, BUTTON_TAKE, TAKE.get(), null)
            .slot(150, 126, 0, BloomeryBlockEntity::isOre, ORE_SLOT.get())
            .slot(170, 126, 1, BloomeryBlockEntity::isCharcoal, COAL_SLOT.get())
            .output(150, 8, 2, BLOOM_OUT.get())
            .build();

    /** Фон — smithy.author.js ({@code guiForge}). */
    public static final PanelLayout FORGE = PanelLayout.builder("forge_hearth", 200, 150, H_CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/forge_hearth.png"))
            .dial(8, 24, true, H_FIRE_C, 1, 1600, Double.NaN, 0, CELSIUS.get(), TEMP.get(), null)
            .lamp(84, 22, FLAGS, HF_LIT, LampColor.AMBER, LIT.get())
            .bar(84, 44, 108, H_BLAST_PCT, 1, 100, 0xFFE0A030, BLAST.get(), v -> PCT.get(v.num(H_BLAST_PCT, 1, 0)))
            .bar(84, 66, 108, H_FUEL_KG10, 10, ForgeHearthBlockEntity.FUEL_CAPACITY_KG, 0xFF5A5A5A, COAL.get(),
                    v -> KG.get(v.num(H_FUEL_KG10, 10, 1)))
            .readout(8, 92, 184, v -> pieceLine(v, HF_PIECE, H_PIECE_C, FIRE_PIECE, false), null)
            .readout(8, 106, 184, v -> pieceLine(v, HF_EDGE, H_EDGE_C, EDGE_PIECE, true), null)
            .button(8, 124, 70, BUTTON_BELLOWS, BELLOWS.get(), null)
            .slot(130, 126, 0, ForgeHearthBlockEntity::isFuel, FUEL_SLOT.get())
            .slot(150, 126, 1, ForgeHearthBlockEntity::isFireable, FIRE_PIECE.get())
            .slot(170, 126, 2, ForgeHearthBlockEntity::isForgeable, EDGE_PIECE.get())
            .build();

    /** Фон — smithy.author.js ({@code guiAnvil}). */
    public static final PanelLayout ANVIL = PanelLayout.builder("smithing_anvil", 200, 190, A_CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/smithing_anvil.png"))
            .readout(8, 8, 184, SmithyPanels::anvilPiece, null)
            .readout(8, 22, 184, v -> v.bit(FLAGS, AF_PIECE) ? pieceLineRaw(v.raw(A_TEMP_C), false) : Component.empty(), null)
            .readout(8, 36, 140, v -> RANGE.get(v.raw(A_UPPER_C), v.raw(A_LOWER_C)), null)
            .lamp(150, 34, FLAGS, AF_IN_RANGE, LampColor.GREEN, IN_RANGE.get())
            .lamp(172, 34, FLAGS, AF_COLD, LampColor.RED, TOO_COLD.get())
            .readout(8, 50, 184, v -> SLAG_CRACK.get(v.raw(A_SLAG_PCT), v.raw(A_CRACK), v.raw(A_FUSION)), null)
            .readout(8, 64, 184, v -> OPERATION.get(opName(v.raw(A_OP))), null)
            .readout(8, 78, 184, v -> LAST.get(event(v.raw(A_EVENT)).get()), null)
            .button(8, 96, 56, BUTTON_DRAW, OP_DRAW.get(), null)
            .button(68, 96, 56, BUTTON_UPSET, OP_UPSET.get(), null)
            .button(128, 96, 56, BUTTON_BEND, OP_BEND.get(), null)
            .button(8, 116, 56, BUTTON_STRIKE, STRIKE.get(), STRIKE_TIP.get())
            .button(68, 116, 56, BUTTON_CUT, CUT.get(), CUT_TIP.get())
            .button(128, 116, 56, BUTTON_WELD, WELD.get(), WELD_TIP.get())
            .button(8, 136, 56, BUTTON_QUENCH, QUENCH.get(), QUENCH_TIP.get())
            .button(68, 136, 56, BUTTON_QUENCH_OIL, QUENCH_OIL.get(), QUENCH_OIL_TIP.get())
            .button(128, 136, 56, BUTTON_SHAFT, SHAFT.get(), SHAFT_TIP.get())
            .lamp(8, 164, FLAGS, AF_WATER, LampColor.WHITE, WATER.get())
            .lamp(48, 164, FLAGS, AF_OIL, LampColor.AMBER, OIL.get())
            .lamp(88, 164, FLAGS, AF_SECOND, LampColor.GREEN, SECOND.get())
            .slot(150, 166, 0, ForgeHearthBlockEntity::isForgeable, PIECE_SLOT.get())
            .slot(170, 166, 1, ForgeHearthBlockEntity::isForgeable, SECOND.get())
            .build();

    /** Фон — smithy.author.js ({@code guiDrawPlate}). */
    public static final PanelLayout DRAW_PLATE = PanelLayout.builder("draw_plate", 200, 150, D_CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/draw_plate.png"))
            .readout(8, 8, 184, v -> v.bit(FLAGS, DF_WIRE) ? WIRE_LINE.get(metal(v.raw(D_METAL)),
                    v.num(D_WIRE_UM, 1000, 2), v.num(D_LENGTH_CM, 100, 2)) : NO_ROD.get(), null)
            .readout(8, 22, 184, v -> DIE.get(v.num(D_DIE_UM, 1000, 2)), null)
            .bar(84, 44, 108, D_STRAIN_PCT, 1, 100, 0xFFB0B0C0, HARDENING.get(), v -> PCT.get(v.raw(D_STRAIN_PCT)))
            .readout(8, 66, 184, v -> PULL_LAST.get(PULLS[v.raw(D_OUTCOME) >= 0 && v.raw(D_OUTCOME) < PULLS.length - 1
                    ? v.raw(D_OUTCOME) : PULLS.length - 1].get(), v.raw(D_FORCE_N)), null)
            .readout(8, 80, 184, v -> LUBE.get(v.raw(D_LUBE)), null)
            .button(8, 100, 56, BUTTON_DIE_SMALLER, SMALLER.get(), null)
            .button(68, 100, 56, BUTTON_DIE_LARGER, LARGER.get(), null)
            .button(128, 100, 56, BUTTON_PULL, PULL.get(), PULL_TIP.get())
            .slot(150, 126, 0, Smithy::drawable, WORK_SLOT.get())
            .slot(170, 126, 1, DrawPlateBlockEntity::isLube, LUBE_SLOT.get())
            .build();

    /** Молот и пресс — один щит, фон своей машины ({@code power_hammer}, {@code crank_press} в smithy.author.js). */
    public static final PanelLayout POWER_HAMMER = powered("power_hammer");
    public static final PanelLayout CRANK_PRESS = powered("crank_press");

    private static PanelLayout powered(String id) {
        return PanelLayout.builder(id, 200, 150, P_CHANNELS)
                .art(PromptCritical.id("textures/gui/panel/" + id + ".png"))
                .readout(8, 8, 184, v -> v.bit(FLAGS, PF_PIECE) ? PIECE.get(v.num(P_A_MM10, 10, 1), v.raw(P_LEN_MM)) : NO_PIECE.get(), null)
                .readout(8, 22, 184, v -> v.bit(FLAGS, PF_PIECE) ? pieceLineRaw(v.raw(P_TEMP_C), false) : Component.empty(), null)
                .readout(8, 36, 184, v -> SHAFT_LINE.get(v.num(P_RPM10, 10, 0), v.num(P_POWER_KW10, 10, 1), v.raw(P_STROKE_J)), null)
                .readout(8, 50, 184, v -> MODE_LINE.get(opName(v.raw(P_OP)), v.raw(P_TARGET_MM), v.raw(P_ENERGY_PCT)), null)
                .readout(8, 64, 184, v -> SERIES_LINE.get(v.raw(P_SERIES), event(v.raw(P_EVENT)).get()), null)
                .lamp(8, 80, FLAGS, PF_ON, LampColor.GREEN, ON.get())
                .lamp(48, 80, FLAGS, PF_SHAFT, LampColor.WHITE, SHAFT_TURNS.get())
                .lamp(88, 80, FLAGS, PF_IN_RANGE, LampColor.GREEN, IN_RANGE.get())
                .lamp(128, 80, FLAGS, PF_COLD, LampColor.RED, TOO_COLD.get())
                .lamp(168, 80, FLAGS, PF_DONE, LampColor.AMBER, AT_TARGET.get())
                .button(8, 100, 56, BUTTON_DRAW, OP_DRAW.get(), null)
                .button(68, 100, 56, BUTTON_UPSET, OP_UPSET.get(), null)
                .button(128, 100, 56, P_BUTTON_RUN, RUN.get(), RUN_TIP.get())
                .button(8, 122, 40, P_BUTTON_THINNER, THINNER.get(), null)
                .button(52, 122, 40, P_BUTTON_THICKER, THICKER.get(), null)
                .button(96, 122, 48, P_BUTTON_ENERGY, ENERGY.get(), ENERGY_TIP.get())
                .slot(170, 126, 0, Smithy::forgeable, PIECE_SLOT.get())
                .build();
    }

    /** Фон — smithy.author.js ({@code wire_mill}). */
    public static final PanelLayout WIRE_MILL = PanelLayout.builder("wire_mill", 200, 150, W_CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/wire_mill.png"))
            .readout(8, 8, 184, v -> v.bit(FLAGS, WF_WIRE) ? WIRE_LINE.get(metal(v.raw(W_METAL)), v.num(W_WIRE_UM, 1000, 2),
                    v.num(W_LENGTH_CM, 100, 1)) : NO_ROD.get(), null)
            .readout(8, 22, 184, v -> TARGET_DIE.get(v.num(W_TARGET_UM, 1000, 2)), null)
            .readout(8, 36, 184, v -> MILL_LINE.get(v.num(W_RPM10, 10, 0), v.num(W_FORCE_KN10, 10, 1)), null)
            .bar(84, 52, 108, W_STRAIN_PCT, 1, 100, 0xFFB0B0C0, HARDENING.get(), v -> PCT.get(v.raw(W_STRAIN_PCT)))
            .readout(8, 74, 184, v -> MILL_STATUS.get(v.raw(W_PASSES), v.raw(W_LAST_N),
                    MILL_STOPS[Math.max(0, Math.min(MILL_STOPS.length - 1, v.raw(W_STOP)))].get()), null)
            .lamp(8, 86, FLAGS, WF_ON, LampColor.GREEN, ON.get())
            .lamp(48, 86, FLAGS, WF_SHAFT, LampColor.WHITE, SHAFT_TURNS.get())
            .button(8, 102, 56, BUTTON_DIE_SMALLER, SMALLER.get(), null)
            .button(68, 102, 56, BUTTON_DIE_LARGER, LARGER.get(), null)
            .button(128, 102, 56, W_BUTTON_RUN, RUN.get(), null)
            .slot(170, 126, 0, Smithy::drawable, WORK_SLOT.get())
            .build();

    public static final PanelLayout HEATING_FURNACE = furnace("heating_furnace", false);
    public static final PanelLayout HARDENING_FURNACE = furnace("hardening_furnace", true);

    private static PanelLayout furnace(String id, boolean hardening) {
        var b = PanelLayout.builder(id, 200, 150, F_CHANNELS)
                .art(PromptCritical.id("textures/gui/panel/" + id + ".png"))
                .dial(8, 24, true, F_CHAMBER_C, 1, 1300, Double.NaN, 0, CELSIUS.get(), CHAMBER.get(), null)
                .lamp(84, 22, FLAGS, FF_LIT, LampColor.AMBER, LIT.get())
                .readout(104, 22, 90, v -> SETPOINT.get(v.raw(F_SETPOINT_C)), null)
                .bar(84, 44, 108, F_POWER_KW10, 10, SmithyFurnaceBlockEntity.MAX_POWER_W / 1000, 0xFFE0A030, BURNER.get(),
                        v -> KW.get(v.num(F_POWER_KW10, 10, 1)))
                .bar(84, 66, 108, F_FUEL_MJ10, 10, SmithyFurnaceBlockEntity.FUEL_BUFFER_J / 1e6, 0xFF5A5A5A, FUEL_HEAT.get(),
                        v -> MJ.get(v.num(F_FUEL_MJ10, 10, 1)))
                .readout(8, 92, 184, v -> v.bit(FLAGS, FF_PIECE) ? GLOW_AT.get(IN_CHAMBER.get(), colour(v.raw(F_PIECE_C), true),
                        round50(v.raw(F_PIECE_C))) : EMPTY.get(IN_CHAMBER.get()), null)
                .button(8, 124, 30, F_BUTTON_DOWN, DOWN.get(), null)
                .button(42, 124, 30, F_BUTTON_UP, UP.get(), null)
                .button(76, 124, 30, F_BUTTON_OFF, OFF.get(), null)
                .slot(150, 126, 0, SmithyFurnaceBlockEntity::isFuel, FUEL_SLOT.get())
                .slot(170, 126, 1, SmithyFurnaceBlockEntity::isCharge, CHARGE_SLOT.get())
                .slot(170, 104, SmithyFurnaceBlockEntity.COATING_SLOT, Electrical::isCoatingMaterial, COATING_SLOT.get());
        if (hardening) {
            b.readout(8, 106, 158, v -> TANK_LINE.get(v.raw(F_TANK_MB), (v.bit(FLAGS, FF_WATER) ? TANK_WATER : TANK_OIL).get(),
                    v.raw(F_HRC), event(v.raw(F_EVENT)).get()), null)
                    .button(110, 124, 36, F_BUTTON_QUENCH, QUENCH_TANK.get(), QUENCH_TANK_TIP.get());
        }
        return b.build();
    }

    private static Component metal(int ordinal) {
        return METALS[Math.max(0, Math.min(METALS.length - 1, ordinal))].get();
    }

    private SmithyPanels() {
    }

    public static List<PanelLayout> all() {
        var out = new java.util.ArrayList<>(List.of(BLOOMERY, FORGE, ANVIL, DRAW_PLATE, POWER_HAMMER, CRANK_PRESS, WIRE_MILL,
                HEATING_FURNACE, HARDENING_FURNACE));
        out.addAll(ElectricalPanels.all());
        return List.copyOf(out);
    }

    private static Component bloomeryStatus(PanelValues v) {
        return BLOOM_STATUS.get(v.num(B_ORE_KG10, 10, 1), v.num(B_IRON_KG10, 10, 1),
                v.raw(B_OVERHEAT_S) > 0 ? OVERHEAT.get(v.raw(B_OVERHEAT_S)) : Component.empty());
    }

    private static Component pieceLine(PanelValues v, int bit, int ch, Tr where, boolean temper) {
        if (!v.bit(FLAGS, bit)) return EMPTY.get(where.get());
        return (temper ? TEMPER_AT : GLOW_AT).get(where.get(), colour(v.raw(ch), temper), round50(v.raw(ch)));
    }

    private static Component pieceLineRaw(int c, boolean temper) {
        return colour(c, temper).copy().append(" · ~" + round50(c) + " °C");
    }

    private static Component colour(int celsius, boolean temper) {
        double k = celsius + 273.15;
        if (temper) {
            var t = HeatColors.temper(k);
            if (t != HeatColors.Temper.NONE || HeatColors.glow(k) == HeatColors.Glow.NONE)
                return ManufacturingText.msg("temper." + t.name().toLowerCase(Locale.ROOT));
        }
        return ManufacturingText.msg("glow." + HeatColors.glow(k).name().toLowerCase(Locale.ROOT));
    }

    private static int round50(int c) {
        return Math.round(c / 50f) * 50;
    }

    private static Component anvilPiece(PanelValues v) {
        if (!v.bit(FLAGS, AF_PIECE)) return NO_PIECE.get();
        return v.raw(A_ROUND) != 0 ? PIECE.get(v.num(A_A_MM10, 10, 1), v.raw(A_LEN_MM))
                : PIECE_FLAT.get(v.num(A_A_MM10, 10, 1), v.num(A_B_MM10, 10, 1), v.raw(A_LEN_MM));
    }

    private static Component opName(int op) {
        return (op == 1 ? OP_UPSET : op == 2 ? OP_BEND : OP_DRAW).get();
    }

    private static Tr event(int e) {
        return e >= 0 && e < ForgeEvent.values().length ? EVENTS[e] : EVENTS[ForgeEvent.values().length];
    }
}
