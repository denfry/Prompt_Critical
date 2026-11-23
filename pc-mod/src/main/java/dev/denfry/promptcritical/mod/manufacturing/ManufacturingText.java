package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.manufacturing.PartDefect;
import dev.denfry.promptcritical.sim.manufacturing.PartRoute;
import dev.denfry.promptcritical.sim.manufacturing.PartSpec;
import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Тексты производства деталей: ключи перевода и их en/ru значения для datagen. */
public final class ManufacturingText {
    private static final String P = "message." + PromptCritical.MOD_ID + ".manufacturing.";
    /** Ключ → {английский, русский}. */
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();
    /** Цвета каления по {@code HeatColors.Glow} (порядок констант). */
    private static final String[] GLOW_EN = {"No glow", "Dark red", "Cherry red", "Light cherry", "Orange", "Yellow", "White"},
            GLOW_RU = {"Не светится", "Тёмно-красный", "Вишнёвый", "Светло-вишнёвый", "Оранжевый", "Жёлтый", "Белый"};
    /** Цвета побежалости по {@code HeatColors.Temper}. */
    private static final String[] TEMPER_EN = {"None", "Light straw", "Straw", "Brown", "Purple", "Blue", "Light blue", "Grey"},
            TEMPER_RU = {"Нет", "Светло-соломенный", "Соломенный", "Коричневый", "Фиолетовый", "Синий", "Светло-синий", "Серый"};

    static {
        msg("part", "%s · %s", "%s · %s");
        msg("not_inspected", "Not inspected", "Не проверено");
        msg("heat_treat", "Heat treatment: %s", "Термообработка: %s");
        msg("hardness", "HRC %s", "HRC %s");
        msg("hardness_soft", "Soft, below 20 HRC", "Мягкая, ниже 20 HRC");
        msg("bloomery.not_ready", "The bloom is not ready yet", "Крица ещё не готова");
        msg("bloomery.status", "Bloomery %s °C · ore %s kg · iron %s kg", "Горн %s °C · руда %s кг · железо %s кг");
        msg("forge.status", "Fire %s °C · fuel %s kg", "Огонь %s °C · топливо %s кг");
        msg("anvil.no_hammer", "You need a smith's hammer", "Нужен кузнечный молот");
        msg("anvil.no_water", "No water next to the anvil to quench in", "Рядом с наковальней нет воды для закалки");
        msg("anvil.no_piece", "Nothing on the anvil", "На наковальне ничего нет");
        msg("anvil.too_hot", "Let it cool first", "Сначала дайте остыть");
        msg("anvil.not_a_shaft", "A shaft must be round or square and at least four times as long as it is thick",
                "Вал должен быть круглым или квадратным и длиннее толщины хотя бы вчетверо");
        msg("anvil.slag_left", "Slag is still in the metal: forge the bloom out at white heat first",
                "В металле ещё шлак: сначала проковать крицу при белом калении");
        // поковка (18 §18.7): размеры, нагрев, шлак
        msg("workpiece.size", "%s · %s × %s mm, %s mm long", "%s · %s × %s мм, длина %s мм");
        msg("workpiece.round", "%s · Ø%s mm, %s mm long", "%s · Ø%s мм, длина %s мм");
        msg("workpiece.hot", "%s, about %s °C", "%s, около %s °C");
        msg("workpiece.cold", "Cold", "Холодная");
        msg("workpiece.temper", "Temper colour: %s", "Цвет побежалости: %s");
        msg("workpiece.slag", "Slag in the metal: %s %%", "Шлак в металле: %s %%");
        msg("workpiece.mass", "%s kg", "%s кг");
        for (var g : dev.denfry.promptcritical.sim.manufacturing.HeatColors.Glow.values()) msg("glow." + g.name().toLowerCase(Locale.ROOT),
                GLOW_EN[g.ordinal()], GLOW_RU[g.ordinal()]);
        for (var t : dev.denfry.promptcritical.sim.manufacturing.HeatColors.Temper.values()) msg("temper." + t.name().toLowerCase(Locale.ROOT),
                TEMPER_EN[t.ordinal()], TEMPER_RU[t.ordinal()]);
        msg("it_grade", "IT%s", "IT%s");
        msg("roughness", "Ra %s µm", "Ra %s мкм");
        msg("defect", "%s: level %s", "%s: уровень %s");
        route(PartRoute.CAST, "cast", "литьё");
        route(PartRoute.FORGED, "forged", "поковка");
        route(PartRoute.ROLLED, "rolled", "прокат");
        route(PartRoute.DRAWN, "drawn", "волочение");
        route(PartRoute.WELDED, "welded", "сварка");
        heat(HeatTreatState.AS_ROLLED, "as rolled", "без обработки");
        heat(HeatTreatState.NORMALIZED, "normalized", "нормализация");
        heat(HeatTreatState.QUENCHED, "quenched", "закалка");
        heat(HeatTreatState.QUENCHED_TEMPERED, "quenched and tempered", "закалка с отпуском");
        heat(HeatTreatState.AGED, "aged", "старение");
        heat(HeatTreatState.SOLUTION_ANNEALED, "solution annealed", "закалка на твёрдый раствор");
        heat(HeatTreatState.STRESS_RELIEVED, "stress relieved", "снятие напряжений");
        defect(PartDefect.POROSITY, "porosity", "пористость");
        defect(PartDefect.CRACK, "crack", "трещина");
        defect(PartDefect.BURNT, "burnt metal", "пережог");
        defect(PartDefect.DECARB, "decarburisation", "обезуглероживание");
        defect(PartDefect.SLAG_INCLUSION, "slag inclusion", "шлаковое включение");
        defect(PartDefect.LACK_OF_FUSION, "lack of fusion", "непровар");
        defect(PartDefect.INSULATION_FLAW, "insulation flaw", "дефект изоляции");
        defect(PartDefect.BUCKLED, "buckling", "коробление");
    }

