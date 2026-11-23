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
                    if (it == Items.GRAY_CONCRETE || it == Items.BRICKS || it == PcBlocks.MODERATOR_BLOCK_GRAPHITE.get().asItem()) continue;
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
    private static final Supplier<Item> GRAPHITE = () -> PcBlocks.MODERATOR_BLOCK_GRAPHITE.get().asItem();
    private static final Supplier<? extends Item> INGOT = PcItems.STEEL_INGOT, PLATE = PcItems.STEEL_PLATE, BEAM = PcItems.STEEL_BEAM,
            PIPE = PcItems.SEAMLESS_PIPE, WIRE = PcItems.COPPER_WIRE, ZR = PcItems.ZIRCALOY_INGOT;

    private static Stage st(String key, int ticks, Need... needs) {
        return new Stage(key, List.of(needs), ticks);
    }

    private static Plan plan(String id, Supplier<? extends Block> unit, double metalT, Stage... stages) {
        return new Plan(id, unit, metalT * 1000, List.of(stages));
    }

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
    private static final Map<String, Plan> IRONMAKING_PLANS = Map.of(
            // каупер ⌀4,5 × 14 м: клёпаная обечайка с куполом ≈ 25 т листа, клапаны, площадка и лестница ≈ 8 т (оценки,
            // docs/research/hot_blast_stove-dimensions.md); насадка 60 т шамота — как в симуляции
            "hot_blast_stove_real", plan("hot_blast_stove_real", PcBlocks.HOT_BLAST_STOVE_REAL, 33,
                    st("foundation", 120, n(CONCRETE, 6)),
                    st("stove_shell", 320, n(PLATE, 250)),
                    st("checker", 240, n(BRICKS, 32)),
                    st("stove_valves", 200, n(INGOT, 60), n(PIPE, 40))),
            // горизонтальная воздуходувная машина: станина, воздушный цилиндр ⌀1,2 м и параллели ≈ 22 т литья, коленвал
            // с маховиком ≈ 8 т (оценки, docs/research/blast_blower-dimensions.md), бетонный фундамент с ямой маховика
            "blast_blower_engine", plan("blast_blower_engine", PcBlocks.BLAST_BLOWER_ENGINE, 30,
                    st("foundation", 100, n(CONCRETE, 6)),
                    st("blowing_tub", 280, n(INGOT, 200), n(PLATE, 20)),
                    st("blower_flywheel", 200, n(INGOT, 80))));

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

    @Nullable
    /** Кузница и машиностроение реального масштаба (18 §18.6–18.7); у {@link Map#of} не больше десяти пар. */
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
                    st("press_frame", 180, n(INGOT, 46), n(PLATE, 4)),
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

    public static Plan get(String id) {
        Plan p = PLANS.get(id);
        if (p == null) p = I0_PLANS.get(id);
        if (p == null) p = IRONMAKING_PLANS.get(id);
        if (p == null) p = POWER_PLANS.get(id);
        if (p == null) p = MINING_COKE_PLANS.get(id);
        if (p == null) p = CARBON_PLANS.get(id);
        if (p == null) p = I1_STEEL_PLANS.get(id);
        if (p == null) p = FOUNDRY_PLANS.get(id);
        if (p == null) p = I0_SHOP_PLANS.get(id);
        if (p == null) p = STEAM_MINE_PLANS.get(id);
        return p != null ? p : SMITHY_PLANS.get(id);
    }

    public static Iterable<Plan> all() {
        return java.util.stream.Stream.of(PLANS, I0_PLANS, IRONMAKING_PLANS, POWER_PLANS, MINING_COKE_PLANS, I1_STEEL_PLANS, FOUNDRY_PLANS, I0_SHOP_PLANS,
                        STEAM_MINE_PLANS, CARBON_PLANS, SMITHY_PLANS)
                .flatMap(m -> m.values().stream()).toList();
    }
}
