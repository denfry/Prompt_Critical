package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.sim.metallurgy.BlastFurnaceUnit;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Засыпной аппарат {@code blast_furnace_top} 3×2×3 (08 §8.5.2): приёмная воронка колошника. Шихту (агломерат, руду,
 * холодный кокс, флюс) принимает воронкой Create или из склада контрапции-скипа над аппаратом и передаёт в бункер
 * колошника домны ({@link BlastFurnaceUnit}); колоши набираются по рудной нагрузке со щита.
 */
public final class BlastFurnaceTopBlockEntity extends IronworksBlockEntity {
    private static final int SKIP_PERIOD = 10, SKIP_ITEMS = 8;

    public BlastFurnaceTopBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.BLAST_FURNACE_TOP.get(), pos, state);
    }

    @Nullable
    private BlastFurnaceControllerBlockEntity controller() {
        return BlastFurnaceControllerBlockEntity.find(level, worldPosition, s -> s.top().equals(worldPosition));
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        if (level.getGameTime() % SKIP_PERIOD != 0) return;
        List<IItemHandlerModifiable> skips = Ironworks.contraptionStores(level,
                new AABB(worldPosition).inflate(1, 0, 1).expandTowards(0, 3, 0));
        if (skips.isEmpty()) return;
        BlastFurnaceControllerBlockEntity c = controller();
        BlastFurnaceUnit u = c == null ? null : c.unit();
        if (u == null) return;
        int moved = 0;
        for (IItemHandlerModifiable st : skips)
            for (int slot = 0; slot < st.getSlots() && moved < SKIP_ITEMS; slot++)
                while (moved < SKIP_ITEMS && !st.getStackInSlot(slot).isEmpty() && c.charge(u, st.getStackInSlot(slot))) {
                    st.extractItem(slot, 1, false);
                    moved++;
                }
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return new Hopper();
    }

    @Override
    public InteractionResult use(Player player) {
        BlastFurnaceControllerBlockEntity c = controller();
        BlastFurnaceUnit u = c == null ? null : c.unit();
        if (u == null) {
            player.displayClientMessage(IronworksText.msg("bf_top_unlinked"), true);
            return InteractionResult.SUCCESS;
        }
        player.displayClientMessage(IronworksText.msg("bf_top", IronworksText.num(u.hopperOreKg()), IronworksText.num(u.hopperCokeKg()),
                IronworksText.num(u.hopperLimestoneKg()), IronworksText.num(u.cokeLayerKg())), true);
        return InteractionResult.SUCCESS;
    }

    /** Воронка колошника: принимает поштучно, пока в бункере есть место. */
    private final class Hopper implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !isValid(stack)) return stack;
            BlastFurnaceControllerBlockEntity c = controller();
            BlastFurnaceUnit u = c == null ? null : c.unit();
            if (u == null || u.furnace() == null) return stack;
            if (simulate) return c.canCharge(u, stack) ? stack.copyWithCount(stack.getCount() - 1) : stack;
            int n = 0;
            while (n < stack.getCount() && c.charge(u, stack)) n++;
            return stack.copyWithCount(stack.getCount() - n);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isValid(stack);
        }

        private static boolean isValid(ItemStack s) {
            return s.is(IronworksTags.FURNACE_ORE) || s.is(IronworksTags.FLUX) || BlastFurnaceControllerBlockEntity.isColdCoke(s);
        }
    }
}
