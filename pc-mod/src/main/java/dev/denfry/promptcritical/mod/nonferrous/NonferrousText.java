package dev.denfry.promptcritical.mod.nonferrous;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Тексты цветной металлургии (08 §8.8): имена блоков, предметов и жидкостей и сообщения (en_us, ru_ru); ключи
 * собирает {@code PcLanguageProvider} одной строкой.
 */
public final class NonferrousText {
    private static final String P = "message.promptcritical.nonferrous.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        // Байер
        block("bayer_digester", "Bayer Digester (bauxite leaching)", "Автоклав выщелачивания боксита");
        block("alumina_precipitator", "Alumina Precipitator", "Декомпозёр алюминатного раствора");
        block("alumina_calciner", "Rotary Calciner (hydrate to alumina)", "Прокалочная печь (гидроксид в глинозём)");
        item("alumina", "Alumina (smelter grade)", "Глинозём металлургический");
        item("aluminium_hydroxide", "Aluminium Hydroxide", "Гидроксид алюминия");
        fluid("sodium_aluminate_liquor", "Sodium Aluminate Liquor", "Алюминатный раствор");
        fluid("red_mud", "Red Mud (bauxite residue)", "Красный шлам");
        // Холл–Эру
        block("reduction_pot", "Hall–Héroult Reduction Pot", "Алюминиевый электролизёр");
        item("aluminium_ingot", "Aluminium Ingot", "Чушка алюминия");
        item("cryolite", "Cryolite", "Криолит");
        item("carbon_anode", "Prebaked Carbon Anode", "Обожжённый анод");
        item("cathode_block", "Cathode Block", "Подовый блок");
        say("pot.anode_effect", "ANODE EFFECT in a reduction pot: alumina in the bath is too low. 30 V on the pot, CF₄ and C₂F₆ to the air. Feed alumina",
                "АНОДНЫЙ ЭФФЕКТ на электролизёре: мало глинозёма в расплаве. 30 В на ванне, CF₄ и C₂F₆ в воздух. Дайте глинозём");
        say("pot.freezing", "A reduction pot has lost its current and is cooling. Restore the DC within minutes or the bath freezes",
                "Электролизёр без тока остывает. Верните постоянный ток за минуты, иначе электролит застынет");
        say("pot.frozen", "A REDUCTION POT HAS FROZEN: solid bath and metal in the shell. Only a reline brings it back",
                "ЭЛЕКТРОЛИЗЁР ЗАМОРОЖЕН: электролит и металл застыли в кожухе. Вернуть его может только капремонт");
        say("pot.cathode_failure", "CATHODE FAILURE: molten aluminium has run through the pot lining",
                "ПРОБОЙ ПОДИНЫ: расплавленный алюминий ушёл сквозь футеровку");
        say("pot.relined", "Pot relined: new cathode and lining. Bake it with current before charging cryolite",
                "Капремонт выполнен: новая подина и футеровка. Обожгите её током, потом заливайте криолит");
        say("pot.reline_refused", "Reline needs a frozen or failed pot and %s cathode blocks and %s refractory bricks in the load",
                "Капремонт — только замёрзшей или пробитой ванне, нужны %s подовых блока и %s огнеупорных кирпичей в загрузке");
    }

    private NonferrousText() {
    }

    private static void block(String id, String en, String ru) {
        LANG.put("block.promptcritical." + id, new String[] {en, ru});
    }

    private static void item(String id, String en, String ru) {
        LANG.put("item.promptcritical." + id, new String[] {en, ru});
    }

    private static void fluid(String id, String en, String ru) {
        LANG.put("fluid_type.promptcritical." + id, new String[] {en, ru});
    }

    private static void say(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    public static Map<String, String[]> lang() {
        return LANG;
    }
}
