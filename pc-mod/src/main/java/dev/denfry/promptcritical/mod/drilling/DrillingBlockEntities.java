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
            type("rotary_table", RotaryTableBlockEntity::new, PcBlocks.ROTARY_TABLE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MudPumpBlockEntity>> MUD_PUMP =
            type("mud_pump", MudPumpBlockEntity::new, PcBlocks.MUD_PUMP);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MudHopperBlockEntity>> MUD_MIXING_HOPPER =
            type("mud_mixing_hopper", MudHopperBlockEntity::new, PcBlocks.MUD_MIXING_HOPPER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BopRamBlockEntity>> BOP_RAM =
            type("bop_ram", BopRamBlockEntity::new, PcBlocks.BOP_RAM);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BopAccumulatorBlockEntity>> BOP_ACCUMULATOR =
            type("bop_accumulator", BopAccumulatorBlockEntity::new, PcBlocks.BOP_ACCUMULATOR);

    private DrillingBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}
