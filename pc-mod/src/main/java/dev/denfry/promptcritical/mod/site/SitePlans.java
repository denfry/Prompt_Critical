package dev.denfry.promptcritical.mod.site;

import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Типовые проекты, которые строятся на стройплощадке (спецификация real-scale machines, §3a): для каждой машины —
 * её блок, этапы монтажа с перечнем материалов и временем и масса прототипа. Материалы — по реальной массе
 * (решение владельца 2026-11-23): металл этапов в сумме — масса машины из её таблицы размеров (docs/research), бетон —
 * фундамент и шахта. Пока тяжёлых деталей GDD 18 (обечайки, днища, роторы) нет, этапы берут слитки, лист, трубы и
 * провод по массе предмета ({@link ItemMass}); число предметов умножается на {@code site.material_scale}.
 */
public final class SitePlans {
    /** Позиция перечня: предмет и сколько нужно при масштабе материалов 1. */
    public record Need(Supplier<? extends Item> item, int base) {
        /** Сколько нужно с учётом {@code site.material_scale}, не меньше одного. */
        public int count() {
            return Math.max(1, (int) Math.round(base * scale()));
        }
    }

    /** Этап монтажа: ключ текста, перечень, время монтажа, тиков. */
    public record Stage(String key, List<Need> needs, int erectTicks) {}

    /** Проект: что ставится, этапы по порядку и масса металла прототипа, кг (оценки помечены в таблицах размеров). */
    public record Plan(String id, Supplier<? extends Block> unit, double metalKg, List<Stage> stages) {
        /** Масса металла в перечнях этапов при масштабе 1, кг (бетон и графит не металл). */
        public double plannedMetalKg() {
            double kg = 0;
            for (Stage s : stages)
                for (Need n : s.needs()) {
                    Item it = n.item().get();
                    if (it == Items.GRAY_CONCRETE || it == Items.BRICKS || it == PcBlocks.MODERATOR_BLOCK_GRAPHITE.get().asItem()
                            || it == PcBlocks.SHIELD_HEAVY_CONCRETE_REAL.get().asItem()) continue;
                    kg += n.base() * ItemMass.kg(it);
                }
            return kg;
        }
    }

    private static Need n(Supplier<? extends Item> item, int count) {
        return new Need(item, count);
    }

    private static final Supplier<Item> CONCRETE = () -> Items.GRAY_CONCRETE;
    private static final Supplier<Item> BRICKS = () -> Items.BRICKS;
    private static final Supplier<Item> HEAVY_CONCRETE = () -> PcBlocks.SHIELD_HEAVY_CONCRETE_REAL.get().asItem();
    private static final Supplier<Item> GRAPHITE = () -> PcBlocks.MODERATOR_BLOCK_GRAPHITE.get().asItem();
    private static final Supplier<? extends Item> INGOT = PcItems.STEEL_INGOT, PLATE = PcItems.STEEL_PLATE, BEAM = PcItems.STEEL_BEAM,
            PIPE = PcItems.SEAMLESS_PIPE, WIRE = PcItems.COPPER_WIRE, ZR = PcItems.ZIRCALOY_INGOT;

    private static Stage st(String key, int ticks, Need... needs) {
        return new Stage(key, List.of(needs), ticks);
    }

    private static Plan plan(String id, Supplier<? extends Block> unit, double metalT, Stage... stages) {
        return new Plan(id, unit, metalT * 1000, List.of(stages));
    }

    /**
     * Водородная станция (art/blockbench/hydrogen, оценки по размерам моделей): батарея A485 с сепараторами и щелочным
     * контуром ≈ 45 т, компрессор PDC на раме ≈ 12 т, рампа из девяти бесшовных баллонов ≈ 27 т, газгольдер 70 м³ с
     * площадкой ≈ 12 т, модуль ТЭ с инвертором ≈ 3,5 т.
     */
    private static final Map<String, Plan> H2_PLANS = Map.of(
            "h2_electrolyser_real", plan("h2_electrolyser_real", PcBlocks.H2_ELECTROLYSER_REAL, 45,
                    st("foundation", 100, n(CONCRETE, 20)),
                    st("cell_stack", 240, n(PLATE, 200), n(INGOT, 100)),
                    st("gas_separators", 160, n(PLATE, 80), n(PIPE, 60)),
                    st("dc_busbars", 100, n(WIRE, 300))),
            "h2_compressor_real", plan("h2_compressor_real", PcBlocks.H2_COMPRESSOR_REAL, 12,
                    st("foundation", 60, n(CONCRETE, 4)),
                    st("compressor_frame", 140, n(BEAM, 30), n(INGOT, 60)),
                    st("coolers_piping", 100, n(PIPE, 40), n(WIRE, 80))),
            "h2_tube_bank_real", plan("h2_tube_bank_real", PcBlocks.H2_TUBE_BANK_REAL, 27,
                    st("tube_frame", 80, n(BEAM, 40)),
                    st("pressure_tubes", 200, n(PIPE, 460))),
            "h2_gasholder_real", plan("h2_gasholder_real", PcBlocks.H2_GASHOLDER_REAL, 12,
                    st("foundation", 80, n(CONCRETE, 10)),
                    st("shell", 160, n(PLATE, 90), n(PIPE, 10)),
                    st("platform", 80, n(BEAM, 25))),
            "h2_fuel_cell_real", plan("h2_fuel_cell_real", PcBlocks.H2_FUEL_CELL_REAL, 3.5,
                    st("fuel_cell_stack", 120, n(PLATE, 20), n(INGOT, 5), n(WIRE, 50)),
                    st("inverter_cabinet", 80, n(PLATE, 3), n(WIRE, 30))));

    private static final Map<String, Plan> PLANS = Map.of(
            // корпус 304 т (ТПУ), внутрикорпусные ≈ 250 т, верхний блок ≈ 135 т, патрубки и трубопроводы ≈ 60 т (оценки)
            "vver_reactor", plan("vver_reactor", PcBlocks.VVER_REACTOR, 770,
                    st("foundation", 200, n(CONCRETE, 200), n(BEAM, 200)),
                    st("vessel", 600, n(INGOT, 3040), n(PLATE, 2500)),
                    st("upper_unit", 400, n(PLATE, 1000), n(PIPE, 600), n(WIRE, 500)),
                    st("piping", 300, n(PLATE, 500), n(PIPE, 200))),
            // ПГВ-1000 ≈ 300 т: корпус ≈ 170 т, коллекторы ≈ 50 т, трубный пучок ≈ 70 т (оценки)
            "vver_steam_generator", plan("vver_steam_generator", PcBlocks.VVER_STEAM_GENERATOR, 300,
                    st("foundation", 160, n(CONCRETE, 60), n(BEAM, 50)),
                    st("shell", 400, n(PLATE, 1700), n(INGOT, 500)),
                    st("tubes", 400, n(PIPE, 1400))),
            // ГЦН-195М ≈ 140 т: корпус ≈ 70 т, двигатель и маховик ≈ 65 т (оценки)
            "vver_coolant_pump", plan("vver_coolant_pump", PcBlocks.VVER_COOLANT_PUMP, 140,
                    st("foundation", 120, n(CONCRETE, 30), n(BEAM, 30)),
                    st("casing", 300, n(INGOT, 700)),
                    st("drive", 300, n(WIRE, 1500), n(PLATE, 300), n(INGOT, 200))),
            // КД-79 ≈ 220 т (оценка)
            "vver_pressurizer", plan("vver_pressurizer", PcBlocks.VVER_PRESSURIZER, 220,
                    st("foundation", 120, n(CONCRETE, 40), n(BEAM, 40)),
                    st("vessel", 400, n(INGOT, 2000), n(PLATE, 150), n(WIRE, 100))),
            // BWR класса Big Rock Point ≈ 300 т: корпус ≈ 180 т, внутрикорпусные ≈ 50 т, приводы ≈ 40 т (оценки)
            "bwr240_reactor", plan("bwr240_reactor", PcBlocks.BWR240_REACTOR, 300,
                    st("pedestal", 200, n(CONCRETE, 120), n(BEAM, 100)),
                    st("drives", 300, n(PIPE, 400), n(INGOT, 200), n(WIRE, 200)),
                    st("vessel", 500, n(INGOT, 1800), n(PLATE, 500)),
                    st("piping", 200, n(PLATE, 150), n(PIPE, 100))),
            // CANDU 6 ≈ 1350 т: торцевые экраны со стальной дробью ≈ 600 т, каландр ≈ 150 т, каналы и фитинги ≈ 250 т,
            // питатели ≈ 190 т, мост перегрузки ≈ 130 т (оценки)
            "candu6_reactor", plan("candu6_reactor", PcBlocks.CANDU6_REACTOR, 1350,
                    st("vault", 400, n(CONCRETE, 1500), n(BEAM, 300)),
                    st("calandria", 600, n(PLATE, 1200), n(INGOT, 6000), n(ZR, 300)),
                    st("end_fittings", 600, n(INGOT, 2300), n(ZR, 200), n(PIPE, 3800)),
                    st("fuelling", 300, n(BEAM, 600), n(PLATE, 400), n(WIRE, 200), n(INGOT, 300))),
            // БН-600 ≈ 3850 т: корпус ≈ 600 т, внутрикорпусные ≈ 800 т, насосы ≈ 600 т, ПТО ≈ 900 т, крыша и пробки
            // ≈ 900 т (оценки; корпус 12,8 × 12,6 м — OSTI 6611821)
            "bn600_reactor", plan("bn600_reactor", PcBlocks.BN600_REACTOR, 3850,
                    st("shaft", 500, n(CONCRETE, 2500), n(BEAM, 500)),
                    st("vessel", 800, n(PLATE, 6000), n(INGOT, 8000)),
                    st("pool_equipment", 800, n(INGOT, 6000), n(PIPE, 6000), n(PLATE, 6000), n(WIRE, 1000)),
                    st("roof", 600, n(INGOT, 8000), n(PLATE, 1000), n(WIRE, 500))),
            // АМ-1: графитовая кладка ≈ 32 м³, 151 канал по 5 м, плита ≈ 40 т, коллекторы ≈ 25 т (оценки)
            "am1_reactor", plan("am1_reactor", PcBlocks.AM1_REACTOR, 120,
                    st("shaft", 240, n(CONCRETE, 300), n(BEAM, 100)),
                    st("graphite", 300, n(GRAPHITE, 32), n(PLATE, 150)),
                    st("channels", 300, n(PIPE, 755), n(PLATE, 400), n(WIRE, 50)),
                    st("piping", 200, n(PIPE, 300), n(PLATE, 100))));

