package dev.denfry.promptcritical.mod.steelworks;

import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.metallurgy.BatchFurnaceUnit;
import dev.denfry.promptcritical.sim.metallurgy.StockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Методическая печь {@code reheating_furnace} 3×2×3 (08 §8.7.2): греет слитки и заготовки до уставки
 * (1150–1250 °C); заготовка, дошедшая до уставки − {@value #READY_MARGIN_K} К, выходит через окно фасада на рольганг
 * перед печью (нет рольганга — в выходы). Угар и пережог выше 1300 °C — {@link StockState#reheated}. Садка — из
 * входа или с рольганга позади печи.
 */
public class ReheatingFurnaceBlockEntity extends BatchFurnaceBlockEntity implements dev.denfry.promptcritical.mod.ops.OpsMachine {
    /** Толкатель, вентиляторы горелок и дымосос методической печи, Вт. */
    @Override
    protected double ratedPowerW() {
        return 110e3;
    }

    public static final double READY_MARGIN_K = 30;

    public ReheatingFurnaceBlockEntity(BlockPos pos, BlockState state) {
        this(SteelworksBlockEntities.REHEATING_FURNACE.get(), pos, state);
    }

    protected ReheatingFurnaceBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, BatchFurnaceUnit.Spec.REHEATING.capacity());
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return Steelworks.isStock(stack);
    }

    // --- пусконаладка (план HP2.3) ---

    /** Непринятую холодную печь в профиле licensing не греют; пробная работа — горелки горят, нагрузка — к уставке. */
    @Override
    protected boolean heatPermitted(BatchFurnaceUnit u) {
        boolean heat = dev.denfry.promptcritical.mod.ops.OpsActions.processPermit(level, this, 0)
                || u.furnace().tempK() >= dev.denfry.promptcritical.mod.ops.OpsActions.WARM_K;
        dev.denfry.promptcritical.mod.ops.OpsActions.trial(level, this, u.gasSupplyLit(), u.furnace().tempK() / u.setpointK(), false);
        return heat;
    }

    @Override
    public dev.denfry.promptcritical.sim.ops.UnitKind opsKind() {
        return dev.denfry.promptcritical.sim.ops.UnitKind.REHEATING_FURNACE;
    }

    /** Остановлена — горелки погашены (условие пневмоиспытания). */
    @Override
    public boolean opsStopped() {
        BatchFurnaceUnit u = level != null && !level.isClientSide ? unit() : null;
        return u == null || !u.gasSupplyLit();
    }

    @Override
    public net.minecraft.world.phys.Vec3 opsBearingPos() {
        return net.minecraft.world.phys.Vec3.atCenterOf(worldPosition);
    }

    @Override
    protected double massKg(ItemStack s) {
        return Ironworks.bulkKg();
    }

    @Override
    protected double thicknessM(ItemStack s) {
        SteelworksComponents.SteelStock st = Steelworks.stock(s);
        return st == null ? 0.15 : Math.min(st.thicknessM(), st.widthM());
    }

    @Override
    protected void discharge(BatchFurnaceUnit u, long now) {
        Direction f = facing();
        PieceReceiver table = Steelworks.receiver(level, Steelworks.world(worldPosition, f, 0, 0, -2));
        for (int i = 0; i < places.getSlots(); i++) {
            if (places.getStackInSlot(i).isEmpty() || !u.furnace().occupied(i)) continue;
            double t = u.furnace().pieceTempK(i);
            if (t < u.setpointK() - READY_MARGIN_K) continue;
            ItemStack s = places.getStackInSlot(i);
            StockState st = Steelworks.state(s, now);
            if (st == null) continue;
            ItemStack piece = Steelworks.piece(st.withTemp(t).reheated(t), Steelworks.stock(s).heatNo(), now);
            if (table != null) {
                if (!table.acceptPiece(piece, f)) return;
            } else if (inv.outputFits(piece)) {
                toOutput(piece);
            } else {
                return;
            }
            u.furnace().unload(i);
            places.setStackInSlot(i, ItemStack.EMPTY);
            return;
        }
    }

    @Override
    public PanelLayout panelLayout() {
        return SteelworksPanels.REHEATING;
    }
}