    private ManufacturingText() {
    }

    private static void msg(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    private static void route(PartRoute r, String en, String ru) {
        msg("route." + r.name().toLowerCase(Locale.ROOT), en, ru);
    }

    private static void heat(HeatTreatState s, String en, String ru) {
        msg("heat_treat." + s.name().toLowerCase(Locale.ROOT), en, ru);
    }

    private static void defect(PartDefect d, String en, String ru) {
        msg("defect." + d.name().toLowerCase(Locale.ROOT), en, ru);
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    /** Число для подсказки: целое без хвоста, иначе до трёх значащих цифр. */
    private static String num(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.3g", v);
    }

    /**
     * Название материала: у стали id материала — имя семейства ({@code medium_carbon}), его переводит ключ
     * сталеплавильного цеха; остальное (литейные сплавы) пока показываем как есть.
     */
    public static Component material(String id) {
        String key = "message." + PromptCritical.MOD_ID + ".steel.family." + id;
        return net.minecraft.locale.Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(id);
    }

    /** Строки подсказки поковки на тике {@code now}. */
    public static void workpieceTooltip(WorkpieceData d, long now, List<Component> out) {
        var w = d.workpiece(now);
        Component mat = material(w.grade().family().name().toLowerCase(Locale.ROOT));
        out.add((w.section() == dev.denfry.promptcritical.sim.manufacturing.Workpiece.Section.ROUND
                ? msg("workpiece.round", mat, num(Math.round(w.aM() * 1000)), num(Math.round(w.lengthM() * 1000)))
                : msg("workpiece.size", mat, num(Math.round(w.aM() * 1000)), num(Math.round(w.bM() * 1000)),
                        num(Math.round(w.lengthM() * 1000)))).withStyle(ChatFormatting.GRAY));
        out.add(msg("workpiece.mass", num(Math.round(w.massKg() * 10) / 10.0)).withStyle(ChatFormatting.GRAY));
        var glow = dev.denfry.promptcritical.sim.manufacturing.HeatColors.glow(w.tempK());
        if (w.tempK() > 333.15) {
            // температуру на глаз по цвету: число — грубо, до ближайших 50 °C, как оценил бы кузнец
            out.add(msg("workpiece.hot", msg("glow." + glow.name().toLowerCase(Locale.ROOT)),
                    num(Math.round((w.tempK() - 273.15) / 50) * 50)).withStyle(ChatFormatting.GOLD));
            var tc = dev.denfry.promptcritical.sim.manufacturing.HeatColors.temper(w.tempK());
            if (glow == dev.denfry.promptcritical.sim.manufacturing.HeatColors.Glow.NONE
                    && tc != dev.denfry.promptcritical.sim.manufacturing.HeatColors.Temper.NONE)
                out.add(msg("workpiece.temper", msg("temper." + tc.name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.GOLD));
        } else out.add(msg("workpiece.cold").withStyle(ChatFormatting.GRAY));
        double slag = w.severity(dev.denfry.promptcritical.sim.manufacturing.PartDefect.SLAG_INCLUSION);
        if (slag > 0) out.add(msg("workpiece.slag", num(Math.round(slag * 50))).withStyle(ChatFormatting.DARK_GRAY));
        for (var e : w.defects().entrySet()) {
            if (e.getKey() == dev.denfry.promptcritical.sim.manufacturing.PartDefect.SLAG_INCLUSION) continue;
            out.add(msg("defect", msg("defect." + e.getKey().name().toLowerCase(Locale.ROOT)),
                    dev.denfry.promptcritical.sim.manufacturing.PartSpec.defectLevel(e.getValue())).withStyle(ChatFormatting.RED));
        }
    }

    /** Строки подсказки детали: материал и маршрут всегда; измеренное только после проверки. */
    public static void tooltip(PartSpec p, List<Component> out) {
        out.add(msg("part", material(p.material()),
                msg("route." + p.route().name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.GRAY));
        if (!p.inspected()) {
            out.add(msg("not_inspected").withStyle(ChatFormatting.GRAY));
            return;
        }
        out.add(msg("heat_treat", msg("heat_treat." + p.heatTreat().name().toLowerCase(Locale.ROOT)))
                .withStyle(ChatFormatting.GRAY));
        out.add((p.hardnessHrc() < 20 ? msg("hardness_soft") : msg("hardness", p.hardnessHrc())).withStyle(ChatFormatting.GRAY));
        if (p.itGrade() > 0) out.add(msg("it_grade", p.itGrade()).withStyle(ChatFormatting.GRAY));
        out.add(msg("roughness", num(p.raUm())).withStyle(ChatFormatting.GRAY));
        for (var e : p.defects().entrySet())
            out.add(msg("defect", msg("defect." + e.getKey().name().toLowerCase(Locale.ROOT)), e.getValue())
                    .withStyle(ChatFormatting.RED));
    }
}
