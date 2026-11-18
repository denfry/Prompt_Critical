package dev.denfry.promptcritical.mod.refinery;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Типы BE облагораживания на НПЗ: гидроочистка, установка Клауса и каталитический риформинг. */
public final class RefineryBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HydrotreaterBlockEntity>> HYDROTREATER =
            BLOCK_ENTITIES.register("hydrotreater",
                    () -> BlockEntityType.Builder.of(HydrotreaterBlockEntity::new, PcBlocks.HYDROTREATER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ClausUnitBlockEntity>> CLAUS_UNIT =
            BLOCK_ENTITIES.register("claus_unit",
                    () -> BlockEntityType.Builder.of(ClausUnitBlockEntity::new, PcBlocks.CLAUS_UNIT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DelayedCokerBlockEntity>> DELAYED_COKER =
            BLOCK_ENTITIES.register("delayed_coker",
                    () -> BlockEntityType.Builder.of(DelayedCokerBlockEntity::new, PcBlocks.DELAYED_COKER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CatalyticReformerBlockEntity>> CATALYTIC_REFORMER =
            BLOCK_ENTITIES.register("catalytic_reformer",
                    () -> BlockEntityType.Builder.of(CatalyticReformerBlockEntity::new, PcBlocks.CATALYTIC_REFORMER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DesalterBlockEntity>> DESALTER =
            BLOCK_ENTITIES.register("electrostatic_desalter",
                    () -> BlockEntityType.Builder.of(DesalterBlockEntity::new, PcBlocks.ELECTROSTATIC_DESALTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VacuumColumnBlockEntity>> VACUUM_COLUMN =
            BLOCK_ENTITIES.register("vacuum_column",
                    () -> BlockEntityType.Builder.of(VacuumColumnBlockEntity::new, PcBlocks.VACUUM_COLUMN.get()).build(null));

    private RefineryBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(RefineryBlockEntities::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, HYDROTREATER.get(), (be, side) -> be.fluids(side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CLAUS_UNIT.get(), (be, side) -> be.items(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CATALYTIC_REFORMER.get(), (be, side) -> be.fluids(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, DELAYED_COKER.get(), (be, side) -> be.fluids(side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, DELAYED_COKER.get(), (be, side) -> be.items(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, DESALTER.get(), (be, side) -> be.fluids(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, VACUUM_COLUMN.get(), (be, side) -> be.fluids(side));
    }
}
