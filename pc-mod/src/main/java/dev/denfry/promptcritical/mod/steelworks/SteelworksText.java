package dev.denfry.promptcritical.mod.steelworks;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.metallurgy.ConverterPhase;
import dev.denfry.promptcritical.sim.metallurgy.Ferroalloy;
import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import dev.denfry.promptcritical.sim.metallurgy.MillRollKind;
import dev.denfry.promptcritical.sim.metallurgy.PipeKind;
import dev.denfry.promptcritical.sim.metallurgy.PipeSpec;
import dev.denfry.promptcritical.sim.metallurgy.SteelFamily;
import dev.denfry.promptcritical.sim.metallurgy.SteelGrade;
import dev.denfry.promptcritical.sim.utilities.AsuState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Тексты сталеплавильного, прокатного и трубного цехов и ВРУ: ключи перевода и их en/ru значения для datagen. */
public final class SteelworksText {
    private static final String P = "message." + PromptCritical.MOD_ID + ".steel.";
    /** Ключ → {английский, русский}. */
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        def("yes", "yes", "есть");
        def("no", "no", "нет");
        def("empty", "empty", "пусто");
        // конвертер
        def("lance", "Oxygen lance: %s m above the bath (set %s m), oxygen %s mB",
                "Кислородная фурма: %s м над ванной (уставка %s м), кислород %s мБ");
        def("bof", "Converter: %s; tilt %s°; metal %s t at %s °C, C %s%%", "Конвертер: %s; наклон %s°; металл %s т при %s °C, C %s%%");
        def("bof_slopping", "Slopping! %s kg of slag and metal over the mouth — raise the lance or cut the oxygen",
                "Выброс! %s кг шлака и металла через горловину — поднимите фурму или убавьте кислород");
        def("bof_overblown", "The heat is overblown: carbon is gone, the iron burns into the slag",
                "Плавка передута: углерод выгорел, железо горит в шлак");
        def("bof_lance_leak", "The lance burnt through — water in the bath!", "Фурма прогорела — вода в ванне!");
        def("bof_scrap_unmelted", "%s kg of scrap did not melt — too little hot metal or too cold a bath",
                "Не растаяло %s кг лома — мало чугуна или холодная ванна");
        phase(ConverterPhase.EMPTY, "empty", "пуст");
        phase(ConverterPhase.CHARGED, "charged", "завалка");
        phase(ConverterPhase.BLOWING, "blowing", "продувка");
        phase(ConverterPhase.BLOWN, "blown", "продут");
        // ковш-печь
        def("station", "Ladle station: %s, %s °C, O %s ppm", "Установка доводки: %s, %s °C, O %s ppm");
        def("station_no_ladle", "Ladle station: no steel ladle next to it", "Установка доводки: рядом нет ковша со сталью");
        def("station_cannot_add", "The bin is empty or there is no liquid steel ladle", "Бункер пуст или нет ковша с жидкой сталью");
        // изложница
        def("mold", "Ingot mold: %s kg at %s °C, solidified %s%%", "Изложница: %s кг при %s °C, затвердело %s%%");
        def("mold_empty", "Ingot mold empty — put a refractory brick on top as a hot top", "Изложница пуста — положите огнеупор сверху как прибыльную надставку");
        def("mold_empty_hot_top", "Ingot mold empty, hot top in place", "Изложница пуста, прибыльная надставка стоит");
        def("mold_hot_top", "Hot top in place: the shrinkage pipe stays in it", "Прибыльная надставка стоит: усадочная раковина уйдёт в неё");
        // печи
        def("furnace", "Furnace: %s °C (set %s °C), %s / %s pieces", "Печь: %s °C (уставка %s °C), %s / %s шт.");
        def("quench_tank", "Quench tank: %s mB at %s °C", "Закалочный бак: %s мБ при %s °C");
        // прокат
        def("table", "Roller table: %s at %s °C", "Рольганг: %s при %s °C");
        def("table_empty", "Roller table: empty (Shift-click — cooling bed)", "Рольганг: пусто (Shift + ПКМ — холодильник)");
        def("bed_empty", "Cooling bed: empty (Shift-click — roller table)", "Холодильник: пусто (Shift + ПКМ — рольганг)");
        def("stand", "Mill stand: gap %s mm, %s rolls; last pass %s MN, %s kN·m, %s kW",
                "Клеть: зазор %s мм, валки %s; последний пропуск %s МН, %s кН·м, %s кВт");
        def("stand_sheared", "Mill stand: SHEAR PIN BROKEN — right-click with an iron nugget (gap %s mm, %s rolls; %s MN, %s kN·m, %s kW)",
                "Клеть: СРЕЗАН ШТИФТ — ПКМ железным самородком (зазор %s мм, валки %s; %s МН, %s кН·м, %s кВт)");
        def("stand_pin_replaced", "Shear pin replaced", "Срезной штифт заменён");
        rolls(MillRollKind.FLAT, "flat", "гладкие");
        rolls(MillRollKind.SECTION, "section", "калиброванные");
        def("outcome.rolled", "rolled", "прокатан");
        def("outcome.not_bitten", "not bitten", "не захвачен");
        def("outcome.shear_pin", "shear pin broke", "срезан штифт");
        // трубы
        def("piercing", "Piercing mill: %s × %s mm, %s; last %s m of pipe", "Прошивной стан: %s × %s мм, %s; последний раз %s м трубы");
        def("welder", "Pipe welder: ⌀ %s mm, line %s m/s, %s kW; last %s m of pipe", "Трубосварочный стан: ⌀ %s мм, линия %s м/с, %s кВт; последний раз %s м трубы");
        def("hydrotest", "Hydrotest bench: %s pipes tested, %s burst; water %s mB", "Гидропресс: испытано %s труб, лопнуло %s; вода %s мБ");
        kind(PipeKind.LINE, "line pipe", "магистральная");
        kind(PipeKind.BOILER, "boiler tube", "котельная");
        kind(PipeKind.HEAT_EXCHANGER, "heat-exchanger tube", "теплообменная");
        kind(PipeKind.DRILL, "drill pipe", "бурильная");
        kind(PipeKind.CASING, "casing", "обсадная");
        kind(PipeKind.TUBING, "tubing", "НКТ");
        kind(PipeKind.STRUCTURAL, "structural", "конструкционная");
        // ВРУ
        def("asu", "Air separation unit: %s; cold box %s °C; O₂ %s Nm³/h at %s%%", "ВРУ: %s; блок %s °C; O₂ %s нм³/ч, %s%%");
        def("asu_incomplete", "Air separation unit not assembled within 8 blocks: compressor %s, molecular sieve %s, expander %s",
                "ВРУ не собрана (в 8 блоках): компрессор — %s, блок осушки — %s, детандер — %s");
        def("asu_repair_need", "Rebuilding the cold box takes %s steel plates", "Для восстановления блока нужно %s стальных листов");
        def("asu_compressor", "Air compressor: %s rpm, %s kW; cooling water %s mB", "Воздушный компрессор: %s об/мин, %s кВт; оборотная вода %s мБ");
        def("cryo_tank", "Cryogenic tank: %s %s / %s mB; gas %s mB", "Криобак: %s %s / %s мБ; газ %s мБ");
        asu(AsuState.OFF, "off", "стоит");
        asu(AsuState.COOLDOWN, "cooling down", "захолаживание");
        asu(AsuState.RUNNING, "running", "работает");
        asu(AsuState.ICED, "iced (warm it up)", "лёд (отогрев)");
        asu(AsuState.TRIPPED, "tripped on hydrocarbons (drain LOX)", "останов по углеводородам (слейте LOX)");
        asu(AsuState.DESTROYED, "destroyed", "разрушена");
        // марки и состояния
        family(SteelFamily.CARBON, "carbon steel", "углеродистая");
        family(SteelFamily.LOW_ALLOY, "low-alloy steel", "низколегированная");
        family(SteelFamily.HEAT_RESISTANT, "heat-resistant steel", "теплоустойчивая");
        family(SteelFamily.STAINLESS_304, "stainless 304", "нержавеющая 304");
        family(SteelFamily.STAINLESS_316, "stainless 316", "нержавеющая 316");
        family(SteelFamily.MARAGING, "maraging steel", "мартенситностареющая");
        family(SteelFamily.SOUR_SERVICE, "sour-service steel", "для сероводорода");
        family(SteelFamily.INCONEL, "nickel alloy", "никелевый сплав");
        family(SteelFamily.TOOL_HSS, "high-speed steel", "быстрорежущая");
        family(SteelFamily.RAIL_WIRE, "rail and wire steel", "рельсовая и пружинная");
        state(HeatTreatState.AS_ROLLED, "as rolled", "горячекатаная");
        state(HeatTreatState.NORMALIZED, "normalized", "нормализованная");
        state(HeatTreatState.QUENCHED, "quenched", "закалённая");
        state(HeatTreatState.QUENCHED_TEMPERED, "quenched and tempered", "улучшенная");
        state(HeatTreatState.AGED, "aged", "состаренная");
        state(HeatTreatState.SOLUTION_ANNEALED, "solution annealed", "аустенизированная");
        state(HeatTreatState.STRESS_RELIEVED, "stress relieved", "после отпуска");
        alloy(Ferroalloy.FERROMANGANESE, "Ferromanganese", "Ферромарганец");
        alloy(Ferroalloy.FERROSILICON, "Ferrosilicon", "Ферросилиций");
        alloy(Ferroalloy.ALUMINIUM, "Aluminium shot", "Алюминиевая дробь");
        alloy(Ferroalloy.FERROCHROME, "Low-carbon ferrochrome", "Низкоуглеродистый феррохром");
        alloy(Ferroalloy.FERROMOLYBDENUM, "Ferromolybdenum", "Ферромолибден");
        alloy(Ferroalloy.FERROVANADIUM, "Ferrovanadium", "Феррованадий");
        alloy(Ferroalloy.FERRONIOBIUM, "Ferroniobium", "Феррониобий");
        alloy(Ferroalloy.FERROTITANIUM, "Ferrotitanium", "Ферротитан");
        alloy(Ferroalloy.NICKEL, "Nickel", "Никель");
        alloy(Ferroalloy.COBALT, "Cobalt", "Кобальт");
        // подсказки предметов
        def("tip.grade", "%s · %s", "%s · %s");
        def("tip.size", "%s × %s mm · heat %s", "%s × %s мм · плавка %s");
        def("tip.chem", "%s", "%s");
        def("tip.hot", "Hot: %s °C — burns in hand", "Горячий: %s °C — жжёт руки");
        def("tip.warm", "Cooling: %s °C", "Остывает: %s °C");
        def("tip.cracked", "Cracked", "С трещинами");
        def("tip.porous", "Porous", "Пористый");
        def("tip.pipe", "⌀ %s × %s mm · %s · %s", "⌀ %s × %s мм · %s · %s");
        def("tip.pipe.seamless", "seamless", "бесшовная");
        def("tip.pipe.welded", "welded", "сварная");
        def("tip.pipe.strength", "SMYS %s MPa · test %s MPa", "σт %s МПа · испытание %s МПа");
        def("tip.pipe.hydro", "Hydrotested", "Прошла гидроиспытание");
        def("tip.pipe.untested", "Not hydrotested", "Гидроиспытание не пройдено");
        def("tip.ferroalloy", "Adds %s in the ladle; recovery %s%%", "Добавляет %s в ковше; усвоение %s%%");
    }

    private SteelworksText() {
    }

    private static void def(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    private static void phase(ConverterPhase p, String en, String ru) {
        def("phase." + lower(p), en, ru);
    }

    private static void rolls(MillRollKind k, String en, String ru) {
        def("rolls." + lower(k), en, ru);
    }

    private static void kind(PipeKind k, String en, String ru) {
        def("pipe_kind." + lower(k), en, ru);
    }

    private static void asu(AsuState s, String en, String ru) {
        def("asu_state." + lower(s), en, ru);
    }

    private static void family(SteelFamily f, String en, String ru) {
        def("family." + lower(f), en, ru);
    }

    private static void state(HeatTreatState s, String en, String ru) {
        def("ht." + lower(s), en, ru);
    }

    private static void alloy(Ferroalloy a, String en, String ru) {
        def("alloy." + a.id, en, ru);
    }

    private static String lower(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    /** Число для сообщения: целое или с одним знаком. */
    public static String num(double v) {
        return Math.abs(v) >= 100 || v == Math.rint(v) ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    public static Component yes(boolean v) {
        return msg(v ? "yes" : "no");
    }

    public static Component phase(ConverterPhase p) {
        return msg("phase." + lower(p));
    }

    public static Component rolls(MillRollKind k) {
        return msg("rolls." + lower(k));
    }

    public static Component pipeKind(PipeKind k) {
        return msg("pipe_kind." + lower(k));
    }

    public static Component asuState(AsuState s) {
        return msg("asu_state." + lower(s));
    }

    /** Марка: обозначение и семейство. */
    public static Component gradeName(SteelGrade g) {
        return Component.literal(g.designation() + " (").append(msg("family." + lower(g.family()))).append(")");
    }

    public static Component heatTreat(HeatTreatState s) {
        return msg("ht." + lower(s));
    }

    public static void stockTooltip(SteelworksComponents.SteelStock s, long now, List<Component> out) {
        SteelGrade g = s.grade();
        out.add(msg("tip.grade", gradeName(g), heatTreat(g.state())).withStyle(ChatFormatting.GRAY));
        out.add(msg("tip.size", s.thicknessMm(), s.widthMm(), s.heatNo()).withStyle(ChatFormatting.DARK_GRAY));
        StringBuilder chem = new StringBuilder();
        for (Map.Entry<String, Float> e : s.chemPct().entrySet()) {
            if (e.getValue() < 0.005f) continue;
            if (!chem.isEmpty()) chem.append(" · ");
            chem.append(e.getKey()).append(' ').append(String.format(Locale.ROOT, "%.2f", e.getValue())).append('%');
        }
        if (!chem.isEmpty()) out.add(msg("tip.chem", chem.toString()).withStyle(ChatFormatting.DARK_GRAY));
        if (s.hot()) {
            double c = s.tempK(now) - 273.15;
            out.add(msg(c + 273.15 >= SteelStockItem.BURN_K ? "tip.hot" : "tip.warm", num(c)).withStyle(ChatFormatting.GOLD));
        }
        if (s.cracked()) out.add(msg("tip.cracked").withStyle(ChatFormatting.RED));
        if (s.porous()) out.add(msg("tip.porous").withStyle(ChatFormatting.RED));
    }

    public static void pipeTooltip(SteelworksComponents.PipeData p, List<Component> out) {
        out.add(msg("tip.pipe", num(p.odM() * 1000), num(p.wallM() * 1000), msg(p.seamless() ? "tip.pipe.seamless" : "tip.pipe.welded"),
                pipeKind(PipeKind.values()[Math.floorMod(p.kind(), PipeKind.values().length)])).withStyle(ChatFormatting.GRAY));
        out.add(msg("tip.pipe.strength", num(p.smysPa() / 1e6), num(p.testPressurePa() / 1e6)).withStyle(ChatFormatting.DARK_GRAY));
        boolean hydro = (p.inspectedMask() & PipeSpec.HYDRO) != 0;
        out.add(msg(hydro ? "tip.pipe.hydro" : "tip.pipe.untested").withStyle(hydro ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
    }

    public static void ferroalloyTooltip(Ferroalloy a, List<Component> out) {
        out.add(msg("tip.ferroalloy", a.element.symbol, Math.round(a.recovery * 100)).withStyle(ChatFormatting.GRAY));
    }
}
