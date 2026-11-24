package dev.denfry.promptcritical.mod.nuclear;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.client.whole.RealScaleBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.radiation.ShieldedContents;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Предмет топливного цикла, поставленный в мир в натуральную величину: баллон UF₆ 30B на подкладках
 * ({@code uf6_cylinder_30b_real}) и ТВС ВВЭР-440 стоймя ({@code vver440_fuel_assembly_real}). Блок хранит сам предмет со
 * всеми данными (заряд UF₆, партия ТВС): ставят его Shift + ПКМ предметом по верхней грани, ПКМ пустой рукой —
 * забрать обратно; сломанный блок роняет предмет. Сам блок отдельно не выпадает.
 */
public final class PlacedItemBlockEntity extends DeviceBlockEntity implements FoundryDevice, RealScaleBlockEntity, ShieldedContents {
    private final ItemStackHandler slot = new ItemStackHandler(1) {
        @Override
        public int getSlotLimit(int s) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int s) {
            setChanged();
        }
    };

    public PlacedItemBlockEntity(BlockPos pos, BlockState state) {
        super(NuclearBlockEntities.PLACED_ITEM.get(), pos, state);
    }

    public ItemStack stack() {
        return slot.getStackInSlot(0);
    }

    /** Блок, которым ставится предмет; {@code null} — предмет так не ставится. */
    @Nullable
    static Block blockFor(ItemStack stack) {
        if (stack.is(PcItems.UF6_CYLINDER_30B.get())) return PcBlocks.UF6_CYLINDER_30B_REAL.get();
        if (stack.is(PcItems.FUEL_ASSEMBLY.get())) return PcBlocks.VVER440_FUEL_ASSEMBLY_REAL.get();
        return null;
    }

    /** Shift + ПКМ баллоном 30B или ТВС по верхней грани блока — положить его в мир. */
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        Block block = blockFor(held);
        if (block == null || !player.isShiftKeyDown() || event.getFace() != Direction.UP) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel().isClientSide) return;
        place(event.getLevel(), player, event.getHand(), event.getHitVec(), held, block);
    }

    /** Поставить {@code block} с предметом внутри; false — места нет. */
    static boolean place(Level level, Player player, InteractionHand hand, BlockHitResult hit, ItemStack held, Block block) {
        BlockPlaceContext ctx = new BlockPlaceContext(new UseOnContext(player, hand, hit));
        if (!ctx.canPlace()) return false;
        BlockState st = block.getStateForPlacement(ctx);
        if (st == null) return false;
        BlockPos at = ctx.getClickedPos();
        if (!level.setBlock(at, st, Block.UPDATE_ALL)) return false;
        if (level.getBlockEntity(at) instanceof PlacedItemBlockEntity be) be.slot.setStackInSlot(0, held.copyWithCount(1));
        if (!player.isCreative()) held.shrink(1);
        level.playSound(null, at, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 0.7f);
        return true;
    }

    @Override
    public void tickDevice() {
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** ПКМ пустой рукой — забрать предмет; блок снимается. */
    @Override
    public InteractionResult use(Player player) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack s = slot.extractItem(0, 1, false);
        if (!s.isEmpty() && !player.addItem(s)) player.drop(s, false);
        level.destroyBlock(worldPosition, false);
        return InteractionResult.SUCCESS;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public ItemStackHandler contents() {
        return slot;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("item", slot.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("item")) slot.deserializeNBT(registries, tag.getCompound("item"));
    }
}
