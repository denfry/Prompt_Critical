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

    static {
        msg("part", "%s · %s", "%s · %s");
        msg("not_inspected", "Not inspected", "Не проверено");
        msg("heat_treat", "Heat treatment: %s", "Термообработка: %s");
        msg("hardness", "HRC %s", "HRC %s");
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

    /** Строки подсказки детали: материал и маршрут всегда; измеренное только после проверки. */
    public static void tooltip(PartSpec p, List<Component> out) {
        out.add(msg("part", Component.literal(p.material()),
                msg("route." + p.route().name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.GRAY));
        if (!p.inspected()) {
            out.add(msg("not_inspected").withStyle(ChatFormatting.GRAY));
            return;
        }
        out.add(msg("heat_treat", msg("heat_treat." + p.heatTreat().name().toLowerCase(Locale.ROOT)))
                .withStyle(ChatFormatting.GRAY));
        out.add(msg("hardness", p.hardnessHrc()).withStyle(ChatFormatting.GRAY));
        if (p.itGrade() > 0) out.add(msg("it_grade", p.itGrade()).withStyle(ChatFormatting.GRAY));
        out.add(msg("roughness", num(p.raUm())).withStyle(ChatFormatting.GRAY));
        for (var e : p.defects().entrySet())
            out.add(msg("defect", msg("defect." + e.getKey().name().toLowerCase(Locale.ROOT)), e.getValue())
                    .withStyle(ChatFormatting.RED));
    }
}
