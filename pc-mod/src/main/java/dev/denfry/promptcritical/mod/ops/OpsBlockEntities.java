package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Типы BE эксплуатации и водоподготовки (13 §13.6.5–13.6.7, 08 §8.12). */
public final class OpsBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MaintenancePanelBlockEntity>> MAINTENANCE_PANEL =
            BLOCK_ENTITIES.register("maintenance_panel", () -> BlockEntityType.Builder.of(MaintenancePanelBlockEntity::new,
                    PcBlocks.MAINTENANCE_PANEL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HydrotestPumpBlockEntity>> HYDROTEST_PUMP =
            BLOCK_ENTITIES.register("hydrotest_pump", () -> BlockEntityType.Builder.of(HydrotestPumpBlockEntity::new,
                    PcBlocks.HYDROTEST_PUMP.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RecordingGaugeBlockEntity>> RECORDING_GAUGE =
            BLOCK_ENTITIES.register("recording_gauge", () -> BlockEntityType.Builder.of(RecordingGaugeBlockEntity::new,
                    PcBlocks.RECORDING_GAUGE.get()).build(null));
    /** Один тип на три блока: ступень — по ID блока. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterTreatmentBlockEntity>> WATER_TREATMENT =
            BLOCK_ENTITIES.register("water_treatment", () -> BlockEntityType.Builder.of(WaterTreatmentBlockEntity::new,
                    PcBlocks.WATER_CLARIFIER.get(), PcBlocks.SAND_FILTER.get(), PcBlocks.ION_EXCHANGE_COLUMN.get()).build(null));

    private OpsBlockEntities() {
    }
}
