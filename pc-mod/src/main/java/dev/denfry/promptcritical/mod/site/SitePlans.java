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
 * её блок и этапы монтажа с перечнем материалов и временем. Количества — оценка пилота [BALANCE].
 */
public final class SitePlans {
    /** Позиция перечня: предмет и сколько нужно. */
    public record Need(Supplier<? extends Item> item, int count) {}

    /** Этап монтажа: ключ текста, перечень, время монтажа, тиков. */
    public record Stage(String key, List<Need> needs, int erectTicks) {}

    /** Проект: что ставится по окончании и этапы по порядку. */
    public record Plan(String id, Supplier<? extends Block> unit, List<Stage> stages) {}

    private static Need n(Supplier<? extends Item> item, int count) {
        return new Need(item, count);
    }

    private static final Supplier<Item> CONCRETE = () -> Items.GRAY_CONCRETE;
    private static final Supplier<Item> COPPER = () -> Items.COPPER_INGOT;

    private static final Map<String, Plan> PLANS = Map.ofEntries(
            Map.entry("bwr240_reactor", new Plan("bwr240_reactor", PcBlocks.BWR240_REACTOR, List.of(
                    new Stage("pedestal", List.of(n(CONCRETE, 48), n(PcItems.STEEL_BEAM, 8)), 200),
                    new Stage("drives", List.of(n(PcItems.SEAMLESS_PIPE, 16), n(PcItems.STEEL_INGOT, 16), n(COPPER, 8)), 240),
                    new Stage("vessel", List.of(n(PcItems.STEEL_PLATE, 32), n(PcItems.STEEL_BEAM, 4)), 360),
                    new Stage("piping", List.of(n(PcItems.SEAMLESS_PIPE, 16)), 200)))),
            Map.entry("candu6_reactor", new Plan("candu6_reactor", PcBlocks.CANDU6_REACTOR, List.of(
                    new Stage("vault", List.of(n(CONCRETE, 128), n(PcItems.STEEL_BEAM, 24)), 300),
                    new Stage("calandria", List.of(n(PcItems.STEEL_PLATE, 48), n(PcItems.ZIRCALOY_INGOT, 16)), 400),
                    new Stage("end_fittings", List.of(n(PcItems.SEAMLESS_PIPE, 48), n(PcItems.STEEL_INGOT, 16)), 360),
                    new Stage("fuelling", List.of(n(PcItems.STEEL_BEAM, 12), n(COPPER, 16), n(PcItems.STEEL_PLATE, 8)), 240)))),
            Map.entry("bn600_reactor", new Plan("bn600_reactor", PcBlocks.BN600_REACTOR, List.of(
                    new Stage("shaft", List.of(n(CONCRETE, 160), n(PcItems.STEEL_BEAM, 24)), 360),
                    new Stage("vessel", List.of(n(PcItems.STEEL_PLATE, 64), n(PcItems.STEEL_BEAM, 8)), 480),
                    new Stage("pool_equipment", List.of(n(PcItems.SEAMLESS_PIPE, 48), n(PcItems.STEEL_INGOT, 24), n(COPPER, 24)), 400),
                    new Stage("roof", List.of(n(PcItems.STEEL_PLATE, 32), n(PcItems.STEEL_INGOT, 16), n(COPPER, 8)), 300)))),
            Map.entry("vver_reactor", new Plan("vver_reactor", PcBlocks.VVER_REACTOR, List.of(
                    new Stage("foundation", List.of(n(CONCRETE, 64), n(PcItems.STEEL_BEAM, 16)), 200),
                    new Stage("vessel", List.of(n(PcItems.STEEL_PLATE, 48), n(PcItems.STEEL_BEAM, 8)), 400),
                    new Stage("upper_unit", List.of(n(PcItems.STEEL_PLATE, 16), n(PcItems.SEAMLESS_PIPE, 16), n(COPPER, 16)), 300),
                    new Stage("piping", List.of(n(PcItems.SEAMLESS_PIPE, 24)), 200)))),
            Map.entry("vver_steam_generator", new Plan("vver_steam_generator", PcBlocks.VVER_STEAM_GENERATOR, List.of(
                    new Stage("foundation", List.of(n(CONCRETE, 24), n(PcItems.STEEL_BEAM, 8)), 160),
                    new Stage("shell", List.of(n(PcItems.STEEL_PLATE, 32)), 300),
                    new Stage("tubes", List.of(n(PcItems.SEAMLESS_PIPE, 32)), 300)))),
            Map.entry("vver_coolant_pump", new Plan("vver_coolant_pump", PcBlocks.VVER_COOLANT_PUMP, List.of(
                    new Stage("foundation", List.of(n(CONCRETE, 16), n(PcItems.STEEL_BEAM, 4)), 120),
                    new Stage("casing", List.of(n(PcItems.STEEL_PLATE, 16), n(PcItems.STEEL_INGOT, 8)), 240),
                    new Stage("drive", List.of(n(COPPER, 24), n(PcItems.STEEL_INGOT, 8)), 200)))),
            Map.entry("vver_pressurizer", new Plan("vver_pressurizer", PcBlocks.VVER_PRESSURIZER, List.of(
                    new Stage("foundation", List.of(n(CONCRETE, 16), n(PcItems.STEEL_BEAM, 4)), 120),
                    new Stage("vessel", List.of(n(PcItems.STEEL_PLATE, 24), n(COPPER, 8)), 300)))));

    private SitePlans() {}

    @Nullable
    public static Plan get(String id) {
        return PLANS.get(id);
    }

    public static Iterable<Plan> all() {
        return PLANS.values();
    }
}
