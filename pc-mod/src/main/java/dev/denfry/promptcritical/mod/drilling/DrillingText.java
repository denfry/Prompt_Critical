package dev.denfry.promptcritical.mod.drilling;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Сообщения сейсморазведки и буровой (en_us, ru_ru); ключи собирает {@code PcLanguageProvider}. */
public final class DrillingText {
    private static final String P = "message.promptcritical.drilling.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("geophones", "Need at least %s geophones within %s blocks (found %s)",
                "Нужно не меньше %s сейсмоприёмников в %s блоках (найдено %s)");
        put("no_charge", "Hold a seismic charge to fire the shot", "Возьмите в руку сейсмический заряд для взрыва");
        put("recording", "Recording: %s %%", "Запись: %s %%");
        put("shot", "Shot fired · %s geophones · survey radius %s blocks",
                "Взрыв · %s сейсмоприёмников · радиус съёмки %s блоков");
        put("no_structure", "No closed structure within %s blocks", "В радиусе %s блоков замкнутых структур нет");
        put("section", "Seismic section: crest of a structure %s m %s, at %s m", "Сейсмический разрез: свод структуры в %s м на %s, глубина %s м");
        put("section_tip", "Crest %s, %s · TWT %s ms · depth %s m · closure %s m",
                "Свод %s, %s · время %s мс · глубина %s м · амплитуда %s м");
        put("section_pore", "Pore pressure (Eaton) ≈ %s g/cm³ equivalent", "Пластовое давление (Итон) ≈ %s г/см³ эквивалента");
        put("section_bright", "Bright spot: amplitude anomaly over gas", "Яркое пятно: аномалия амплитуды над газом");
        put("section_blank", "Blank: shoot a survey with the seismic recorder", "Пусто: проведите съёмку сейсмостанцией");
        put("rig_missing", "Rig incomplete: %s", "Буровая не собрана: %s");
        put("rig_spud", "Spudded below the surface casing at %s m; mud %s g/cm³, pits %s m³",
                "Бурение начато из-под кондуктора с %s м; раствор %s г/см³, ёмкости %s м³");
        put("rig_status", "Depth %s m · %s m/h · WOB %s t · SPP %s MPa · casing %s MPa · pit gain %s m³",
                "Забой %s м · %s м/ч · нагрузка %s т · стояк %s МПа · затрубье %s МПа · прирост %s м³");
        put("barite", "Barite %s kg added: mud %s g/cm³ (kill mud %s)", "Добавлено барита %s кг: раствор %s г/см³ (глушения %s)");
        put("barite_no_rig", "No driller's console within reach", "Рядом нет пульта бурильщика");
        put("bop_needs", "No annular preventer under the rig", "Под буровой нет кольцевого превентора");
        put("kill_refused", "Shut the well in and stop the pumps first; for the second circulation weight the mud up to %s g/cm³",
                "Сначала закройте скважину и остановите насосы; для второго цикла утяжелите раствор до %s г/см³");
        put("kill_stage", "Kill: %s · SIDPP %s MPa · SICP %s MPa · kill mud %s g/cm³ · ICP %s / FCP %s MPa",
                "Глушение: %s · ИДТ %s МПа · ИДК %s МПа · раствор глушения %s г/см³ · Pнач %s / Pкон %s МПа");
        put("blowout", "BLOWOUT: the well is flowing uncontrolled", "ОТКРЫТЫЙ ФОНТАН: скважина фонтанирует неуправляемо");
        put("part.rotary_table", "rotary table", "ротор");
        put("part.mud_pump", "mud pump", "буровой насос");
        put("part.drawworks", "drawworks", "лебёдка");
        put("part.derrick_segment", "derrick over the rotary", "вышка над ротором");
        put("part.bop_annular", "annular preventer", "кольцевой превентор");
        put("part.choke_manifold", "choke manifold", "манифольд дросселирования");
        put("part.mud_tank", "mud tanks (at least %s)", "ёмкости раствора (не меньше %s)");
        for (String s : new String[] {"idle", "first_circulation", "weight_up", "second_circulation", "done"}) {
            put("stage." + s, switch (s) {
                case "idle" -> "not started";
                case "first_circulation" -> "first circulation, hold ICP";
                case "weight_up" -> "weight up the mud";
                case "second_circulation" -> "second circulation, ICP to FCP";
                default -> "well killed";
            }, switch (s) {
                case "idle" -> "не начато";
                case "first_circulation" -> "первый цикл, держать Pнач";
                case "weight_up" -> "утяжелить раствор";
                case "second_circulation" -> "второй цикл, от Pнач к Pкон";
                default -> "скважина заглушена";
            });
        }
    }

    private DrillingText() {
    }

    private static void put(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    public static String num(double v, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }

    public static Map<String, String[]> lang() {
        return LANG;
    }
}
