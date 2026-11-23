package dev.denfry.promptcritical.mod.mine;

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

/** Типы BE шахты (08 §8.2): вентилятор, насосы, подъём. */
public final class MineBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MainMineFanBlockEntity>> MAIN_MINE_FAN =
            BLOCK_ENTITIES.register("main_mine_fan", () -> BlockEntityType.Builder.of(MainMineFanBlockEntity::new,
                    PcBlocks.MAIN_MINE_FAN.get(), PcBlocks.GUIBAL_FAN.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DegasificationPumpBlockEntity>> DEGASIFICATION_PUMP =
            type("degasification_pump", DegasificationPumpBlockEntity::new, PcBlocks.DEGASIFICATION_PUMP);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HeadframeSteelBlockEntity>> HEADFRAME_STEEL =
            type("headframe_steel", HeadframeSteelBlockEntity::new, PcBlocks.HEADFRAME_STEEL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MineHoistBlockEntity>> MINE_HOIST =
            BLOCK_ENTITIES.register("mine_hoist", () -> BlockEntityType.Builder.of(MineHoistBlockEntity::new,
                    PcBlocks.MINE_HOIST.get(), PcBlocks.WINDING_ENGINE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MineDrainagePumpBlockEntity>> MINE_DRAINAGE_PUMP =
            BLOCK_ENTITIES.register("mine_drainage_pump", () -> BlockEntityType.Builder.of(MineDrainagePumpBlockEntity::new,
                    PcBlocks.MINE_DRAINAGE_PUMP.get(), PcBlocks.MINE_DRAINAGE_PUMP_GEARED.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LongwallShearerBlockEntity>> LONGWALL_SHEARER =
            BLOCK_ENTITIES.register("longwall_shearer", () -> BlockEntityType.Builder.of(LongwallShearerBlockEntity::new,
                    PcBlocks.LONGWALL_SHEARER.get(), PcBlocks.LONGWALL_SHEARER_REAL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PoweredRoofSupportBlockEntity>> POWERED_ROOF_SUPPORT =
            BLOCK_ENTITIES.register("powered_roof_support", () -> BlockEntityType.Builder.of(PoweredRoofSupportBlockEntity::new,
                    PcBlocks.POWERED_ROOF_SUPPORT.get(), PcBlocks.POWERED_ROOF_SUPPORT_REAL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShaftSignalBlockEntity>> SHAFT_SIGNAL =
            type("shaft_signal", ShaftSignalBlockEntity::new, PcBlocks.SHAFT_SIGNAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LampRackBlockEntity>> LAMP_RACK =
            type("lamp_rack", LampRackBlockEntity::new, PcBlocks.LAMP_RACK);

    private MineBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        CapLampService.register();
        dev.denfry.promptcritical.mod.gear.ElectricGearService.register();
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}
