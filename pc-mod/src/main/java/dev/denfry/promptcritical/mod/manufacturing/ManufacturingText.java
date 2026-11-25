package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.manufacturing.ElectricalSheet;
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
        msg("anvil.no_oil", "No quench oil next to the anvil", "Рядом с наковальней нет закалочного масла");
        msg("anvil.second_busy", "Clear the second slot to take the cut-off half", "Освободите второй слот под отрубленную половину");
        msg("anvil.weld_two", "Forge welding needs a piece in each slot", "Для сварки нужен кусок в каждом слоте");
        msg("hot.burn", "The hot piece burns your hands: carry it with tongs", "Горячая поковка жжёт руки: носите её клещами");
        msg("draw.no_tongs", "You need tongs to pull the wire through the plate", "Чтобы тянуть проволоку через доску, нужны клещи");
        msg("draw.too_strong", "Too thick to pull by hand: %s N needed, the plate gives %s N",
                "Руками не протянуть: нужно %s Н, доска даёт %s Н");
        msg("draw.broke", "The wire broke at the die: anneal it in the forge first", "Проволока оборвалась у волоки: сначала отожгите её в горне");
        msg("draw.no_reduction", "Pick a die smaller than the wire", "Выберите волоку меньше проволоки");
        msg("draw.not_drawable", "Only copper, wrought iron and plain carbon steel are drawn, as a cold round rod up to 12 mm",
                "В волоку идут только медь, кричное железо и углеродистая сталь — холодным круглым прутком до 12 мм");
        msg("wire.line", "%s · Ø%s mm · %s m · work hardening %s", "%s · Ø%s мм · %s м · наклёп %s");
        msg("wire.metal.copper", "copper", "медь");
        msg("wire.insulation", "Insulation: %s, class %s", "Изоляция: %s, класс %s");
        msg("wire.insulation.paper", "paper", "бумага");
        msg("wire.insulation.enamel", "phenolic enamel", "фенольная эмаль");
        msg("wire.insulation.glass_enamel", "glass fibre and enamel", "стекловолокно и эмаль");
        msg("sheet.line", "%s mm · Si %s %% · work hardening %s · %s", "%s мм · Si %s %% · наклёп %s · %s");
        msg("sheet.loss", "Core loss %s W/kg at 1.5 T, 50 Hz", "Потери %s Вт/кг при 1,5 Тл, 50 Гц");
        msg("sheet.short", "Cut %s m long: roll on to full sheets before the notching press",
                "Отрезок %s м: докатать до мерных листов, пресс короткие не берёт");
        msg("coating.none", "bare", "без покрытия");
        msg("coating.paper", "paper interleaf", "бумажная прокладка");
        msg("coating.phenolic", "phenolic varnish", "фенольный лак");
        msg("lamination.line", "%s laminations Ø%s mm · sheet %s mm · burr %s µm", "%s пластин Ø%s мм · лист %s мм · заусенец %s мкм");
        msg("lamination.edges_raw", "Cut edges work hardened", "Кромки нагартованы вырубкой");
        msg("lamination.edges_relieved", "Annealed after punching", "Отожжены после вырубки");
        msg("lamination.coated_after", "Varnished after punching", "Лакированы после вырубки");
        msg("core.line", "%s laminations · %s mm · %s kg · stacking factor %s", "%s пластин · %s мм · %s кг · заполнение %s");
        msg("core.loss", "Core loss %s W/kg · shorted laminations %s %%", "Потери %s Вт/кг · замкнуто пластин %s %%");
        msg("stator.line", "Insulation class %s · slot fill %s %% · phase %s Ω at 75 °C",
                "Класс изоляции %s · заполнение паза %s %% · фаза %s Ом при 75 °C");
        msg("stator.efficiency", "5.5 kW motor: core %s W, copper %s W, efficiency %s %%",
                "Двигатель 5,5 кВт: сталь %s Вт, медь %s Вт, КПД %s %%");
        msg("stator.insulation", "Insulation resistance when new: %s MΩ", "Сопротивление изоляции новой обмотки: %s МОм");
        msg("wire.metal.wrought_iron", "wrought iron", "кричное железо");
        msg("wire.metal.mild_steel", "mild steel", "мягкая сталь");
        msg("wire.metal.medium_carbon", "medium carbon steel", "среднеуглеродистая сталь");
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
        msg("assembly.output_busy", "Take the assembled machine out first", "Сначала заберите собранную машину");
        msg("assembly.not_ready", "Not every position holds a fitting part", "Не во всех позициях подходящие детали");
        msg("passport.score", "Assembly passport: %s/100", "Паспорт сборки: %s/100");
        msg("passport.bearing", "Bearing wear ×%s (seat fit)", "Износ подшипника ×%s (посадка)");
        msg("passport.imbalance", "Imbalance %s mm/s", "Дисбаланс %s мм/с");
        msg("passport.misalignment", "Misalignment %s mm", "Несоосность %s мм");
        msg("passport.seal", "Shaft seal leak %s", "Течь уплотнения вала %s");
        msg("passport.flange", "Flange leak %s", "Течь по фланцам %s");
        msg("passport.insulation", "Winding insulation %s MΩ", "Изоляция обмотки %s МОм");
        msg("passport.efficiency", "Efficiency ×%s (core losses)", "КПД ×%s (потери в пакете)");
        // контроль деталей (18 §18.6.10)
        msg("inspect.no_part", "Hold the part to measure in your other hand", "Деталь для замера держите во второй руке");
        msg("inspect.already", "This part is already inspected", "Эта деталь уже проверена");
        msg("inspect.not_applicable.caliper", "The caliper cannot prove this fit: it is finer than IT12, take a micrometer",
                "Штангенциркулем эту посадку не проверить: она точнее IT12, нужен микрометр");
        msg("inspect.not_applicable.micrometer", "The fit is finer than IT6, beyond a shop micrometer",
                "Посадка точнее IT6, цеховым микрометром её не проверить");
        msg("inspect.not_applicable.hardness_tester", "The part was not heat treated, its hardness needs no check",
                "Деталь без термообработки, твёрдость проверять не нужно");
        msg("inspect.not_applicable.radiography", "Only castings and welded parts need radiography",
                "Радиография нужна только отливкам и сварным деталям");
        msg("inspect.tool_hint.caliper", "Use with a part in the other hand · reads to 0.1 mm, fits down to IT12",
                "ПКМ с деталью во второй руке · отсчёт 0,1 мм, посадки до IT12");
        msg("inspect.tool_hint.micrometer", "Use with a part in the other hand · reads to 0.01 mm, fits down to IT6",
                "ПКМ с деталью во второй руке · отсчёт 0,01 мм, посадки до IT6");
        msg("inspect.tool_hint.hardness_tester", "Use with a heat treated part in the other hand · HRC scale",
                "ПКМ с термообработанной деталью во второй руке · шкала HRC");
        msg("file.no_part", "Hold a forged part in your other hand", "Кованую деталь держите во второй руке");
        msg("file.too_hard", "The file skids: the part is hardened, file it before quenching",
                "Напильник скользит: деталь закалена, опиливают до закалки");
        msg("file.already_fine", "The part is already finished finer than a file can", "Деталь уже обработана точнее, чем может напильник");
        msg("file.done", "Filed to IT%s", "Опилено до IT%s");
        msg("file.hint", "Use with a forged part in the other hand · IT13, Ra 6.3 µm · soft steel only",
                "ПКМ с кованой деталью во второй руке · IT13, Ra 6,3 мкм · только незакалённая сталь");
        msg("inspect.done", "%s · part inspected", "%s · деталь проверена");
        msg("inspect.partial", "Measured: %s", "Измерено: %s");
        msg("reading.dimension", "Size: IT%s, Ra %s µm", "Размер: IT%s, Ra %s мкм");
        msg("reading.dimension_raw", "Size: as forged or cast, Ra %s µm", "Размер: без обработки, Ra %s мкм");
        msg("reading.hardness", "Hardness: %s", "Твёрдость: %s");
        msg("reading.internal_clean", "Film: no internal defects", "Снимок: внутренних дефектов нет");
        msg("reading.internal", "Film: %s", "Снимок: %s");
    }

    /** Показание одного замера детали. */
    public static MutableComponent reading(PartSpec p, dev.denfry.promptcritical.sim.manufacturing.PartInspection.Check c) {
        return switch (c) {
            case DIMENSION -> p.itGrade() > 0 ? msg("reading.dimension", p.itGrade(), num(p.raUm()))
                    : msg("reading.dimension_raw", num(p.raUm()));
            case HARDNESS -> msg("reading.hardness", p.hardnessHrc() < 20 ? msg("hardness_soft") : msg("hardness", p.hardnessHrc()));
            case INTERNAL -> {
                MutableComponent found = null;
                for (var e : p.defects().entrySet()) {
                    if (!INTERNAL_DEFECTS.contains(e.getKey())) continue;
                    MutableComponent d = msg("defect", msg("defect." + e.getKey().name().toLowerCase(Locale.ROOT)), e.getValue());
                    found = found == null ? d : found.append(", ").append(d);
                }
                yield found == null ? msg("reading.internal_clean") : msg("reading.internal", found);
            }
        };
    }

    /** Дефекты, видимые на снимке, а не снаружи. */
    private static final java.util.Set<PartDefect> INTERNAL_DEFECTS =
            java.util.EnumSet.of(PartDefect.POROSITY, PartDefect.SLAG_INCLUSION, PartDefect.LACK_OF_FUSION, PartDefect.CRACK);

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
    /** Подсказка проволоки: металл, диаметр, длина и наклёп в процентах от предельного. */
    public static void wireTooltip(WireData d, List<Component> out) {
        var w = d.wire();
        String hardening = Math.round(100 * w.strain() / w.metal().maxStrain) + " %";
        out.add(msg("wire.line", msg("wire.metal." + w.metal().name().toLowerCase(Locale.ROOT)),
                String.format(Locale.ROOT, "%.2f", w.diameterM() * 1000), String.format(Locale.ROOT, "%.2f", w.lengthM()), hardening)
                .withStyle(ChatFormatting.GRAY));
    }

    /** Изоляция обмоточного провода. */
    public static void magnetWireTooltip(WireData d, List<Component> out) {
        var ins = d.wireInsulation();
        if (ins == dev.denfry.promptcritical.sim.manufacturing.WireInsulation.NONE) return;
        out.add(msg("wire.insulation", msg("wire.insulation." + ins.name().toLowerCase(Locale.ROOT)), ins.insulationClass.name())
                .withStyle(ChatFormatting.GRAY));
    }

    private static String f(String fmt, double v) {
        return String.format(Locale.ROOT, fmt, v);
    }

    private static Component coating(dev.denfry.promptcritical.sim.manufacturing.SheetCoating c) {
        return msg("coating." + c.name().toLowerCase(Locale.ROOT));
    }

    public static void sheetTooltip(ElectricalData.Sheet d, List<Component> out) {
        var s = d.sheet();
        out.add(msg("sheet.line", f("%.2f", s.thicknessM() * 1000), f("%.1f", s.siFrac() * 100),
                Math.round(100 * Math.min(1, s.strain())) + " %", coating(s.coating())).withStyle(ChatFormatting.GRAY));
        out.add(msg("sheet.loss", f("%.1f", s.lossWPerKg(1.5, 50))).withStyle(ChatFormatting.GRAY));
        if (d.lengthFrac() < 1 - 1e-3)
            out.add(msg("sheet.short", f("%.2f", d.lengthFrac() * ElectricalSheet.SHEET_LENGTH_M)).withStyle(ChatFormatting.GOLD));
    }

    public static void laminationTooltip(ElectricalData.Bundle d, List<Component> out) {
        var l = d.lamination();
        out.add(msg("lamination.line", ElectricalData.LAMINATIONS_PER_BUNDLE, Math.round(l.shape().outerM() * 1000),
                f("%.2f", l.sheet().thicknessM() * 1000), Math.round(l.burrM() * 1e6)).withStyle(ChatFormatting.GRAY));
        out.add(msg(l.edgesRelieved() ? "lamination.edges_relieved" : "lamination.edges_raw").withStyle(ChatFormatting.GRAY));
        out.add((l.coatedAfterPunch() ? msg("lamination.coated_after") : coating(l.sheet().coating())).copy()
                .withStyle(ChatFormatting.GRAY));
    }

    private static void coreLines(dev.denfry.promptcritical.sim.manufacturing.CoreStack c, List<Component> out) {
        out.add(msg("core.line", c.count(), Math.round(c.lengthM() * 1000), f("%.1f", c.massKg()), f("%.3f", c.stackingFactor()))
                .withStyle(ChatFormatting.GRAY));
        out.add(msg("core.loss", f("%.1f", c.lossWPerKg(1.5, 50)), Math.round(100 * c.shortedFraction()))
                .withStyle(ChatFormatting.GRAY));
    }

    public static void coreTooltip(ElectricalData.Core d, List<Component> out) {
        coreLines(d.stack(), out);
    }

    /** Подсказка машины со стенда (18 §18.8.3): оценка и только отличные от номинала строки. */
    public static void passportTooltip(dev.denfry.promptcritical.sim.manufacturing.assembly.MachinePassport p,
            List<Component> out) {
        out.add(msg("passport.score", p.score()).withStyle(ChatFormatting.GOLD));
        if (p.bearingWearFactor() != 1) out.add(msg("passport.bearing", f("%.2f", p.bearingWearFactor())).withStyle(ChatFormatting.GRAY));
        if (p.imbalanceMmS() > 0) out.add(msg("passport.imbalance", f("%.1f", p.imbalanceMmS())).withStyle(ChatFormatting.GRAY));
        if (p.misalignmentMm() > 0) out.add(msg("passport.misalignment", f("%.3f", p.misalignmentMm())).withStyle(ChatFormatting.GRAY));
        if (p.sealLeak() > 0) out.add(msg("passport.seal", f("%.3f", p.sealLeak())).withStyle(ChatFormatting.GRAY));
        if (p.flangeLeak() > 0) out.add(msg("passport.flange", f("%.2f", p.flangeLeak())).withStyle(ChatFormatting.GRAY));
        if (p.hasWinding()) out.add(msg("passport.insulation", num(p.insulationMOhm())).withStyle(ChatFormatting.GRAY));
        if (p.efficiencyFactor() != 1) out.add(msg("passport.efficiency", f("%.3f", p.efficiencyFactor())).withStyle(ChatFormatting.GRAY));
    }

    public static void statorTooltip(ElectricalData.Wound d, List<Component> out) {
        var st = d.stator();
        out.add(msg("stator.line", st.insulationClass().name(), Math.round(100 * st.fillFactor()), f("%.3f", st.phaseResistanceOhm()))
                .withStyle(ChatFormatting.GRAY));
        out.add(msg("stator.efficiency", Math.round(st.coreLossW()), Math.round(st.copperLossW()), f("%.1f", 100 * st.efficiency()))
                .withStyle(ChatFormatting.GRAY));
        out.add(msg("stator.insulation", Math.round(st.insulationStartMOhm())).withStyle(ChatFormatting.GRAY));
        coreLines(st.core(), out);
    }

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
        tooltip(p, 0, out);
    }

    /** То же с маской уже сделанных замеров: их показания видны до полной проверки. */
    public static void tooltip(PartSpec p, int checks, List<Component> out) {
        out.add(msg("part", material(p.material()),
                msg("route." + p.route().name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.GRAY));
        if (!p.inspected()) {
            out.add(msg("not_inspected").withStyle(ChatFormatting.GRAY));
            for (var c : dev.denfry.promptcritical.sim.manufacturing.PartInspection.Check.values())
                if (dev.denfry.promptcritical.sim.manufacturing.PartInspection.done(checks, c))
                    out.add(reading(p, c).withStyle(ChatFormatting.DARK_GRAY));
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
