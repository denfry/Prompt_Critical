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
        m("cage_dry", "The fish cage must float on water", "Садок должен стоять на воде");
        m("cage_status", "Water %s °C (waste heat +%s K), fish %s kg, feed %s kg, caught %s",
                "Вода %s °C (сбросное тепло +%s K), рыба %s кг, корм %s кг, выловлено %s");
        m("cage_dead", "The fish died of overheating: restock with a bucket of cod", "Рыба погибла от перегрева: зарыбьте ведром трески");
        m("dh_status", "Outside %s °C: network supply %s °C, return %s °C, turbine backpressure %s bar",
                "Снаружи %s °C: подача сети %s °C, обратка %s °C, противодавление турбины %s бар");
        m("dh_heat", "Steam gives %s kW, settlements need %s kW (%s beds), delivered %s kW, network water %s kg/s",
                "Пар даёт %s кВт, поселениям нужно %s кВт (кроватей: %s), отпущено %s кВт, сетевая вода %s кг/с");
        m("dh_short", "Heat shortfall: settlements get %s %% less than they need", "Недоотпуск тепла: поселения недополучают %s %%");
        m("fish_kill", "Fish kill: the cage water reached %s °C", "Замор рыбы: вода у садка %s °C");
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
