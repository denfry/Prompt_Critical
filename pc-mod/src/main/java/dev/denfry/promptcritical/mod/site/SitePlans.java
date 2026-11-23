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
                    st("fan_engine", 300, n(INGOT, 140), n(PIPE, 20), n(BRICKS, 30))));

    private SitePlans() {}

    /** Множитель числа предметов на этапах ({@code site.material_scale}); вне сервера с конфигом — 1. */
    public static double scale() {
        try {
            return dev.denfry.promptcritical.mod.config.ServerConfig.SITE_MATERIAL_SCALE.get();
        } catch (IllegalStateException | NullPointerException e) {
            return 1.0;
        }
    }

    @Nullable
    public static Plan get(String id) {
        Plan p = PLANS.get(id);
        return p != null ? p : I0_PLANS.get(id);
    }

    public static Iterable<Plan> all() {
        return java.util.stream.Stream.concat(PLANS.values().stream(), I0_PLANS.values().stream()).toList();
    }
}
