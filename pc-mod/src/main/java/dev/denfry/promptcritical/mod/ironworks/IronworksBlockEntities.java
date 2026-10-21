package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Типы BE коксохима и домны (08 §8.4–8.5). */
public final class IronworksBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CokeOvenBlockEntity>> COKE_OVEN =
            type("coke_oven", CokeOvenBlockEntity::new, PcBlocks.COKE_OVEN);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GasExhausterBlockEntity>> GAS_EXHAUSTER =
            type("gas_exhauster", GasExhausterBlockEntity::new, PcBlocks.GAS_EXHAUSTER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ByproductPlantBlockEntity>> BYPRODUCT_PLANT =
            type("byproduct_plant", ByproductPlantBlockEntity::new, PcBlocks.BYPRODUCT_PLANT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuenchTowerBlockEntity>> QUENCH_TOWER =
            type("quench_tower", QuenchTowerBlockEntity::new, PcBlocks.QUENCH_TOWER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectrodeExtruderBlockEntity>> ELECTRODE_EXTRUDER =
            type("electrode_extruder", ElectrodeExtruderBlockEntity::new, PcBlocks.ELECTRODE_EXTRUDER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BakingFurnaceBlockEntity>> BAKING_FURNACE =
            type("baking_furnace", BakingFurnaceBlockEntity::new, PcBlocks.BAKING_FURNACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MagneticSeparatorBlockEntity>> MAGNETIC_SEPARATOR =
            type("magnetic_separator", MagneticSeparatorBlockEntity::new, PcBlocks.MAGNETIC_SEPARATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SinterStrandBlockEntity>> SINTER_STRAND =
            type("sinter_strand", SinterStrandBlockEntity::new, PcBlocks.SINTER_STRAND);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlastFurnaceControllerBlockEntity>> BLAST_FURNACE_CONTROLLER =
            type("blast_furnace_controller", BlastFurnaceControllerBlockEntity::new, PcBlocks.BLAST_FURNACE_CONTROLLER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlastFurnaceTopBlockEntity>> BLAST_FURNACE_TOP =
            type("blast_furnace_top", BlastFurnaceTopBlockEntity::new, PcBlocks.BLAST_FURNACE_TOP);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HotBlastStoveBlockEntity>> HOT_BLAST_STOVE =
            type("hot_blast_stove", HotBlastStoveBlockEntity::new, PcBlocks.HOT_BLAST_STOVE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlastBlowerBlockEntity>> BLAST_BLOWER =
            type("blast_blower", BlastBlowerBlockEntity::new, PcBlocks.BLAST_BLOWER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DustCatcherBlockEntity>> DUST_CATCHER =
            type("dust_catcher", DustCatcherBlockEntity::new, PcBlocks.DUST_CATCHER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CastHouseMachineBlockEntity>> CAST_HOUSE_MACHINE =
            type("cast_house_machine", CastHouseMachineBlockEntity::new, PcBlocks.CAST_HOUSE_MACHINE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LadleBlockEntity>> LADLE =
            type("ladle", LadleBlockEntity::new, PcBlocks.LADLE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SlagGranulatorBlockEntity>> SLAG_GRANULATOR =
            type("slag_granulator", SlagGranulatorBlockEntity::new, PcBlocks.SLAG_GRANULATOR);

    private IronworksBlockEntities() {
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}
