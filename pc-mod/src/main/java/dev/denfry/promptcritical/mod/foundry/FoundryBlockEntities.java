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
            BLOCK_ENTITIES.register("coal_jig", () -> BlockEntityType.Builder.of(CoalJigBlockEntity::new,
                    PcBlocks.COAL_JIG.get(), PcBlocks.COAL_JIG_REAL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CupolaBlockEntity>> CUPOLA =
            type("cupola_furnace", CupolaBlockEntity::new, PcBlocks.CUPOLA_FURNACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CupolaRealBlockEntity>> CUPOLA_REAL =
            type("cupola_furnace_real", CupolaRealBlockEntity::new, PcBlocks.CUPOLA_FURNACE_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrucibleBlockEntity>> CRUCIBLE =
            BLOCK_ENTITIES.register("crucible_furnace", () -> BlockEntityType.Builder.of(CrucibleBlockEntity::new,
                    PcBlocks.CRUCIBLE_FURNACE.get(), PcBlocks.CRUCIBLE_FURNACE_REAL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SandMoldBlockEntity>> SAND_MOLD =
            type("sand_mold", SandMoldBlockEntity::new, PcBlocks.SAND_MOLD);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MoldingStationBlockEntity>> MOLDING_STATION =
            BLOCK_ENTITIES.register("molding_station", () -> BlockEntityType.Builder.of(MoldingStationBlockEntity::new,
                    PcBlocks.MOLDING_STATION.get(), PcBlocks.MOLDING_STATION_REAL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShakeoutGrateBlockEntity>> SHAKEOUT_GRATE =
            BLOCK_ENTITIES.register("shakeout_grate", () -> BlockEntityType.Builder.of(ShakeoutGrateBlockEntity::new,
                    PcBlocks.SHAKEOUT_GRATE.get(), PcBlocks.SHAKEOUT_GRATE_REAL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineToolBlockEntity>> MACHINE_LATHE =
            type("machine_lathe", MachineToolBlockEntity::lathe, PcBlocks.MACHINE_LATHE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineToolBlockEntity>> DRILL_PRESS =
            type("drill_press", MachineToolBlockEntity::drillPress, PcBlocks.DRILL_PRESS);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProductionToolBlockEntity>> MILLING_MACHINE =
            type("milling_machine", ProductionToolBlockEntity::milling, PcBlocks.MILLING_MACHINE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProductionToolBlockEntity>> SHAPING_MACHINE =
            type("shaping_machine", ProductionToolBlockEntity::shaper, PcBlocks.SHAPING_MACHINE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProductionToolBlockEntity>> SURFACE_GRINDER =
            type("surface_grinder", ProductionToolBlockEntity::grinder, PcBlocks.SURFACE_GRINDER);

    private FoundryBlockEntities() {
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}
