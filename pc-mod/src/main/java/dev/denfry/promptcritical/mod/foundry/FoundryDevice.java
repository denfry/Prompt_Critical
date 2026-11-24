package dev.denfry.promptcritical.mod.foundry;

import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/** BE литейки и цеха: склад для автоматики, руки игрока, тик. */
public interface FoundryDevice {
    /** Склад для воронок и хопперов; {@code null} — нет. */
    @Nullable
    IItemHandler items(@Nullable Direction side);

    /** Бак для труб; {@code null} — нет. */
    @Nullable
    default IFluidHandler fluids(@Nullable Direction side) {
        return null;
    }

    /**
     * Бак за патрубком машины реального масштаба: клетка (mx, my, mz) и грань {@code modelFace} — в координатах модели
     * ({@link dev.denfry.promptcritical.mod.multiblock.WholePorts}), {@code side} — мировая грань. Машине, у которой на
     * одной грани несколько патрубков разных жидкостей, клетка различает их; по умолчанию — {@link #fluids}.
     */
    @Nullable
    default IFluidHandler fluidsAt(int mx, int my, int mz, Direction modelFace, Direction side) {
        return fluids(side);
    }

    /** Предмет можно положить в машину рукой (проверяется на обеих сторонах). */
    boolean accepts(ItemStack stack);

    /** Игрок кладёт предмет из руки. Только сервер. */
    ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack);

    /** Игрок кликнул пустой рукой: забрать продукцию, показать состояние. Только сервер. */
    InteractionResult use(Player player);

    /** Тик BE (на обеих сторонах). */
    void tickDevice();
}
