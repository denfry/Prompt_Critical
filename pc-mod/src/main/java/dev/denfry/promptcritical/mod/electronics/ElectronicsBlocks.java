package dev.denfry.promptcritical.mod.electronics;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Кремний и микросхемы (11 §11.5): типы BE, способности для воронок и труб, общие помощники. */
public final class ElectronicsBlocks {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SubmergedArcFurnaceBlockEntity>> SUBMERGED_ARC_FURNACE =
            type("submerged_arc_furnace", SubmergedArcFurnaceBlockEntity::new, PcBlocks.SUBMERGED_ARC_FURNACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrichlorosilaneReactorBlockEntity>> TRICHLOROSILANE_REACTOR =
            type("trichlorosilane_reactor", TrichlorosilaneReactorBlockEntity::new, PcBlocks.TRICHLOROSILANE_REACTOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SiemensReactorBlockEntity>> SIEMENS_REACTOR =
            type("siemens_reactor", SiemensReactorBlockEntity::new, PcBlocks.SIEMENS_REACTOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CzochralskiPullerBlockEntity>> CZOCHRALSKI_PULLER =
            type("czochralski_puller", CzochralskiPullerBlockEntity::new, PcBlocks.CZOCHRALSKI_PULLER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaferSawBlockEntity>> WAFER_SAW =
            type("wafer_saw", WaferSawBlockEntity::new, PcBlocks.WAFER_SAW);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CleanroomAirlockBlockEntity>> CLEANROOM_AIRLOCK =
            type("cleanroom_airlock", CleanroomAirlockBlockEntity::new, PcBlocks.CLEANROOM_AIRLOCK);

    private ElectronicsBlocks() {
    }

    public static void register(IEventBus modBus) {
        ElectronicsComponents.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(ElectronicsBlocks::capabilities);
        ElectronicsText.registerTooltips();
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        for (var t : java.util.List.of(SUBMERGED_ARC_FURNACE.get(), CLEANROOM_AIRLOCK.get(), SIEMENS_REACTOR.get()))
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, t, FoundryDevice::items);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, TRICHLOROSILANE_REACTOR.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CZOCHRALSKI_PULLER.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, WAFER_SAW.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, TRICHLOROSILANE_REACTOR.get(), FoundryDevice::fluids);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, SIEMENS_REACTOR.get(), FoundryDevice::fluids);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CZOCHRALSKI_PULLER.get(), FoundryDevice::fluids);
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }

    /** Монокристаллический слиток (поликристалл пилить нельзя — его переплавляют). */
    public static boolean isMonoIngot(ItemStack s) {
        var i = s.get(ElectronicsComponents.INGOT.get());
        return s.is(PcItems.SILICON_INGOT.get()) && i != null && i.monocrystal();
    }

    /**
     * Склад для воронок: вставка — в первые {@code inputs} слотов (каждый проверяет предмет сам), выемка — только из
     * {@code outputCount} выходных начиная с {@code firstOutput}.
     */
    public static final class SlotsIo implements IItemHandler {
        private final ItemStackHandler inv;
        private final int inputs, firstOutput, outputCount;

        public SlotsIo(ItemStackHandler inv, int inputs, int firstOutput) {
            this(inv, inputs, firstOutput, 1);
        }

        public SlotsIo(ItemStackHandler inv, int inputs, int firstOutput, int outputCount) {
            this.inv = inv;
            this.inputs = inputs;
            this.firstOutput = firstOutput;
            this.outputCount = outputCount;
        }

        private boolean output(int slot) {
            return slot >= firstOutput && slot < firstOutput + outputCount;
        }

        @Override
        public int getSlots() {
            return inv.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inv.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot < inputs ? inv.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return output(slot) ? inv.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inv.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot < inputs && inv.isItemValid(slot, stack);
        }
    }
}
