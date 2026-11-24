package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Типы BE радиационной части: стеллаж бассейна выдержки, ультразвуковая ванна, душ и установка контроля санпропускника. */
public final class RadiationBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FuelRackBlockEntity>> FUEL_RACK =
            BLOCK_ENTITIES.register("fuel_rack", () -> BlockEntityType.Builder.of(FuelRackBlockEntity::new, PcBlocks.FUEL_RACK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UltrasonicBathBlockEntity>> ULTRASONIC_BATH =
            BLOCK_ENTITIES.register("ultrasonic_bath",
                    () -> BlockEntityType.Builder.of(UltrasonicBathBlockEntity::new, PcBlocks.ULTRASONIC_BATH.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DeconShowerBlockEntity>> DECON_SHOWER =
            BLOCK_ENTITIES.register("decon_shower", () -> BlockEntityType.Builder.of(DeconShowerBlockEntity::new, PcBlocks.DECON_SHOWER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HandFootMonitorBlockEntity>> HAND_FOOT_MONITOR =
            BLOCK_ENTITIES.register("hand_foot_monitor",
                    () -> BlockEntityType.Builder.of(HandFootMonitorBlockEntity::new, PcBlocks.HAND_FOOT_MONITOR.get(),
                            PcBlocks.HAND_FOOT_MONITOR_REAL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PortalMonitorBlockEntity>> PORTAL_MONITOR =
            BLOCK_ENTITIES.register("portal_monitor",
                    () -> BlockEntityType.Builder.of(PortalMonitorBlockEntity::new, PcBlocks.PORTAL_MONITOR.get(), PcBlocks.PORTAL_MONITOR_REAL.get()).build(null));
    // радзащита реального масштаба
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HotCellBlockEntity>> HOT_CELL =
            BLOCK_ENTITIES.register("hot_cell", () -> BlockEntityType.Builder.of(HotCellBlockEntity::new, PcBlocks.HOT_CELL_REAL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpentFuelCaskBlockEntity>> SPENT_FUEL_CASK =
            BLOCK_ENTITIES.register("spent_fuel_cask",
                    () -> BlockEntityType.Builder.of(SpentFuelCaskBlockEntity::new, PcBlocks.SPENT_FUEL_CASK_REAL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CaskCartBlockEntity>> CASK_CART =
            BLOCK_ENTITIES.register("cask_transport_cart",
                    () -> BlockEntityType.Builder.of(CaskCartBlockEntity::new, PcBlocks.CASK_TRANSPORT_CART_REAL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DryStorageModuleBlockEntity>> DRY_STORAGE_MODULE =
            BLOCK_ENTITIES.register("dry_storage_module",
                    () -> BlockEntityType.Builder.of(DryStorageModuleBlockEntity::new, PcBlocks.DRY_STORAGE_MODULE_REAL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ZoneControllerBlockEntity>> ZONE_CONTROLLER =
            BLOCK_ENTITIES.register("zone_controller",
                    () -> BlockEntityType.Builder.of(ZoneControllerBlockEntity::new, PcBlocks.ZONE_CONTROLLER.get()).build(null));
    /** Шкафы санпропускника: «чистый» и «грязный» — один тип с разной логикой блока. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LockerBlockEntity>> LOCKER =
            BLOCK_ENTITIES.register("locker",
                    () -> BlockEntityType.Builder.of(LockerBlockEntity::new, PcBlocks.CLEAN_LOCKER.get(), PcBlocks.DIRTY_LOCKER.get()).build(null));

    private RadiationBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener((RegisterCapabilitiesEvent e) -> {
            e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, FUEL_RACK.get(), FoundryDevice::items);
            e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ULTRASONIC_BATH.get(), FoundryDevice::items);
            e.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ULTRASONIC_BATH.get(), FoundryDevice::fluids);
            e.registerBlockEntity(Capabilities.FluidHandler.BLOCK, DECON_SHOWER.get(), FoundryDevice::fluids);
            // шлюз горячей камеры и корзина открытого контейнера — для воронок и манипуляторов Create
            e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, HOT_CELL.get(), FoundryDevice::items);
            e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SPENT_FUEL_CASK.get(), FoundryDevice::items);
        });
        ContaminationData.register(modBus);
        RadGear.register(modBus);
        ContaminationService.register();
    }
}
