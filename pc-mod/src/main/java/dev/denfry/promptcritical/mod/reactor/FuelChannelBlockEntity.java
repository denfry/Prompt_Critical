package dev.denfry.promptcritical.mod.reactor;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.reactor.PileLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Графитовый блок с каналами блочков {@code fuel_channel}: до {@link PileLayout#FULL_LOAD} кассет урановых блочков
 * (полная загрузка — решётка T0, 24 канала на м²). Загрузка — со щита, рукой или воронкой; пульт котла читает её
 * при каждом обходе кладки.
 */
public final class FuelChannelBlockEntity extends IronworksBlockEntity implements PanelHost {
    private final ItemStackHandler slugs = new ItemStackHandler(PileLayout.FULL_LOAD) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(PcItems.URANIUM_SLUG.get());
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public FuelChannelBlockEntity(BlockPos pos, BlockState state) {
        super(ReactorBlockEntities.FUEL_CHANNEL.get(), pos, state);
    }

    /**
     * Уран одной кассеты, кг: решётка T0 при полной загрузке — 1,5 % объёма блока 1 м³ (блочки ⌀28 мм, 24 канала/м²) при
     * 19,05 г/см³, на {@link PileLayout#FULL_LOAD} кассет.
     */
    public static final double CASSETTE_U_KG = 0.015 * 1e6 * 19.05 / PileLayout.FULL_LOAD / 1000;
    public static final double U_METAL_DENSITY = 19.05;

    /**
     * Облучение блочков за шаг (12 §12.0.2): делений в узле в секунду делятся поровну между кассетами; каждая копит
     * продукты деления ({@link dev.denfry.promptcritical.sim.radiation.SpentFuel#irradiate}) и выгорание. Вынутая
     * кассета — источник излучения.
     */
    public void irradiate(double nodeFissionsPerS, double dtS, long now) {
        int n = load();
        if (n <= 0 || !(nodeFissionsPerS > 0) || !(dtS > 0)) return;
        double f = nodeFissionsPerS / n;
        double mwd = f * dev.denfry.promptcritical.sim.neutronics.PointKinetics.FISSION_ENERGY * dtS / 86400 / 1e6;
        for (int i = 0; i < slugs.getSlots(); i++) {
            ItemStack s = slugs.getStackInSlot(i);
            if (s.isEmpty()) continue;
            var a = s.get(dev.denfry.promptcritical.mod.environment.ActivityData.ACTIVITY.get());
            var inv = a != null ? a.inventory(now)
                    : new dev.denfry.promptcritical.sim.radiation.ActivityInventory(java.util.Map.of(), CASSETTE_U_KG, U_METAL_DENSITY);
            var next = dev.denfry.promptcritical.sim.radiation.SpentFuel.irradiate(inv, f, dtS);
            ItemStack out = s.copy();
            out.set(dev.denfry.promptcritical.mod.environment.ActivityData.ACTIVITY.get(),
                    dev.denfry.promptcritical.mod.environment.ActivityData.of(next, now));
            out.set(dev.denfry.promptcritical.mod.isotopes.IsotopeComponents.BURNUP.get(),
                    out.getOrDefault(dev.denfry.promptcritical.mod.isotopes.IsotopeComponents.BURNUP.get(), 0.0) + mwd / CASSETTE_U_KG);
            slugs.setStackInSlot(i, out);
        }
    }

    /** Кассет в канале. */
    public int load() {
        int n = 0;
        for (int i = 0; i < slugs.getSlots(); i++) if (!slugs.getStackInSlot(i).isEmpty()) n++;
        return n;
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return slugs;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.URANIUM_SLUG.get());
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        for (int i = 0; i < slugs.getSlots() && !stack.isEmpty(); i++) {
            if (slugs.getStackInSlot(i).isEmpty()) {
                slugs.setStackInSlot(i, stack.split(1));
                break;
            }
        }
        player.displayClientMessage(ReactorPanels.MSG_CHANNEL.get(load(), PileLayout.FULL_LOAD), true);
        return ItemInteractionResult.SUCCESS;
    }

    /** Shift + ПКМ: выгрузить одну кассету в руки. */
    @Override
    public InteractionResult use(Player player) {
        for (int i = slugs.getSlots() - 1; i >= 0; i--) {
            ItemStack s = slugs.getStackInSlot(i);
            if (s.isEmpty()) continue;
            slugs.setStackInSlot(i, ItemStack.EMPTY);
            if (!player.addItem(s)) player.drop(s, false);
            break;
        }
        player.displayClientMessage(ReactorPanels.MSG_CHANNEL.get(load(), PileLayout.FULL_LOAD), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return ReactorPanels.CHANNEL;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return slugs;
    }

    @Override
    public void readPanel(int[] out) {
        out[ReactorPanels.C_LOAD] = load();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("slugs", slugs.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("slugs")) slugs.deserializeNBT(registries, tag.getCompound("slugs"));
    }
}
