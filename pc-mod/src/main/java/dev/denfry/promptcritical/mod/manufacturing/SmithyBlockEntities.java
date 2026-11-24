package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Типы BE кузницы ручной ступени (18 §18.7). */
public final class SmithyBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BloomeryBlockEntity>> BLOOMERY =
            type("bloomery", BloomeryBlockEntity::new, PcBlocks.BLOOMERY, PcBlocks.BLOOMERY_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ForgeHearthBlockEntity>> FORGE_HEARTH =
            type("forge_hearth", ForgeHearthBlockEntity::new, PcBlocks.FORGE_HEARTH, PcBlocks.FORGE_HEARTH_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmithingAnvilBlockEntity>> SMITHING_ANVIL =
            type("smithing_anvil", SmithingAnvilBlockEntity::new, PcBlocks.SMITHING_ANVIL);
    /** Сборочный стенд (18 §18.8.2). */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblyStandBlockEntity>> ASSEMBLY_STAND =
            type("assembly_stand", AssemblyStandBlockEntity::new, PcBlocks.ASSEMBLY_STAND);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DrawPlateBlockEntity>> DRAW_PLATE =
            type("draw_plate", DrawPlateBlockEntity::new, PcBlocks.DRAW_PLATE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerHammerBlockEntity>> POWER_HAMMER =
            type("power_hammer", PowerHammerBlockEntity::hammer, PcBlocks.POWER_HAMMER, PcBlocks.POWER_HAMMER_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerHammerBlockEntity>> CRANK_PRESS =
            type("crank_press", PowerHammerBlockEntity::press, PcBlocks.CRANK_PRESS, PcBlocks.CRANK_PRESS_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WireMillBlockEntity>> WIRE_MILL =
            type("wire_mill", WireMillBlockEntity::new, PcBlocks.WIRE_MILL, PcBlocks.WIRE_MILL_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmithyFurnaceBlockEntity>> HEATING_FURNACE =
            type("heating_furnace", SmithyFurnaceBlockEntity::heating, PcBlocks.HEATING_FURNACE, PcBlocks.HEATING_FURNACE_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmithyFurnaceBlockEntity>> HARDENING_FURNACE =
            type("hardening_furnace", SmithyFurnaceBlockEntity::hardening, PcBlocks.HARDENING_FURNACE, PcBlocks.HARDENING_FURNACE_REAL);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SheetMillBlockEntity>> SHEET_MILL =
            type("sheet_mill", SheetMillBlockEntity::new, PcBlocks.SHEET_MILL, PcBlocks.SHEET_MILL_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NotchingPressBlockEntity>> NOTCHING_PRESS =
            type("notching_press", NotchingPressBlockEntity::new, PcBlocks.NOTCHING_PRESS, PcBlocks.NOTCHING_PRESS_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindingMachineBlockEntity>> WINDING_MACHINE =
            type("winding_machine", WindingMachineBlockEntity::new, PcBlocks.WINDING_MACHINE, PcBlocks.WINDING_MACHINE_REAL);

    private SmithyBlockEntities() {
    }

    /** Склад горнов открыт воронкам и Create — третий уровень (автоматика) подаёт руду, уголь и прокат сам. */
    static void capabilities(RegisterCapabilitiesEvent event) {
        for (var t : java.util.List.<BlockEntityType<?>>of(BLOOMERY.get(), FORGE_HEARTH.get(), SMITHING_ANVIL.get(), DRAW_PLATE.get(),
                POWER_HAMMER.get(), CRANK_PRESS.get(), WIRE_MILL.get(), HEATING_FURNACE.get(), HARDENING_FURNACE.get(),
                SHEET_MILL.get(), NOTCHING_PRESS.get(), WINDING_MACHINE.get(), ASSEMBLY_STAND.get()))
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, cast(t),
                    (be, side) -> ((dev.denfry.promptcritical.mod.foundry.FoundryDevice) be).items(side));
        // бак закалочной печи: вода или масло трубами Create и вёдрами
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, HARDENING_FURNACE.get(), (be, side) -> be.fluids(side));
    }

    @SuppressWarnings("unchecked")
    private static BlockEntityType<BlockEntity> cast(BlockEntityType<?> t) {
        return (BlockEntityType<BlockEntity>) t;
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }

    /** Тип BE старой машины и её пары реального масштаба (тот же BE на обоих блоках). */
    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block, Supplier<? extends Block> real) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get(), real.get()).build(null));
    }
}
