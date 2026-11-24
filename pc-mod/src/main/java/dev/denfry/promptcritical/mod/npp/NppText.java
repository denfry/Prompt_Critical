package dev.denfry.promptcritical.mod.npp;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Сообщения АЭС T1 и T2a (en_us, ru_ru); ключи собирает {@code PcLanguageProvider}. */
public final class NppText {
    private static final String P = "message.promptcritical.npp.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("scram", "REACTOR SCRAM: %s", "АВАРИЙНАЯ ЗАЩИТА РЕАКТОРА: %s");
        put("reset", "Scram rods cocked", "Стержни АЗ взведены");
        put("orm_violated", "Operational reactivity margin below %s equivalent rods: the rods are too far out, insert the shim rods",
                "Оперативный запас реактивности ниже %s эквивалентных стержней: стержни выведены слишком далеко, погрузите РР");
        put("reset_refused", "Cannot cock the scram rods: the cause is still there", "Взвести АЗ нельзя: причина срабатывания не устранена");
        put("loaded", "Fuel loaded into channel %s", "Топливо загружено в канал %s");
        put("unloaded", "Channel %s unloaded: %s MWd/kg", "Канал %s выгружен: %s МВт·сут/кг");
        put("not_shut_down", "Refuelling only on a shut-down reactor", "Перегрузка только на заглушенном реакторе");
        put("channel_failed", "CHANNEL FAILURE: dryout broke %s channel(s), water in the graphite, activity in the hall",
                "РАЗРУШЕНИЕ КАНАЛА: кризис теплообмена разрушил каналов: %s; вода в кладке, активность в зале");
        put("protection_off", "Reactor protections BYPASSED", "Защиты реактора ВЫВЕДЕНЫ");
        put("protection_on", "Reactor protections in service", "Защиты реактора введены");
        put("candu_refuelled", "Fuelling machine: channel %s refuelled", "Перегрузочная машина: канал %s перегружен");
        put("pwr_unloaded", "Assembly %s unloaded: %s MWd/kg", "ТВС %s выгружена: %s МВт·сут/кг");
        put("mcp_on", "Main coolant pump %s breaker closed", "Выключатель ГЦН-%s включён");
        put("mcp_off", "Main coolant pump %s tripped", "ГЦН-%s отключён");
        // бассейн выдержки и перегрузочная машина ВВЭР-70
        put("pool_status", "Spent fuel pool: %s/%s assemblies, water %s m at %s °C, decay heat %s kW, cooling %s kW, %s m of water over the heads, %s µSv/h at the surface; %s",
                "Бассейн выдержки: ТВС %s/%s, вода %s м при %s °C, остаточное тепло %s кВт, охлаждение %s кВт, над головками %s м воды, у зеркала %s мкЗв/ч; %s");
        put("pool_cooling", "cooling pumps running", "насосы охлаждения работают");
        put("pool_no_suction", "LEVEL BELOW THE COOLING SUCTION: pumps idle, make up the water", "УРОВЕНЬ НИЖЕ ВСАСА ОХЛАЖДЕНИЯ: насосы без воды, нужна подпитка");
        put("pool_no_power", "NO POWER to the cooling and make-up pumps", "НЕТ ПИТАНИЯ насосов охлаждения и подпитки");
        put("pool_full", "Every rack position in the pool is taken", "Все гнёзда стеллажей бассейна заняты");
        put("pool_clad", "SPENT FUEL POOL: uncovered assemblies burst their cladding, fuel aerosol around the pool",
                "БАССЕЙН ВЫДЕРЖКИ: оголённые ТВС разрушили оболочки, аэрозоль топлива вокруг бассейна");
        put("rm_busy", "The refuelling machine is busy", "Перегрузочная машина занята");
        put("rm_no_power", "Refuelling machine: no power to the drives", "Перегрузочная машина: нет питания приводов");
        put("rm_no_reactor", "Refuelling machine: no VVER-70 reactor under the bridge", "Перегрузочная машина: под мостом нет реактора ВВЭР-70");
        put("rm_no_pool", "Refuelling machine: no spent fuel pool under the bridge", "Перегрузочная машина: под мостом нет бассейна выдержки");
        put("rm_out_of_reach", "Refuelling machine: the reactor or the pool is out of reach of the bridge, trolley or mast",
                "Перегрузочная машина: реактор или бассейн вне хода моста, тележки или штанги");
        put("rm_not_shut_down", "Refuelling machine: the reactor is not shut down", "Перегрузочная машина: реактор не остановлен");
        put("rm_core_empty", "Refuelling machine: the core is empty", "Перегрузочная машина: зона пуста");
        put("rm_core_full", "Refuelling machine: no free position in the core, the assembly stays in the mast",
                "Перегрузочная машина: свободной позиции в зоне нет, ТВС осталась в штанге");
        put("rm_pool_full", "Refuelling machine: no free rack in the pool", "Перегрузочная машина: в бассейне нет свободного гнезда");
        put("rm_no_fresh", "Refuelling machine: no fresh assembly in the pool", "Перегрузочная машина: в бассейне нет свежей ТВС");
        put("rm_nothing", "Refuelling machine: nothing to grip, returning to park", "Перегрузочная машина: захватывать нечего, на стоянку");
        put("rm_start_unload", "Refuelling machine: unloading the most burnt assembly to the pool", "Перегрузочная машина: выгрузка самой выгоревшей ТВС в бассейн");
        put("rm_start_load", "Refuelling machine: loading a fresh assembly from the pool", "Перегрузочная машина: загрузка свежей ТВС из бассейна");
        put("rm_done_unload", "Refuelling machine: assembly set in the pool rack", "Перегрузочная машина: ТВС установлена в стеллаж бассейна");
        put("rm_done_load", "Refuelling machine: assembly set in the core", "Перегрузочная машина: ТВС установлена в зону");
        put("rm_status", "Refuelling machine: %s (%s)", "Перегрузочная машина: %s (%s)");
        put("rm_unloading", "unloading", "выгрузка");
        put("rm_loading", "loading", "загрузка");
        put("rm_phase_idle", "parked", "на стоянке");
        put("rm_phase_to_source", "travelling to the pick-up", "переезд к месту захвата");
        put("rm_phase_lower_source", "lowering the mast", "штанга вниз");
        put("rm_phase_grip", "gripping", "захват");
        put("rm_phase_raise_source", "raising the assembly", "подъём сборки");
        put("rm_phase_to_target", "carrying the assembly", "перенос сборки");
        put("rm_phase_lower_target", "lowering the assembly", "опускание сборки");
        put("rm_phase_release", "releasing", "расцепка");
        put("rm_phase_raise_target", "raising the mast", "штанга вверх");
        put("rm_phase_park", "returning to park", "на стоянку");
        put("pwr_permit", "Start-up power trip lifted", "Пусковая уставка АЗ по мощности снята");
        put("pwr_permit_refused", "The start-up trip can be lifted only above 10 % power", "Снять пусковую уставку можно только выше 10 % мощности");
        put("pwr_dnb", "DEPARTURE FROM NUCLEATE BOILING: assemblies with failed cladding: %s, activity in the coolant",
                "КРИЗИС ТЕПЛООБМЕНА: разгерметизированы оболочки ТВС: %s; активность в теплоносителе");
        put("pwr_rupture", "PRIMARY CIRCUIT RUPTURE: the pressure broke the loop", "РАЗРЫВ ПЕРВОГО КОНТУРА: давление разорвало петлю");
        put("accident_drill_0", "Drill: the break is isolated", "Учение: течь отсечена");
        put("accident_drill_1", "Drill: small break opened in a cold leg", "Учение: малая течь в холодной нитке");
        put("accident_drill_2", "Drill: guillotine break of the main coolant pipe", "Учение: разрыв главного трубопровода на полное сечение");
        put("accident_loca", "LOSS OF COOLANT ACCIDENT: break of %s cm²", "АВАРИЯ С ПОТЕРЕЙ ТЕПЛОНОСИТЕЛЯ: течь %s см²");
        put("accident_sbo", "STATION BLACKOUT: no AC on the safety buses, ECCS pumps and makeup are lost",
                "ПОЛНОЕ ОБЕСТОЧИВАНИЕ: на секциях надёжного питания нет напряжения, насосы САОЗ и подпитка потеряны");
        put("accident_uncovered", "CORE UNCOVERED: the level is below the top of the fuel", "ОГОЛЕНИЕ АКТИВНОЙ ЗОНЫ: уровень ниже верха топлива");
        put("accident_clad", "CORE HEAT-UP: cladding burst, assemblies with failed cladding: %s",
                "РАЗОГРЕВ ЗОНЫ: оболочки вздулись и лопнули, разгерметизировано ТВС: %s");
        put("sfr_clad", "SODIUM POOL OVERHEATED: fuel cladding failing, %s %% of the core",
                "ПЕРЕГРЕВ НАТРИЯ: разрушаются оболочки твэлов, %s %% зоны");
        put("accident_h2_burn", "HYDROGEN BURN in the containment: %s MJ", "ГОРЕНИЕ ВОДОРОДА в гермооболочке: %s МДж");
        put("ria_eject", "ROD EJECTION: a control cluster blew out of its housing, %s $ of reactivity",
                "ВЫБРОС СТЕРЖНЯ: кластер СУЗ выбит из чехла, реактивность %s $");
        put("ria_burst", "PROMPT CRITICAL BURST: %s MJ in %s ms, hot-spot fuel enthalpy %s cal/g",
                "РАЗГОН НА МГНОВЕННЫХ НЕЙТРОНАХ: вспышка %s МДж за %s мс, энтальпия топлива в горячей точке %s кал/г");
        put("ria_failed", "REACTIVITY ACCIDENT: cladding failed in %s assemblies", "РЕАКТИВНОСТНАЯ АВАРИЯ: разрушены оболочки ТВС: %s");
        put("fci", "STEAM EXPLOSION: molten fuel hit the water, %s MJ", "ПАРОВОЙ ВЗРЫВ: расплав топлива попал в воду, %s МДж");
        put("fci_in_vessel", "STEAM EXPLOSION in the lower plenum: %s MJ, the vessel holds it", "ПАРОВОЙ ВЗРЫВ в нижней камере: %s МДж, корпус его выдержал");
        put("ria_reseated", "Drill: the cluster is back and its housing is sealed", "Учение: кластер на месте, чехол привода заглушён");
        put("ria_reseat_refused", "The cluster can be restored only on a shut-down reactor",
                "Вернуть кластер можно только на заглушенном реакторе");
        put("sgtr_on", "Drill: a tube of steam generator 1 torn through", "Учение: порвана трубка ПГ-1");
        put("sgtr_off", "Drill: the torn tube is plugged", "Учение: порванная трубка заглушена");
        put("sg_isolated", "Steam generator 1 isolated: steam and feed valves closed", "ПГ-1 отсечён: БЗОК и питание закрыты");
        put("sg_restored", "Steam generator 1 back on steam and feed", "ПГ-1 снова на паре и питании");
        put("porv_open", "Pressurizer relief valve held open", "ИПУ компенсатора давления открыт ключом");
        put("porv_closed", "Pressurizer relief valve back on its setpoint", "ИПУ компенсатора давления на уставке");
        put("sgtr_n16", "N-16 IN THE STEAM LINE: primary coolant is leaking into steam generator 1",
                "N-16 В ПАРОПРОВОДЕ: теплоноситель первого контура течёт в ПГ-1");
        put("sgtr_stuck", "SG SAFETY VALVE STUCK OPEN: primary coolant vents straight to the atmosphere",
                "ПК ПГ ЗАЛИП ОТКРЫТЫМ: теплоноситель первого контура уходит прямо в атмосферу");
        put("melt_candling", "CORE MELT: the cladding is melting and running down the fuel", "ПЛАВЛЕНИЕ ЗОНЫ: оболочки плавятся и стекают по топливу");
        put("melt_pool", "CORE MELT: a pool of molten fuel inside a crust", "ПЛАВЛЕНИЕ ЗОНЫ: бассейн расплавленного топлива в корке");
        put("melt_plenum", "CORE MELT: the crust gave way, corium in the lower plenum", "ПЛАВЛЕНИЕ ЗОНЫ: корка прорвана, кориум в нижней камере");
        put("melt_vessel", "VESSEL FAILED: the corium is through the lower head", "КОРПУС ПРОПЛАВЛЕН: кориум прошёл через днище");
        put("melt_dch", "DIRECT CONTAINMENT HEATING: melt blown out at pressure, %s MJ into the containment",
                "ПРЯМОЙ НАГРЕВ ГЕРМООБОЛОЧКИ: расплав выброшен под давлением, %s МДж в гермооболочку");
        put("melt_mcci", "CORIUM ON THE CONCRETE: %s m³ of floor eaten through", "КОРИУМ НА БЕТОНЕ: проплавлено %s м³ основания");
        put("accident_h2_detonation", "HYDROGEN DETONATION in the containment: %s MJ, the walls take twice the burn pressure",
                "ДЕТОНАЦИЯ ВОДОРОДА в гермооболочке: %s МДж, стены получают вдвое больше давления горения");
        put("accident_breach", "CONTAINMENT FAILED: overpressure tore the shell, the release goes outside",
                "РАЗРУШЕНИЕ ГЕРМООБОЛОЧКИ: давление разорвало её, выброс уходит наружу");
    }

    private NppText() {
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
}
