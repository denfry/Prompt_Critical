package dev.denfry.promptcritical.mod.safety;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Переносной вентилятор {@code portable_ventilator} с рукавом (13 §13.2.6): ПКМ — пуск и останов; работающий в
 * {@value ManwayBlockEntity#VENTILATOR_RANGE} блоках от открытого люка подаёт в замкнутое пространство
 * {@value dev.denfry.promptcritical.sim.ops.ConfinedSpaceAtmosphere#VENTILATOR_M3S} м³/с воздуха [допущение:
 * питание 0,4 кВ (07) не моделируется — вентилятор с собственным приводом].
 */
public final class PortableVentilatorBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    /** Двигатель переносного вентилятора, Вт: от розетки рядом с люком. Розетка или силовой щит рядом; при недостатке машина работает медленнее. */
    public static final double POWER_W = 2.2e3;
    private boolean powered = true;
    public PortableVentilatorBlockEntity(BlockPos pos, BlockState state) {
        super(SafetyBlockEntities.PORTABLE_VENTILATOR.get(), pos, state);
    }

    /** Вентилятор включён и (на сервере) получает питание. */
    public boolean running() {
        return switchedOn() && (level == null || level.isClientSide || powered);
    }

    /** Выключатель вентилятора включён. */
    public boolean switchedOn() {
        BlockState s = getBlockState();
        return s.hasProperty(PcProperties.RUNNING) && s.getValue(PcProperties.RUNNING);
    }

    public void setRunning(boolean on) {
        BlockState s = getBlockState();
        if (level != null && s.hasProperty(PcProperties.RUNNING) && s.getValue(PcProperties.RUNNING) != on)
            level.setBlock(worldPosition, s.setValue(PcProperties.RUNNING, on), Block.UPDATE_ALL);
    }

    @Override
    public InteractionResult use(Player player) {
        setRunning(!switchedOn());
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, switchedOn() ? 1.0f : 0.8f);
        player.displayClientMessage(SafetyText.msg(switchedOn() ? "ventilator_on" : "ventilator_off"), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void tickDevice() {
        if (level != null && !level.isClientSide && switchedOn()) powered = dev.denfry.promptcritical.mod.grid.MachinePower.powered(level, worldPosition, POWER_W);
        if (level != null && level.isClientSide && running() && level.random.nextInt(4) == 0)
            level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 0.8,
                    worldPosition.getZ() + 0.5, 0, 0.05, 0);
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
}
