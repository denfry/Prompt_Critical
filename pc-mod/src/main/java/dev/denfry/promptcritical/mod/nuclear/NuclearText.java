package dev.denfry.promptcritical.mod.nuclear;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Сообщения ядерного топливного цикла (en_us, ru_ru); ключи собирает {@code PcLanguageProvider}. */
public final class NuclearText {
    private static final String P = "message.promptcritical.nuclear.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("cylinder.empty", "Empty · holds up to %s kg UF₆", "Пустой · вмещает до %s кг UF₆");
        put("cylinder.charge", "UF₆ %s kg · U-235 %s %%", "UF₆ %s кг · U-235 %s %%");
        put("cylinder.u234", "U-234 %s %% · uranium %s kg", "U-234 %s %% · урана %s кг");
        put("cylinder.limit", "Limit %s kg, U-235 up to %s %%", "Предел %s кг, U-235 до %s %%");
        put("cascade.start", "Centrifuge hall: rotors spinning up, UF₆ to the cascade", "Цех центрифуг: роторы разгоняются, UF₆ в каскад");
        put("cascade.stop", "Centrifuge hall stopped, rotors coasting down", "Цех центрифуг остановлен, роторы выбегают");
        put("cascade.excursion", "CRITICALITY EXCURSION in the product desublimer: blue flash, your dose about %s Gy",
                "СЦР В ДЕСУБЛИМАТОРЕ ОТБОРА: голубая вспышка, ваша доза около %s Гр");
    }

    static {
        put("conversion.start", "UO₂ line: UF₆ to hydrolysis, ammonia to precipitation, hydrogen to the furnace",
                "Линия UO₂: UF₆ на гидролиз, аммиак на осаждение, водород в печь");
        put("conversion.stop", "UO₂ line stopped", "Линия UO₂ остановлена");
        put("press.start", "Pellet press running", "Пресс таблеток работает");
        put("press.stop", "Pellet press stopped", "Пресс таблеток остановлен");
        put("sintering.start", "Sintering furnace: hydrogen on, boats moving", "Печь спекания: водород подан, лодочки пошли");
        put("sintering.stop", "Sintering furnace stopped", "Печь спекания остановлена");
        put("rods.start", "Rod loading and welding line running", "Линия снаряжения и заварки твэлов работает");
        put("rods.stop", "Rod line stopped", "Линия твэлов остановлена");
        put("jig.start", "Assembly jig: rods are being pulled into the skeleton", "Стапель: твэлы протягивают в каркас");
        put("jig.stop", "Assembly jig stopped", "Стапель остановлен");
        // топливный цикл реального масштаба
        put("centrifuge.single", "Gas centrifuge (the cascade console counts 1 machine; a section holds %s): %s",
                "Газовая центрифуга (пульт каскада считает 1 машину; в секции их %s): %s");
        put("centrifuge.section", "Cascade section of %s centrifuges: %s", "Секция каскада из %s центрифуг: %s");
        put("centrifuge.spinning", "rotors at speed", "роторы на оборотах");
        put("centrifuge.idle", "stopped, no power from a cascade console", "стоит, нет питания от пульта каскада");
        put("centrifuge.open", "casing torn open by a rotor crash; Shift + right-click to fit a new rotor",
                "корпус вскрыт разрывом ротора; Shift + ПКМ — поставить новый ротор");
        put("centrifuge.repaired", "New rotor fitted, casing closed", "Новый ротор поставлен, корпус закрыт");
        put("autoclave.empty", "Autoclave empty: right-click with a UF₆ cylinder to roll it in",
                "Автоклав пуст: ПКМ баллоном UF₆ — закатить");
        put("autoclave.busy", "A cylinder is already inside", "Баллон уже внутри");
        put("autoclave.heating", "Heating: cylinder %s °C (UF₆ melts above 64 °C) · UF₆ %s kg · U-235 %s %%",
                "Прогрев: баллон %s °C (UF₆ плавится выше 64 °C) · UF₆ %s кг · U-235 %s %%");
        put("autoclave.ready", "Hot: cylinder %s °C, feeding the cascade · UF₆ %s kg · U-235 %s %%",
                "Прогрет: баллон %s °C, питает каскад · UF₆ %s кг · U-235 %s %%");
        put("lot.uranium", "U %s kg · U-235 %s %% (%s g)", "U %s кг · U-235 %s %% (%s г)");
        put("lot.density", "Pellets %s %% of theoretical density · %s pcs", "Таблетки %s %% теоретической плотности · %s шт.");
        put("lot.design", "%s%s", "%s%s");
        put("lot.gas.he", " · helium fill", " · заправка гелием");
        put("lot.gas.ar", " · argon fill", " · заправка аргоном");
        put("design.vver_440", "VVER-440", "ВВЭР-440");
        put("design.vver_1000", "VVER-1000", "ВВЭР-1000");
        put("design.pwr_17x17", "PWR 17×17", "PWR 17×17");
        put("design.am_1", "AM-1 channel", "Канал АМ-1");
        put("design.candu_37", "CANDU 37-element bundle", "37-элементный пучок CANDU");
        put("lot.burnup", "Spent: %s MWd/kg · U-236 %s %% · Pu %s g (Pu-239 %s %%)",
                "Отработанное: %s МВт·сут/кг · U-236 %s %% · Pu %s г (Pu-239 %s %%)");
        put("lot.mox", "MOX: Pu %s %% of heavy metal (%s g) · Pu-239 %s %% · Pu-240 %s %%",
                "МОКС: Pu %s %% тяжёлого металла (%s г) · Pu-239 %s %% · Pu-240 %s %%");
    }

    public static Component design(dev.denfry.promptcritical.sim.fuel.FuelDesign d) {
        return msg("design." + d.name().toLowerCase(Locale.ROOT));
    }

    private NuclearText() {
    }

    static void put(String key, String en, String ru) {
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