    /**
     * Прототипы АЭС реального масштаба (docs/research/2026-11-24-reactor-prototypes): ВВЭР-70 Райнсберга. Массы —
     * оценки по габаритам листов и толщинам стенок (корпус ∅2,85 × 11 м со стенкой 140 мм ≈ 115 т, днище и крышка
     * ≈ 30 т, внутрикорпусные ≈ 50 т, верхний блок с 19 приводами ≈ 25 т, патрубки и задвижки ≈ 15 т).
     */
    private static final Map<String, Plan> PROTOTYPE_PLANS = Map.of(
            "vver70_reactor_real", plan("vver70_reactor_real", PcBlocks.VVER70_REACTOR_REAL, 235,
                    st("foundation", 160, n(CONCRETE, 60), n(BEAM, 50)),
                    st("vessel", 500, n(INGOT, 1250), n(PLATE, 400)),
                    st("upper_unit", 300, n(PLATE, 200), n(PIPE, 200), n(WIRE, 300)),
                    st("piping", 240, n(PLATE, 150), n(PIPE, 200))),
            // вертикальный ПГ ∅2,2 × 8 м: корпус ≈ 38 т, трубный пучок ≈ 20 т (оценки)
            "vver70_steam_generator_real", plan("vver70_steam_generator_real", PcBlocks.VVER70_STEAM_GENERATOR_REAL, 60,
                    st("foundation", 100, n(CONCRETE, 10), n(BEAM, 10)),
                    st("shell", 240, n(PLATE, 300), n(INGOT, 100)),
                    st("tubes", 240, n(PIPE, 400))),
            // экранированный ГЦН: улитка ≈ 8 т литья, статор с рубашкой и ротор ≈ 6 т (оценки)
            "vver70_coolant_pump_real", plan("vver70_coolant_pump_real", PcBlocks.VVER70_COOLANT_PUMP_REAL, 15,
                    st("foundation", 80, n(CONCRETE, 5), n(BEAM, 5)),
                    st("casing", 200, n(INGOT, 80)),
                    st("canned_motor", 200, n(WIRE, 200), n(PLATE, 30), n(INGOT, 15))),
            // газовый КД 8 м³: сосуд ∅1,4 × 6,2 м со стенкой 80 мм ≈ 21 т, стойка баллонов азота ≈ 3 т (оценки)
            "vver70_pressurizer_real", plan("vver70_pressurizer_real", PcBlocks.VVER70_PRESSURIZER_REAL, 25,
                    st("foundation", 80, n(CONCRETE, 6), n(BEAM, 6)),
                    st("vessel", 240, n(INGOT, 150), n(PLATE, 60)),
                    st("gas_rack", 120, n(PIPE, 40), n(PLATE, 10))),
            // бассейн выдержки: облицовка 4 мм нержавейки на 185 м² ≈ 6 т, стеллажи на 256 гнёзд ≈ 8 т, трубы
            // охлаждения ≈ 2 т; бетон стен не металл (оценки)
            "vver70_fuel_pool_real", plan("vver70_fuel_pool_real", PcBlocks.VVER70_FUEL_POOL_REAL, 16,
                    st("pool_walls", 300, n(CONCRETE, 120)),
                    st("pool_liner", 240, n(PLATE, 60), n(PIPE, 40)),
                    st("pool_racks", 200, n(PLATE, 80))),
            // перегрузочная машина: рельсы ≈ 4 т, мост пролётом 9 м ≈ 25 т, тележка со штангой ≈ 13 т, кабели ≈ 2 т (оценки)
            "vver70_refuelling_machine_real", plan("vver70_refuelling_machine_real", PcBlocks.VVER70_REFUELLING_MACHINE_REAL, 45,
                    st("rm_rails", 120, n(BEAM, 40)),
                    st("rm_bridge", 300, n(BEAM, 250)),
                    st("rm_trolley", 240, n(PLATE, 100), n(PIPE, 60), n(WIRE, 200))),
            // БОР-60: бак ∅2,2 × 6 м с двойным корпусом ≈ 70 т, поворотные пробки с защитой ≈ 35 т, приводы СУЗ и
            // линии ≈ 13 т (оценки)
            "bor60_reactor_real", plan("bor60_reactor_real", PcBlocks.BOR60_REACTOR_REAL, 120,
                    st("foundation", 160, n(CONCRETE, 40), n(BEAM, 50)),
                    st("vessel", 400, n(INGOT, 500), n(PLATE, 200)),
                    st("rotating_plugs", 300, n(INGOT, 250), n(PLATE, 100)),
                    st("cps_drives", 240, n(PIPE, 100), n(WIRE, 300), n(PLATE, 50))),
            // ПТО одной петли: корпус ≈ 8 т, трубный пучок ≈ 5 т, насос второго контура ≈ 1 т (оценки)
            "bor60_ihx_real", plan("bor60_ihx_real", PcBlocks.BOR60_IHX_REAL, 15,
                    st("foundation", 80, n(CONCRETE, 5), n(BEAM, 10)),
                    st("shell", 200, n(PLATE, 60), n(INGOT, 20)),
                    st("tubes", 200, n(PIPE, 100)),
                    st("secondary_pump", 120, n(WIRE, 100))),
            // вертикальный натриевый насос: корпус ≈ 12 т литья, двигатель и вал ≈ 7 т (оценки)
            "bor60_primary_pump_real", plan("bor60_primary_pump_real", PcBlocks.BOR60_PRIMARY_PUMP_REAL, 20,
                    st("foundation", 80, n(CONCRETE, 5), n(BEAM, 10)),
                    st("casing", 200, n(INGOT, 120)),
                    st("pump_motor", 200, n(WIRE, 200), n(PLATE, 30), n(INGOT, 20))),
            // прямоточный ПГ: корпус ≈ 15 т, змеевики ≈ 10 т, линия сброса с мембраной ≈ 4 т (оценки)
            "bor60_steam_generator_real", plan("bor60_steam_generator_real", PcBlocks.BOR60_STEAM_GENERATOR_REAL, 30,
                    st("foundation", 80, n(CONCRETE, 5), n(BEAM, 10)),
                    st("shell", 240, n(PLATE, 150)),
                    st("tubes", 240, n(PIPE, 200)),
                    st("sg_relief", 120, n(PIPE, 40), n(PLATE, 20))));

    /** Машины эры I0 реального масштаба. */
    private static final Map<String, Plan> I0_PLANS = Map.of(
            // ланкаширский котёл 8,5 × ⌀2,3 м ≈ 22 т (Crofton), кладка ≈ 55 м³ кирпича (оценка)
            "lancashire_boiler", plan("lancashire_boiler", PcBlocks.LANCASHIRE_BOILER, 22,
                    st("setting", 200, n(CONCRETE, 6), n(BRICKS, 30)),
                    st("shell", 300, n(PLATE, 150), n(INGOT, 50)),
                    st("mountings", 200, n(INGOT, 10), n(PIPE, 20), n(BRICKS, 25))),
            // стальной копёр 16 м: башня и подкосы из клёпаных балок ≈ 45 т, шкив ⌀3 м ≈ 2 т, площадка ≈ 3 т (оценки)
            "headframe_steel", plan("headframe_steel", PcBlocks.HEADFRAME_STEEL, 50,
                    st("foundation", 160, n(CONCRETE, 20)),
                    st("tower", 400, n(BEAM, 400), n(PLATE, 50)),
                    st("sheave_deck", 200, n(PLATE, 30), n(INGOT, 20))),
            // горизонтальная машина ≈ 16 т литья и поковок + маховик ≈ 5 т (оценки), каменный фундамент
            "mill_engine", plan("mill_engine", PcBlocks.MILL_ENGINE, 21,
                    st("foundation", 120, n(CONCRETE, 8)),
                    st("bed", 300, n(INGOT, 130), n(PLATE, 20), n(PIPE, 20)),
                    st("flywheel", 240, n(INGOT, 50))),
            // пара машин 28 × 48 дюймов на барабан ⌀15 футов (Parkfield): машины и станины ≈ 46 т, вал с барабаном и
            // тормозом ≈ 42 т (оценки, docs/research/winding_engine-dimensions.md), каменные фундаменты
            "winding_engine", plan("winding_engine", PcBlocks.WINDING_ENGINE, 88,
                    st("foundation", 160, n(CONCRETE, 30)),
                    st("engines", 400, n(INGOT, 400), n(PLATE, 40), n(PIPE, 40)),
                    st("drum", 320, n(INGOT, 300), n(PLATE, 120))),
            // Guibal ⌀9,1 м: ротор с осью, спицами и лопатками ≈ 14 т, машина 28 × 48 дюймов ≈ 15 т (оценки);
            // кирпичный кожух в приямке и эвазе 16,5 м
            "guibal_fan", plan("guibal_fan", PcBlocks.GUIBAL_FAN, 29,
                    st("fan_house", 240, n(CONCRETE, 6), n(BRICKS, 40)),
                    st("rotor", 300, n(INGOT, 80), n(PLATE, 60)),
                    st("fan_engine", 300, n(INGOT, 140), n(PIPE, 20), n(BRICKS, 30))),
            // вагранка Кирка 24″: обечайка, стойки и подовая плита ≈ 3 т, шамотная футеровка ≈ 4 т (оценки, cupola-dimensions.md)
            "cupola_furnace_real", plan("cupola_furnace_real", PcBlocks.CUPOLA_FURNACE_REAL, 3,
                    st("foundation", 80, n(CONCRETE, 2)),
                    st("shell", 200, n(PLATE, 12), n(INGOT, 8)),
                    st("lining", 160, n(BRICKS, 2)),
                    st("blower_platform", 160, n(PLATE, 6), n(PIPE, 2), n(INGOT, 2))),
            // трёхплунжерный насос с передачей ≈ 8 т (оценка): чугунная станина ≈ 3,5 т, коленвал и колёса ≈ 2 т,
            // плунжеры, клапанные коробки, коллекторы и воздушный колпак ≈ 2,5 т; бетонный фундамент
            "mine_drainage_pump_geared", plan("mine_drainage_pump_geared", PcBlocks.MINE_DRAINAGE_PUMP_GEARED, 8,
                    st("foundation", 80, n(CONCRETE, 3)),
                    st("pump_bed", 200, n(INGOT, 30), n(PLATE, 5)),
                    st("gearing", 160, n(INGOT, 20)),
                    st("rams", 200, n(INGOT, 15), n(PIPE, 20))),
            // пара толкателей с бункерами ≈ 2 × 1 т литья, рама, плита и линия вала ≈ 2 т (оценки)
            "lancashire_stoker", plan("lancashire_stoker", PcBlocks.LANCASHIRE_STOKER, 4,
                    st("stoker_frame", 120, n(INGOT, 15), n(PLATE, 5)),
                    st("stokers", 160, n(INGOT, 18), n(PLATE, 2))),
            // коксовая яма: кладка ≈ 5 т шамота, металла — колосники, рама крышки и уголки ≈ 0,4 т (оценки)
            "crucible_furnace_real", plan("crucible_furnace_real", PcBlocks.CRUCIBLE_FURNACE_REAL, 0.4,
                    st("pit", 100, n(BRICKS, 2), n(INGOT, 2)),
                    st("flue", 80, n(BRICKS, 1), n(INGOT, 2))));

