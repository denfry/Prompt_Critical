package dev.denfry.promptcritical.mod.safety;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Факельный сепаратор капель {@code flare_knockout_drum} (13 §13.4.3): горизонтальный аппарат 3×1×1 перед стволом.
 * Капли конденсата крупнее 300–600 мкм (API 521) оседают в нём; ёмкость {@value #CAPACITY_MB} л, откачка — трубой
 * (газовый бензин). Полный аппарат больше не ловит — жидкость уходит в факел.
 */
public final class FlareKnockoutDrumBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    public static final int CAPACITY_MB = 4000;

    private final FluidTank tank = new FluidTank(CAPACITY_MB, s -> false) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            updateLevel();
        }
    };
    private double fraction;

    public FlareKnockoutDrumBlockEntity(BlockPos pos, BlockState state) {
        super(SafetyBlockEntities.FLARE_KNOCKOUT_DRUM.get(), pos, state);
    }

    public int liquidMb() {
        return tank.getFluidAmount();
    }

    public boolean full() {
        return tank.getFluidAmount() >= CAPACITY_MB;
    }

    /** Для испытаний: залить аппарат. */
    public void setLiquid(int mb) {
        tank.setFluid(mb <= 0 ? FluidStack.EMPTY : new FluidStack(PcFluids.NATURAL_GASOLINE.source.get(), Math.min(mb, CAPACITY_MB)));
        updateLevel();
    }

    /**
     * Поймать {@code litres} конденсата за секунду.
     *
     * @return весь пойман (аппарат не полон)
     */
    public boolean catchLiquid(double litres) {
        if (full()) return false;
        fraction += litres;
        int mb = (int) fraction;
        if (mb > 0) {
            fraction -= mb;
            int room = CAPACITY_MB - tank.getFluidAmount();
            tank.setFluid(new FluidStack(PcFluids.NATURAL_GASOLINE.source.get(), tank.getFluidAmount() + Math.min(mb, room)));
            setChanged();
            updateLevel();
            return mb <= room;
        }
        return true;
    }

    private void updateLevel() {
        if (level == null || level.isClientSide) return;
        int l = tank.getFluidAmount() >= CAPACITY_MB * 2 / 3 ? 2 : tank.getFluidAmount() >= CAPACITY_MB / 3 ? 1 : 0;
        BlockState s = getBlockState();
        if (s.hasProperty(PcProperties.MUD_LEVEL) && s.getValue(PcProperties.MUD_LEVEL) != l)
            level.setBlock(worldPosition, s.setValue(PcProperties.MUD_LEVEL, l), Block.UPDATE_CLIENTS);
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
        if (!level.isClientSide) player.displayClientMessage(SafetyText.msg("ko_level", tank.getFluidAmount(), CAPACITY_MB), true);
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("tank")) tank.readFromNBT(registries, tag.getCompound("tank"));
    }
}
