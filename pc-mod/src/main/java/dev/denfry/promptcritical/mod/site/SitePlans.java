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

    private SitePlans() {}

    /** Множитель числа предметов на этапах ({@code site.material_scale}); вне сервера с конфигом — 1. */
    public static double scale() {
        try {
            return dev.denfry.promptcritical.mod.config.ServerConfig.SITE_MATERIAL_SCALE.get();
        } catch (IllegalStateException | NullPointerException e) {
            return 1.0;
        }
    }

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
    public static Plan get(String id) {
        Plan p = PLANS.get(id);
        if (p == null) p = I0_PLANS.get(id);
        return p != null ? p : STEAM_MINE_PLANS.get(id);
    }

    public static Iterable<Plan> all() {
        return java.util.stream.Stream.of(PLANS, I0_PLANS, STEAM_MINE_PLANS).flatMap(m -> m.values().stream()).toList();
    }
}
