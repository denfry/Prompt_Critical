package dev.denfry.promptcritical.mod.greenhouse;

import dev.denfry.promptcritical.mod.PromptCritical;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Тексты теплицы и парника (17 §17.3.6): ключ → {en, ru} для datagen. */
public final class GreenhouseText {
    private static final String P = "message." + PromptCritical.MOD_ID + ".greenhouse.";
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        m("not_enclosed", "The coil is not inside a closed greenhouse (at most 4096 blocks of air under glass)",
                "Регистр не внутри замкнутой теплицы (не больше 4096 блоков воздуха под стеклом)");
        m("status", "Greenhouse %s m³, glass %s m², walls %s m²: inside %s °C, outside %s °C",
                "Теплица %s м³, стекло %s м², стены %s м²: внутри %s °C, снаружи %s °C");
        m("heat", "Coil %s kW of %s kW from the condenser, losses %s kW, water %s kg/s",
                "Регистр %s кВт из %s кВт конденсатора, потери %s кВт, вода %s кг/с");
        m("no_condenser", "No heat: put the coil against a turbine condenser", "Нет тепла: поставьте регистр вплотную к конденсатору турбины");
        m("growth", "Growth ×%s for %s plants", "Рост ×%s для растений: %s");
        m("too_cold", "Below 5 °C: plants inside do not grow", "Ниже 5 °C: растения внутри не растут");
    }

    private GreenhouseText() {
    }

    static void m(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    static void t(String key, String en, String ru) {
        LANG.put(key, new String[] {en, ru});
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }
}
