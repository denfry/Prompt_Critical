package dev.denfry.promptcritical.mod.steelworks;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Типы BE сталеплавильного, прокатного и трубного цехов и ВРУ (08 §8.6–8.7, §8.11). */
public final class SteelworksBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BofVesselBlockEntity>> BOF_VESSEL =
            type("bof_vessel", BofVesselBlockEntity::new, PcBlocks.BOF_VESSEL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OxygenLanceBlockEntity>> OXYGEN_LANCE =
            type("oxygen_lance", OxygenLanceBlockEntity::new, PcBlocks.OXYGEN_LANCE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LadleStationBlockEntity>> LADLE_STATION =
            type("ladle_station", LadleStationBlockEntity::new, PcBlocks.LADLE_STATION);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IngotMoldBlockEntity>> INGOT_MOLD =
            type("ingot_mold", IngotMoldBlockEntity::new, PcBlocks.INGOT_MOLD);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReheatingFurnaceBlockEntity>> REHEATING_FURNACE =
            type("reheating_furnace", ReheatingFurnaceBlockEntity::new, PcBlocks.REHEATING_FURNACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RollerTableBlockEntity>> ROLLER_TABLE =
            type("roller_table", RollerTableBlockEntity::new, PcBlocks.ROLLER_TABLE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MillStandBlockEntity>> ROLLING_MILL_STAND =
            type("rolling_mill_stand", MillStandBlockEntity::new, PcBlocks.ROLLING_MILL_STAND);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PiercingMillBlockEntity>> PIERCING_MILL =
            type("piercing_mill", PiercingMillBlockEntity::new, PcBlocks.PIERCING_MILL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PipeWelderBlockEntity>> PIPE_WELDER =
            type("pipe_welder", PipeWelderBlockEntity::new, PcBlocks.PIPE_WELDER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HydrotestBenchBlockEntity>> HYDROTEST_BENCH =
            type("hydrotest_bench", HydrotestBenchBlockEntity::new, PcBlocks.HYDROTEST_BENCH);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HeatTreatFurnaceBlockEntity>> HEAT_TREATMENT_FURNACE =
            type("heat_treatment_furnace", HeatTreatFurnaceBlockEntity::new, PcBlocks.HEAT_TREATMENT_FURNACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuenchTankBlockEntity>> QUENCH_TANK =
            type("quench_tank", QuenchTankBlockEntity::new, PcBlocks.QUENCH_TANK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirSeparatorBlockEntity>> AIR_SEPARATOR =
            type("air_separator", AirSeparatorBlockEntity::new, PcBlocks.AIR_SEPARATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AsuCompressorBlockEntity>> ASU_MAIN_COMPRESSOR =
            type("asu_main_compressor", AsuCompressorBlockEntity::new, PcBlocks.ASU_MAIN_COMPRESSOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CryoTankBlockEntity>> CRYOGENIC_TANK =
            type("cryogenic_tank", CryoTankBlockEntity::new, PcBlocks.CRYOGENIC_TANK);

    private SteelworksBlockEntities() {
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}
