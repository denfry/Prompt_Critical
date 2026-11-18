package dev.denfry.promptcritical.mod.safety;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
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
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Гидрозатвор факела {@code flare_seal_drum} (13 §13.4.3): слой воды не пускает воздух и пламя из ствола назад в
 * коллектор. Вода — трубой, {@value #CAPACITY_MB} мБ; при сбросе убывает. После проскока пламени затвор снова
 * «взводится», только когда его долили выше порога.
 */
public final class FlareSealDrumBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    public static final int CAPACITY_MB = 1000;

    private final FluidTank tank = new FluidTank(CAPACITY_MB, s -> s.getFluid().isSame(Fluids.WATER)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private double loss;
    private boolean armed = true;

    public FlareSealDrumBlockEntity(BlockPos pos, BlockState state) {
        super(SafetyBlockEntities.FLARE_SEAL_DRUM.get(), pos, state);
    }

    public int waterMb() {
        return tank.getFluidAmount();
    }

    public void setWater(int mb) {
        tank.setFluid(mb <= 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, Math.min(mb, CAPACITY_MB)));
        setChanged();
    }

    /** Потеря воды затвора за секунду сброса. */
    void lose(double mb) {
        loss += mb;
        int n = (int) loss;
        if (n > 0) {
            loss -= n;
            tank.drain(n, IFluidHandler.FluidAction.EXECUTE);
        }
        if (tank.getFluidAmount() >= FlareIgnitionPanelBlockEntity.SEAL_LOW_MB) armed = true;
    }

    /**
     * Проскок пламени происходит один раз до долива.
     *
     * @return проскок сейчас
     */
    boolean arm() {
        if (!armed) return false;
        armed = false;
        return true;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return tank;
    }

    @Override
    public void tickDevice() {
    }

    @Override
    public InteractionResult use(Player player) {
        if (!level.isClientSide) player.displayClientMessage(SafetyText.msg("seal_level", tank.getFluidAmount(), CAPACITY_MB), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putBoolean("armed", armed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("tank")) tank.readFromNBT(registries, tag.getCompound("tank"));
        armed = !tag.contains("armed") || tag.getBoolean("armed");
    }
}
