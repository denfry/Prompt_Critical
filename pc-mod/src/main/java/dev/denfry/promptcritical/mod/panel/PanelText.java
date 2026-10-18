package dev.denfry.promptcritical.mod.panel;

import dev.denfry.promptcritical.mod.PromptCritical;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Надписи местных щитов: ключ → {английский, русский} для datagen; раскладки объявляют их при загрузке класса. */
public final class PanelText {
    private static final String P = "panel." + PromptCritical.MOD_ID + ".";
    static final Map<String, String[]> LANG = new LinkedHashMap<>();

    /** Общие надписи щита. */
    public static final Tr INVENTORY = tr("inventory", "Inventory", "Инвентарь");
    public static final Tr SHIFT_HINT = tr("shift_hint", "Sneak + use: quick action", "Shift + ПКМ: быстрое действие");
    public static final Tr RPM = tr("unit.rpm", "rpm", "об/мин"), MPA = tr("unit.mpa", "MPa", "МПа"),
            CELSIUS = tr("unit.celsius", "°C", "°C"), PA = tr("unit.pa", "Pa", "Па"), KGH = tr("unit.kgh", "kg/h", "кг/ч"),
            M3H = tr("unit.m3h", "m³/h", "м³/ч"), PERCENT = tr("unit.percent", "%", "%");
    /** Вал Create: тахометр на щите каждой машины с валом. */
    public static final Tr SHAFT = tr("shaft", "Shaft", "Вал"), RUNNING = tr("running", "Running", "Работа"),
            SHAFT_TIP = tr("shaft_tip", "Create shaft %s rpm", "Вал Create %s об/мин");

    private PanelText() {
    }

    /** Перевод с подстановкой; {@link #get} без аргументов — простая надпись. */
    public record Tr(String key) {
        public MutableComponent get(Object... args) {
            return Component.translatable(key, args);
        }
    }

    public static Tr tr(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
        return new Tr(P + key);
    }

    /** Все надписи щитов, включая объявленные раскладками машин. */
    public static Map<String, String[]> lang() {
        PanelLayouts.all();
        return LANG;
    }

    /** Число для щита: {@code decimals} знаков, без «-0». */
    public static String num(double v, int decimals) {
        if (Double.isNaN(v)) return "—";
        String s = String.format(Locale.ROOT, "%." + decimals + "f", v);
        return s.matches("-0[.,]?0*") ? s.substring(1) : s;
    }
}