    /** Доменный цех реального масштаба (08 §8.5). */
    private static final Map<String, Plan> IRONMAKING_PLANS = Map.ofEntries(
            // каупер ⌀4,5 × 14 м: клёпаная обечайка с куполом ≈ 25 т листа, клапаны, площадка и лестница ≈ 8 т (оценки,
            // docs/research/hot_blast_stove-dimensions.md); насадка 60 т шамота — как в симуляции
            Map.entry("hot_blast_stove_real", plan("hot_blast_stove_real", PcBlocks.HOT_BLAST_STOVE_REAL, 33,
                    st("foundation", 120, n(CONCRETE, 6)),
                    st("stove_shell", 320, n(PLATE, 250)),
                    st("checker", 240, n(BRICKS, 32)),
                    st("stove_valves", 200, n(INGOT, 60), n(PIPE, 40)))),
            // горизонтальная воздуходувная машина: станина, воздушный цилиндр ⌀1,2 м и параллели ≈ 22 т литья, коленвал
            // с маховиком ≈ 8 т (оценки, docs/research/blast_blower-dimensions.md), бетонный фундамент с ямой маховика
            Map.entry("blast_blower_engine", plan("blast_blower_engine", PcBlocks.BLAST_BLOWER_ENGINE, 30,
                    st("foundation", 100, n(CONCRETE, 6)),
                    st("blowing_tub", 280, n(INGOT, 200), n(PLATE, 20)),
                    st("blower_flywheel", 200, n(INGOT, 80)))),
            // лента Дуайта — Ллойда 1,07 × 8 м: паллеты ≈ 25 т литья, рама, вакуум-камеры и коллектор ≈ 20 т, бункер, горн
            // и привод ≈ 10 т (оценки); горн зажигания выложен кирпичом
            Map.entry("sinter_strand_real", plan("sinter_strand_real", PcBlocks.SINTER_STRAND_REAL, 55,
                    st("foundation", 120, n(CONCRETE, 8)),
                    st("strand_frame", 280, n(PLATE, 150), n(BEAM, 40)),
                    st("pallets", 280, n(INGOT, 250), n(PIPE, 20)),
                    st("ignition_hood", 200, n(PLATE, 60), n(BRICKS, 3)))),
            // клёпаный корпус ⌀2,9 × 5 м с конусом и куполом ≈ 9 т листа, колонны, газоотвод и площадка ≈ 6 т (оценки)
            Map.entry("dust_catcher_real", plan("dust_catcher_real", PcBlocks.DUST_CATCHER_REAL, 15,
                    st("foundation", 80, n(CONCRETE, 2)),
                    st("catcher_shell", 240, n(PLATE, 90), n(BEAM, 30)),
                    st("catcher_mains", 160, n(PIPE, 40), n(PLATE, 10)))),
            // эксгаустер: улитка ⌀1,6 м, ротор и подшипники ≈ 6 т литья, редуктор, муфта и байпас ≈ 2 т (оценки)
            Map.entry("gas_exhauster_real", plan("gas_exhauster_real", PcBlocks.GAS_EXHAUSTER_REAL, 8,
                    st("foundation", 60, n(CONCRETE, 3)),
                    st("exhauster", 200, n(INGOT, 50), n(PLATE, 10)),
                    st("exhauster_drive", 120, n(INGOT, 15), n(PIPE, 10)))),
            // аппараты улавливания: стальные корпуса с трубными пучками, тарелками или насадкой (оценки по габаритам)
            Map.entry("byproduct_plant_cooler_real", plan("byproduct_plant_cooler_real", PcBlocks.BYPRODUCT_PLANT_COOLER_REAL, 12,
                    st("foundation", 60, n(CONCRETE, 2)),
                    st("byproduct_vessel", 200, n(PLATE, 70), n(PIPE, 60)),
                    st("byproduct_piping", 120, n(PIPE, 20), n(INGOT, 10)))),
            Map.entry("byproduct_plant_precipitator_real", plan("byproduct_plant_precipitator_real", PcBlocks.BYPRODUCT_PLANT_PRECIPITATOR_REAL, 8,
                    st("foundation", 60, n(CONCRETE, 2)),
                    st("byproduct_vessel", 200, n(PLATE, 55), n(INGOT, 15)),
                    st("byproduct_piping", 120, n(PIPE, 20)))),
            Map.entry("byproduct_plant_saturator_real", plan("byproduct_plant_saturator_real", PcBlocks.BYPRODUCT_PLANT_SATURATOR_REAL, 6,
                    st("foundation", 60, n(CONCRETE, 2)),
                    st("byproduct_vessel", 160, n(PLATE, 40), n(INGOT, 10)),
                    st("byproduct_piping", 100, n(PIPE, 20)))),
            Map.entry("byproduct_plant_scrubber_real", plan("byproduct_plant_scrubber_real", PcBlocks.BYPRODUCT_PLANT_SCRUBBER_REAL, 18,
                    st("foundation", 80, n(CONCRETE, 3)),
                    st("byproduct_vessel", 280, n(PLATE, 130), n(INGOT, 20)),
                    st("byproduct_piping", 160, n(PIPE, 60)))),
            // поворотная колонна на тумбе ≈ 2 т, пушка с двигателем или бурмашина ≈ 2–3 т (оценки)
            Map.entry("cast_house_mud_gun_real", plan("cast_house_mud_gun_real", PcBlocks.CAST_HOUSE_MUD_GUN_REAL, 5,
                    st("cast_house_column", 120, n(INGOT, 20), n(CONCRETE, 1)),
                    st("mud_gun", 160, n(INGOT, 25), n(PLATE, 5), n(PIPE, 4)))),
            Map.entry("cast_house_drill_real", plan("cast_house_drill_real", PcBlocks.CAST_HOUSE_DRILL_REAL, 4,
                    st("cast_house_column", 120, n(INGOT, 20), n(CONCRETE, 1)),
                    st("taphole_drill", 140, n(INGOT, 15), n(PLATE, 3), n(PIPE, 6)))),
            // ковш на 20 т: клёпаный кожух с цапфами ≈ 8 т, стойка и червячный механизм ≈ 10 т, футеровка (оценки)
            Map.entry("ladle_real", plan("ladle_real", PcBlocks.LADLE_REAL, 18,
                    st("ladle_stand", 200, n(INGOT, 100), n(CONCRETE, 2)),
                    st("ladle_shell", 240, n(PLATE, 80), n(BRICKS, 3)))),
            // бетонный бак и струйная коробка, ковшовый элеватор с жёлобом ≈ 10 т металла (оценки)
            Map.entry("slag_granulator_real", plan("slag_granulator_real", PcBlocks.SLAG_GRANULATOR_REAL, 10,
                    st("granulator_tank", 200, n(CONCRETE, 10), n(PLATE, 30), n(PIPE, 20)),
                    st("granulator_elevator", 200, n(INGOT, 40), n(BEAM, 20)))));

