package dev.denfry.promptcritical.mod.steelworks;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/**
 * Агрегат, принимающий заготовку с рольганга (08 §8.7): следующий рольганг, клеть, печь, прошивной стан,
 * холодильник. Заготовка — один предмет с {@code steel_stock}.
 */
public interface PieceReceiver {
    /**
     * Принять заготовку, идущую в направлении {@code travel}; {@code false} — не принял (занят, не захватил,
     * холодная), заготовка остаётся у отправителя.
     */
    boolean acceptPiece(ItemStack piece, Direction travel);
}
