package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Типы BE литейки, цеха и углеобогащения (08 §8.3, §8.9–8.10). */
public final class FoundryBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoalJigBlockEntity>> COAL_JIG =
            type("coal_jig", CoalJigBlockEntity::new, PcBlocks.COAL_JIG);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CupolaBlockEntity>> CUPOLA =
            type("cupola_furnace", CupolaBlockEntity::new, PcBlocks.CUPOLA_FURNACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrucibleBlockEntity>> CRUCIBLE =
            type("crucible_furnace", CrucibleBlockEntity::new, PcBlocks.CRUCIBLE_FURNACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SandMoldBlockEntity>> SAND_MOLD =
            type("sand_mold", SandMoldBlockEntity::new, PcBlocks.SAND_MOLD);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MoldingStationBlockEntity>> MOLDING_STATION =
            type("molding_station", MoldingStationBlockEntity::new, PcBlocks.MOLDING_STATION);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShakeoutGrateBlockEntity>> SHAKEOUT_GRATE =
            type("shakeout_grate", ShakeoutGrateBlockEntity::new, PcBlocks.SHAKEOUT_GRATE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineToolBlockEntity>> MACHINE_LATHE =
            type("machine_lathe", MachineToolBlockEntity::lathe, PcBlocks.MACHINE_LATHE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineToolBlockEntity>> DRILL_PRESS =
            type("drill_press", MachineToolBlockEntity::drillPress, PcBlocks.DRILL_PRESS);

    private FoundryBlockEntities() {
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}
