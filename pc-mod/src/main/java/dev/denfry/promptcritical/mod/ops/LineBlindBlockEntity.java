package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.sim.ops.EnergyKind;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Поворотная заглушка («очки») {@code line_blind} на фланце (13 §13.1.2): ставится между трубой и аппаратом по оси
 * фасада. В положении «открыто» жидкость проходит насквозь — что приходит с одной стороны, уходит в аппарат с другой;
 * глухой стороной линия отсечена (позитивная изоляция для опасных сред). Положение — {@code open} блока; точка
 * принимает замок LOTO, запертую заглушку не повернуть. Перестановка мгновенная [упрощение: GDD требует 10 с работы
 * при давлении в линии ≤ 0,05 МПа, давление в трубах Create не известно].
 */
public class LineBlindBlockEntity extends DeviceBlockEntity implements FoundryDevice, IsolationPoint {
    private boolean forwarding;

    public LineBlindBlockEntity(BlockPos pos, BlockState state) {
        super(OpsBlockEntities.LINE_BLIND.get(), pos, state);
    }

    public boolean open() {
        BlockState s = getBlockState();
        return s.hasProperty(BlockStateProperties.OPEN) && s.getValue(BlockStateProperties.OPEN);
    }

    private Direction facing() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof PcBlock pc ? pc.facing(s) : Direction.NORTH;
    }

    // --- проход жидкости ---

    /** Сторона трубы: фасад и тыл; жидкость уходит в соседа с противоположной стороны. */
    @Override
    @Nullable
    public IFluidHandler fluids(@Nullable Direction side) {
        if (side == null || side.getAxis() != facing().getAxis()) return null;
        return new PassThrough(side);
    }

    @Nullable
    private IFluidHandler beyond(Direction from) {
        if (level == null || !open() || forwarding) return null;
        Direction out = from.getOpposite();
        return level.getCapability(Capabilities.FluidHandler.BLOCK, worldPosition.relative(out), from);
    }

    private final class PassThrough implements IFluidHandler {
        private final Direction from;

        PassThrough(Direction from) {
            this.from = from;
        }

        private <T> T through(java.util.function.Function<IFluidHandler, T> f, T none) {
            IFluidHandler h = beyond(from);
            if (h == null) return none;
            forwarding = true; // цепочка заглушек не зацикливается
            try {
                return f.apply(h);
            } finally {
                forwarding = false;
            }
        }

        @Override
        public int getTanks() {
            return through(IFluidHandler::getTanks, 0);
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return through(h -> h.getFluidInTank(tank), FluidStack.EMPTY);
        }

        @Override
        public int getTankCapacity(int tank) {
            return through(h -> h.getTankCapacity(tank), 0);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return through(h -> h.isFluidValid(tank, stack), false);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return through(h -> h.fill(resource, action), 0);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return through(h -> h.drain(resource, action), FluidStack.EMPTY);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return through(h -> h.drain(maxDrain, action), FluidStack.EMPTY);
        }
    }

    // --- руки ---

    @Override
    public InteractionResult use(Player player) {
        if (Lockouts.locked(level, worldPosition)) {
            player.displayClientMessage(Lockouts.lockedMessage(level, worldPosition), true);
            return InteractionResult.SUCCESS;
        }
        boolean open = !open();
        level.setBlock(worldPosition, getBlockState().setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
        level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.7f, 0.7f);
        player.displayClientMessage(LockoutText.msg(open ? "blind_open" : "blind_closed"), true);
        return InteractionResult.SUCCESS;
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
    public void tickDevice() {
    }

    // --- LOTO ---

    @Override
    public EnergyKind energy() {
        return EnergyKind.PRESSURE;
    }

    @Override
    public boolean isolatedPosition() {
        return !open();
    }

    /** Глухая заглушка — позитивная изоляция: среда за ней не проходит. */
    @Override
    public boolean noEnergy() {
        return !open();
    }
}
