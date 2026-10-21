package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Типы BE электросети I1 (07 §7.22). */
public final class GridBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShaftGeneratorBlockEntity>> SHAFT_GENERATOR =
            type("shaft_generator", ShaftGeneratorBlockEntity::new, PcBlocks.SHAFT_GENERATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricMotorBlockEntity>> ELECTRIC_MOTOR =
            type("electric_motor", ElectricMotorBlockEntity::new, PcBlocks.ELECTRIC_MOTOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BreakerCellBlockEntity>> SWITCHGEAR_CELL =
            type("switchgear_cell", BreakerCellBlockEntity::mv, PcBlocks.SWITCHGEAR_CELL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BreakerCellBlockEntity>> LV_SWITCHBOARD =
            type("lv_switchboard", BreakerCellBlockEntity::lv, PcBlocks.LV_SWITCHBOARD);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DistributionBoxBlockEntity>> LV_DISTRIBUTION_BOX =
            type("lv_distribution_box", DistributionBoxBlockEntity::new, PcBlocks.LV_DISTRIBUTION_BOX);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DisconnectorBlockEntity>> DISCONNECTOR =
            type("disconnector", DisconnectorBlockEntity::new, PcBlocks.DISCONNECTOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TransformerBlockEntity>> DISTRIBUTION_TRANSFORMER =
            type("distribution_transformer", TransformerBlockEntity::new, PcBlocks.DISTRIBUTION_TRANSFORMER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RelayCabinetBlockEntity>> RELAY_CABINET =
            type("relay_cabinet", RelayCabinetBlockEntity::new, PcBlocks.RELAY_CABINET);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConsumerBlockEntity>> GRID_CONSUMER =
            type("grid_consumer", ConsumerBlockEntity::new, PcBlocks.GRID_CONSUMER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConnectorBlockEntity>> WIRE_CONNECTOR_LV =
            type("wire_connector_lv", ConnectorBlockEntity::lv, PcBlocks.WIRE_CONNECTOR_LV);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConnectorBlockEntity>> WIRE_CONNECTOR_MV =
            type("wire_connector_mv", ConnectorBlockEntity::mv, PcBlocks.WIRE_CONNECTOR_MV);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InstrumentBlockEntity>> SYNCHROSCOPE =
            type("instrument_synchroscope", InstrumentBlockEntity::synchroscope, PcBlocks.INSTRUMENT_SYNCHROSCOPE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InstrumentBlockEntity>> ENERGY_METER =
            type("energy_meter", InstrumentBlockEntity::meter, PcBlocks.ENERGY_METER);

    private GridBlockEntities() {
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}