    /** Энергетика и водоподготовка реального масштаба (07 §7.3.1, §7.6.3, §7.9.6, 08 §8.12); массы — оценки. */
    private static final Map<String, Plan> POWER_PLANS = Map.of(
            // ДА-25: бак ≈ 15 м³ из листа 10 мм ≈ 6 т, колонка Ø 1 м ≈ 2 т, площадка, лестница и обвязка ≈ 1 т (оценки)
            "deaerator_real", plan("deaerator_real", PcBlocks.DEAERATOR_REAL, 9,
                    st("saddles", 80, n(CONCRETE, 2)),
                    st("storage_tank", 240, n(PLATE, 50), n(INGOT, 10)),
                    st("deaerating_column", 200, n(PLATE, 20), n(BEAM, 5), n(PIPE, 10))),
            // фильтр Ø 0,9 м × 4 м: корпус с днищами и опорами ≈ 1 т, фронт задвижек ≈ 0,2 т (оценки)
            "ion_exchange_column_real", plan("ion_exchange_column_real", PcBlocks.ION_EXCHANGE_COLUMN_REAL, 1.2,
                    st("filter_vessel", 120, n(PLATE, 8), n(INGOT, 2)),
                    st("valve_nest", 80, n(PIPE, 4))),
            // генератор 500 кВ·А около 1905 г., тихоходный многополюсный: плита и стояки ≈ 3 т, статор ≈ 5 т, индуктор ≈ 4 т (оценки)
            "shaft_generator_real", plan("shaft_generator_real", PcBlocks.SHAFT_GENERATOR_REAL, 12,
                    st("bedplate", 120, n(CONCRETE, 2), n(INGOT, 30)),
                    st("stator", 240, n(INGOT, 30), n(PLATE, 10), n(WIRE, 100)),
                    st("field", 200, n(INGOT, 30), n(WIRE, 100))),
            // двигатель 1 МВт 6 кВ: плита и подшипники ≈ 1,5 т, станина со статором ≈ 2,5 т, ротор ≈ 2 т (оценки)
            "electric_motor_mv_real", plan("electric_motor_mv_real", PcBlocks.ELECTRIC_MOTOR_MV_REAL, 6,
                    st("bedplate", 100, n(CONCRETE, 1), n(INGOT, 15)),
                    st("motor_frame", 200, n(PLATE, 15), n(WIRE, 100)),
                    st("motor_rotor", 160, n(INGOT, 20))));

    /** Добыча, обогащение и коксохим реального масштаба (оценки масс — в комментариях). */
    private static final Map<String, Plan> MINING_COKE_PLANS = Map.of(
            // двухшнековый комбайн для пласта 2 м ≈ 37 т, 12 м забойного конвейера (рештаки ≈ 0,8 т на 1,5 м) ≈ 6,5 т (оценки)
            "longwall_shearer_real", plan("longwall_shearer_real", PcBlocks.LONGWALL_SHEARER_REAL, 44,
                    st("face_conveyor", 160, n(PLATE, 55), n(INGOT, 10)),
                    st("shearer_body", 300, n(INGOT, 180), n(PLATE, 40), n(WIRE, 50)),
                    st("ranging_arms", 240, n(INGOT, 120), n(PLATE, 20))),
            // машина Баума на 36 т/ч: клёпаный бак 2,3 × 3,3 м с бункерами ≈ 9 т, стойки ≈ 1 т, клапаны, вал и
            // вентилятор ≈ 2 т (оценки, docs/research/coal_jig-dimensions.md)
            "coal_jig_real", plan("coal_jig_real", PcBlocks.COAL_JIG_REAL, 12,
                    st("foundation", 80, n(CONCRETE, 4)),
                    st("jig_tank", 240, n(PLATE, 80), n(BEAM, 15)),
                    st("air_valves", 160, n(INGOT, 15), n(PIPE, 20))),
            // башня тушения: камера 3 × 3 м с коллектором ≈ 5 т, зонт и труба 2,5 × 2,5 × 9 м из листа 8 мм ≈ 7 т (оценки,
            // docs/research/quench_tower-dimensions.md); опоры над путём игрок ставит сам
            "quench_tower_real", plan("quench_tower_real", PcBlocks.QUENCH_TOWER_REAL, 12,
                    st("quench_chamber", 200, n(PLATE, 40), n(PIPE, 20)),
                    st("stack", 240, n(PLATE, 60), n(BEAM, 10))),
            // двухстоечная щитовая секция шага 2 м для пласта 2 м ≈ 13 т: основание с мостом ≈ 5 т, стойки и
            // гидроблок ≈ 4 т, перекрытие с ограждением ≈ 4 т (оценки)
            "powered_roof_support_real", plan("powered_roof_support_real", PcBlocks.POWERED_ROOF_SUPPORT_REAL, 13,
                    st("pontoons", 160, n(PLATE, 40), n(INGOT, 10)),
                    st("support_legs", 160, n(INGOT, 30), n(PIPE, 20)),
                    st("canopy", 160, n(PLATE, 40))));

    /** Углеродная линия реального масштаба (08 §8.4.5): массы металла — оценки, кирпич кладки не металл. */
    private static final Map<String, Plan> CARBON_PLANS = Map.of(
            // ванна Ачесона 12 м: кладка ≈ 60 м³, металл — торцевые электродные головки, рамы и шины ≈ 8 т (оценки)
            "acheson_furnace_real", plan("acheson_furnace_real", PcBlocks.ACHESON_FURNACE_REAL, 8,
                    st("foundation", 120, n(CONCRETE, 10)),
                    st("furnace_bed", 240, n(BRICKS, 40), n(INGOT, 30)),
                    st("electrode_heads", 200, n(INGOT, 30), n(WIRE, 200))),
            // горизонтальный гидравлический пресс: плиты, колонны и траверса ≈ 25 т, цилиндр массы ≈ 11 т, насосная ≈ 4 т (оценки)
            "electrode_extruder_real", plan("electrode_extruder_real", PcBlocks.ELECTRODE_EXTRUDER_REAL, 40,
                    st("press_frame", 300, n(INGOT, 200), n(BEAM, 50)),
                    st("mud_cylinder", 240, n(INGOT, 100), n(PIPE, 20)),
                    st("pump_unit", 160, n(INGOT, 30), n(PIPE, 20))),
            // камера обжига 5 × 4 × 4 м: стальной кожух ≈ 10 т, коллектор, газоходы и крышка ≈ 5 т (оценки)
            "baking_furnace_real", plan("baking_furnace_real", PcBlocks.BAKING_FURNACE_REAL, 15,
                    st("foundation", 120, n(CONCRETE, 10)),
                    st("baking_casing", 240, n(PLATE, 80), n(BEAM, 20)),
                    st("baking_flues", 200, n(BRICKS, 40), n(PIPE, 40), n(INGOT, 30))));

    /**
     * Сталь и прокат эры I1 реального масштаба (08 §8.6.1, §8.7.2). Металл — оценки из таблиц размеров
     * (docs/research/bof_vessel-dimensions.md, reheating_furnace-dimensions.md), кирпич футеровки и кладки не металл.
     */
    private static final Map<String, Plan> I1_STEEL_PLANS = Map.of(
            // конвертер на 15 т: корпус ⌀3,6 × 4,8 м с цапфовым кольцом ≈ 32 т, опоры с подшипниками ≈ 15 т, редуктор
            // наклона, камин и газоход ≈ 14 т; футеровка 0,6–0,7 м ≈ 40 м³ (оценки)
            "bof_vessel_real", plan("bof_vessel_real", PcBlocks.BOF_VESSEL_REAL, 60,
                    st("foundation", 200, n(CONCRETE, 40)),
                    st("converter_stand", 300, n(BEAM, 100), n(INGOT, 50)),
                    st("vessel", 400, n(PLATE, 220), n(INGOT, 100)),
                    st("lining", 240, n(BRICKS, 40)),
                    st("tilt_drive_hood", 300, n(INGOT, 80), n(PLATE, 40), n(PIPE, 20))),
            // методическая печь 3 × 1,9 × 3 м: кладка ≈ 14 м³, стойки, стяжки, заслонки, толкатель и горелка ≈ 8 т (оценки)
            "reheating_furnace_real", plan("reheating_furnace_real", PcBlocks.REHEATING_FURNACE_REAL, 8,
                    st("foundation", 120, n(CONCRETE, 4)),
                    st("furnace_brickwork", 240, n(BRICKS, 14)),
                    st("furnace_ironwork", 200, n(INGOT, 40), n(BEAM, 20)),
                    st("pusher_burner", 160, n(INGOT, 15), n(PIPE, 5))),
            // ДСП Эру на 6 т: кожух и свод ≈ 16 т, люлька, стойки и рукава электродов ≈ 9 т; футеровка ≈ 15 м³ (оценки)
            "electric_arc_furnace_real", plan("electric_arc_furnace_real", PcBlocks.ELECTRIC_ARC_FURNACE_REAL, 25,
                    st("foundation", 160, n(CONCRETE, 15)),
                    st("vessel", 300, n(PLATE, 120), n(INGOT, 40)),
                    st("lining", 200, n(BRICKS, 15)),
                    st("electrode_gear", 240, n(INGOT, 80), n(PIPE, 10), n(WIRE, 100))),
            // стенд доводки: портал с бункерами ≈ 7 т, тележка ковша с рельсами ≈ 3 т (оценки)
            "ladle_station_real", plan("ladle_station_real", PcBlocks.LADLE_STATION_REAL, 10,
                    st("foundation", 120, n(CONCRETE, 6)),
                    st("station_frame", 240, n(BEAM, 50), n(PLATE, 20)),
                    st("ladle_car", 160, n(INGOT, 25), n(PIPE, 5))),
            // чугунная изложница на слиток ≈ 1,2 т весит ≈ 1,5 т (оценка)
            "ingot_mold_real", plan("ingot_mold_real", PcBlocks.INGOT_MOLD_REAL, 1.5,
                    st("ingot_mold_casting", 100, n(INGOT, 15))),
            // печь с выкатным подом 3 × 4 × 5 м: каркас, кожух, тележка и дверь ≈ 18 т, горелки ≈ 2 т, кладка ≈ 25 м³ (оценки)
            "heat_treatment_furnace_real", plan("heat_treatment_furnace_real", PcBlocks.HEAT_TREATMENT_FURNACE_REAL, 20,
                    st("foundation", 160, n(CONCRETE, 10)),
                    st("furnace_brickwork", 240, n(BRICKS, 25)),
                    st("casing_car", 300, n(PLATE, 100), n(INGOT, 60), n(BEAM, 20)),
                    st("burners", 120, n(PIPE, 20))),
            // стан ТВЧ: формовочные клети ≈ 20 т, сварочный узел с генератором ≈ 10 т (оценки)
            "pipe_welder_real", plan("pipe_welder_real", PcBlocks.PIPE_WELDER_REAL, 30,
                    st("foundation", 160, n(CONCRETE, 10)),
                    st("forming_stands", 300, n(INGOT, 200)),
                    st("weld_box", 240, n(INGOT, 70), n(PLATE, 20), n(WIRE, 100))),
            // прошивной стан с валками ⌀0,9 м: станина, клеть шестерён и шпиндели ≈ 30 т, жёлоб, стержень и столы ≈ 10 т (оценки)
            "piercing_mill_real", plan("piercing_mill_real", PcBlocks.PIERCING_MILL_REAL, 40,
                    st("foundation", 200, n(CONCRETE, 20)),
                    st("mill_housing", 400, n(INGOT, 250), n(PLATE, 50)),
                    st("run_out", 240, n(INGOT, 60), n(BEAM, 40))));

