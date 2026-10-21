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
public final class ReheatingFurnaceBlockEntity extends BatchFurnaceBlockEntity {
    public static final double READY_MARGIN_K = 30;

    public ReheatingFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(SteelworksBlockEntities.REHEATING_FURNACE.get(), pos, state, BatchFurnaceUnit.Spec.REHEATING.capacity());
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return Steelworks.isStock(stack);
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
