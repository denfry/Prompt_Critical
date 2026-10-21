package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.metallurgy.ElectrodeState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Тексты коксохима и домны: ключи перевода и их en/ru значения для datagen. */
public final class IronworksText {
    private static final String P = "message." + PromptCritical.MOD_ID + ".iron.";
    /** Ключ → {английский, русский}. */
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        // коксовая батарея
        msg("push_overload", "Oven %s is not ready: the cake stuck, the pusher overloaded", "Камера %s не готова: пирог прилип, выталкиватель перегружен");
        msg("refractory_failed", "Coke battery brickwork has failed: the walls are cracked", "Кладка коксовой батареи разрушена: простенки треснули");
        msg("gas_main_explosion", "Air was sucked into the gas main — explosion!", "В газосборник подсосало воздух — взрыв!");
        msg("battery_unformed", "Coke battery not formed: %s", "Батарея не собрана: %s");
        msg("problem.forming", "checking…", "проверяется…");
        msg("problem.no_oven", "no oven here", "здесь нет камеры");
        msg("problem.short_oven", "an oven needs 4–8 blocks along its axis", "камере нужно 4–8 блоков вдоль оси");
        msg("problem.oven_length", "oven length must be 4–8 blocks", "длина камеры — 4–8 блоков");
        msg("problem.oven_incomplete", "the oven is not a full 3-high wall of coke oven blocks", "камера — не сплошная стенка высотой 3");
        msg("problem.wall_missing", "a flue wall (coke battery wall) is missing between or beside the ovens",
                "не хватает простенка (кладки батареи) между камерами или по краям");
        msg("problem.oven_count", "a battery needs 3–12 ovens", "в батарее 3–12 камер");
        msg("problem.unloaded", "part of the structure is in an unloaded chunk", "часть конструкции в незагруженном чанке");
        msg("problem.no_standpipe", "every oven needs a gas collector main standpipe on top", "над каждой камерой нужен стояк газосборника");
        msg("exhauster", "Exhauster: %s Nm³/h, outlet %s mB", "Эксгаустер: %s нм³/ч, в баке %s мБ");
        msg("byproduct", "%s: captured %s kg; product %s mB, reagent %s mB", "%s: уловлено %s кг; продукт %s мБ, реагент %s мБ");
        msg("byproduct.cooler", "Primary cooler", "Первичный холодильник");
        msg("byproduct.precipitator", "Tar precipitator", "Электрофильтр");
        msg("byproduct.saturator", "Ammonia saturator", "Сатуратор");
        msg("byproduct.scrubber", "Benzol scrubber", "Бензольный скруббер");
        msg("quench_tower", "Quench tower: water %s mB", "Тушильная башня: вода %s мБ");
        msg("extruder", "Electrode press: binder %s%%, pitch %s kg", "Электродный пресс: связующее %s%%, пек %s кг");
        msg("baking", "Baking kiln: %s °C, %s electrodes, soak %s h", "Обжиговая печь: %s °C, электродов %s, выдержка %s ч");
        // подготовка руды
        msg("separator", "Magnetic separator: last concentrate %s%% Fe; pending %s kg concentrate, %s kg tailings",
                "Магнитный сепаратор: последний концентрат %s%% Fe; накоплено %s кг концентрата, %s кг хвостов");
        msg("sinter", "Sinter strand: basicity %s; breeze %s kg, flux %s kg; ignition gas %s mB",
                "Агломашина: основность %s; мелочь %s кг, флюс %s кг; газ зажигания %s мБ");
        // домна
        msg("blower", "Blast blower: %s Nm³/h cold blast", "Воздуходувка: холодное дутьё %s нм³/ч");
        msg("stove", "Hot blast stove: %s, dome %s °C, gas %s Nm³/h", "Воздухонагреватель: %s, купол %s °C, газ %s нм³/ч");
        msg("stove_mode.on_gas", "on gas", "на газе");
        msg("stove_mode.on_blast", "on blast", "на дутье");
        msg("stove_mode.isolated", "isolated", "отсечён");
        msg("dust_catcher", "Dust catcher: blast furnace gas %s mB", "Пылеуловитель: доменный газ %s мБ");
        msg("bf_unformed", "Blast furnace not formed: %s", "Домна не собрана: %s");
        msg("problem.no_shell", "the controller must touch a 5×5 furnace shell ring from outside",
                "контроллер должен касаться кольца кожуха 5×5 снаружи");
        msg("problem.no_hearth", "the hearth (bottom layer) must be a solid 5×5 of shell", "лещадь (нижний слой) — сплошной кожух 5×5");
        msg("problem.too_low", "the shell must be at least 12 blocks high", "высота кожуха — не меньше 12 блоков");
        msg("problem.too_high", "the shell must be at most 18 blocks high", "высота кожуха — не больше 18 блоков");
        msg("problem.shaft_blocked", "the 3×3 shaft inside the shell must be empty", "шахта 3×3 внутри кожуха должна быть пустой");
        msg("problem.taphole_place", "the taphole goes in layer 1 above the hearth, not in a corner",
                "лётка — в первом слое над лещадью, не в углу");
        msg("problem.taphole_count", "the furnace needs exactly one taphole", "у печи ровно одна лётка");
        msg("problem.tuyere_place", "tuyeres go in layer 2 above the hearth, not in corners", "фурмы — во втором слое над лещадью, не в углах");
        msg("problem.tuyere_count", "the furnace needs 4–12 tuyeres", "у печи 4–12 фурм");
        msg("problem.no_top", "the furnace top must sit over the middle of the shell", "засыпной аппарат — над серединой кожуха");
        msg("bf", "Blast furnace: %s; hearth %s °C, hot metal %s t; blast %s Nm³/h at %s °C",
                "Домна: %s; горн %s °C, чугуна %s т; дутьё %s нм³/ч при %s °C");
        msg("bf_state", "Blast furnace: %s", "Домна: %s");
        state("cold", "cold", "холодная");
        state("blowing_in", "blowing in", "задувка");
        state("running", "running", "работает");
        state("banked", "banked", "тихий ход");
        state("hanging", "hanging", "подвисание");
        state("slip", "slip", "осадка");
        state("chilled", "chilled hearth", "закозление горна");
        state("burnthrough", "hearth burnthrough", "прорыв горна");
        msg("bf_slip", "The burden slipped — a gas surge through the bleeders", "Осадка шихты — выброс газа через свечи");
        msg("bf_tuyere_burnt", "A tuyere burnt through and leaks water into the hearth (%s burnt in total) — replace it",
                "Фурма прогорела, вода течёт в горн (всего прогорело %s) — замените фурму");
        msg("bf_tuyere_replaced", "Tuyere replaced", "Фурма заменена");
        msg("bf_burnthrough", "Hearth burnthrough: %s t of metal and slag broke out!", "Прорыв горна: вырвалось %s т металла и шлака!");
        msg("bf_blowing", "The taphole is blowing gas — the hearth is drained, plug it", "Лётка дует газом — горн выпущен, забейте лётку");
        msg("bf_spill", "No ladle or sand bed at the runner — hot metal spills on the floor", "У жёлоба нет ковша и песка — чугун растекается по площадке");
        msg("bf_top", "Furnace top: hopper ore %s kg, coke %s kg, flux %s kg; charge layer %s kg coke",
                "Колошник: в бункере руда %s кг, кокс %s кг, флюс %s кг; колоша — %s кг кокса");
        msg("bf_top_unlinked", "Furnace top: no formed blast furnace below", "Колошник: под ним нет собранной домны");
        msg("cast_house.drill", "Taphole drill", "Бурмашина");
        msg("cast_house.mud_gun", "Mud gun", "Пушка");
        msg("cast_house_no_taphole", "No blast furnace taphole within reach", "Рядом нет лётки домны");
        msg("cast_house_drilled", "Taphole drilled open", "Лётка вскрыта");
        msg("cast_house_drill_failed", "The taphole is already open or the hearth is cold", "Лётка уже открыта или горн холодный");
        msg("cast_house_no_clay", "Mud gun needs 2 taphole clay (has %s)", "Пушке нужно 2 лёточной массы (есть %s)");
        msg("cast_house_plug_idle", "The taphole is already plugged", "Лётка уже забита");
        msg("cast_house_plugged", "Taphole plugged (clay left: %s)", "Лётка забита (массы осталось: %s)");
        msg("ladle", "Ladle: %s t at %s °C; C %s%%, Si %s%%, S %s%%", "Ковш: %s т при %s °C; C %s%%, Si %s%%, S %s%%");
        msg("ladle_skull", "Ladle: %s t frozen into a skull (%s °C); C %s%%, Si %s%%, S %s%% — Shift-click to knock it out",
                "Ковш: %s т застыло «козлом» (%s °C); C %s%%, Si %s%%, S %s%% — Shift + ПКМ, чтобы выбить");
        msg("ladle_empty", "Ladle empty; lining %s °C, wear %s%%", "Ковш пуст; футеровка %s °C, износ %s%%");
        msg("granulator", "Slag granulator: water %s mB, %s kg pending", "Грануляция шлака: вода %s мБ, накоплено %s кг");
        // подсказки предметов
        msg("tip.coke", "Ash %s%% · S %s%% · M25 %s · moisture %s%%", "Зола %s%% · S %s%% · M25 %s · влага %s%%");
        msg("tip.hot", "Hot — quench it", "Горячий — потушите");
        msg("tip.sinter", "Fe %s%% · SiO₂ %s%% · CaO %s%%", "Fe %s%% · SiO₂ %s%% · CaO %s%%");
        msg("tip.electrode", "%s, ⌀ %s cm × %s cm", "%s, ⌀ %s см × %s см");
        msg("tip.electrode.green", "green", "сырой");
        msg("tip.electrode.baked", "baked", "обожжённый");
        msg("tip.electrode.graphitized", "graphitized", "графитированный");
        msg("tip.cracked", "Cracked", "С трещинами");
    }

    private IronworksText() {
    }

    private static void msg(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    private static void state(String id, String en, String ru) {
        msg("bf_state." + id, en, ru);
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    /** Число для сообщения: целое или с одним знаком. */
    public static String num(double v) {
        return Math.abs(v) >= 100 || v == Math.rint(v) ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    public static void cokeTooltip(IronworksComponents.CokeAssay a, List<Component> out) {
        out.add(msg("tip.coke", a.ashPct(), num(a.sulfurPm() / 10.0), a.m25(), a.moisturePct()).withStyle(ChatFormatting.GRAY));
        if (a.hot()) out.add(msg("tip.hot").withStyle(ChatFormatting.GOLD));
    }

    public static void sinterTooltip(IronworksComponents.SinterAssay a, List<Component> out) {
        out.add(msg("tip.sinter", a.fePct(), a.sio2Pct(), a.caoPct()).withStyle(ChatFormatting.GRAY));
    }

    public static void electrodeTooltip(IronworksComponents.Electrode e, List<Component> out) {
        String stage = switch (e.stage()) {
            case ElectrodeState.BAKED -> "baked";
            case ElectrodeState.GRAPHITIZED -> "graphitized";
            default -> "green";
        };
        out.add(msg("tip.electrode", msg("tip.electrode." + stage), e.diameterCm(), e.lengthCm()).withStyle(ChatFormatting.GRAY));
        if (e.cracked()) out.add(msg("tip.cracked").withStyle(ChatFormatting.RED));
    }
}