    /**
     * Станочный цех I0 реального масштаба (08 §8.10): станок около 1900 года — чугунная станина и узлы, контрпривод на
     * двух стойках с ремнём ≈ 0,4 т (оценки по каталожным массам станков того времени), фундаментная плита.
     */
    private static final Map<String, Plan> I0_SHOP_PLANS = Map.of(
            "machine_lathe_real", plan("machine_lathe_real", PcBlocks.MACHINE_LATHE_REAL, 2.1,
                    st("foundation", 60, n(CONCRETE, 1)),
                    st("machine_frame", 200, n(INGOT, 15)),
                    st("countershaft", 120, n(INGOT, 4), n(PIPE, 2))),
            "drill_press_real", plan("drill_press_real", PcBlocks.DRILL_PRESS_REAL, 1.3,
                    st("foundation", 60, n(CONCRETE, 1)),
                    st("machine_frame", 200, n(INGOT, 9)),
                    st("countershaft", 120, n(INGOT, 2), n(PIPE, 2))),
            "milling_machine_real", plan("milling_machine_real", PcBlocks.MILLING_MACHINE_REAL, 1.8,
                    st("foundation", 60, n(CONCRETE, 1)),
                    st("machine_frame", 200, n(INGOT, 13)),
                    st("countershaft", 120, n(INGOT, 3), n(PIPE, 2))),
            "shaping_machine_real", plan("shaping_machine_real", PcBlocks.SHAPING_MACHINE_REAL, 1.6,
                    st("foundation", 60, n(CONCRETE, 1)),
                    st("machine_frame", 200, n(INGOT, 12)),
                    st("countershaft", 120, n(INGOT, 2), n(PIPE, 2))),
            "surface_grinder_real", plan("surface_grinder_real", PcBlocks.SURFACE_GRINDER_REAL, 1.3,
                    st("foundation", 60, n(CONCRETE, 1)),
                    st("machine_frame", 200, n(INGOT, 9)),
                    st("countershaft", 120, n(INGOT, 2), n(PIPE, 2))));

    /**
     * Машина старой серии, которую заменяет машина реального масштаба {@code id}, и сколько её блоков она стоит: игроку,
     * приложившему чертёж, постройка засчитывается и как постановка двойника — задания маршрута, главы и инструкции
     * справочника (11 §11.1) ждут блоки старой серии. У реакторов двойников нет.
     */
    public record Twin(java.util.function.Supplier<? extends Block> block, int count) {}

    private static final Map<String, Twin> TWINS = Map.ofEntries(
            Map.entry("lancashire_boiler", new Twin(PcBlocks.FIRE_TUBE_BOILER, 1)),
            Map.entry("lancashire_stoker", new Twin(PcBlocks.GRATE_STOKER, 1)),
            Map.entry("mill_engine", new Twin(PcBlocks.STEAM_ENGINE_CYLINDER, 1)),
            Map.entry("winding_engine", new Twin(PcBlocks.MINE_HOIST, 1)),
            Map.entry("headframe_steel", new Twin(PcBlocks.HEADFRAME, 2)),
            Map.entry("guibal_fan", new Twin(PcBlocks.MAIN_MINE_FAN, 1)),
            Map.entry("mine_drainage_pump_geared", new Twin(PcBlocks.MINE_DRAINAGE_PUMP, 1)),
            Map.entry("cupola_furnace_real", new Twin(PcBlocks.CUPOLA_FURNACE, 1)),
            Map.entry("crucible_furnace_real", new Twin(PcBlocks.CRUCIBLE_FURNACE, 1)),
            Map.entry("coal_jig_real", new Twin(PcBlocks.COAL_JIG, 1)),
            Map.entry("longwall_shearer_real", new Twin(PcBlocks.LONGWALL_SHEARER, 1)),
            Map.entry("powered_roof_support_real", new Twin(PcBlocks.POWERED_ROOF_SUPPORT, 1)),
            Map.entry("machine_lathe_real", new Twin(PcBlocks.MACHINE_LATHE, 1)),
            Map.entry("drill_press_real", new Twin(PcBlocks.DRILL_PRESS, 1)),
            Map.entry("milling_machine_real", new Twin(PcBlocks.MILLING_MACHINE, 1)),
            Map.entry("shaping_machine_real", new Twin(PcBlocks.SHAPING_MACHINE, 1)),
            Map.entry("surface_grinder_real", new Twin(PcBlocks.SURFACE_GRINDER, 1)),
            Map.entry("molding_station_real", new Twin(PcBlocks.MOLDING_STATION, 1)),
            Map.entry("shakeout_grate_real", new Twin(PcBlocks.SHAKEOUT_GRATE, 1)),
            Map.entry("hot_blast_stove_real", new Twin(PcBlocks.HOT_BLAST_STOVE, 1)),
            Map.entry("blast_blower_engine", new Twin(PcBlocks.BLAST_BLOWER, 1)),
            Map.entry("deaerator_real", new Twin(PcBlocks.DEAERATOR, 1)),
            Map.entry("ion_exchange_column_real", new Twin(PcBlocks.ION_EXCHANGE_COLUMN, 1)),
            Map.entry("portal_monitor_real", new Twin(PcBlocks.PORTAL_MONITOR, 2)),
            Map.entry("hand_foot_monitor_real", new Twin(PcBlocks.HAND_FOOT_MONITOR, 1)),
            Map.entry("shaft_generator_real", new Twin(PcBlocks.SHAFT_GENERATOR, 1)),
            Map.entry("electric_motor_mv_real", new Twin(PcBlocks.ELECTRIC_MOTOR, 1)),
            Map.entry("quench_tower_real", new Twin(PcBlocks.QUENCH_TOWER, 1)),
            Map.entry("gas_turbine_heavy_real", new Twin(PcBlocks.GAS_TURBINE, 1)),
            Map.entry("hrsg_real", new Twin(PcBlocks.HRSG, 1)),
            Map.entry("steam_reformer_real", new Twin(PcBlocks.STEAM_REFORMER, 1)),
            Map.entry("ammonia_converter_real", new Twin(PcBlocks.AMMONIA_CONVERTER, 1)),
            Map.entry("methanol_converter_real", new Twin(PcBlocks.METHANOL_CONVERTER, 1)),
            Map.entry("pyrolysis_furnace_real", new Twin(PcBlocks.PYROLYSIS_FURNACE, 1)),
            Map.entry("chlor_alkali_electrolyser_real", new Twin(PcBlocks.CHLOR_ALKALI_CELL, 1)),
            Map.entry("pvc_reactor_real", new Twin(PcBlocks.PVC_REACTOR, 1)),
            Map.entry("bof_vessel_real", new Twin(PcBlocks.BOF_VESSEL, 1)),
            Map.entry("reheating_furnace_real", new Twin(PcBlocks.REHEATING_FURNACE, 1)),
            Map.entry("derrick_segment_real", new Twin(PcBlocks.DERRICK_SEGMENT, 1)),
            Map.entry("rotary_table_real", new Twin(PcBlocks.ROTARY_TABLE, 1)),
            Map.entry("drawworks_real", new Twin(PcBlocks.DRAWWORKS, 1)),
            Map.entry("mud_pump_real", new Twin(PcBlocks.MUD_PUMP, 1)),
            Map.entry("pumpjack_real", new Twin(PcBlocks.PUMPJACK, 1)),
            Map.entry("three_phase_separator_real", new Twin(PcBlocks.THREE_PHASE_SEPARATOR, 1)));

    @Nullable
    public static Twin twin(String id) {
        return TWINS.get(id);
    }

    private SitePlans() {}

    /** Множитель числа предметов на этапах ({@code site.material_scale}); вне сервера с конфигом — 1. */
    public static double scale() {
        try {
            return dev.denfry.promptcritical.mod.config.ServerConfig.SITE_MATERIAL_SCALE.get();
        } catch (IllegalStateException | NullPointerException e) {
            return 1.0;
        }
    }

    /** Литейка I0 реального масштаба (08 §8.9); третья карта — у {@link Map#of} не больше десяти пар. */
    private static final Map<String, Plan> FOUNDRY_PLANS = Map.of(
            // встряхивающе-прессовая машина со столом под опоку ≈ 2 т литья, привод, компрессор и эстакада бункера ≈ 0,5 т (оценки)
            "molding_station_real", plan("molding_station_real", PcBlocks.MOLDING_STATION_REAL, 2.5,
                    st("jolt_squeeze", 120, n(INGOT, 18), n(PLATE, 2)),
                    st("sand_stand", 80, n(INGOT, 3), n(PLATE, 2))),
            // настил 1,5 × 1,6 м с рамой, пружинами и вибратором ≈ 1,2 т, кожух отсоса с воздуховодом ≈ 0,6 т (оценки)
            "shakeout_grate_real", plan("shakeout_grate_real", PcBlocks.SHAKEOUT_GRATE_REAL, 1.8,
                    st("shakeout_deck", 100, n(INGOT, 8), n(PLATE, 4)),
                    st("dust_hood", 60, n(INGOT, 2), n(PLATE, 4))));

