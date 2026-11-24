package dev.denfry.promptcritical.mod.drilling;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Типы BE нефти и газа I2: сейсмостанция, пульт бурильщика, ротор, буровой насос, гидроворонка. */
public final class DrillingBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SeismicRecorderBlockEntity>> SEISMIC_RECORDER =
            type("seismic_recorder", SeismicRecorderBlockEntity::new, PcBlocks.SEISMIC_RECORDER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DrillersConsoleBlockEntity>> DRILLERS_CONSOLE =
            type("drillers_console", DrillersConsoleBlockEntity::new, PcBlocks.DRILLERS_CONSOLE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RotaryTableBlockEntity>> ROTARY_TABLE =
            type("rotary_table", RotaryTableBlockEntity::new, PcBlocks.ROTARY_TABLE, PcBlocks.ROTARY_TABLE_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MudPumpBlockEntity>> MUD_PUMP =
            type("mud_pump", MudPumpBlockEntity::new, PcBlocks.MUD_PUMP, PcBlocks.MUD_PUMP_REAL);
    /** Буровая реального масштаба: секция вышки и лебёдка — контроллеры цельных моделей (09 §9.5). */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DerrickRealBlockEntity>> DERRICK_REAL =
            type("derrick_segment_real", DerrickRealBlockEntity::new, PcBlocks.DERRICK_SEGMENT_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DrawworksRealBlockEntity>> DRAWWORKS_REAL =
            type("drawworks_real", DrawworksRealBlockEntity::new, PcBlocks.DRAWWORKS_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MudHopperBlockEntity>> MUD_MIXING_HOPPER =
            type("mud_mixing_hopper", MudHopperBlockEntity::new, PcBlocks.MUD_MIXING_HOPPER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LoggingUnitBlockEntity>> LOGGING_UNIT =
            type("logging_unit", LoggingUnitBlockEntity::new, PcBlocks.LOGGING_UNIT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoreAnalysisStationBlockEntity>> CORE_ANALYSIS_STATION =
            type("core_analysis_station", CoreAnalysisStationBlockEntity::new, PcBlocks.CORE_ANALYSIS_STATION);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BopRamBlockEntity>> BOP_RAM =
            type("bop_ram", BopRamBlockEntity::new, PcBlocks.BOP_RAM);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BopAccumulatorBlockEntity>> BOP_ACCUMULATOR =
            type("bop_accumulator", BopAccumulatorBlockEntity::new, PcBlocks.BOP_ACCUMULATOR);

    private DrillingBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }

    @SafeVarargs
    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block>... blocks) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory,
                java.util.Arrays.stream(blocks).map(Supplier::get).toArray(Block[]::new)).build(null));
    }
}
