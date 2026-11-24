package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.client.whole.RealScaleBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.grid.MachinePower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Промежуточный теплообменник «натрий–натрий» БОР-60 ({@code bor60_ihx_real}): одна петля второго контура. Тепло
 * через него считает реактор ({@code SodiumFastReactor}); здесь — насос второго контура этой петли, электрический,
 * от щита площадки: без питания второй контур петли стоит (остаётся естественная циркуляция).
 */
public final class IhxBlockEntity extends DeviceBlockEntity implements FoundryDevice, RealScaleBlockEntity {
    /** Насос второго натриевого контура петли, Вт [est]. */
    public static final double SECONDARY_PUMP_W = 150e3;
    private boolean powered;
    private double hotC = Double.NaN, coldC = Double.NaN;

    public IhxBlockEntity(BlockPos pos, BlockState state) {
        super(NppBlockEntities.IHX.get(), pos, state);
    }

    /** Насос второго контура петли запитан. */
    public boolean pumping() {
        return powered;
    }

    /** Температуры второго контура для сводки, °C (их задаёт реактор). */
    void report(double hotC, double coldC) {
        this.hotC = hotC;
        this.coldC = coldC;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel) || level.getGameTime() % 20 != 0) return;
        powered = MachinePower.powered(level, worldPosition, SECONDARY_PUMP_W);
        BlockState st = getBlockState();
        if (st.hasProperty(PcProperties.RUNNING) && st.getValue(PcProperties.RUNNING) != powered)
            level.setBlock(worldPosition, st.setValue(PcProperties.RUNNING, powered), 3);
    }

    @Override
    @Nullable
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
    public InteractionResult use(Player player) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        player.displayClientMessage(NppText.msg(powered ? "ihx_status" : "ihx_no_power",
                Double.isNaN(hotC) ? "—" : String.format(java.util.Locale.ROOT, "%.0f", hotC),
                Double.isNaN(coldC) ? "—" : String.format(java.util.Locale.ROOT, "%.0f", coldC)), true);
        return InteractionResult.SUCCESS;
    }
}
