package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.metallurgy.CastingDefect;
import dev.denfry.promptcritical.sim.metallurgy.CastingKind;
import dev.denfry.promptcritical.sim.metallurgy.CastingSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Тексты литейки и цеха: ключи перевода и их ru/en значения для datagen. */
public final class FoundryText {
    private static final String P = "message." + PromptCritical.MOD_ID + ".";
    /** Ключ → {английский, русский}. */
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        kind(CastingKind.VALVE_BODY, "valve body", "корпус задвижки");
        kind(CastingKind.PUMP_CASING, "pump casing", "корпус насоса");
        kind(CastingKind.IMPELLER, "impeller", "рабочее колесо");
        kind(CastingKind.GEAR_BLANK, "gear blank", "заготовка шестерни");
        kind(CastingKind.WHEEL, "wheel", "колесо");
        kind(CastingKind.FLYWHEEL, "flywheel", "маховик");
        kind(CastingKind.INGOT_MOLD, "ingot mould", "изложница");
        kind(CastingKind.BEARING_HOUSING, "bearing housing", "корпус подшипника");
        alloy("gray_iron", "grey cast iron", "серый чугун");
        alloy("carbon_steel", "carbon steel", "углеродистая сталь");
        alloy("bronze", "tin bronze", "оловянная бронза");
        alloy("copper", "copper", "медь");
        alloy("aluminium", "aluminium", "алюминий");
        defect(CastingDefect.SHRINKAGE_CAVITY, "shrinkage cavity", "усадочная раковина");
        defect(CastingDefect.HOT_TEAR, "hot tear", "горячая трещина");
        defect(CastingDefect.COLD_SHUT, "cold shut", "холодный спай");
        defect(CastingDefect.GAS_POROSITY, "gas porosity", "газовая пористость");
        defect(CastingDefect.CHILL, "chill (white iron)", "отбел");
        defect(CastingDefect.NONE, "sound", "годная");
        msg("riser", "with riser", "с прибылью");
        msg("no_riser", "no riser", "без прибыли");
        msg("pattern", "Pattern: %s, %s", "Модель: %s, %s");
        msg("jig", "Jig: feed %s kg, water %s L, cut %s / %s g/cm³", "Отсадка: питание %s кг, вода %s л, разделение %s / %s г/см³");
        msg("jig_cut", "Clean coal cut at %s g/cm³", "Плотность разделения концентрата %s г/см³");
        msg("cupola", "Cupola: stock %s kg iron + %s kg coke; well %s kg at %s °C; blast %s m³/s",
                "Вагранка: в шахте %s кг металла и %s кг кокса; в горне %s кг при %s °C; дутьё %s м³/с");
        msg("crucible", "Crucible: %s %s kg at %s °C, %s%% molten; fuel %s kg",
                "Тигель: %s %s кг, %s °C, расплавлено %s%%; топливо %s кг");
        msg("crucible_empty", "Crucible: empty; fuel %s kg", "Тигель: пуст; топливо %s кг");
        msg("crucible_alloy", "The crucible holds %s — tap it first", "В тигле %s — сначала разлейте");
        msg("mold_unformed", "Empty flask: make moulds on a moulding machine", "Пустая опока: формы делает формовочная машина");
        msg("mold_empty", "Mould: %s, %s, moisture %s%%", "Форма: %s, %s, влажность %s%%");
        msg("mold_liquid", "Mould: %s, %s — solidifying, %s s", "Форма: %s, %s — затвердевает, %s с");
        msg("mold_cooling", "Mould: %s, %s — cooling", "Форма: %s, %s — остывает");
        msg("mold_ready", "Mould: %s, %s — ready for shakeout", "Форма: %s, %s — готова к выбивке");
        msg("molding", "Moulding machine: %s; sand %s / %s, water %s mB", "Формовочная машина: %s; смесь %s / %s, вода %s мБ");
        msg("molding_no_pattern", "Moulding machine: no pattern", "Формовочная машина: нет модели");
        msg("shakeout", "Shakeout grate: %s mould(s) ready", "Выбивная решётка: готово форм — %s");
        msg("tool", "%s %s%%, spindle %s rpm, tool wear %s%%", "%s %s%%, шпиндель %s об/мин, износ резца %s%%");
        msg("tool_idle", "No workpiece; spindle %s rpm, tool wear %s%%", "Заготовки нет; шпиндель %s об/мин, износ резца %s%%");
        msg("no_tool", "no tool", "нет резца");
        msg("chill_rejected", "Chilled white iron cannot be cut", "Отбелённый чугун не режется");
        msg("not_needed", "This casting needs no such operation", "Этой отливке операция не нужна");
        msg("casting", "%s, %s", "%s, %s");
        msg("casting_dn", "DN %s", "DN %s");
        msg("casting_turned", "turned", "точёная");
        msg("casting_drilled", "drilled", "сверлёная");
        msg("casting_defect", "Defect found: %s", "Найден дефект: %s");
        msg("casting_serial", "No. %s", "№ %s");
        msg("mold_item", "%s, %s, moisture %s%%", "%s, %s, влажность %s%%");
    }

    private FoundryText() {
    }

    private static void kind(CastingKind k, String en, String ru) {
        LANG.put(kindKey(k), new String[] {en, ru});
    }

    private static void alloy(String id, String en, String ru) {
        LANG.put(P + "alloy." + id, new String[] {en, ru});
    }

    private static void defect(CastingDefect d, String en, String ru) {
        LANG.put(P + "defect." + d.name().toLowerCase(Locale.ROOT), new String[] {en, ru});
    }

    private static void msg(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    private static String kindKey(CastingKind k) {
        return P + "casting_kind." + k.name().toLowerCase(Locale.ROOT);
    }

    public static MutableComponent kind(CastingKind k) {
        return Component.translatable(kindKey(k));
    }

    public static MutableComponent alloy(String id) {
        return Component.translatable(P + "alloy." + id);
    }

    public static MutableComponent defect(CastingDefect d) {
        return Component.translatable(P + "defect." + d.name().toLowerCase(Locale.ROOT));
    }

    public static MutableComponent riser(boolean riser) {
        return Component.translatable(P + (riser ? "riser" : "no_riser"));
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    public static MutableComponent pattern(FoundryComponents.Pattern p) {
        return msg("pattern", kind(p.kind()), riser(p.riser()));
    }

    /** Число для сообщения: целое или с одним знаком. */
    public static String num(double v) {
        return Math.abs(v) >= 100 || v == Math.rint(v) ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    /** Строки подсказки отливки: вид и сплав, проход, обработка, найденный дефект, номер. */
    public static void castingTooltip(CastingSpec c, List<Component> out) {
        out.add(msg("casting", kind(c.kind()), alloy(c.alloyId())).withStyle(ChatFormatting.GRAY));
        if (c.dn() > 0) out.add(msg("casting_dn", c.dn()).withStyle(ChatFormatting.GRAY));
        if ((c.machinedMask() & CastingSpec.TURNED) != 0) out.add(msg("casting_turned").withStyle(ChatFormatting.GRAY));
        if ((c.machinedMask() & CastingSpec.DRILLED) != 0) out.add(msg("casting_drilled").withStyle(ChatFormatting.GRAY));
        if (c.defect() == CastingDefect.CHILL || c.defectFound())
            out.add(msg("casting_defect", defect(c.defect())).withStyle(ChatFormatting.RED));
        out.add(msg("casting_serial", c.serial()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
