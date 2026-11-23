package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.sim.radiation.ContaminationLimits;
import dev.denfry.promptcritical.sim.radiation.ContaminationProbe;
import dev.denfry.promptcritical.sim.radiation.SurfaceContamination;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Тексты поверхностного загрязнения и дезактивации (en_us, ru_ru) и строка показаний радиометра. */
public final class ContaminationText {
    private static final String P = "message.promptcritical.contamination.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("where.block", "Surface", "Поверхность");
        put("where.hands", "Hands", "Руки");
        put("where.shoes", "Shoes", "Обувь");
        put("where.item", "Item", "Предмет");
        put("line", "%s: α %s cps (%s Bq/cm²) · β %s cps (%s Bq/cm²) · %s",
                "%s: α %s имп/с (%s Бк/см²) · β %s имп/с (%s Бк/см²) · %s");
        put("below_mda", "<%s", "<%s");
        put("within", "within NRB-99/2009", "в норме НРБ-99/2009");
        put("over", "%s× over the NRB-99/2009 limit", "превышение нормы НРБ-99/2009 в %s раз");
        put("coat.applied", "Strippable coating sprayed on. It can be peeled off in %s h",
                "Снимаемое покрытие нанесено. Снять плёнку можно через %s ч");
        put("coat.already", "This surface is already coated", "Поверхность уже покрыта");
        put("coat.curing", "The coating is still wet: %s h to go", "Покрытие ещё не высохло: осталось %s ч");
        put("coat.peeled", "Film peeled off and took %s %% of the activity with it",
                "Плёнка снята и унесла %s %% активности");
        put("monitor.clean", "Hand and foot monitor: CLEAN. You may leave the zone", "Установка контроля: ЧИСТО. Выход из зоны разрешён");
        put("monitor.dirty", "Hand and foot monitor: CONTAMINATED (%s). Go to the decontamination shower",
                "Установка контроля: ЗАГРЯЗНЕНО (%s). Пройдите в душ санпропускника");
        put("portal.alarm", "Portal monitor: GAMMA ALARM (%s × threshold). You are carrying active material out of the zone",
                "Портальный монитор: ТРЕВОГА ПО ГАММА (%s × порога). Вы выносите активный материал из зоны");
        put("portal.clean", "Portal monitor: clear", "Портальный монитор: чисто");
        put("portal.status", "Portal monitor: background %s µSv/h · alarms above %s µSv/h at the column (2 s, 5σ)",
                "Портальный монитор: фон %s мкЗв/ч · тревога выше %s мкЗв/ч у колонны (2 с, 5σ)");
        put("shower.status", "Decon shower: water %s l · liquid waste %s l, %s MBq",
                "Душ санпропускника: вода %s л · ЖРО %s л, %s МБк");
        put("shower.dry", "The shower has no water: connect a pipe", "В душе нет воды: подведите трубу");
        put("sprayed", "Sprayed: %s → %s Bq/cm²", "Обработано: %s → %s Бк/см²");
        put("sprayer.empty", "empty", "пусто");
        put("sprayer.status", "Sprayer: %s %s l · collected waste %s l, %s MBq", "Распылитель: %s %s л · собрано смыва %s л, %s МБк");
        put("tooltip", "Contaminated: α %s Bq/cm² · β %s Bq/cm²", "Загрязнён: α %s Бк/см² · β %s Бк/см²");
        put("film", "Peeled coating film: solid radioactive waste", "Содранная плёнка покрытия: твёрдые радиоактивные отходы");
    }

    private ContaminationText() {
    }

    private static void put(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    public static Map<String, String[]> lang() {
        return LANG;
    }

    public static String num(double v) {
        if (v == 0) return "0";
        if (v >= 100) return String.format(Locale.ROOT, "%.0f", v);
        if (v >= 1) return String.format(Locale.ROOT, "%.1f", v);
        return String.format(Locale.ROOT, "%.2g", v);
    }

    /**
     * Строка радиометра: скорости счёта по α и β, пересчёт в Бк/см² (ниже МДА — «&lt;МДА») и сравнение с нормой
     * {@code limit}. Цвет — зелёный в норме, красный сверх нормы.
     */
    public static MutableComponent reading(String where, SurfaceContamination c, ContaminationLimits.Surface limit, ContaminationProbe probe) {
        double ac = probe.alphaCps(c), bc = probe.betaCps(c);
        double a = probe.alphaBqCm2(ac), b = probe.betaBqCm2(bc);
        Object aText = a < probe.alphaMda() ? msg("below_mda", num(probe.alphaMda())) : num(a);
        Object bText = b < probe.betaMda() ? msg("below_mda", num(probe.betaMda())) : num(b);
        double ratio = limit.ratio(c);
        MutableComponent verdict = ratio > 1 ? msg("over", num(ratio)) : msg("within");
        return msg("line", msg("where." + where), num(ac), aText, num(bc), bText, verdict)
                .withStyle(ratio > 1 ? ChatFormatting.RED : ChatFormatting.GREEN);
    }
}
