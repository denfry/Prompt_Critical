package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.content.DecorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Светильник с питанием от щитка освещения: горит, пока ближайший щиток в радиусе группы под напряжением и его
 * автомат включён. Светильники с аккумулятором («Выход», аварийный) после пропадания сети светят ещё
 * {@link LampKind#batteryTicks()} тиков и заряжаются от сети вдвое дольше.
 */
public class LampBlock extends DecorBlock implements EntityBlock {
    /** Светильник опрашивает щиток раз в столько тиков. */
    static final int PERIOD = 20;
    private final LampKind kind;

    protected LampBlock(Properties props, Spec spec, double[] box, LampKind kind) {
        super(props, spec, box, false);
        this.kind = kind;
    }

    public static LampBlock create(Properties props, Spec spec, double[] box, LampKind kind) {
        return construct(spec, () -> new LampBlock(props, spec, box, kind));
    }

    public LampKind kind() {
        return kind;
    }

    @Override
    protected BlockState defaults(BlockState state) {
        BlockState s = super.defaults(state);
        return s.hasProperty(BlockStateProperties.LIT) ? s.setValue(BlockStateProperties.LIT, false) : s;
    }

    /** Включает и гасит щиток, а не сам светильник. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LampBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != GridBlockEntities.LAMP.get()) return null;
        return (l, p, s, be) -> ((LampBlockEntity) be).serverTick();
    }

    /** Состояние светильника: заряд аккумулятора аварийного блока. */
    public static class LampBlockEntity extends BlockEntity {
        private int battery;

        public LampBlockEntity(BlockPos pos, BlockState state) {
            super(GridBlockEntities.LAMP.get(), pos, state);
        }

        void serverTick() {
            if (level == null || !(getBlockState().getBlock() instanceof LampBlock lamp)) return;
            long now = level.getGameTime();
            if (Math.floorMod(now + worldPosition.hashCode(), PERIOD) != 0) return;
            LampKind k = lamp.kind();
            LightingBoardBlockEntity board = LightingBoards.nearest(level, worldPosition);
            boolean mains = board != null && board.feed(worldPosition, k.watts(), now)
                    // без щитка — от РИТЭГа рядом (маяки, 12 §12.5.1)
                    || board == null && dev.denfry.promptcritical.mod.isotopes.RtgBlockEntity.feedLamp(level, worldPosition, k.watts(), now);
            boolean lit = mains;
            if (k.batteryTicks() > 0) {
                int before = battery;
                if (mains) battery = Math.min(k.batteryTicks(), battery + PERIOD / 2);
                else battery = Math.max(0, battery - PERIOD);
                lit = mains || battery > 0;
                if (battery != before) setChanged();
            }
            BlockState s = getBlockState();
            if (s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT) != lit)
                level.setBlock(worldPosition, s.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_ALL);
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            if (battery > 0) tag.putInt("battery", battery);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            battery = tag.getInt("battery");
        }
    }
}