    /** Котельная, дегазация и обогащение реального масштаба; своя карта — у {@link Map#of} не больше десяти пар. */
    private static final Map<String, Plan> STEAM_MINE_PLANS = Map.of(
            // насос ВВН-25 на станине ≈ 2,5 т, сепаратор газ-вода с трубопроводами ≈ 1 т (оценки)
            "degasification_pump_real", plan("degasification_pump_real", PcBlocks.DEGASIFICATION_PUMP_REAL, 3.5,
                    st("vacuum_pump", 120, n(INGOT, 20), n(PLATE, 5)),
                    st("separator", 80, n(PLATE, 6), n(PIPE, 8))),
            // ВДН-11,2: улитка ≈ 1,5 т, ротор с валом и опорой ≈ 1 т; фундамент — бетон (оценки)
            "forced_draft_fan_real", plan("forced_draft_fan_real", PcBlocks.FORCED_DRAFT_FAN_REAL, 2.5,
                    st("foundation", 60, n(CONCRETE, 2)),
                    st("fan_casing", 100, n(PLATE, 12), n(INGOT, 3)),
                    st("impeller", 80, n(INGOT, 8), n(PLATE, 2))),
            // ДН-12,5: улитка ≈ 2,2 т, ротор ≈ 1,3 т (оценки)
            "induced_draft_fan_real", plan("induced_draft_fan_real", PcBlocks.INDUCED_DRAFT_FAN_REAL, 3.5,
                    st("foundation", 60, n(CONCRETE, 2)),
                    st("fan_casing", 100, n(PLATE, 18), n(INGOT, 4)),
                    st("impeller", 80, n(INGOT, 10), n(PLATE, 3))),
            // ПБМ-90/250: ванна с рамой ≈ 2,5 т, барабан с магнитной системой и редуктором ≈ 2,5 т (оценки)
            "magnetic_separator_real", plan("magnetic_separator_real", PcBlocks.MAGNETIC_SEPARATOR_REAL, 5,
                    st("separator_tank", 100, n(PLATE, 20), n(INGOT, 5)),
                    st("separator_drum", 120, n(INGOT, 20), n(PLATE, 5))),
            // камера 6,3 м³ ≈ 2,5 т листа, блок аэратора с мостом, двигателем и пеногоном ≈ 1,5 т (оценки)
            "flotation_cell_real", plan("flotation_cell_real", PcBlocks.FLOTATION_CELL_REAL, 4,
                    st("cell_tank", 100, n(PLATE, 25)),
                    st("aerator", 100, n(INGOT, 12), n(PLATE, 3))),
            // корпус ⌀1,5 м на юбке ≈ 6 т нержавеющего листа, вводы, шины и шкаф ≈ 2 т (оценки)
            "electrode_boiler_real", plan("electrode_boiler_real", PcBlocks.ELECTRODE_BOILER_REAL, 8,
                    st("shell", 140, n(PLATE, 50), n(INGOT, 10)),
                    st("hv_terminals", 100, n(INGOT, 10), n(WIRE, 100))));

    /**
     * Цветная металлургия реального масштаба (08 §8.8). Металл — оценки по размерам моделей (листы, отливки, шины);
     * бетон, кирпич и угольные блоки — не металл.
     */
    /**
     * I3 реального масштаба (art/blockbench/i3): массы — оценки по классу прототипа (каталожных масс в листе
     * docs/research/2026-11-24-i3 нет): металл в позициях, огнеупор и бетон сверх него.
     */
    private static final Map<String, Plan> I3_PLANS = Map.of(
            // ГТУ класса V94.2: корпуса компрессора, камеры и турбины, ротор, рама ≈ 234 т (оценка класса)
            "gas_turbine_heavy_real", plan("gas_turbine_heavy_real", PcBlocks.GAS_TURBINE_HEAVY_REAL, 234,
                    st("gt_foundation", 300, n(CONCRETE, 150), n(BEAM, 140)),
                    st("gt_casings", 500, n(INGOT, 1000), n(PLATE, 500)),
                    st("gt_rotor", 400, n(INGOT, 600), n(PIPE, 100))),
            // горизонтальный КУ за ГТУ 150 МВт: каркас, обшивка, трубные пакеты, барабан ≈ 1500 т (оценка)
            "hrsg_real", plan("hrsg_real", PcBlocks.HRSG_REAL, 1500,
                    st("hrsg_steelwork", 400, n(CONCRETE, 600), n(BEAM, 1000)),
                    st("hrsg_casing", 500, n(PLATE, 4000)),
                    st("hrsg_banks", 800, n(PIPE, 8000)),
                    st("hrsg_drum", 400, n(INGOT, 1000), n(PLATE, 1000))),
            // трубчатая печь риформинга 100 т/сут: каркас, обшивка, реакционные трубы, конвекционная шахта ≈ 400 т (оценка)
            "steam_reformer_real", plan("steam_reformer_real", PcBlocks.STEAM_REFORMER_REAL, 400,
                    st("reformer_structure", 300, n(CONCRETE, 200), n(BEAM, 600)),
                    st("reformer_box", 500, n(PLATE, 1200), n(BRICKS, 1500)),
                    st("reformer_tubes", 400, n(PIPE, 800)),
                    st("reformer_convection", 400, n(PIPE, 600), n(PLATE, 800))),
            // колонна синтеза ⌀1,5 × 11 м, многослойный корпус и насадка ≈ 80 т (оценка)
            "ammonia_converter_real", plan("ammonia_converter_real", PcBlocks.AMMONIA_CONVERTER_REAL, 80,
                    st("converter_foundation", 120, n(CONCRETE, 40)),
                    st("converter_shell", 400, n(INGOT, 500), n(PLATE, 200)),
                    st("converter_internals", 200, n(PIPE, 100))),
            // реактор Lurgi ⌀3 м, ≈ 2000 труб, барабан ≈ 150 т (оценка)
            "methanol_converter_real", plan("methanol_converter_real", PcBlocks.METHANOL_CONVERTER_REAL, 150,
                    st("methanol_foundation", 120, n(CONCRETE, 60), n(BEAM, 100)),
                    st("methanol_shell", 300, n(PLATE, 500)),
                    st("methanol_tubes", 400, n(PIPE, 700)),
                    st("methanol_drum", 200, n(PLATE, 200))),
            // печь пиролиза 60 тыс. т/год: каркас, радиантная камера, змеевики, ЗИА и конвекция ≈ 600 т (оценка)
            "pyrolysis_furnace_real", plan("pyrolysis_furnace_real", PcBlocks.PYROLYSIS_FURNACE_REAL, 600,
                    st("pyrolysis_structure", 400, n(CONCRETE, 300), n(BEAM, 1000)),
                    st("pyrolysis_box", 500, n(PLATE, 1500), n(BRICKS, 2000)),
                    st("pyrolysis_coils", 400, n(PIPE, 1000)),
                    st("pyrolysis_convection", 500, n(PIPE, 1500), n(PLATE, 1000))),
            // биполярный электролизёр на 120 элементов: стойка, элементы, шины ≈ 60 т (оценка)
            "chlor_alkali_electrolyser_real", plan("chlor_alkali_electrolyser_real", PcBlocks.CHLOR_ALKALI_ELECTROLYSER_REAL, 60,
                    st("electrolyser_rack", 160, n(BEAM, 150)),
                    st("electrolyser_elements", 300, n(PLATE, 300)),
                    st("electrolyser_busbars", 160, n(INGOT, 150))),
            // реактор ПВХ 30 м³ с рубашкой, приводом мешалки и площадкой обслуживания ≈ 40 т (оценка)
            "pvc_reactor_real", plan("pvc_reactor_real", PcBlocks.PVC_REACTOR_REAL, 40,
                    st("pvc_floor", 160, n(CONCRETE, 20), n(BEAM, 100)),
                    st("pvc_vessel", 300, n(PLATE, 250)),
                    st("pvc_drive", 160, n(INGOT, 50))));

    private static final Map<String, Plan> NONFERROUS_PLANS = Map.of(
            // бак 4,6 × 1,15 × 1,3 м ≈ 4 м³ бетона, свинцовая футеровка ≈ 1,5 т, контактные шины и штанги ≈ 1,5 т меди (оценки)
            "electrorefining_cell_real", plan("electrorefining_cell_real", PcBlocks.ELECTROREFINING_CELL_REAL, 3,
                    st("refining_tank", 160, n(CONCRETE, 4), n(PLATE, 14)),
                    st("contact_bars", 120, n(WIRE, 60), n(INGOT, 8))),
            // бочка ⌀4 × 8,5 м из листа 30–40 мм ≈ 65 т, бандажи ≈ 25 т, роликоопоры, венец и привод ≈ 20 т; магнезитовая
            // футеровка ≈ 45 м³ (оценки)
            "peirce_smith_converter_real", plan("peirce_smith_converter_real", PcBlocks.PEIRCE_SMITH_CONVERTER_REAL, 110,
                    st("foundation", 200, n(CONCRETE, 24)),
                    st("roller_stands", 240, n(INGOT, 220), n(BEAM, 60)),
                    st("shell", 400, n(PLATE, 600), n(INGOT, 120)),
                    st("lining", 240, n(BRICKS, 45)),
                    st("tuyeres_drive", 300, n(INGOT, 80), n(PIPE, 40))),
            // кожух 8 × 3,5 × 1,25 м ≈ 25 т, катодные стержни ≈ 5 т, анодная ошиновка ≈ 5 т алюминия, портальная надстройка
            // ≈ 10 т; угольные подовые блоки и футеровка (оценки)
            "reduction_pot_real", plan("reduction_pot_real", PcBlocks.REDUCTION_POT_REAL, 45,
                    st("shell", 300, n(PLATE, 220), n(INGOT, 50)),
                    st("cathode_lining", 240, n(BRICKS, 20), n(GRAPHITE, 24)),
                    st("busbars", 240, n(WIRE, 300), n(INGOT, 50)),
                    st("anode_superstructure", 300, n(BEAM, 60), n(INGOT, 70))),
            // автоклав ⌀2,5 × 9 м из листа 30 мм ≈ 17 т, днища ≈ 3 т, площадка, лестница и обвязка ≈ 2 т (оценки)
            "bayer_digester_real", plan("bayer_digester_real", PcBlocks.BAYER_DIGESTER_REAL, 22,
                    st("foundation", 120, n(CONCRETE, 4)),
                    st("vessel", 320, n(PLATE, 180), n(INGOT, 20)),
                    st("piping", 160, n(PIPE, 20), n(INGOT, 10))),
            // бак ⌀6 × 9 м из листа 12 мм ≈ 16 т, днище ≈ 3 т, мост с мешалкой и приводом ≈ 8 т (оценки)
            "alumina_precipitator_real", plan("alumina_precipitator_real", PcBlocks.ALUMINA_PRECIPITATOR_REAL, 27,
                    st("ring_foundation", 160, n(CONCRETE, 30)),
                    st("tank", 320, n(PLATE, 190), n(INGOT, 10)),
                    st("agitator", 200, n(BEAM, 30), n(INGOT, 40))),
            // печь 16 × 6 м: кладка подины и стен ≈ 160 м³, свод ≈ 90 м³; стойки каркаса, стяжки, пяты свода, горелки,
            // загрузочные трубы и газоход ≈ 40 т (оценки)
            "matte_smelter_real", plan("matte_smelter_real", PcBlocks.MATTE_SMELTER_REAL, 40,
                    st("foundation", 200, n(CONCRETE, 60)),
                    st("hearth_walls", 300, n(BRICKS, 160)),
                    st("sprung_arch", 300, n(BRICKS, 90)),
                    st("buckstays", 240, n(BEAM, 200), n(INGOT, 120)),
                    st("burners_flue", 200, n(PIPE, 40), n(INGOT, 60))));

