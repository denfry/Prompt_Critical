package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Типы BE АЭС: реакторы T1 и T2a, ГЦН, парогенератор, компенсатор давления. */
public final class NppBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerReactorBlockEntity>> POWER_REACTOR =
            type("power_reactor", PowerReactorBlockEntity::new, PcBlocks.POWER_REACTOR, PcBlocks.AM1_REACTOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PrimaryPumpBlockEntity>> PRIMARY_PUMP =
            type("primary_pump", PrimaryPumpBlockEntity::new, PcBlocks.PRIMARY_PUMP, PcBlocks.VVER_COOLANT_PUMP, PcBlocks.VVER70_COOLANT_PUMP_REAL,
                    PcBlocks.NPD_PUMP_REAL, PcBlocks.BOR60_PRIMARY_PUMP_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamGeneratorBlockEntity>> STEAM_GENERATOR =
            type("steam_generator", SteamGeneratorBlockEntity::new, PcBlocks.STEAM_GENERATOR, PcBlocks.VVER_STEAM_GENERATOR, PcBlocks.VVER70_STEAM_GENERATOR_REAL,
                    PcBlocks.NPD_BOILER_REAL, PcBlocks.BOR60_STEAM_GENERATOR_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PwrReactorBlockEntity>> PWR_REACTOR =
            type("pwr_reactor", PwrReactorBlockEntity::new, PcBlocks.PWR_REACTOR, PcBlocks.VVER_REACTOR, PcBlocks.VVER70_REACTOR_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BwrReactorBlockEntity>> BWR_REACTOR =
            type("bwr_reactor", BwrReactorBlockEntity::new, PcBlocks.BWR_REACTOR, PcBlocks.BWR240_REACTOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CanduReactorBlockEntity>> CANDU_REACTOR =
            type("candu_reactor", CanduReactorBlockEntity::new, PcBlocks.CANDU_REACTOR, PcBlocks.CANDU6_REACTOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SfrReactorBlockEntity>> SFR_REACTOR =
            type("sfr_reactor", SfrReactorBlockEntity::new, PcBlocks.SFR_REACTOR, PcBlocks.BN600_REACTOR, PcBlocks.BOR60_REACTOR_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PressurizerBlockEntity>> PRESSURIZER =
            type("pressurizer", PressurizerBlockEntity::new, PcBlocks.PRESSURIZER, PcBlocks.VVER_PRESSURIZER, PcBlocks.VVER70_PRESSURIZER_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EccsPumpBlockEntity>> ECCS_PUMP =
            type("eccs_pump", EccsPumpBlockEntity::new, PcBlocks.ECCS_PUMP);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpentFuelPoolBlockEntity>> FUEL_POOL =
            type("vver70_fuel_pool", SpentFuelPoolBlockEntity::new, PcBlocks.VVER70_FUEL_POOL_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RefuellingMachineBlockEntity>> REFUELLING_MACHINE =
            type("vver70_refuelling_machine", RefuellingMachineBlockEntity::new, PcBlocks.VVER70_REFUELLING_MACHINE_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NpdReactorBlockEntity>> NPD_REACTOR =
            type("npd_reactor", NpdReactorBlockEntity::new, PcBlocks.NPD_REACTOR_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NpdFuellingMachineBlockEntity>> NPD_FUELLING_MACHINE =
            type("npd_fuelling_machine", NpdFuellingMachineBlockEntity::new, PcBlocks.NPD_FUELLING_MACHINE_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IhxBlockEntity>> IHX =
            type("bor60_ihx", IhxBlockEntity::new, PcBlocks.BOR60_IHX_REAL);

    private NppBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(NppBlockEntities::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, POWER_REACTOR.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PWR_REACTOR.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BWR_REACTOR.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CANDU_REACTOR.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CANDU_REACTOR.get(), FoundryDevice::fluids);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SFR_REACTOR.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, FUEL_POOL.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, NPD_REACTOR.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, NPD_REACTOR.get(), FoundryDevice::fluids);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, NPD_FUELLING_MACHINE.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, NPD_FUELLING_MACHINE.get(), FoundryDevice::fluids);
    }

    /** Тип для четырёх блоков с одной логикой: старая серия, ВВЭР-1000, ВВЭР-70 и NPD. */
    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> a, Supplier<? extends Block> b,
            Supplier<? extends Block> c, Supplier<? extends Block> d) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, a.get(), b.get(), c.get(), d.get()).build(null));
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }

    /** Тип для нескольких блоков: старая серия и машина реального масштаба с той же логикой. */
    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> a, Supplier<? extends Block> b) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, a.get(), b.get()).build(null));
    }

    /** Тип для трёх блоков: старая серия, ВВЭР-1000 и прототип реального масштаба с той же логикой. */
    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> a, Supplier<? extends Block> b,
            Supplier<? extends Block> c) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, a.get(), b.get(), c.get()).build(null));
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> a, Supplier<? extends Block> b,
            Supplier<? extends Block> c, Supplier<? extends Block> d) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, a.get(), b.get(), c.get(), d.get()).build(null));
    }
}
