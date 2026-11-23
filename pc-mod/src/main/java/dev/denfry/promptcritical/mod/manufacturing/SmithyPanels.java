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
            A_EVENT = 8, A_LOWER_C = 9, A_UPPER_C = 10, A_ROUND = 11, A_CHANNELS = 12;
    public static final int AF_PIECE = 0, AF_IN_RANGE = 1, AF_COLD = 2, AF_WATER = 3;
    public static final int BUTTON_DRAW = 0, BUTTON_UPSET = 1, BUTTON_BEND = 2, BUTTON_STRIKE = 3, BUTTON_QUENCH = 4,
            BUTTON_SHAFT = 5;

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
            SLAG_CRACK = PanelText.tr("smithy.slag_crack", "Slag %s %% · cracks: level %s", "Шлак %s %% · трещины: уровень %s"),
            LAST = PanelText.tr("smithy.last", "Last: %s", "Последнее: %s"),
            NO_PIECE = PanelText.tr("smithy.no_piece", "Put a workpiece on the anvil", "Положите поковку на наковальню");
    private static final Tr[] EVENTS = new Tr[ForgeEvent.values().length + 1];

    static {
        String[][] ev = {{"Good blow", "Хороший удар"}, {"Cracked", "Трещина"}, {"Buckled", "Изгиб при осадке"},
                {"Too thin", "Слишком тонко"}, {"Burnt", "Пережог"}, {"Did not harden", "Не закалилась"},
                {"Hardened only at the surface", "Закалилась только снаружи"}, {"Quench crack", "Закалочная трещина"}};
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
            .slot(150, 126, 1, ForgeHearthBlockEntity::isForgeable, FIRE_PIECE.get())
            .slot(170, 126, 2, ForgeHearthBlockEntity::isForgeable, EDGE_PIECE.get())
            .build();

    /** Фон — smithy.author.js ({@code guiAnvil}). */
    public static final PanelLayout ANVIL = PanelLayout.builder("smithing_anvil", 200, 150, A_CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/smithing_anvil.png"))
            .readout(8, 8, 184, SmithyPanels::anvilPiece, null)
            .readout(8, 22, 184, v -> v.bit(FLAGS, AF_PIECE) ? pieceLineRaw(v.raw(A_TEMP_C), false) : Component.empty(), null)
            .readout(8, 36, 140, v -> RANGE.get(v.raw(A_UPPER_C), v.raw(A_LOWER_C)), null)
            .lamp(150, 34, FLAGS, AF_IN_RANGE, LampColor.GREEN, IN_RANGE.get())
            .lamp(172, 34, FLAGS, AF_COLD, LampColor.RED, TOO_COLD.get())
            .readout(8, 50, 184, v -> SLAG_CRACK.get(v.raw(A_SLAG_PCT), v.raw(A_CRACK)), null)
            .readout(8, 64, 184, v -> OPERATION.get(opName(v.raw(A_OP))), null)
            .readout(8, 78, 184, v -> LAST.get(event(v.raw(A_EVENT)).get()), null)
            .button(8, 96, 56, BUTTON_DRAW, OP_DRAW.get(), null)
            .button(68, 96, 56, BUTTON_UPSET, OP_UPSET.get(), null)
            .button(128, 96, 56, BUTTON_BEND, OP_BEND.get(), null)
            .button(8, 116, 56, BUTTON_STRIKE, STRIKE.get(), STRIKE_TIP.get())
            .button(68, 116, 56, BUTTON_QUENCH, QUENCH.get(), QUENCH_TIP.get())
            .button(128, 116, 56, BUTTON_SHAFT, SHAFT.get(), SHAFT_TIP.get())
            .lamp(8, 136, FLAGS, AF_WATER, LampColor.WHITE, WATER.get())
            .slot(170, 132, 0, ForgeHearthBlockEntity::isForgeable, PIECE_SLOT.get())
            .build();

    private SmithyPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(BLOOMERY, FORGE, ANVIL);
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
