package dev.denfry.promptcritical.mod.drilling;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.client.whole.RealScaleBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.drilling.Well;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Буровая лебёдка реального масштаба {@code drawworks_real} (У2-5-5, 6 × 3 × 3,
 * art/blockbench/drilling/drawworks_real.author.js). Своей симуляции у лебёдки нет, как и у старой {@code drawworks}:
 * пульт бурильщика считает её частью буровой ({@link DrillersConsoleBlockEntity#scan()}), а нагрузку на крюке и подачу
 * долота ведёт скважина. Лебёдка лишь показывает их на своём щите — индикатор веса, высоту талевого блока над ротором
 * (блок проходит свечу {@value #STAND_M} м за наращивание) и тормоз — и отпускает тормоз и травит канат, пока идёт
 * бурение ({@code running}).
 */
public final class DrawworksRealBlockEntity extends BlockEntity implements FoundryDevice, PanelHost, RealScaleBlockEntity {
    /** Длина ведущей трубы над ротором, м: столько проходит талевый блок от наращивания до наращивания. */
    public static final double STAND_M = 9;
    private static final int PERIOD = 20;

    private double hookN, depthM, ropMs;
    private boolean rig;

    public DrawworksRealBlockEntity(BlockPos pos, BlockState state) {
        super(DrillingBlockEntities.DRAWWORKS_REAL.get(), pos, state);
    }

    /** Доля свечи, пройденная от наращивания: 0 — блок наверху, 1 — ведущая труба ушла в ротор. */
    public static double kellyDown(double depthM) {
        double f = depthM / STAND_M;
        return f - Math.floor(f);
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel) || level.getGameTime() % PERIOD != 0) return;
        DrillersConsoleBlockEntity c = DrillersConsoleBlockEntity.near(level, worldPosition);
        Well w = c == null ? null : c.well();
        rig = w != null;
        hookN = rig ? w.hookLoadN() : 0;
        depthM = rig ? w.holeDepthM() : 0;
        ropMs = rig ? w.ropMs() : 0;
        BlockState st = getBlockState();
        boolean running = ropMs > 0;
        if (st.getValue(PcProperties.RUNNING) != running) level.setBlock(worldPosition, st.setValue(PcProperties.RUNNING, running), Block.UPDATE_ALL);
    }

    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(wholeModelFacing(), 2);
    }

    @Override
    public PanelLayout panelLayout() {
        return DrillingPanels.DRAWWORKS_REAL;
    }

    @Override
    public Component panelTitle() {
        return getBlockState().getBlock().getName();
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
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[DrillingPanels.W_HOOK_T10] = (int) Math.round(hookN / 9.80665e3 * 10);
        out[DrillingPanels.W_BLOCK_PERMIL] = (int) Math.round(1000 * (1 - kellyDown(depthM)));
        out[DrillingPanels.W_DEPTH_X10] = (int) Math.round(depthM * 10);
        out[DrillingPanels.W_ROP_X10] = (int) Math.round(ropMs * 3600 * 10);
        out[DrillingPanels.W_FLAGS] = (ropMs > 0 ? 0 : 1 << DrillingPanels.WF_BRAKE) | (rig ? 1 << DrillingPanels.WF_RIG : 0);
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
        return InteractionResult.PASS;
    }
}