    @Nullable
    /** Кузница и машиностроение реального масштаба (18 §18.6–18.7); у {@link Map#of} не больше десяти пар. */
    /** Сборка машин из деталей (18 §18.8.2): плита 3 × 3 м ≈ 2,5 т, портал с талью и опоры ≈ 0,5 т (оценки). */
    private static final Map<String, Plan> ASSEMBLY_PLANS = Map.of(
            "assembly_stand", plan("assembly_stand", PcBlocks.ASSEMBLY_STAND, 3.0,
                    st("assembly_plate", 120, n(INGOT, 20), n(CONCRETE, 2)),
                    st("assembly_hoist", 80, n(PLATE, 3), n(BEAM, 2))));

    /**
     * Буровая и промысел I2 реального масштаба (09 §9.5, §9.6): массы прототипов — по каталогам (ротор Р-560 5,9 т,
     * насос У8-6М 25,5 т, качалка СК-8 11,6 т) или оценкам по габаритам (секция вышки, лебёдка, сепаратор).
     */
    private static final Map<String, Plan> OIL_RIG_PLANS = Map.of(
            // секция четырёхногой вышки 3 м: ноги и пояса из уголка, раскосы ≈ 2,2 т (оценка по вышке ВМ-41, 30 т)
            "derrick_segment_real", plan("derrick_segment_real", PcBlocks.DERRICK_SEGMENT_REAL, 2.2,
                    st("rig_derrick_legs", 100, n(BEAM, 16), n(PLATE, 6))),
            // ротор Р-560: литой корпус, стол с коническим венцом, вкладыши ≈ 5,9 т (каталог Уралмаша)
            "rotary_table_real", plan("rotary_table_real", PcBlocks.ROTARY_TABLE_REAL, 5.9,
                    st("rig_rotary_housing", 120, n(INGOT, 40), n(PLATE, 4)),
                    st("rig_rotary_table", 100, n(INGOT, 15))),
            // лебёдка У2-5-5: барабан с тормозами, цепная трансмиссия, гидротормоз, рама ≈ 25 т (оценка)
            "drawworks_real", plan("drawworks_real", PcBlocks.DRAWWORKS_REAL, 25,
                    st("rig_drawworks_skid", 120, n(BEAM, 40), n(CONCRETE, 4)),
                    st("rig_drawworks_drum", 200, n(INGOT, 120), n(PLATE, 30)),
                    st("rig_drawworks_drive", 160, n(INGOT, 60))),
            // насос У8-6М: приводная часть с коленчатым валом, гидравлическая коробка, рама ≈ 25,5 т (каталог)
            "mud_pump_real", plan("mud_pump_real", PcBlocks.MUD_PUMP_REAL, 25.5,
                    st("rig_pump_power_end", 200, n(INGOT, 140)),
                    st("rig_pump_fluid_end", 160, n(INGOT, 80), n(BEAM, 35))),
            // качалка СК-8: рама и стойка, редуктор с кривошипами и противовесами, балансир с головкой ≈ 11,6 т (ГОСТ 5866)
            "pumpjack_real", plan("pumpjack_real", PcBlocks.PUMPJACK_REAL, 11.6,
                    st("pumpjack_frame", 120, n(BEAM, 40), n(CONCRETE, 4)),
                    st("pumpjack_gearbox", 160, n(INGOT, 56)),
                    st("pumpjack_beam", 120, n(PLATE, 20))),
            // НГС-1,6-2000: обечайка ⌀2 м и днища ≈ 7,6 т листа, внутренние устройства, опоры и арматура ≈ 3 т (оценка)
            "three_phase_separator_real", plan("three_phase_separator_real", PcBlocks.THREE_PHASE_SEPARATOR_REAL, 10.5,
                    st("separator_shell", 160, n(PLATE, 80), n(CONCRETE, 4)),
                    st("separator_internals", 120, n(INGOT, 15), n(BEAM, 10))),
            // НМ 1250-260 с двигателем 1250 кВт на общей раме: агрегат 10,95 т, насос 2,81 т (ГОСТ 12124-87, каталоги)
            "mainline_pump_real", plan("mainline_pump_real", PcBlocks.MAINLINE_PUMP_REAL, 10.95,
                    st("mainline_pump_frame", 100, n(BEAM, 20), n(CONCRETE, 4)),
                    st("mainline_pump_casing", 160, n(INGOT, 28)),
                    st("mainline_pump_motor", 160, n(INGOT, 60))),
            // ПП-0,63: обечайка с днищами и жаровой трубой, змеевики, рама, горелка и труба — 12,5–13 т порожний (каталоги)
            "line_heater_real", plan("line_heater_real", PcBlocks.LINE_HEATER_REAL, 12.8,
                    st("line_heater_skid", 100, n(BEAM, 20), n(CONCRETE, 4)),
                    st("line_heater_shell", 160, n(PLATE, 70)),
                    st("line_heater_coils", 140, n(INGOT, 38))),
            // РВС-1000: стенка из восьми поясов, днище, коническая крыша, лестница и площадки ≈ 30 т стали (оценка листа)
            "oil_tank_real", plan("oil_tank_real", PcBlocks.OIL_TANK_REAL, 30,
                    st("tank_foundation", 160, n(CONCRETE, 20)),
                    st("tank_shell", 300, n(PLATE, 220)),
                    st("tank_roof", 160, n(PLATE, 50), n(BEAM, 30))),
            // то же с плавающей крышей: понтон с затвором и катучая лестница вместо конической крыши ≈ 33 т (оценка)
            "oil_tank_floating_real", plan("oil_tank_floating_real", PcBlocks.OIL_TANK_FLOATING_REAL, 33,
                    st("tank_foundation", 160, n(CONCRETE, 20)),
                    st("tank_shell", 300, n(PLATE, 220)),
                    st("tank_pontoon", 200, n(PLATE, 80), n(BEAM, 30))));

    private static final Map<String, Plan> SMITHY_PLANS = Map.of(
            // молот 2 cwt: колонна с цилиндрами ≈ 3,5 т, шабот ≈ 0,7 т (каталоги Massey; оценки)
            "power_hammer_real", plan("power_hammer_real", PcBlocks.POWER_HAMMER_REAL, 4.2,
                    st("hammer_frame", 160, n(INGOT, 30), n(PLATE, 3)),
                    st("hammer_anvil", 80, n(INGOT, 8), n(CONCRETE, 2))),
            // глиняная шахта 1,7 м на каменном основании, пара мехов; железо — фурма, обручи и сопла ≈ 0,1 т (оценки)
            "bloomery_real", plan("bloomery_real", PcBlocks.BLOOMERY_REAL, 0.1,
                    st("bloomery_shaft", 120, n(BRICKS, 12)),
                    st("bloomery_bellows", 60, n(() -> Items.IRON_INGOT, 1))),
            // кирпичный горн с трубой 3 м, железный колпак и большие мехи (Holmström; оценки)
            "forge_hearth_real", plan("forge_hearth_real", PcBlocks.FORGE_HEARTH_REAL, 0.3,
                    st("hearth_chimney", 140, n(BRICKS, 24), n(INGOT, 1)),
                    st("hearth_bellows", 80, n(PLATE, 2))),
            // пресс 60 т: С-станина ≈ 5 т, кривошипный вал, маховик и привод ≈ 1 т (каталоги Bliss; оценки)
            "crank_press_real", plan("crank_press_real", PcBlocks.CRANK_PRESS_REAL, 6.0,
                    st("crank_press_frame", 180, n(INGOT, 46), n(PLATE, 4)),
                    st("press_drive", 100, n(INGOT, 8), n(PLATE, 2))),
            // волочильный стан на одну волоку: станина 2 м и барабан 0,6 м (Bernhard; оценки)
            "wire_mill_real", plan("wire_mill_real", PcBlocks.WIRE_MILL_REAL, 0.8,
                    st("wire_bench", 80, n(INGOT, 4)),
                    st("wire_drum", 80, n(INGOT, 4))),
            // клети с валками ⌀0,35 м ≈ 6 т, шестерённая клеть и рольганги ≈ 2 т (Roberts; оценки)
            "sheet_mill_real", plan("sheet_mill_real", PcBlocks.SHEET_MILL_REAL, 8.0,
                    st("sheet_stand", 200, n(INGOT, 56), n(PLATE, 4)),
                    st("sheet_tables", 100, n(INGOT, 10), n(PLATE, 10))),
            // быстроходный пазовырубной пресс с делительным столом ≈ 3 т (оценки)
            "notching_press_real", plan("notching_press_real", PcBlocks.NOTCHING_PRESS_REAL, 3.0,
                    st("notch_frame", 140, n(INGOT, 24), n(PLATE, 2)),
                    st("notch_index", 80, n(INGOT, 4), n(PLATE, 1))),
            // станина 2 м с бабкой, планшайбой и катушечной стойкой ≈ 1,2 т (оценки)
            "winding_machine_real", plan("winding_machine_real", PcBlocks.WINDING_MACHINE_REAL, 1.2,
                    st("winding_bed", 100, n(INGOT, 10)),
                    st("winding_spool", 60, n(INGOT, 2), n(WIRE, 4))),
            // кирпичная камера 1,6 × 1,2 м с топкой, дверью и железной трубой 4 м (оценки)
            "heating_furnace_real", plan("heating_furnace_real", PcBlocks.HEATING_FURNACE_REAL, 1.0,
                    st("heating_chamber", 160, n(BRICKS, 30), n(CONCRETE, 2)),
                    st("heating_iron", 100, n(INGOT, 6), n(PLATE, 6))),
            // муфельная печь и закалочный бак (оценки)
            "hardening_furnace_real", plan("hardening_furnace_real", PcBlocks.HARDENING_FURNACE_REAL, 0.8,
                    st("hardening_muffle", 120, n(BRICKS, 18), n(INGOT, 3)),
                    st("quench_tank", 60, n(PLATE, 6), n(INGOT, 1))));

