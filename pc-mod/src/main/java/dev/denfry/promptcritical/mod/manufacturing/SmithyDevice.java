package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Устройство кузницы: состояние процесса живёт в самом BE (своего узла в топологии симуляции у ручной ступени нет —
 * один кузнец, один горн), склад под слоты щита, рука игрока кладёт в склад.
 */
public abstract class SmithyDevice extends BlockEntity implements FoundryDevice, PanelHost,
        dev.denfry.promptcritical.mod.client.whole.WholeModelHost {
    // --- цельная модель (реальный масштаб) ---

    /** Id цельной модели у машины реального масштаба (блок {@code *_real}); у старой — null. */
    @Override
    @Nullable
    public String wholeModelId() {
        return dev.denfry.promptcritical.mod.client.whole.WholeModelHost.idOf(getBlockState());
    }

    @Override
    public Direction wholeModelFacing() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof dev.denfry.promptcritical.mod.block.PcBlock b ? b.facing(s) : Direction.NORTH;
    }

    /** Свет — с фасада, перед контроллером. */
    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(wholeModelFacing());
    }

    protected final MachineInventory inv;

    protected SmithyDevice(BlockEntityType<?> type, BlockPos pos, BlockState state, MachineInventory.Role[] roles,
            java.util.function.BiPredicate<Integer, ItemStack> filter) {
        super(type, pos, state);
        inv = new MachineInventory(roles, filter, this::setChanged);
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return inv.external();
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if ((Smithy.forgeable(stack) || Smithy.drawable(stack)) && stack.getCount() > 1) {
            // поковку, прокат и пруток кладут по одному куску, остальное остаётся в руке
            ItemStack rest = inv.insertFromPlayer(stack.split(1));
            stack.grow(rest.getCount());
            player.setItemInHand(hand, stack);
        } else player.setItemInHand(hand, inv.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public Component panelTitle() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public BlockPos panelPos() {
        return worldPosition;
    }

    @Override
    public boolean panelValid(Player player) {
        return PanelHost.near(this, player);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inv.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inventory")) inv.load(registries, tag.getCompound("inventory"));
    }
}
