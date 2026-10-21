package dev.denfry.promptcritical.mod.automation;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.automation.AutomationKind;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Типы BE автоматики A1–A2 и пульта (03d §3d.3.1, §3d.4.1, 03c §3c.2): блок → вид двойника, со щитом или без. */
public final class AutomationBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    /** Блок: вид двойника и щит настроек. */
    private record Entry(AutomationKind kind, boolean panel,
            DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> type) {
    }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> RELAY =
            entry("relay", AutomationKind.RELAY, true, PcBlocks.RELAY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> RELAY_LATCHING =
            entry("relay_latching", AutomationKind.RELAY_LATCHING, false, PcBlocks.RELAY_LATCHING);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> TIME_RELAY =
            entry("time_relay", AutomationKind.TIME_RELAY, true, PcBlocks.TIME_RELAY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> STEPPING_SWITCH =
            entry("stepping_switch", AutomationKind.STEPPING_SWITCH, true, PcBlocks.STEPPING_SWITCH);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> CAM_CONTROLLER =
            entry("cam_controller", AutomationKind.CAM_CONTROLLER, true, PcBlocks.CAM_CONTROLLER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> PROCESS_SWITCH =
            entry("process_switch", AutomationKind.PROCESS_SWITCH, true, PcBlocks.PROCESS_SWITCH);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerSupplyBlockEntity>> RELAY_POWER_SUPPLY =
            BLOCK_ENTITIES.register("relay_power_supply", () -> BlockEntityType.Builder.of(PowerSupplyBlockEntity::new,
                    PcBlocks.RELAY_POWER_SUPPLY.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RelayRackBlockEntity>> RELAY_RACK =
            BLOCK_ENTITIES.register("relay_rack", () -> BlockEntityType.Builder.of(RelayRackBlockEntity::new,
                    PcBlocks.RELAY_RACK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> REDSTONE_INTERFACE =
            entry("redstone_interface", AutomationKind.REDSTONE_INTERFACE, false, PcBlocks.REDSTONE_INTERFACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> PUSHBUTTON =
            entry("pushbutton", AutomationKind.PUSHBUTTON, false, PcBlocks.PUSHBUTTON);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> SELECTOR_SWITCH =
            entry("selector_switch", AutomationKind.SELECTOR_SWITCH, false, PcBlocks.SELECTOR_SWITCH);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> KEY_SWITCH =
            entry("key_switch", AutomationKind.KEY_SWITCH, false, PcBlocks.KEY_SWITCH);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> INDICATOR_LAMP =
            entry("indicator_lamp", AutomationKind.INDICATOR_LAMP, false, PcBlocks.INDICATOR_LAMP);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> GAS_DETECTOR =
            entry("gas_detector", AutomationKind.GAS_DETECTOR, true, PcBlocks.GAS_DETECTOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> ANALOG_SUMMER =
            entry("analog_summer", AutomationKind.ANALOG_SUMMER, true, PcBlocks.ANALOG_SUMMER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> ANALOG_INTEGRATOR =
            entry("analog_integrator", AutomationKind.ANALOG_INTEGRATOR, true, PcBlocks.ANALOG_INTEGRATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> ANALOG_DIFFERENTIATOR =
            entry("analog_differentiator", AutomationKind.ANALOG_DIFFERENTIATOR, true, PcBlocks.ANALOG_DIFFERENTIATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> ANALOG_MULTIPLIER =
            entry("analog_multiplier", AutomationKind.ANALOG_MULTIPLIER, true, PcBlocks.ANALOG_MULTIPLIER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> ANALOG_FUNCTION =
            entry("analog_function", AutomationKind.ANALOG_FUNCTION, true, PcBlocks.ANALOG_FUNCTION);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> ANALOG_COMPARATOR =
            entry("analog_comparator", AutomationKind.ANALOG_COMPARATOR, true, PcBlocks.ANALOG_COMPARATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> MANUAL_STATION =
            entry("manual_station", AutomationKind.MANUAL_STATION, true, PcBlocks.MANUAL_STATION);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> SIGNAL_CONVERTER =
            entry("signal_converter", AutomationKind.SIGNAL_CONVERTER, true, PcBlocks.SIGNAL_CONVERTER);

    private AutomationBlockEntities() {
    }

    private static DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> entry(String name, AutomationKind kind,
            boolean panel, Supplier<? extends Block> block) {
        @SuppressWarnings("unchecked")
        DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> type = BLOCK_ENTITIES.register(name,
                () -> BlockEntityType.Builder.of((pos, state) -> create(name, pos, state), block.get()).build(null));
        ENTRIES.put(name, new Entry(kind, panel, type));
        return type;
    }

    /** BE блока {@code name} (фабрика блоков в {@code PcBlocks}). */
    public static AutomationBlockEntity create(String name, BlockPos pos, BlockState state) {
        if (name.equals("relay_power_supply")) return new PowerSupplyBlockEntity(pos, state);
        Entry e = ENTRIES.get(name);
        if (e == null) throw new IllegalArgumentException("no automation block " + name);
        return e.panel ? new AutomationPanelBlockEntity(e.type.get(), pos, state, e.kind)
                : new AutomationBlockEntity(e.type.get(), pos, state, e.kind);
    }

    /** Все типы BE с клеммами — для рендера проводов на клиенте. */
    public static Iterable<DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>>> wiredTypes() {
        return ENTRIES.values().stream().map(Entry::type).toList();
    }
}