    /**
     * Радзащита реального масштаба (docs/research/2026-11-24-radprot): стена горячей камеры — плитки магнетитового
     * бетона (по 3,6 т), модуль хранения — бетон; металл — сталь и чугун.
     */
    private static final Map<String, Plan> RADPROT_PLANS = Map.of(
            // две колонны со сцинтилляторами, перекладина с табло ≈ 0,3 т, кабели и электроника ≈ 0,1 т (TPM-903B 40 кг — переносной; оценки)
            "portal_monitor_real", plan("portal_monitor_real", PcBlocks.PORTAL_MONITOR_REAL, 0.4,
                    st("detector_columns", 80, n(PLATE, 3), n(WIRE, 10))),
            // HandFoot-Fibre 57–63 кг (SPC-284), округлено до листа
            "hand_foot_monitor_real", plan("hand_foot_monitor_real", PcBlocks.HAND_FOOT_MONITOR_REAL, 0.1,
                    st("monitor_body", 60, n(PLATE, 1))),
            // стена 5 × 4 × 1 м магнетитового бетона = 20 м³ (72 т); облицовка камеры, корпус окна, шлюз и два
            // манипулятора ≈ 4,5 т стали (оценки)
            "hot_cell_real", plan("hot_cell_real", PcBlocks.HOT_CELL_REAL, 4.5,
                    st("shield_wall", 300, n(HEAVY_CONCRETE, 20), n(PLATE, 20)),
                    st("window_and_manipulators", 240, n(PLATE, 10), n(INGOT, 15))),
            // CASTOR V/19: ≈ 108 т порожний, корпус из чугуна с шаровидным графитом, крышки стальные (GNS)
            "spent_fuel_cask_real", plan("spent_fuel_cask_real", PcBlocks.SPENT_FUEL_CASK_REAL, 108,
                    st("cast_body", 400, n(INGOT, 1000)),
                    st("lids", 160, n(PLATE, 80))),
            // тележка 160 т грузоподъёмности: рама и ложементы ≈ 35 т, тележки с колёсными парами и привод ≈ 5 т (оценки)
            "cask_transport_cart_real", plan("cask_transport_cart_real", PcBlocks.CASK_TRANSPORT_CART_REAL, 40,
                    st("cart_frame", 240, n(BEAM, 200), n(PLATE, 150)),
                    st("cart_bogies", 160, n(INGOT, 45), n(WIRE, 50))),
            // NUHOMS: ≈ 63 м³ железобетона (≈ 150 т), арматура ≈ 8 т, дверь и тепловые экраны ≈ 6 т (оценки)
            "dry_storage_module_real", plan("dry_storage_module_real", PcBlocks.DRY_STORAGE_MODULE_REAL, 14,
                    st("module_body", 400, n(CONCRETE, 63), n(INGOT, 80)),
                    st("module_door", 160, n(PLATE, 60))));

    /**
     * Топливный цикл реального масштаба (fuelcycle-dimensions; массы — оценки по классу оборудования, металл без
     * футеровки и бетона).
     */
    private static final Map<String, Plan> FUELCYCLE_PLANS = Map.of(
            // корпус ∅0,25 × 1,8 м с приводом и опорной плитой ≈ 0,1 т
            "gas_centrifuge_real", plan("gas_centrifuge_real", PcBlocks.GAS_CENTRIFUGE_REAL, 0.1,
                    st("centrifuge_casing", 40, n(PLATE, 1))),
            // 16 машин по 0,1 т, коллекторы, рама и кабельный лоток ≈ 0,6 т
            "centrifuge_cascade_section_real", plan("centrifuge_cascade_section_real", PcBlocks.CENTRIFUGE_CASCADE_SECTION_REAL, 2.2,
                    st("cascade_machines", 200, n(PLATE, 16)),
                    st("cascade_headers", 120, n(PIPE, 6), n(BEAM, 3))),
            // башня ∅0,6 × 4,3 м, бункер, фильтр, каркас с площадкой ≈ 6 т
            "fluorinator_real", plan("fluorinator_real", PcBlocks.FLUORINATOR_REAL, 6,
                    st("fluorinator_tower", 240, n(PLATE, 30)),
                    st("fluorinator_frame", 160, n(BEAM, 25), n(PIPE, 10))),
            // сосуд давления ∅1,6 × 4 м с дверью и кольцом затвора ≈ 12 т
            "uf6_autoclave_real", plan("uf6_autoclave_real", PcBlocks.UF6_AUTOCLAVE_REAL, 12,
                    st("autoclave_shell", 300, n(PLATE, 90)),
                    st("autoclave_door", 160, n(INGOT, 30))),
            // станина, карусель на 16 позиций, головка с роликами ≈ 8 т
            "pellet_press_real", plan("pellet_press_real", PcBlocks.PELLET_PRESS_REAL, 8,
                    st("press_base", 240, n(INGOT, 60)),
                    st("press_turret", 200, n(PLATE, 20))),
            // кожух 14 м, муфели, водоохлаждаемая зона, толкатель и двери ≈ 25 т металла
            "sintering_furnace_real", plan("sintering_furnace_real", PcBlocks.SINTERING_FURNACE_REAL, 25,
                    st("furnace_shell", 400, n(PLATE, 150)),
                    st("furnace_pusher", 200, n(BEAM, 80), n(PIPE, 40))),
            // рольганг, камера с окном, баллон и шкаф ≈ 3 т
            "fuel_rod_welder_real", plan("fuel_rod_welder_real", PcBlocks.FUEL_ROD_WELDER_REAL, 3,
                    st("welder_table", 160, n(BEAM, 15)),
                    st("weld_chamber", 160, n(PLATE, 10), n(WIRE, 50))),
            // основание с поворотным гнездом, две колонны 4,5 м, балка с талью ≈ 5 т
            "fuel_assembly_stand_real", plan("fuel_assembly_stand_real", PcBlocks.FUEL_ASSEMBLY_STAND_REAL, 5,
                    st("stand_columns", 240, n(BEAM, 35)),
                    st("stand_hoist", 160, n(PLATE, 15))));

    public static Plan get(String id) {
        Plan p = PLANS.get(id);
        if (p == null) p = PROTOTYPE_PLANS.get(id);
        if (p == null) p = I0_PLANS.get(id);
        if (p == null) p = I3_PLANS.get(id);
        if (p == null) p = IRONMAKING_PLANS.get(id);
        if (p == null) p = POWER_PLANS.get(id);
        if (p == null) p = MINING_COKE_PLANS.get(id);
        if (p == null) p = CARBON_PLANS.get(id);
        if (p == null) p = I1_STEEL_PLANS.get(id);
        if (p == null) p = FOUNDRY_PLANS.get(id);
        if (p == null) p = I0_SHOP_PLANS.get(id);
        if (p == null) p = STEAM_MINE_PLANS.get(id);
        if (p == null) p = SMITHY_PLANS.get(id);
        if (p == null) p = ASSEMBLY_PLANS.get(id);
        if (p == null) p = RADPROT_PLANS.get(id);
        if (p == null) p = FUELCYCLE_PLANS.get(id);
        if (p == null) p = OIL_RIG_PLANS.get(id);
        if (p == null) p = H2_PLANS.get(id);
        return p != null ? p : NONFERROUS_PLANS.get(id);
    }

    public static Iterable<Plan> all() {
        return java.util.stream.Stream.of(PLANS, PROTOTYPE_PLANS, I3_PLANS, I0_PLANS, IRONMAKING_PLANS, POWER_PLANS, MINING_COKE_PLANS, I1_STEEL_PLANS, FOUNDRY_PLANS, I0_SHOP_PLANS,
                        STEAM_MINE_PLANS, CARBON_PLANS, SMITHY_PLANS, ASSEMBLY_PLANS, NONFERROUS_PLANS, RADPROT_PLANS,
                        FUELCYCLE_PLANS, OIL_RIG_PLANS, H2_PLANS)
                .flatMap(m -> m.values().stream()).toList();
    }
}
