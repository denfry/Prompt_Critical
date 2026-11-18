package dev.denfry.promptcritical.mod.scada;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Тип BE шлюза телемеханики. Мост в CC: Tweaked ({@code mod.scada.cc.CcIntegration}) подключается, только если CC
 * загружен: класс с API CC до этого не загружается, и мод работает без CC.
 */
public final class ScadaBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HistorianBlockEntity>> HISTORIAN =
            BLOCK_ENTITIES.register("historian_server",
                    () -> BlockEntityType.Builder.of(HistorianBlockEntity::new, PcBlocks.HISTORIAN_SERVER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TelemetryGatewayBlockEntity>> TELEMETRY_GATEWAY =
            BLOCK_ENTITIES.register("rtu_station",
                    () -> BlockEntityType.Builder.of(TelemetryGatewayBlockEntity::new, PcBlocks.RTU_STATION.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TwinWorkstationBlockEntity>> TWIN_WORKSTATION =
            BLOCK_ENTITIES.register("simulation_workstation",
                    () -> BlockEntityType.Builder.of(TwinWorkstationBlockEntity::new, PcBlocks.SIMULATION_WORKSTATION.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrainingSimulatorBlockEntity>> TRAINING_SIMULATOR =
            BLOCK_ENTITIES.register("training_simulator",
                    () -> BlockEntityType.Builder.of(TrainingSimulatorBlockEntity::new, PcBlocks.TRAINING_SIMULATOR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SimulatorDeskBlockEntity>> SIMULATOR_CONSOLE =
            BLOCK_ENTITIES.register("simulator_console",
                    () -> BlockEntityType.Builder.of(SimulatorDeskBlockEntity::console, PcBlocks.SIMULATOR_CONSOLE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SimulatorDeskBlockEntity>> INSTRUCTOR_STATION =
            BLOCK_ENTITIES.register("instructor_station",
                    () -> BlockEntityType.Builder.of(SimulatorDeskBlockEntity::instructor, PcBlocks.INSTRUCTOR_STATION.get()).build(null));

    private ScadaBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        OperatorLicenseItem.COMPONENTS.register(modBus);
        if (ModList.get().isLoaded("computercraft")) dev.denfry.promptcritical.mod.scada.cc.CcIntegration.register(modBus);
    }
}
