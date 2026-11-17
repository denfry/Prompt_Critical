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
