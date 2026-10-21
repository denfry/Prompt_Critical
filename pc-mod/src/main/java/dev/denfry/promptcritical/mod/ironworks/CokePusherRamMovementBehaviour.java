package dev.denfry.promptcritical.mod.ironworks;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Штанга коксовыталкивателя {@code coke_pusher_ram} на контрапции Create (08 §8.4.1): поршень, gantry или вагон
 * подводят её к торцу камеры; когда головка (на 0,65 блока впереди) входит в торцевую клетку камеры, батарея
 * выталкивает пирог ({@link CokeOvenBlockEntity#ramPush}). Контрапция стоит, пока идёт выталкивание (3 с); неготовая
 * камера перегружает поршень — пирог прилипает, контрапция стоит 2 с.
 */
public final class CokePusherRamMovementBehaviour implements MovementBehaviour {
    private static final int PUSH_TICKS = 60, OVERLOAD_TICKS = 40;

    /** Счётчик остановки на время выталкивания (не сохраняется). */
    private static final class Stall {
        int ticks;

        Stall(int ticks) {
            this.ticks = ticks;
        }
    }

    @Override
    public Vec3 getActiveAreaOffset(MovementContext ctx) {
        return Vec3.atLowerCornerOf(ctx.state.getValue(BlockStateProperties.HORIZONTAL_FACING).getNormal()).scale(0.65);
    }

    @Override
    public void visitNewPosition(MovementContext ctx, BlockPos pos) {
        if (!(ctx.world instanceof ServerLevel level) || ctx.temporaryData instanceof Stall) return;
        if (!(level.getBlockEntity(pos) instanceof CokeOvenBlockEntity oven)) return;
        CokeOvenBlockEntity master = oven.master();
        if (master == null) return;
        CokeOvenBlockEntity.PushOutcome o = master.ramPush(pos, worldFacing(ctx));
        if (o == CokeOvenBlockEntity.PushOutcome.NONE) return;
        ctx.stall = true;
        ctx.temporaryData = new Stall(o == CokeOvenBlockEntity.PushOutcome.PUSHED ? PUSH_TICKS : OVERLOAD_TICKS);
    }

    @Override
    public void tick(MovementContext ctx) {
        if (ctx.temporaryData instanceof Stall s && --s.ticks <= 0) {
            ctx.temporaryData = null;
            ctx.stall = false;
        }
    }

    @Override
    public void stopMoving(MovementContext ctx) {
        ctx.temporaryData = null;
    }

    /** Куда смотрит штанга в мире (контрапция может быть повёрнута). */
    static Direction worldFacing(MovementContext ctx) {
        Vec3 local = Vec3.atLowerCornerOf(ctx.state.getValue(BlockStateProperties.HORIZONTAL_FACING).getNormal());
        Vec3 world = ctx.rotation == null ? local : ctx.rotation.apply(local);
        return Direction.getNearest(world.x, 0, world.z);
    }
}
