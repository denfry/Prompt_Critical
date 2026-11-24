package dev.denfry.promptcritical.mod.hydrogen;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Типы BE водородной станции. */
public final class HydrogenBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectrolyserBlockEntity>> ELECTROLYSER =
            BLOCK_ENTITIES.register("h2_electrolyser", () -> BlockEntityType.Builder.of(ElectrolyserBlockEntity::new,
                    PcBlocks.H2_ELECTROLYSER_REAL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<H2CompressorBlockEntity>> H2_COMPRESSOR =
            BLOCK_ENTITIES.register("h2_compressor", () -> BlockEntityType.Builder.of(H2CompressorBlockEntity::new,
                    PcBlocks.H2_COMPRESSOR_REAL.get()).build(null));
    /** Рампа и газгольдер — один BE, сосуд по блоку. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<H2StorageBlockEntity>> H2_STORAGE =
            BLOCK_ENTITIES.register("h2_storage", () -> BlockEntityType.Builder.of(H2StorageBlockEntity::new,
                    PcBlocks.H2_TUBE_BANK_REAL.get(), PcBlocks.H2_GASHOLDER_REAL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FuelCellBlockEntity>> FUEL_CELL =
            BLOCK_ENTITIES.register("h2_fuel_cell", () -> BlockEntityType.Builder.of(FuelCellBlockEntity::new,
                    PcBlocks.H2_FUEL_CELL_REAL.get()).build(null));

    private HydrogenBlockEntities() {
    }
}
