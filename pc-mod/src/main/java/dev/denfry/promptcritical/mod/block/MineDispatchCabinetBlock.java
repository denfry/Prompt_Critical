package dev.denfry.promptcritical.mod.block;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.mine.MinePanels;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * {@code mine_dispatch_cabinet} (08 §8.2.5): диспетчерская шахты. Показывает сводку по газам и проветриванию;
 * {@code alarm} — метан выше уставки АГЗ где-либо в выработках. Пустая рука — щит с приборами газа и водоотлива,
 * с Shift — сводка в чат. Пульты и CC — 03c, 03b.
 */
public class MineDispatchCabinetBlock extends PcDeviceBlock {
    protected MineDispatchCabinetBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static MineDispatchCabinetBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new MineDispatchCabinetBlock(props, spec));
    }

    /** Щит шкафа: показания берёт из шахты, к которой привязан шкаф. */
    private record Panel(ServerLevel level, BlockPos pos, Component title) implements PanelHost {
        @Override
        public PanelLayout panelLayout() {
            return MinePanels.CABINET;
        }

        @Override
        public Component panelTitle() {
            return title;
        }

        @Override
        public BlockPos panelPos() {
            return pos;
        }

        @Override
        public void readPanel(int[] out) {
            SimLevelBinding b = SimBindings.get(level);
            if (b == null) java.util.Arrays.fill(out, 0);
            else b.mines().readCabinet(pos, out);
        }

        @Override
        public boolean panelValid(Player player) {
            return level.getBlockState(pos).getBlock() instanceof MineDispatchCabinetBlock
                    && player.distanceToSqr(pos.getCenter()) <= PanelHost.REACH_SQ;
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        SimLevelBinding b = SimBindings.get((ServerLevel) level);
        if (b == null) return InteractionResult.PASS;
        BlockPos cabinet = controllerPos(state, pos);
        if (player.isShiftKeyDown()) b.mines().report(cabinet, player);
        else if (player instanceof ServerPlayer sp) PanelHost.open(sp, new Panel((ServerLevel) level, cabinet, getName()));
        return InteractionResult.CONSUME;
    }
}
